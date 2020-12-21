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
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingEscapingDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.areAllPrintDirectivesStreamable;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constantNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralRemainderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.StringType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.objectweb.asm.Label;

/**
 * A helper for compiling {@link MsgNode messages}.
 *
 * <p>The strategy is to mostly rely on a runtime support library in {@code JbcsrcRuntime} to handle
 * actual message formatting so this class is responsible for:
 *
 * <ul>
 *   <li>Stashing a default {@code ImmutableList<SoyMsgPart>} in a static field to handle missing
 *       translations
 *   <li>performing lookup from the RenderContext to get the translation
 *   <li>generating code calculate placeholder values
 * </ul>
 */
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
  private static final ConstructorRef SOY_MSG_PLURAL_CASE_SPEC_LONG =
      ConstructorRef.create(SoyMsgPluralCaseSpec.class, long.class);

  private static Statement exitLoggableElement(
      ExpressionCompiler exprCompiler, AppendableExpression appendable, DetachState detachState) {
    return appendable.exitLoggableElement().toStatement();
  }

  /**
   * A helper interface that allows the MsgCompiler to interact with the SoyNodeCompiler in a
   * limited way.
   */
  interface PlaceholderCompiler {
    /**
     * Compiles the expression to a {@link SoyValueProvider} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Expression compileToSoyValueProvider(ExprRootNode node, ExpressionDetacher detacher);

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
  private final TemplateParameterLookup parameterLookup;
  private final FieldManager fields;
  private final AppendableExpression appendableExpression;
  private final PlaceholderCompiler placeholderCompiler;

  MsgCompiler(
      Expression thisVar,
      DetachState detachState,
      TemplateParameterLookup parameterLookup,
      FieldManager fields,
      AppendableExpression appendableExpression,
      PlaceholderCompiler placeholderCompiler) {
    this.thisVar = checkNotNull(thisVar);
    this.detachState = checkNotNull(detachState);
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
        msg.getAlternateId().isPresent()
            ? parameterLookup
                .getRenderContext()
                .getSoyMsgPartsWithAlternateId(
                    partsAndId.id, soyMsgDefaultParts, msg.getAlternateId().getAsLong())
            : parameterLookup.getRenderContext().getSoyMsgParts(partsAndId.id, soyMsgDefaultParts);
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
    return printMsg.withSourceLocation(msg.getSourceLocation());
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

  private Expression partsToPartsList(ImmutableList<SoyMsgPart> parts) {
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
        if (item.spec().getType() == SoyMsgPluralCaseSpec.Type.EXPLICIT) {
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
            (msg.getEscapingMode() == EscapingMode.ESCAPE_HTML
                    ? MethodRef.HANDLE_BASIC_TRANSLATION_AND_ESCAPE_HTML
                    : MethodRef.HANDLE_BASIC_TRANSLATION)
                .invoke(soyMsgParts));
    // Note: there is no point in trying to stream here, since we are starting with a constant
    // string.
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
    Label reattachPoint = new Label();
    ConstructorRef cstruct =
        msg.isPlrselMsg() ? ConstructorRef.PLRSEL_MSG_RENDERER : ConstructorRef.MSG_RENDERER;
    Expression renderer =
        cstruct.construct(
            constant(partsAndId.id),
            soyMsgParts,
            locale,
            constant(msg.getVarNameToRepNodeMap().size()),
            constant(msg.getEscapingMode() == EscapingMode.ESCAPE_HTML));
    for (Map.Entry<String, MsgSubstUnitNode> entry : msg.getVarNameToRepNodeMap().entrySet()) {
      String phName = entry.getKey();
      PlaceholderAndEndTag placeholder =
          compilePlaceholder(
              msg, phName, entry.getValue(), detachState.createExpressionDetacher(reattachPoint));
      if (placeholder.endTagToMatch().isPresent()) {
        renderer =
            renderer.invoke(
                MethodRef.MSG_RENDERER_SET_PLACEHOLDER_AND_ORDERING,
                constant(phName),
                placeholder.expression(),
                constant(placeholder.endTagToMatch().get()));
      } else {
        renderer =
            renderer.invoke(
                MethodRef.MSG_RENDERER_SET_PLACEHOLDER, constant(phName), placeholder.expression());
      }
    }

    Statement initMsgRenderer =
        fields.getCurrentRenderee().putInstanceField(thisVar, renderer).labelStart(reattachPoint);

    Statement render;
    if (areAllPrintDirectivesStreamable(escapingDirectives)) {
      Statement initAppendable = Statement.NULL_STATEMENT;
      Statement clearAppendable = Statement.NULL_STATEMENT;
      Expression appendable = appendableExpression;
      if (!escapingDirectives.isEmpty()) {
        PrintDirectives.AppendableAndFlushBuffersDepth wrappedAppendable =
            applyStreamingEscapingDirectives(
                escapingDirectives, appendable, parameterLookup.getPluginContext());
        FieldRef currentAppendableField = fields.getCurrentAppendable();
        initAppendable =
            currentAppendableField.putInstanceField(thisVar, wrappedAppendable.appendable());
        appendable = currentAppendableField.accessor(thisVar).asNonNullable();
        clearAppendable =
            currentAppendableField.putInstanceField(
                thisVar, constantNull(LOGGING_ADVISING_APPENDABLE_TYPE));
        if (wrappedAppendable.flushBuffersDepth() >= 0) {
          clearAppendable =
              Statement.concat(
                  AppendableExpression.forExpression(appendable)
                      .flushBuffers(wrappedAppendable.flushBuffersDepth()),
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
                          /* isLast */ constant(true))),
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

  @AutoValue
  abstract static class PlaceholderAndEndTag {
    static PlaceholderAndEndTag create(Expression placeholderValue) {
      return create(placeholderValue, Optional.empty());
    }

    static PlaceholderAndEndTag create(
        Expression placeholderValue, Optional<String> matchingEndTag) {
      return new AutoValue_MsgCompiler_PlaceholderAndEndTag(placeholderValue, matchingEndTag);
    }

    abstract Expression expression();

    abstract Optional<String> endTagToMatch();
  }

  private PlaceholderAndEndTag compilePlaceholder(
      MsgNode originalMsg,
      String placeholderName,
      MsgSubstUnitNode substUnitNode,
      ExpressionDetacher expressionDetacher) {
    if (substUnitNode instanceof MsgPluralNode) {
      return PlaceholderAndEndTag.create(
          placeholderCompiler.compileToSoyValueProvider(
              ((MsgPluralNode) substUnitNode).getExpr(), expressionDetacher));
    } else if (substUnitNode instanceof MsgSelectNode) {
      return PlaceholderAndEndTag.create(
          placeholderCompiler.compileToSoyValueProvider(
              ((MsgSelectNode) substUnitNode).getExpr(), expressionDetacher));
    } else if (substUnitNode instanceof MsgPlaceholderNode) {
      return compileNormalPlaceholder(
          originalMsg, placeholderName, (MsgPlaceholderNode) substUnitNode);
    } else {
      throw new AssertionError("unexpected child: " + substUnitNode);
    }
  }

  private PlaceholderAndEndTag compileNormalPlaceholder(
      MsgNode originalMsg, String placeholderName, MsgPlaceholderNode placeholder) {
    if (placeholder.numChildren() == 0) {
      // special case for when a placeholder magically compiles to the empty string
      // the CombineConsecutiveRawTextNodesPass will just delete it, so we end up with an empty
      // placeholder.
      return PlaceholderAndEndTag.create(FieldRef.EMPTY_STRING_DATA.accessor());
    }
    ExtraCodeCompiler prefix = ExtraCodeCompiler.NO_OP;
    ExtraCodeCompiler suffix = ExtraCodeCompiler.NO_OP;
    Optional<String> closeTagPlaceholderNameToMatch = Optional.empty();
    final StandaloneNode initialNode = placeholder.getChild(0);
    if (initialNode instanceof MsgHtmlTagNode
        && placeholder.getParent().getKind() == Kind.VE_LOG_NODE) {
      final VeLogNode veLogNode = (VeLogNode) placeholder.getParent();
      // NOTE: we can't call getOpenTagNode or getCloseTagNode since they have been desugared by
      // now and don't exist.  Instead we rely on the fact that earlier compile passes have
      // validated the log structure and know that if this is the first or last element in velog
      // then needs to be instrumented.
      int childIndex = veLogNode.getChildIndex(placeholder);
      if (childIndex == 0) {
        // this corresponds to the open tag node.  We need to prefix the placeholder with an
        // enterLoggableElement(LogStatement) call.  This may also require additional detaches.
        prefix =
            new ExtraCodeCompiler() {
              @Override
              public Statement compile(
                  ExpressionCompiler exprCompiler,
                  AppendableExpression appendable,
                  DetachState detachStateForExtraCodeCompiler) {
                // this is very similar to SoyNodeCompiler.visitVeLogNode but
                // 1. we don't have to worry about logonly
                // 2. we need to only generate 'half' of it
                Label restartPoint = new Label();
                Expression veData =
                    exprCompiler.compileSubExpression(
                        veLogNode.getVeDataExpression(),
                        detachStateForExtraCodeCompiler.createExpressionDetacher(restartPoint));
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
          // an ordering constraint
          suffix = MsgCompiler::exitLoggableElement;
        } else {
          // To ensure that close tag comes after the open tag we need to track this at runtime.
          closeTagPlaceholderNameToMatch = Optional.of(closeTagPlaceholderName);
        }
      } else if (childIndex == veLogNode.numChildren() - 1) {
        suffix = MsgCompiler::exitLoggableElement;
      } else {
        // this must be some other html tag within the velog statement, it is just a normal
        // placeholder. No suffix/prefix/closeTagPlaceholderNameToMatch is necessary.
      }
    }

    return PlaceholderAndEndTag.create(
        placeholderCompiler.compileToSoyValueProvider(placeholderName, initialNode, prefix, suffix),
        closeTagPlaceholderNameToMatch);
  }
}
