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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_STRING_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.ConstructorRef;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.MsgDefaultConstantFactory;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

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
  public static final Type IMMUTABLE_LIST_TYPE = Type.getType(ImmutableList.class);
  private static final Handle MESSAGE_FACTORY_HANDLE =
      MethodRef.create(
              MsgDefaultConstantFactory.class,
              "bootstrapMsgConstant",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class)
          .asHandle();

  private static final String MSG_DEFAULT_DESCRIPTOR =
      Type.getMethodDescriptor(IMMUTABLE_LIST_TYPE);

  /**
   * A helper interface that allows the MsgCompiler to interact with the SoyNodeCompiler in a
   * limited way.
   */
  interface PlaceholderCompiler {
    @AutoValue
    abstract static class Placeholder {
      static Placeholder create(Expression soyValueProvider, boolean requiresDetachLogicToResolve) {
        soyValueProvider.checkAssignableTo(SOY_VALUE_PROVIDER_TYPE);
        return new AutoValue_MsgCompiler_PlaceholderCompiler_Placeholder(
            soyValueProvider, requiresDetachLogicToResolve);
      }

      abstract Expression soyValueProvider();

      abstract boolean requiresDetachLogicToResolve();
    }
    /**
     * Compiles the expression to a {@link SoyValueProvider} valued expression.
     *
     * <p>If the node requires detach logic, it should use the given label as the reattach point.
     */
    Placeholder compile(ExprRootNode node, ExpressionDetacher detacher);

    /**
     * Compiles the given node to a statement that writes the result into the given appendable.
     *
     * <p>The statement is guaranteed to be written to a location with a stack depth of zero.
     */
    Placeholder compile(
        String phname, StandaloneNode node, ExtraCodeCompiler prefix, ExtraCodeCompiler suffix);
  }

  private final DetachState detachState;
  private final TemplateParameterLookup parameterLookup;
  private final TemplateVariableManager variableManager;
  private final AppendableExpression appendableExpression;
  private final PlaceholderCompiler placeholderCompiler;

  MsgCompiler(
      DetachState detachState,
      TemplateParameterLookup parameterLookup,
      TemplateVariableManager variableManager,
      AppendableExpression appendableExpression,
      PlaceholderCompiler placeholderCompiler) {
    this.detachState = checkNotNull(detachState);
    this.parameterLookup = checkNotNull(parameterLookup);
    this.variableManager = checkNotNull(variableManager);
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
    List<Object> constantParts = MsgDefaultConstantFactory.msgToPartsList(partsAndId.parts);
    return new Expression(
        IMMUTABLE_LIST_TYPE, Expression.Feature.NON_NULLABLE, Expression.Feature.CHEAP) {
      @Override
      protected void doGen(CodeBuilder cb) {
        cb.visitInvokeDynamicInsn(
            "default", MSG_DEFAULT_DESCRIPTOR, MESSAGE_FACTORY_HANDLE, constantParts.toArray());
      }
    };
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

  // TODO(lukes): this is really similar to SoyNodeCompiler.visitPrintNode

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
    boolean requiresDetachLogic = false;
    for (Map.Entry<String, MsgSubstUnitNode> entry : msg.getVarNameToRepNodeMap().entrySet()) {
      String phName = entry.getKey();
      PlaceholderAndEndTag placeholder =
          compilePlaceholder(
              msg, phName, entry.getValue(), detachState.createExpressionDetacher(reattachPoint));
      requiresDetachLogic = requiresDetachLogic || placeholder.requiresDetachLogic();
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
    TemplateVariableManager.Scope scope = variableManager.enterScope();
    TemplateVariableManager.Variable msgRendererVar =
        scope.createSynthetic(
            SyntheticVarName.renderee(), renderer, TemplateVariableManager.SaveStrategy.STORE);
    Statement initMsgRenderer = msgRendererVar.initializer().labelStart(reattachPoint);

    Statement render;
    if (areAllPrintDirectivesStreamable(escapingDirectives)) {
      Statement initAppendable = Statement.NULL_STATEMENT;
      Statement clearAppendable = Statement.NULL_STATEMENT;
      AppendableExpression appendable = appendableExpression;
      if (!escapingDirectives.isEmpty()) {
        PrintDirectives.AppendableAndFlushBuffersDepth wrappedAppendable =
            applyStreamingEscapingDirectives(
                escapingDirectives, appendable, parameterLookup.getPluginContext());
        TemplateVariableManager.Variable appendableVar =
            scope.createSynthetic(
                SyntheticVarName.appendable(),
                wrappedAppendable.appendable(),
                TemplateVariableManager.SaveStrategy.STORE);
        initAppendable = appendableVar.initializer();
        appendable = AppendableExpression.forExpression(appendableVar.accessor());
        if (wrappedAppendable.flushBuffersDepth() >= 0) {
          clearAppendable = appendable.flushBuffers(wrappedAppendable.flushBuffersDepth());
        }
      }
      Expression callRenderAndResolve =
          msgRendererVar
              .accessor()
              .invoke(
                  MethodRef.SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE,
                  appendable,
                  // set the isLast field to true since we know this will only be rendered
                  // once.
                  /* isLast */ constant(true));
      render =
          Statement.concat(
              initAppendable,
              requiresDetachLogic
                  ? detachState.detachForRender(callRenderAndResolve)
                  : detachState.assertFullyRenderered(callRenderAndResolve),
              clearAppendable);
    } else {
      Label start = new Label();
      SoyExpression value =
          SoyExpression.forSoyValue(
              StringType.getInstance(),
              (requiresDetachLogic
                      ? detachState
                          .createExpressionDetacher(start)
                          .resolveSoyValueProvider(msgRendererVar.accessor())
                      : msgRendererVar.accessor().invoke(MethodRef.SOY_VALUE_PROVIDER_RESOLVE))
                  .checkedCast(SOY_STRING_TYPE));
      for (SoyPrintDirective directive : escapingDirectives) {
        value = parameterLookup.getRenderContext().applyPrintDirective(directive, value);
      }
      render =
          appendableExpression.appendString(value.unboxAsString()).toStatement().labelStart(start);
    }
    return Statement.concat(initMsgRenderer, render, scope.exitScope());
  }

  @AutoValue
  abstract static class PlaceholderAndEndTag {
    static PlaceholderAndEndTag create(PlaceholderCompiler.Placeholder placeholder) {
      return create(placeholder, Optional.empty());
    }

    static PlaceholderAndEndTag create(
        PlaceholderCompiler.Placeholder placeholder, Optional<String> matchingEndTag) {
      return new AutoValue_MsgCompiler_PlaceholderAndEndTag(
          placeholder.soyValueProvider(),
          placeholder.requiresDetachLogicToResolve(),
          matchingEndTag);
    }

    abstract Expression expression();

    abstract boolean requiresDetachLogic();

    abstract Optional<String> endTagToMatch();
  }

  private PlaceholderAndEndTag compilePlaceholder(
      MsgNode originalMsg,
      String placeholderName,
      MsgSubstUnitNode substUnitNode,
      ExpressionDetacher expressionDetacher) {
    if (substUnitNode instanceof MsgPluralNode) {
      return PlaceholderAndEndTag.create(
          placeholderCompiler.compile(
              ((MsgPluralNode) substUnitNode).getExpr(), expressionDetacher));
    } else if (substUnitNode instanceof MsgSelectNode) {
      return PlaceholderAndEndTag.create(
          placeholderCompiler.compile(
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
      return PlaceholderAndEndTag.create(
          PlaceholderCompiler.Placeholder.create(
              FieldRef.EMPTY_STRING_DATA.accessor(), /* requiresDetachLogicToResolve= */ false));
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
              public boolean requiresDetachLogic(TemplateAnalysis analysis) {
                return ExpressionCompiler.requiresDetach(analysis, veLogNode.getVeDataExpression());
              }

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
        placeholderCompiler.compile(placeholderName, initialNode, prefix, suffix),
        closeTagPlaceholderNameToMatch);
  }

  private static Statement exitLoggableElement(
      ExpressionCompiler exprCompiler, AppendableExpression appendable, DetachState detachState) {
    return appendable.exitLoggableElement().toStatement();
  }
}
