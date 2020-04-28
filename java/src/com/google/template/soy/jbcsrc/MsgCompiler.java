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
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingEscapingDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.areAllPrintDirectivesStreamable;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantNull;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective.Streamable.AppendableAndOptions;
import com.google.template.soy.jbcsrc.restricted.Statement;
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
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.StringType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.objectweb.asm.Label;

/**
 * A helper for compiling {@link MsgNode messages}.
 *
 * <p>The strategy is to mostly rely on a runtime support library in {@code JbcsrcRuntime} to handle
 * actual message formatting so this class is responsible for:
 *
 * <ul>
 *   <li>Stashing a default ImmutableList<SoyMsgPart> in a static field to handle missing
 *       translations
 *   <li>performing lookup from the RenderContext to get the translation
 *   <li>generating code calculate placeholder values
 * </ul>
 */
final class MsgCompiler {
  private static final ConstructorRef SOY_MSG_PLACEHOLDER_PART =
      ConstructorRef.create(SoyMsgPlaceholderPart.class, String.class, String.class);
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
  private static final ConstructorRef SOY_MSG_PLURAL_CASE_SPEC_LONG =
      ConstructorRef.create(SoyMsgPluralCaseSpec.class, long.class);

  private static final ExtraCodeCompiler EXIT_LOGGABLE_ELEMENT =
      new ExtraCodeCompiler() {
        @Override
        public Statement compile(ExpressionCompiler exprCompiler, AppendableExpression appendable) {
          return appendable.exitLoggableElement().toStatement();
        }
      };

  /**
   * A helper interface that allows the MsgCompiler to interact with the SoyNodeCompiler in a
   * limited way.
   */
  interface PlaceholderCompiler {
    /**
     * Compiles the expression to a {@link String} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToString(ExprRootNode node, Label reattachPoint);

    /**
     * Compiles the expression to an {@code NumberData} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToNumber(ExprRootNode node, Label reattachPoint);

    /**
     * Compiles the given node to a statement that writes the result into the given appendable.
     *
     * <p>The statement is guaranteed to be written to a location with a stack depth of zero.
     */
    Expression compileToSoyValueProvider(
        String phname, StandaloneNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix);
  }

  private final Expression thisVar;
  private final DetachState detachState;
  private final TemplateVariableManager variables;
  private final TemplateParameterLookup parameterLookup;
  private final FieldManager fields;
  private final AppendableExpression appendableExpression;
  private final PlaceholderCompiler placeholderCompiler;

  MsgCompiler(
      Expression thisVar,
      DetachState detachState,
      TemplateVariableManager variables,
      TemplateParameterLookup parameterLookup,
      FieldManager fields,
      AppendableExpression appendableExpression,
      PlaceholderCompiler placeholderCompiler) {
    this.thisVar = checkNotNull(thisVar);
    this.detachState = checkNotNull(detachState);
    this.variables = checkNotNull(variables);
    this.parameterLookup = checkNotNull(parameterLookup);
    this.fields = checkNotNull(fields);
    this.appendableExpression = checkNotNull(appendableExpression);
    this.placeholderCompiler = checkNotNull(placeholderCompiler);
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
      MsgPartsAndIds partsAndId, MsgNode msg, ImmutableList<SoyPrintDirective> escapingDirectives) {
    Expression soyMsgDefaultParts = compileDefaultMessagePartsConstant(partsAndId);
    Expression soyMsgParts =
        parameterLookup.getRenderContext().getSoyMsgParts(partsAndId.id, soyMsgDefaultParts);
    Statement printMsg;
    if (msg.isRawTextMsg()) {
      // Simplest case, just a static string translation
      printMsg = handleBasicTranslation(msg, escapingDirectives, soyMsgParts);
    } else {
      // String translation + placeholders
      printMsg =
          handleTranslationWithPlaceholders(
              msg,
              escapingDirectives,
              soyMsgParts,
              parameterLookup.getPluginContext().getULocale(),
              partsAndId);
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
    return fields
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
          constant(((SoyMsgPlaceholderPart) part).getPlaceholderName()), constantNull(STRING_TYPE));
    } else if (part instanceof SoyMsgPluralPart) {
      SoyMsgPluralPart pluralPart = (SoyMsgPluralPart) part;
      List<Expression> caseExprs = new ArrayList<>(pluralPart.getCases().size());
      for (Case<SoyMsgPluralCaseSpec> item : pluralPart.getCases()) {
        Expression spec;
        if (item.spec().getType() == Type.EXPLICIT) {
          spec = SOY_MSG_PLURAL_CASE_SPEC_LONG.construct(constant(item.spec().getExplicitValue()));
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
          constant(((SoyMsgRawTextPart) part).getRawText(), fields));
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
      MsgNode msg, List<SoyPrintDirective> escapingDirectives, Expression soyMsgParts) {
    // optimize for simple constant translations (very common)
    // this becomes: renderContext.getSoyMessge(<id>).getParts().get(0).getRawText()
    SoyExpression text =
        SoyExpression.forString(
            soyMsgParts
                .invoke(MethodRef.LIST_GET, constant(0))
                .checkedCast(SoyMsgRawTextPart.class)
                .invoke(MethodRef.SOY_MSG_RAW_TEXT_PART_GET_RAW_TEXT));
    // Note: there is no point in trying to stream here, since we are starting with a constant
    // string.
    if (msg.getEscapingMode() == EscapingMode.ESCAPE_HTML) {
      text = SoyExpression.forString(MethodRef.MSG_RENDERER_ESCAPE_HTML.invoke(text));
    }
    for (SoyPrintDirective directive : escapingDirectives) {
      text = parameterLookup.getRenderContext().applyPrintDirective(directive, text);
    }
    return appendableExpression.appendString(text.coerceToString()).toStatement();
  }

  /** Handles a complex message with placeholders. */
  private Statement handleTranslationWithPlaceholders(
      MsgNode msg,
      ImmutableList<SoyPrintDirective> escapingDirectives,
      Expression soyMsgParts,
      Expression locale,
      MsgPartsAndIds partsAndId) {
    // We need to render placeholders into a buffer and then pack them into a map to pass to
    // JbcSrcRuntime.MsgRenderer.

    Map<String, Function<Expression, Statement>> placeholderNameToPutStatement =
        new LinkedHashMap<>();
    putPlaceholdersIntoMap(msg, partsAndId.parts, placeholderNameToPutStatement);
    // sanity check
    checkState(!placeholderNameToPutStatement.isEmpty());
    ConstructorRef cstruct =
        msg.isPlrselMsg() ? ConstructorRef.PLRSEL_MSG_RENDERER : ConstructorRef.MSG_RENDERER;
    Statement initRendererStatement =
        fields
            .getCurrentRenderee()
            .putInstanceField(
                thisVar,
                cstruct.construct(
                    constant(partsAndId.id),
                    soyMsgParts,
                    locale,
                    constant(placeholderNameToPutStatement.size()),
                    constant(msg.getEscapingMode() == EscapingMode.ESCAPE_HTML)));
    List<Statement> initializationStatements = new ArrayList<>();
    initializationStatements.add(initRendererStatement);
    for (Function<Expression, Statement> fn : placeholderNameToPutStatement.values()) {
      initializationStatements.add(fn.apply(fields.getCurrentRenderee().accessor(thisVar)));
    }

    Statement initMsgRenderer = Statement.concat(initializationStatements);
    Statement render;
    if (areAllPrintDirectivesStreamable(escapingDirectives)) {
      Statement initAppendable = Statement.NULL_STATEMENT;
      Statement clearAppendable = Statement.NULL_STATEMENT;
      Expression appendable = appendableExpression;
      if (!escapingDirectives.isEmpty()) {
        AppendableAndOptions wrappedAppendable =
            applyStreamingEscapingDirectives(
                escapingDirectives, appendable, parameterLookup.getPluginContext(), variables);
        FieldRef currentAppendableField = fields.getCurrentAppendable();
        initAppendable =
            currentAppendableField.putInstanceField(thisVar, wrappedAppendable.appendable());
        appendable = currentAppendableField.accessor(thisVar);
        clearAppendable =
            currentAppendableField.putInstanceField(
                thisVar, constantNull(LOGGING_ADVISING_APPENDABLE_TYPE));
        if (wrappedAppendable.closeable()) {
          clearAppendable =
              Statement.concat(
                  appendableExpression
                      .checkedCast(BytecodeUtils.CLOSEABLE_TYPE)
                      .invokeVoid(MethodRef.CLOSEABLE_CLOSE),
                  clearAppendable);
        }
      }
      render =
          Statement.concat(
              initAppendable,
              detachState.detachForRender(
                  fields
                      .getCurrentRenderee()
                      .accessor(thisVar)
                      .invoke(
                          MethodRef.SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE,
                          appendable,
                          // set the isLast field to true since we know this will only be rendered
                          // once.
                          /* isLast=*/ constant(true))),
              clearAppendable);
    } else {
      Label start = new Label();
      SoyExpression value =
          SoyExpression.forSoyValue(
              StringType.getInstance(),
              detachState
                  .createExpressionDetacher(start)
                  .resolveSoyValueProvider(fields.getCurrentRenderee().accessor(thisVar))
                  .checkedCast(SOY_STRING_TYPE));
      for (SoyPrintDirective directive : escapingDirectives) {
        value = parameterLookup.getRenderContext().applyPrintDirective(directive, value);
      }
      render =
          appendableExpression.appendString(value.unboxAsString()).toStatement().labelStart(start);
    }
    return Statement.concat(
        initMsgRenderer,
        render,
        // clear the field
        fields
            .getCurrentRenderee()
            .putInstanceField(
                thisVar,
                BytecodeUtils.constantNull(ConstructorRef.MSG_RENDERER.instanceClass().type())));
  }

  /**
   * Adds a {@link Statement} to {@link Map#put} every msg placeholder, plural variable and select
   * case value into {@code mapExpression}
   */
  private void putPlaceholdersIntoMap(
      MsgNode originalMsg,
      Iterable<? extends SoyMsgPart> parts,
      Map<String, Function<Expression, Statement>> placeholderNameToPutStatement) {
    for (SoyMsgPart child : parts) {
      if (child instanceof SoyMsgRawTextPart || child instanceof SoyMsgPluralRemainderPart) {
        // raw text doesn't have placeholders and remainders use the same placeholder as plural they
        // are a member of.
        continue;
      }
      if (child instanceof SoyMsgPluralPart) {
        putPluralPartIntoMap(originalMsg, placeholderNameToPutStatement, (SoyMsgPluralPart) child);
      } else if (child instanceof SoyMsgSelectPart) {
        putSelectPartIntoMap(originalMsg, placeholderNameToPutStatement, (SoyMsgSelectPart) child);
      } else if (child instanceof SoyMsgPlaceholderPart) {
        putPlaceholderIntoMap(
            originalMsg, placeholderNameToPutStatement, (SoyMsgPlaceholderPart) child);
      } else {
        throw new AssertionError("unexpected child: " + child);
      }
    }
  }

  private void putSelectPartIntoMap(
      MsgNode originalMsg,
      Map<String, Function<Expression, Statement>> placeholderNameToPutStatement,
      SoyMsgSelectPart select) {
    MsgSelectNode repSelectNode = originalMsg.getRepSelectNode(select.getSelectVarName());
    if (!placeholderNameToPutStatement.containsKey(select.getSelectVarName())) {
      Label reattachPoint = new Label();
      Expression value =
          placeholderCompiler.compileToString(repSelectNode.getExpr(), reattachPoint);
      placeholderNameToPutStatement.put(
          select.getSelectVarName(),
          putToMapFunction(select.getSelectVarName(), value, reattachPoint));
    }
    // Recursively visit select cases
    for (Case<String> caseOrDefault : select.getCases()) {
      putPlaceholdersIntoMap(originalMsg, caseOrDefault.parts(), placeholderNameToPutStatement);
    }
  }

  private void putPluralPartIntoMap(
      MsgNode originalMsg,
      Map<String, Function<Expression, Statement>> placeholderNameToPutStatement,
      SoyMsgPluralPart plural) {
    MsgPluralNode repPluralNode = originalMsg.getRepPluralNode(plural.getPluralVarName());
    if (!placeholderNameToPutStatement.containsKey(plural.getPluralVarName())) {
      Label reattachPoint = new Label();
      Expression value =
          placeholderCompiler.compileToNumber(repPluralNode.getExpr(), reattachPoint);
      placeholderNameToPutStatement.put(
          plural.getPluralVarName(),
          putToMapFunction(plural.getPluralVarName(), value, reattachPoint));
    }
    // Recursively visit plural cases
    for (Case<SoyMsgPluralCaseSpec> caseOrDefault : plural.getCases()) {
      putPlaceholdersIntoMap(originalMsg, caseOrDefault.parts(), placeholderNameToPutStatement);
    }
  }

  private void putPlaceholderIntoMap(
      MsgNode originalMsg,
      Map<String, Function<Expression, Statement>> placeholderNameToPutStatement,
      SoyMsgPlaceholderPart placeholder)
      throws AssertionError {
    final String placeholderName = placeholder.getPlaceholderName();
    if (!placeholderNameToPutStatement.containsKey(placeholderName)) {
      MsgPlaceholderNode repPlaceholderNode =
          originalMsg.getRepPlaceholderNode(placeholder.getPlaceholderName());
      if (repPlaceholderNode.numChildren() == 0) {
        // special case for when a placeholder magically compiles to the empty string
        // the CombineConsecutiveRawTextNodesPass will just delete it, so we end up with an empty
        // placeholder.
        placeholderNameToPutStatement.put(
            placeholderName,
            putToMapFunction(placeholderName, FieldRef.EMPTY_STRING_DATA.accessor()));
        return;
      }
      final StandaloneNode initialNode = repPlaceholderNode.getChild(0);
      Function<Expression, Statement> putEntyInMap;
      if (initialNode instanceof MsgHtmlTagNode
          && repPlaceholderNode.getParent().getKind() == Kind.VE_LOG_NODE) {
        final VeLogNode veLogNode = (VeLogNode) repPlaceholderNode.getParent();
        // NOTE: we can't call getOpenTagNode or getCloseTagNode since they have been desugared by
        // now and don't exist.  Instead we rely on the fact that earlier compile passes have
        // validated the log structure and know that if this is the first or last element in velog
        // then needs to be instrumented.
        int childIndex = veLogNode.getChildIndex(repPlaceholderNode);

        if (childIndex == 0) {
          // this corresponds to the open tag node.  We need to prefix the placeholder with an
          // enterLoggableElement(LogStatement) call.  This may also require additional detaches.
          final ExtraCodeCompiler enterLoggableElement =
              new ExtraCodeCompiler() {
                @Override
                public Statement compile(
                    ExpressionCompiler exprCompiler, AppendableExpression appendable) {
                  // this is very similar to SoyNodeCompiler.visitVeLogNode but
                  // 1. we don't have to worry about logonly
                  // 2. we need to only generate 'half' of it
                  Label restartPoint = new Label();
                  Expression veData = exprCompiler.compile(veLogNode.getVeDataExpression());
                  return appendable
                      .enterLoggableElement(
                          MethodRef.CREATE_LOG_STATEMENT.invoke(
                              /*logonly*/ BytecodeUtils.constant(false), veData))
                      .toStatement()
                      .labelStart(restartPoint);
                }
              };
          // we need to get the name of the placeholder that closes this velog node.
          final String closeTagPlaceholderName =
              originalMsg
                  .getPlaceholder(
                      (MsgPlaceholderNode) veLogNode.getChild(veLogNode.numChildren() - 1))
                  .name();
          if (closeTagPlaceholderName.equals(placeholderName)) {
            // if they are the same this is a selfclosing tag e.g. <input> which is fine. Don't add
            // an order constraint
            putEntyInMap =
                putToMapFunction(
                    placeholderName,
                    placeholderCompiler.compileToSoyValueProvider(
                        placeholderName,
                        initialNode,
                        /* prefix= */ enterLoggableElement,
                        /* suffix= */ EXIT_LOGGABLE_ELEMENT));
          } else {
            // We need to call a different method in this case to add the ordering constraint
            // between the start and end tag.
            putEntyInMap =
                mapExpression ->
                    mapExpression
                        // need to cast since it is stored in a SoyValueProvider field
                        .checkedCast(ConstructorRef.MSG_RENDERER.instanceClass().type())
                        .invokeVoid(
                            MethodRef.MSG_RENDERER_SET_PLACEHOLDER_AND_ORDERING,
                            constant(placeholderName),
                            placeholderCompiler.compileToSoyValueProvider(
                                placeholderName,
                                initialNode,
                                /* prefix= */ enterLoggableElement,
                                /* suffix= */ ExtraCodeCompiler.NO_OP),
                            constant(closeTagPlaceholderName));
          }
        } else if (childIndex == veLogNode.numChildren() - 1) {
          putEntyInMap =
              putToMapFunction(
                  placeholderName,
                  placeholderCompiler.compileToSoyValueProvider(
                      placeholderName,
                      initialNode,
                      /* prefix= */ ExtraCodeCompiler.NO_OP,
                      /* suffix= */ EXIT_LOGGABLE_ELEMENT));
        } else {
          // this must be some other html tag within the velog statement, it is just a normal
          // placeholder
          putEntyInMap = addNodeToPlaceholderMap(placeholderName, initialNode);
        }
      } else if (initialNode instanceof MsgHtmlTagNode
          || initialNode instanceof CallNode
          || initialNode instanceof PrintNode
          || initialNode instanceof RawTextNode) {
        putEntyInMap = addNodeToPlaceholderMap(placeholderName, initialNode);
      } else {
        // the AST for MsgNodes guarantee that these are the only options
        throw new AssertionError("Unexpected child: " + initialNode.getClass());
      }
      placeholderNameToPutStatement.put(placeholderName, putEntyInMap);
    }
  }

  /**
   * Returns a statement that adds the content rendered by the call to the map.
   *
   * @param mapKey The map key
   * @param node The node
   */
  private Function<Expression, Statement> addNodeToPlaceholderMap(
      String mapKey, StandaloneNode node) {
    return putToMapFunction(
        mapKey,
        placeholderCompiler.compileToSoyValueProvider(
            mapKey,
            node,
            /* prefix= */ ExtraCodeCompiler.NO_OP,
            /* suffix= */ ExtraCodeCompiler.NO_OP));
  }

  private Function<Expression, Statement> putToMapFunction(
      String mapKey, Expression valueExpression) {
    return putToMapFunction(mapKey, valueExpression, null);
  }

  private Function<Expression, Statement> putToMapFunction(
      final String mapKey, final Expression valueExpression, @Nullable final Label labelStart) {
    return mapExpression -> {
      Statement statement =
          mapExpression
              // need to cast since it is stored in a SoyValueProvider field
              .checkedCast(ConstructorRef.MSG_RENDERER.instanceClass().type())
              .invokeVoid(
                  MethodRef.MSG_RENDERER_SET_PLACEHOLDER, constant(mapKey), valueExpression);
      if (labelStart != null) {
        statement = statement.labelStart(labelStart);
      }
      return statement;
    };
  }
}
