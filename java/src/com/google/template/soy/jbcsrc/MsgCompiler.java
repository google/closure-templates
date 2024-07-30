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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jbcsrc.PrintDirectives.applyStreamingEscapingDirectives;
import static com.google.template.soy.jbcsrc.PrintDirectives.areAllPrintDirectivesStreamable;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.STRING_DATA_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.MsgDefaultConstantFactory;
import com.google.template.soy.msgs.internal.MsgUtils.MsgPartsAndIds;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
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
import java.util.Optional;
import java.util.stream.Stream;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
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
  private static final Handle MESSAGE_FACTORY_HANDLE =
      MethodRef.createPure(
              MsgDefaultConstantFactory.class,
              "bootstrapMsgConstant",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              Object[].class)
          .asHandle();
  private static final Handle PLACEHOLDER_ORDERING_HANDLE =
      MethodRef.createPure(
              MsgDefaultConstantFactory.class,
              "placeholderOrdering",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              String[].class)
          .asHandle();
  private static final Handle PLACEHOLDER_INDEX_FUNCTION_HANDLE =
      MethodRef.createPure(
              MsgDefaultConstantFactory.class,
              "placeholderIndexFunction",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              String[].class)
          .asHandle();

  /**
   * A helper interface that allows the MsgCompiler to interact with the SoyNodeCompiler in a
   * limited way.
   */
  interface PlaceholderCompiler {
    @AutoValue
    abstract class Placeholder {
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
      MsgPartsAndIds partsAndId,
      MsgNode msg,
      ImmutableList<SoyJbcSrcPrintDirective> escapingDirectives,
      boolean isFallback) {

    Statement printMsg;
    if (msg.isRawTextMsg()) {
      Expression soyMsgDefaultText =
          isFallback
              ? null
              : constant(
                  ((SoyMsgRawTextPart) Iterables.getOnlyElement(partsAndId.parts)).getRawText());
      Expression soyMsgPart =
          msg.getAlternateId().isPresent()
              ? parameterLookup
                  .getRenderContext()
                  .getBasicSoyMsgPartWithAlternateId(
                      partsAndId.id, soyMsgDefaultText, msg.getAlternateId().getAsLong())
              : parameterLookup
                  .getRenderContext()
                  .getBasicSoyMsgPart(partsAndId.id, soyMsgDefaultText);
      // Simplest case, just a static string translation
      printMsg = handleBasicTranslation(msg, escapingDirectives, soyMsgPart);
    } else {
      Expression soyMsgDefaultParts =
          isFallback ? null : compileDefaultMessagePartsConstant(partsAndId);
      Expression soyMsgParts =
          msg.getAlternateId().isPresent()
              ? parameterLookup
                  .getRenderContext()
                  .getSoyMsgPartsWithAlternateId(
                      partsAndId.id, soyMsgDefaultParts, msg.getAlternateId().getAsLong())
              : parameterLookup
                  .getRenderContext()
                  .getSoyMsgParts(partsAndId.id, soyMsgDefaultParts);
      // String translation + placeholders
      printMsg =
          handleTranslationWithPlaceholders(
              msg,
              escapingDirectives,
              soyMsgParts,
              parameterLookup.getPluginContext().getULocale());
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
    ImmutableList<Object> constantParts =
        MsgDefaultConstantFactory.msgToPartsList(partsAndId.parts);
    return constant(
        BytecodeUtils.SOY_MSG_RAW_PARTS_TYPE,
        new ConstantDynamic(
            "defaultMsg",
            BytecodeUtils.SOY_MSG_RAW_PARTS_TYPE.getDescriptor(),
            MESSAGE_FACTORY_HANDLE,
            constantParts.toArray()),
        Features.of(Feature.CHEAP, Feature.NON_JAVA_NULLABLE));
  }

  /** Handles a translation consisting of a single raw text node. */
  private Statement handleBasicTranslation(
      MsgNode msg,
      ImmutableList<SoyJbcSrcPrintDirective> escapingDirectives,
      Expression soyMsgPart) {
    // optimize for simple constant translations (very common)
    // this becomes: renderContext.getSoyMessge(<id>).getParts().get(0).getRawText()
    SoyExpression text =
        SoyExpression.forString(
            msg.getEscapingMode() == EscapingMode.ESCAPE_HTML
                ? MethodRefs.HANDLE_BASIC_TRANSLATION_AND_ESCAPE_HTML.invoke(soyMsgPart)
                : soyMsgPart);
    // Note: there is no point in trying to stream here, since we are starting with a constant
    // string.
    for (SoyJbcSrcPrintDirective directive : escapingDirectives) {
      text = directive.applyForJbcSrc(parameterLookup.getPluginContext(), text, ImmutableList.of());
    }
    return appendableExpression.appendString(text.coerceToString()).toStatement();
  }

  private static SortedSetMultimap<String, String> getEndPlaceholderToStartPlaceholders(
      ImmutableList<PlaceholderAndEndTag> placeholders) {
    SortedSetMultimap<String, String> endPlaceholderToStartPlaceholders = TreeMultimap.create();
    for (var placeholder : placeholders) {
      if (placeholder.endTagToMatch().isPresent()) {
        endPlaceholderToStartPlaceholders.put(
            placeholder.endTagToMatch().get(), placeholder.placeholderName());
      }
    }
    return endPlaceholderToStartPlaceholders;
  }

  // TODO(lukes): this is really similar to SoyNodeCompiler.visitPrintNode

  /** Handles a complex message with placeholders. */
  private Statement handleTranslationWithPlaceholders(
      MsgNode msg,
      ImmutableList<SoyJbcSrcPrintDirective> escapingDirectives,
      Expression soyMsgParts,
      Expression locale) {
    Label reattachPoint = new Label();
    var detacher = detachState.createExpressionDetacher(reattachPoint);
    ImmutableList<String> placeholderNames =
        ImmutableList.sortedCopyOf(msg.getVarNameToRepNodeMap().keySet());
    ImmutableList<PlaceholderAndEndTag> placeholders =
        placeholderNames.stream()
            .map(
                name ->
                    compilePlaceholder(msg, name, msg.getVarNameToRepNodeMap().get(name), detacher))
            .collect(toImmutableList());

    var endPlaceholderToStartPlaceholders = getEndPlaceholderToStartPlaceholders(placeholders);
    MethodRef cstruct =
        msg.isPlrselMsg() ? MethodRefs.PLRSEL_MSG_RENDERER : MethodRefs.MSG_RENDERER;
    ImmutableList.Builder<Expression> ctorParams =
        ImmutableList.<Expression>builder()
            .add(soyMsgParts)
            // Pass the full set of placeholder names to list indexes as a constant map.
            .add(
                BytecodeUtils.constant(
                    BytecodeUtils.TO_INT_FUNCTION_TYPE,
                    new ConstantDynamic(
                        "placeholderIndexFunction",
                        BytecodeUtils.TO_INT_FUNCTION_TYPE.getDescriptor(),
                        PLACEHOLDER_INDEX_FUNCTION_HANDLE,
                        placeholderNames.toArray()),
                    Features.of(Feature.NON_JAVA_NULLABLE)))
            .add(
                BytecodeUtils.asImmutableList(
                        placeholders.stream()
                            .map(PlaceholderAndEndTag::expression)
                            .collect(toImmutableList()))
                    // If the placeholders are all constants (e.g. html tags), then we can promote
                    // the whole list to a constant.  This is somewhat common.
                    .toMaybeConstant())
            .add(constant(msg.getEscapingMode() == EscapingMode.ESCAPE_HTML));
    if (msg.isPlrselMsg()) {
      // PlrSel messages always have accept locale since they need it to resolve plural parts, so we
      // only need to pass it if we have a plural part.
      if (msg.hasPluralNode()) {
        ctorParams.add(locale);
      } else {
        ctorParams.add(BytecodeUtils.constantNull(BytecodeUtils.ULOCALE_TYPE));
      }
    }
    if (!endPlaceholderToStartPlaceholders.isEmpty()) {
      ctorParams.add(
          BytecodeUtils.constant(
              BytecodeUtils.IMMUTABLE_SET_MULTIMAP_TYPE,
              new ConstantDynamic(
                  "msgOrdering",
                  BytecodeUtils.IMMUTABLE_SET_MULTIMAP_TYPE.getDescriptor(),
                  PLACEHOLDER_ORDERING_HANDLE,
                  endPlaceholderToStartPlaceholders.entries().stream()
                      .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                      .toArray(Object[]::new)),
              Features.of(Feature.NON_JAVA_NULLABLE)));
    } else {
      ctorParams.add(BytecodeUtils.constantNull(BytecodeUtils.IMMUTABLE_SET_MULTIMAP_TYPE));
    }
    var renderer = cstruct.invoke(ctorParams.build());
    boolean requiresDetachLogic =
        placeholders.stream().anyMatch(PlaceholderAndEndTag::requiresDetachLogic);
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
              .invoke(MethodRefs.SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE, appendable);
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
                      : msgRendererVar.accessor().invoke(MethodRefs.SOY_VALUE_PROVIDER_RESOLVE))
                  .checkedCast(STRING_DATA_TYPE));
      for (var directive : escapingDirectives) {
        value =
            directive.applyForJbcSrc(parameterLookup.getPluginContext(), value, ImmutableList.of());
      }
      render =
          appendableExpression
              .appendString(value.unboxAsStringUnchecked())
              .toStatement()
              .labelStart(start);
    }
    return Statement.concat(initMsgRenderer, render, scope.exitScope());
  }

  @AutoValue
  abstract static class PlaceholderAndEndTag {
    static PlaceholderAndEndTag create(
        PlaceholderCompiler.Placeholder placeholder, String placeholderName) {
      return create(placeholder, placeholderName, Optional.empty());
    }

    static PlaceholderAndEndTag create(
        PlaceholderCompiler.Placeholder placeholder,
        String placeholderName,
        Optional<String> matchingEndTag) {
      ;
      return new AutoValue_MsgCompiler_PlaceholderAndEndTag(
          placeholder.soyValueProvider(),
          placeholderName,
          placeholder.requiresDetachLogicToResolve(),
          matchingEndTag);
    }

    abstract Expression expression();

    abstract String placeholderName();

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
              ((MsgPluralNode) substUnitNode).getExpr(), expressionDetacher),
          placeholderName);
    } else if (substUnitNode instanceof MsgSelectNode) {
      return PlaceholderAndEndTag.create(
          placeholderCompiler.compile(
              ((MsgSelectNode) substUnitNode).getExpr(), expressionDetacher),
          placeholderName);
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
              FieldRef.EMPTY_STRING_DATA.accessor(), /* requiresDetachLogicToResolve= */ false),
          placeholderName);
    }
    ExtraCodeCompiler prefix = ExtraCodeCompiler.NO_OP;
    ExtraCodeCompiler suffix = ExtraCodeCompiler.NO_OP;
    Optional<String> closeTagPlaceholderNameToMatch = Optional.empty();
    StandaloneNode initialNode = placeholder.getChild(0);
    if (initialNode instanceof MsgHtmlTagNode
        && placeholder.getParent().getKind() == Kind.VE_LOG_NODE) {
      VeLogNode veLogNode = (VeLogNode) placeholder.getParent();
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
                        MethodRefs.CREATE_LOG_STATEMENT.invoke(
                            /*logonly*/ BytecodeUtils.constant(false), veData))
                    .toStatement()
                    .labelStart(restartPoint);
              }
            };
        // we need to get the name of the placeholder that closes this velog node.
        String closeTagPlaceholderName =
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
        placeholderName,
        closeTagPlaceholderNameToMatch);
  }

  private static Statement exitLoggableElement(
      ExpressionCompiler exprCompiler, AppendableExpression appendable, DetachState detachState) {
    return appendable.exitLoggableElement().toStatement();
  }
}
