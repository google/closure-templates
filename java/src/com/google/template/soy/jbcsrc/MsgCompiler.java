/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.BytecodeUtils.constantNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec.Type;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Label;

/** A helper for compiling {@link MsgNode messages} */
final class MsgCompiler {
  private static final ConstructorRef SOY_MSG_PLACEHOLDER_PART =
      ConstructorRef.create(SoyMsgPlaceholderPart.class, String.class);
  private static final ConstructorRef SOY_MSG_PLURAL_REMAINDER_PART =
      ConstructorRef.create(SoyMsgPluralRemainderPart.class, String.class);
  private static final ConstructorRef SOY_MSG_PURAL_PART =
      ConstructorRef.create(SoyMsgPluralPart.class, String.class, int.class, Iterable.class);
  private static final ConstructorRef SOY_MSG_SELECT_PART =
      ConstructorRef.create(SoyMsgSelectPart.class, String.class, Iterable.class);
  private static final MethodRef SOY_MSG_RAW_TEXT_PART_OF =
      MethodRef.create(SoyMsgRawTextPart.class, "of", String.class);
  private static final MethodRef CASE_CREATE =
      MethodRef.create(Case.class, "create", Object.class, Iterable.class);
  private static final ConstructorRef SOY_MSG_PLURAL_CASE_SPEC_TYPE =
      ConstructorRef.create(SoyMsgPluralCaseSpec.class, SoyMsgPluralCaseSpec.Type.class);
  private static final ConstructorRef SOY_MSG_PLURAL_CASE_SPEC_INT =
      ConstructorRef.create(SoyMsgPluralCaseSpec.class, int.class);

  /**
   * A helper interface that allows the MsgCompiler to interact with the SoyNodeCompiler in a
   * limited way.
   */
  interface SoyNodeToStringCompiler {
    /**
     * Compiles the expression to a {@link String} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToString(ExprRootNode node, Label reattachPoint);

    /**
     * Compiles the expression to an {@code IntegerData} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToInt(ExprRootNode node, Label reattachPoint);

    /**
     * Compiles the print node to a {@link String} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToString(PrintNode node, Label reattachPoint);

    /**
     * Compiles the given CallNode to a statement that writes the result into the given appendable.
     *
     * <p>The statement is guaranteed to be written to a location with a stack depth of zero.
     */
    Statement compileToBuffer(CallNode call, AppendableExpression appendable);

    /**
     * Compiles the given MsgHtmlTagNode to a statement that writes the result into the given
     * appendable.
     *
     * <p>The statement is guaranteed to be written to a location with a stack depth of zero.
     */
    Statement compileToBuffer(MsgHtmlTagNode htmlTagNode, AppendableExpression appendable);
  }

  private final Expression thisVar;
  private final DetachState detachState;
  private final TemplateVariableManager variables;
  private final TemplateParameterLookup parameterLookup;
  private final AppendableExpression appendableExpression;
  private final SoyNodeToStringCompiler soyNodeCompiler;

  MsgCompiler(
      Expression thisVar,
      DetachState detachState,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      AppendableExpression appendableExpression,
      SoyNodeToStringCompiler soyNodeCompiler) {
    this.thisVar = checkNotNull(thisVar);
    this.detachState = checkNotNull(detachState);
    this.variables = checkNotNull(variables);
    this.parameterLookup = checkNotNull(parameterLookup);
    this.appendableExpression = checkNotNull(appendableExpression);
    this.soyNodeCompiler = checkNotNull(soyNodeCompiler);
  }

  /**
   * Compiles the given {@link MsgNode} to a statement with the given escaping directives applied.
   *
   * <p>The returned statement must be written to a location with a stack depth of zero, since
   * placeholder formatting may require detach logic.
   *
   * @param partsAndId The computed msg id
   * @param msg The msg node
   * @param escapingDirectives The set of escaping directives to apply.
   */
  Statement compileMessage(
      MsgPartsAndIds partsAndId, MsgNode msg, List<String> escapingDirectives) {
    Expression soyMsgDefaultParts = compileDefaultMessagePartsConstant(partsAndId);
    Expression soyMsgParts =
        parameterLookup
            .getRenderContext()
            .invoke(
                MethodRef.RENDER_CONTEXT_GET_SOY_MSG_PARTS,
                constant(partsAndId.id),
                soyMsgDefaultParts);
    Statement printMsg;
    if (msg.isRawTextMsg()) {
      // Simplest case, just a static string translation
      printMsg = handleBasicTranslation(escapingDirectives, soyMsgParts);
    } else {
      // String translation + placeholders
      printMsg =
          handleTranslationWithPlaceholders(
              msg,
              escapingDirectives,
              soyMsgParts,
              parameterLookup.getRenderContext().invoke(MethodRef.RENDER_CONTEXT_GET_LOCALE),
              partsAndId.parts);
    }
    return Statement.concat(
        printMsg.withSourceLocation(msg.getSourceLocation()),
        detachState.detachLimited(appendableExpression));
  }

  /**
   * Returns an expression that evaluates to a constant {@code ImmutableList<SoyMsgPart>} used as
   * the default message for when translations don't exist.
   *
   * <p>For each msg we generate a static final field that holds an {@code
   * ImmutableList<SoyMsgPart>} which means we have to go through the somewhat awkward process of
   * generating code to construct objects we have at compile time. We could do something like use
   * java serialization, but just invoking the SoyMsgPart constructors isn't too hard.
   */
  private Expression compileDefaultMessagePartsConstant(MsgPartsAndIds partsAndId) {
    return variables
        .addStaticField("msg_parts_" + partsAndId.id, partsToPartsList(partsAndId.parts))
        .accessor();
  }

  private Expression partsToPartsList(ImmutableList<SoyMsgPart> parts) throws AssertionError {
    List<Expression> partsExprs = new ArrayList<>(parts.size());
    for (SoyMsgPart part : parts) {
      partsExprs.add(partToPartExpression(part));
    }
    // ensure that the runtime type is immutablelist, ensures monomorphism
    return BytecodeUtils.asImmutableList(partsExprs);
  }

  /** Returns an {@link Expression} that evaluates to an equivalent SoyMsgPart as the argument. */
  private Expression partToPartExpression(SoyMsgPart part) {
    if (part instanceof SoyMsgPlaceholderPart) {
      return SOY_MSG_PLACEHOLDER_PART.construct(
          constant(((SoyMsgPlaceholderPart) part).getPlaceholderName()));
    } else if (part instanceof SoyMsgPluralPart) {
      SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) part;
      List<Expression> caseExprs = new ArrayList<>(pluralPart.getCases().size());
      for (Case<SoyMsgPluralCaseSpec> item : pluralPart.getCases()) {
        Expression spec;
        if (item.spec().getType() == Type.EXPLICIT) {
          spec = SOY_MSG_PLURAL_CASE_SPEC_INT.construct(constant(item.spec().getExplicitValue()));
        } else {
          spec =
              SOY_MSG_PLURAL_CASE_SPEC_TYPE.construct(
                  FieldRef.enumReference(item.spec().getType()).accessor());
        }
        caseExprs.add(CASE_CREATE.invoke(spec, partsToPartsList(item.parts())));
      }
      return SOY_MSG_PURAL_PART.construct(
          constant(pluralPart.getPluralVarName()),
          constant(pluralPart.getOffset()),
          BytecodeUtils.asList(caseExprs));
    } else if (part instanceof SoyMsgPluralRemainderPart) {
      return SOY_MSG_PLURAL_REMAINDER_PART.construct(
          constant(((SoyMsgPluralRemainderPart) part).getPluralVarName()));
    } else if (part instanceof SoyMsgRawTextPart) {
      return SOY_MSG_RAW_TEXT_PART_OF.invoke(
          constant(((SoyMsgRawTextPart) part).getRawText(), variables));
    } else if (part instanceof SoyMsgSelectPart) {
      SoyMsgSelectPart selectPart = (SoyMsgSelectPart) part;
      List<Expression> caseExprs = new ArrayList<>(selectPart.getCases().size());
      for (Case<String> item : selectPart.getCases()) {
        caseExprs.add(
            CASE_CREATE.invoke(
                item.spec() == null ? constantNull(STRING_TYPE) : constant(item.spec()),
                partsToPartsList(item.parts())));
      }
      return SOY_MSG_SELECT_PART.construct(
          constant(selectPart.getSelectVarName()), BytecodeUtils.asList(caseExprs));
    } else {
      throw new AssertionError("unrecognized part: " + part);
    }
  }

  /** Handles a translation consisting of a single raw text node. */
  private Statement handleBasicTranslation(
      List<String> escapingDirectives, Expression soyMsgParts) {
    // optimize for simple constant translations (very common)
    // this becomes: renderContext.getSoyMessge(<id>).getParts().get(0).getRawText()
    SoyExpression text =
        SoyExpression.forString(
            soyMsgParts
                .invoke(MethodRef.LIST_GET, constant(0))
                .checkedCast(SoyMsgRawTextPart.class)
                .invoke(MethodRef.SOY_MSG_RAW_TEXT_PART_GET_RAW_TEXT));
    for (String directive : escapingDirectives) {
      text = text.applyPrintDirective(parameterLookup.getRenderContext(), directive);
    }
    return appendableExpression.appendString(text.coerceToString()).toStatement();
  }

  /** Handles a complex message with placeholders. */
  private Statement handleTranslationWithPlaceholders(
      MsgNode msg,
      List<String> escapingDirectives,
      Expression soyMsgParts,
      Expression locale,
      ImmutableList<SoyMsgPart> parts) {
    // We need to render placeholders into a buffer and then pack them into a map to pass to
    // Runtime.renderSoyMsgWithPlaceholders.
    Expression placeholderMap = variables.getMsgPlaceholderMapField().accessor(thisVar);
    Map<String, Statement> placeholderNameToPutStatement = new LinkedHashMap<>();
    putPlaceholdersIntoMap(placeholderMap, msg, parts, placeholderNameToPutStatement);
    // sanity check
    checkState(!placeholderNameToPutStatement.isEmpty());
    variables.setMsgPlaceholderMapMinSize(placeholderNameToPutStatement.size());
    Statement populateMap = Statement.concat(placeholderNameToPutStatement.values());
    Statement clearMap = placeholderMap.invokeVoid(MethodRef.LINKED_HASH_MAP_CLEAR);
    Statement render;
    if (escapingDirectives.isEmpty()) {
      render =
          MethodRef.RUNTIME_RENDER_SOY_MSG_PARTS_WITH_PLACEHOLDERS.invokeVoid(
              soyMsgParts, locale, placeholderMap, appendableExpression);
    } else {
      // render into the handy buffer we already have!
      Statement renderToBuffer =
          MethodRef.RUNTIME_RENDER_SOY_MSG_PARTS_WITH_PLACEHOLDERS.invokeVoid(
              soyMsgParts, locale, placeholderMap, tempBuffer());
      // N.B. the type here is always 'string'
      SoyExpression value =
          SoyExpression.forString(
              tempBuffer().invoke(MethodRef.ADVISING_STRING_BUILDER_GET_AND_CLEAR));
      for (String directive : escapingDirectives) {
        value = value.applyPrintDirective(parameterLookup.getRenderContext(), directive);
      }
      render =
          Statement.concat(
              renderToBuffer,
              appendableExpression.appendString(value.coerceToString()).toStatement());
    }
    Statement detach = detachState.detachLimited(appendableExpression);
    return Statement.concat(populateMap, render, clearMap, detach)
        .withSourceLocation(msg.getSourceLocation());
  }

  /**
   * Adds a {@link Statement} to {@link Map#put} every msg placeholder, plural variable and select
   * case value into {@code mapExpression}
   */
  private void putPlaceholdersIntoMap(
      Expression mapExpression,
      MsgNode originalMsg,
      Iterable<? extends SoyMsgPart> parts,
      Map<String, Statement> placeholderNameToPutStatement) {
    for (SoyMsgPart child : parts) {
      if (child instanceof SoyMsgRawTextPart || child instanceof SoyMsgPluralRemainderPart) {
        // raw text doesn't have placeholders and remainders use the same placeholder as plural they
        // are a member of.
        continue;
      }
      if (child instanceof SoyMsgPluralPart) {
        putPluralPartIntoMap(
            mapExpression, originalMsg, placeholderNameToPutStatement, (SoyMsgPluralPart) child);
      } else if (child instanceof SoyMsgSelectPart) {
        putSelectPartIntoMap(
            mapExpression, originalMsg, placeholderNameToPutStatement, (SoyMsgSelectPart) child);
      } else if (child instanceof SoyMsgPlaceholderPart) {
        putPlaceholderIntoMap(
            mapExpression,
            originalMsg,
            placeholderNameToPutStatement,
            (SoyMsgPlaceholderPart) child);
      } else {
        throw new AssertionError("unexpected child: " + child);
      }
    }
  }

  private void putSelectPartIntoMap(
      Expression mapExpression,
      MsgNode originalMsg,
      Map<String, Statement> placeholderNameToPutStatement,
      SoyMsgSelectPart select) {
    MsgSelectNode repSelectNode = originalMsg.getRepSelectNode(select.getSelectVarName());
    if (!placeholderNameToPutStatement.containsKey(select.getSelectVarName())) {
      Label reattachPoint = new Label();
      Expression value = soyNodeCompiler.compileToString(repSelectNode.getExpr(), reattachPoint);
      placeholderNameToPutStatement.put(
          select.getSelectVarName(),
          putToMap(mapExpression, select.getSelectVarName(), value).labelStart(reattachPoint));
    }
    // Recursively visit select cases
    for (Case<String> caseOrDefault : select.getCases()) {
      putPlaceholdersIntoMap(
          mapExpression, originalMsg, caseOrDefault.parts(), placeholderNameToPutStatement);
    }
  }

  private void putPluralPartIntoMap(
      Expression mapExpression,
      MsgNode originalMsg,
      Map<String, Statement> placeholderNameToPutStatement,
      SoyMsgPluralPart plural) {
    MsgPluralNode repPluralNode = originalMsg.getRepPluralNode(plural.getPluralVarName());
    if (!placeholderNameToPutStatement.containsKey(plural.getPluralVarName())) {
      Label reattachPoint = new Label();
      Expression value = soyNodeCompiler.compileToInt(repPluralNode.getExpr(), reattachPoint);
      placeholderNameToPutStatement.put(
          plural.getPluralVarName(),
          putToMap(mapExpression, plural.getPluralVarName(), value)
              .labelStart(reattachPoint)
              .withSourceLocation(repPluralNode.getSourceLocation()));
    }
    // Recursively visit plural cases
    for (Case<SoyMsgPluralCaseSpec> caseOrDefault : plural.getCases()) {
      putPlaceholdersIntoMap(
          mapExpression, originalMsg, caseOrDefault.parts(), placeholderNameToPutStatement);
    }
  }

  private void putPlaceholderIntoMap(
      Expression mapExpression,
      MsgNode originalMsg,
      Map<String, Statement> placeholderNameToPutStatement,
      SoyMsgPlaceholderPart placeholder)
      throws AssertionError {
    String placeholderName = placeholder.getPlaceholderName();
    if (!placeholderNameToPutStatement.containsKey(placeholderName)) {
      MsgPlaceholderNode repPlaceholderNode =
          originalMsg.getRepPlaceholderNode(placeholder.getPlaceholderName());
      if (repPlaceholderNode.numChildren() == 0) {
        throw new IllegalStateException("empty rep node for: " + placeholderName);
      }
      StandaloneNode initialNode = repPlaceholderNode.getChild(0);
      Statement putEntyInMap;
      if (initialNode instanceof MsgHtmlTagNode) {
        putEntyInMap =
            addHtmlTagNodeToPlaceholderMap(
                mapExpression, placeholderName, (MsgHtmlTagNode) initialNode);
      } else if (initialNode instanceof CallNode) {
        putEntyInMap =
            addCallNodeToPlaceholderMap(mapExpression, placeholderName, (CallNode) initialNode);
      } else if (initialNode instanceof PrintNode) {
        putEntyInMap =
            addPrintNodeToPlaceholderMap(mapExpression, placeholderName, (PrintNode) initialNode);
      } else if (initialNode instanceof RawTextNode) {
        putEntyInMap =
            addRawTextNodeToPlaceholderMap(
                mapExpression, placeholderName, (RawTextNode) initialNode);
      } else {
        // the AST for MsgNodes guarantee that these are the only options
        throw new AssertionError("Unexpected child: " + initialNode.getClass());
      }
      placeholderNameToPutStatement.put(
          placeholder.getPlaceholderName(),
          putEntyInMap.withSourceLocation(repPlaceholderNode.getSourceLocation()));
    }
  }

  /**
   * Returns a statement that adds the content of the raw text node to the map.
   *
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param rawText The node
   */
  private Statement addRawTextNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, RawTextNode rawText) {
    return mapExpression
        .invoke(
            MethodRef.LINKED_HASH_MAP_PUT,
            constant(mapKey),
            constant(rawText.getRawText(), variables))
        .toStatement()
        .withSourceLocation(rawText.getSourceLocation());
  }

  /**
   * Returns a statement that adds the content of the node to the map.
   *
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param htmlTagNode The node
   */
  private Statement addHtmlTagNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, MsgHtmlTagNode htmlTagNode) {
    Optional<String> rawText = tryGetRawTextContent(htmlTagNode);
    Statement putStatement;
    if (rawText.isPresent()) {
      putStatement =
          mapExpression
              .invoke(MethodRef.LINKED_HASH_MAP_PUT, constant(mapKey), constant(rawText.get()))
              .toStatement();
    } else {
      Statement renderIntoBuffer = soyNodeCompiler.compileToBuffer(htmlTagNode, tempBuffer());
      Statement putBuffer = putBufferIntoMapForPlaceholder(mapExpression, mapKey);
      putStatement = Statement.concat(renderIntoBuffer, putBuffer);
    }
    return putStatement.withSourceLocation(htmlTagNode.getSourceLocation());
  }

  /**
   * Returns a statement that adds the content rendered by the call to the map.
   *
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param callNode The node
   */
  private Statement addCallNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, CallNode callNode) {
    Statement renderIntoBuffer = soyNodeCompiler.compileToBuffer(callNode, tempBuffer());
    Statement putBuffer = putBufferIntoMapForPlaceholder(mapExpression, mapKey);
    return Statement.concat(renderIntoBuffer, putBuffer)
        .withSourceLocation(callNode.getSourceLocation());
  }

  /**
   * Returns a statement that adds the content rendered by the call to the map.
   *
   * @param mapExpression The map to put the new entry in
   * @param mapKey The map key
   * @param printNode The node
   */
  private Statement addPrintNodeToPlaceholderMap(
      Expression mapExpression, String mapKey, PrintNode printNode) {
    // This is much like the escaping path of visitPrintNode but somewhat simpler because our
    // ultimate target is a string rather than putting bytes on the output stream.
    Label reattachPoint = new Label();
    Expression compileToString = soyNodeCompiler.compileToString(printNode, reattachPoint);
    return putToMap(mapExpression, mapKey, compileToString)
        .labelStart(reattachPoint)
        .withSourceLocation(printNode.getSourceLocation());
  }

  private Statement putToMap(Expression mapExpression, String mapKey, Expression valueExpression) {
    return mapExpression
        .invoke(MethodRef.LINKED_HASH_MAP_PUT, constant(mapKey), valueExpression)
        .toStatement();
  }

  private AppendableExpression tempBuffer() {
    return AppendableExpression.forStringBuilder(variables.getTempBufferField().accessor(thisVar));
  }

  private Statement putBufferIntoMapForPlaceholder(Expression mapExpression, String mapKey) {
    return mapExpression
        .invoke(
            MethodRef.LINKED_HASH_MAP_PUT,
            constant(mapKey),
            tempBuffer().invoke(MethodRef.ADVISING_STRING_BUILDER_GET_AND_CLEAR))
        .toStatement();
  }

  private Optional<String> tryGetRawTextContent(ParentSoyNode<?> initialNode) {
    if (initialNode.numChildren() == 1 && initialNode.getChild(0) instanceof RawTextNode) {
      return Optional.of(((RawTextNode) initialNode.getChild(0)).getRawText());
    }
    return Optional.absent();
  }
}
