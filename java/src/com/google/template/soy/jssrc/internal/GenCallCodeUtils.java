/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.Expressions.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.Expressions.dottedIdNoRequire;
import static com.google.template.soy.jssrc.dsl.Expressions.fromExpr;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ASSIGN_DEFAULTS;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_GET_DELEGATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.createHtmlOutputBufferFunction;
import static com.google.template.soy.jssrc.internal.JsRuntime.createNodeBuilderFunction;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunction;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.TemplateLiteralNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.Id;
import com.google.template.soy.jssrc.dsl.Return;
import com.google.template.soy.jssrc.dsl.TryCatch;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.ModernSoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.TemplateMetadata;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import com.google.template.soy.types.TemplateType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/** Generates JS code for {call}s and {delcall}s. */
public class GenCallCodeUtils {

  /**
   * Returns true if the given template has a positional signature where all explicit {@code @param}
   * declarations are exploded into explicit parameters.
   */
  static boolean hasPositionalSignature(TemplateMetadata metadata) {
    return hasPositionalSignature(metadata.getTemplateType());
  }

  public static boolean hasPositionalSignature(TemplateType type) {
    // This signature style is not possible to do if there are indirect calls, since those require
    // the whole `opt_data` parameter
    // If there are no parameters, then there is no value in exploding into multiple functions
    // If the template is a Soy element, then we also need the `opt_data` object.
    return type.getDataAllCallSituations().isEmpty()
        && !type.getActualParameters().isEmpty()
        // only basic templates are supported for now.
        // deltemplates require the object style to support the relatively weak type checking we
        // perform on them.  elements could be supported with some changes to the base class.
        && type.getTemplateKind() == TemplateType.TemplateKind.BASIC
        // Modifiable/modifies templates cannot use positional params, because we don't know if any
        // associated `modifies` templates will use data="all" and the signatures must all match.
        && !type.isModifiable()
        && !type.isModifying();
  }

  private final VisitorsState state;

  /** Instance of DelTemplateNamer to use. */
  private final DelTemplateNamer delTemplateNamer;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  protected GenCallCodeUtils(
      VisitorsState state,
      DelTemplateNamer delTemplateNamer,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor) {
    this.state = checkNotNull(state);
    this.delTemplateNamer = checkNotNull(delTemplateNamer);
    this.isComputableAsJsExprsVisitor = checkNotNull(isComputableAsJsExprsVisitor);
  }

  /**
   * Generates the JS expression for a given call.
   *
   * <p>Important: If there are CallParamContentNode children whose contents are not computable as
   * JS expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param&lt;n&gt;' temporary variables.
   *
   * <p>Here are five example calls:
   *
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   *
   * Their respective generated calls might be the following:
   *
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$assignDefaults({goo: 'Blah'}, opt_data.boo))
   *   some.func({goo: param65})
   * </pre>
   *
   * Note that in the last case, the param content is not computable as JS expressions, so we assume
   * that code has been generated to define the temporary variable 'param&lt;n&gt;'.
   *
   * @param callNode The call to generate code for.
   * @return The JS expression for the call.
   */
  public Expression gen(CallNode callNode, TranslateExprNodeVisitor exprTranslator) {

    // Build the JS CodeChunk for the callee's name.
    Callee callee = genCallee(callNode, exprTranslator);

    // Generate the data object to pass to callee
    Expression call;
    if (canPerformPositionalCall(callNode)) {
      List<Expression> params = new ArrayList<>();
      params.add(JsRuntime.SOY_INTERNAL_CALL_MARKER);
      params.add(JsRuntime.IJ_DATA);
      params.addAll(
          getPositionalParams((CallBasicNode) callNode, exprTranslator, hasVariant(callNode)));
      maybeAddVariantParam(callNode, exprTranslator, params);
      call = generateCallExpr(callNode, callee.positionalStyle().get(), params);
    } else {
      List<Expression> params = new ArrayList<>();
      params.add(genObjToPass(callNode, exprTranslator));
      params.add(JsRuntime.IJ_DATA);
      maybeAddVariantParam(callNode, exprTranslator, params);
      call = generateCallExpr(callNode, callee.objectStyle(), params);
    }
    if (callNode.getEscapingDirectives().isEmpty()) {
      return call;
    }
    return applyEscapingDirectives(call, callNode);
  }

  /**
   * Like gen(), but if the call is a NodeBuilder, return a wrapping html output buffer so that it
   * can be evaluated e.g. in message placeholders.
   */
  public Expression genStandalone(CallNode callNode, TranslateExprNodeVisitor exprTranslator) {
    Expression callExpr = gen(callNode, exprTranslator);
    return useNodeBuilder(callNode) ? createHtmlOutputBufferFunction().call(callExpr) : callExpr;
  }

  private Expression generateCallExpr(
      CallNode callNode, Expression callee, List<Expression> params) {
    if (useNodeBuilder(callNode)) {
      Expression callExpr = callee.call(params);
      return createNodeBuilderFunction()
          .call(
              // When using a node builder, we need place the try/catch inside of the lambda. That
              // is done here.
              // (When not using node builder, we need to place the output variable concatenation
              // statement inside of a try/catch. That is done in GenJsTemplateBodyVisitor.)
              callNode.isErrorFallbackSkip()
                  ? Expressions.tsArrowFunction(
                      ImmutableList.of(
                          TryCatch.create(
                              Return.create(callExpr),
                              Return.create(Expressions.stringLiteral("")))))
                  : Expressions.tsArrowFunction(callExpr));
    } else {
      return callee.call(params);
    }
  }

  public boolean useNodeBuilder(CallNode callNode) {
    return state.outputVarHandler.currentOutputVarStyle() == OutputVarHandler.Style.LAZY
        && callNode instanceof CallBasicNode
        && ((CallBasicNode) callNode).isLazy();
  }

  public static boolean hasVariant(CallNode callNode) {
    return callNode instanceof CallBasicNode && ((CallBasicNode) callNode).getVariantExpr() != null;
  }

  /**
   * Adds a parameter expression intended for opt_variant to the end of params, if the call has a
   * variant expression. The compiler enforces that the target of any call with "variant" has a
   * "usevarianttype" attribute, so the callee template will have the opt_variant param generated.
   */
  public static void maybeAddVariantParam(
      CallNode callNode, TranslateExprNodeVisitor exprTranslator, List<Expression> params) {
    if (hasVariant(callNode)) {
      params.add(exprTranslator.exec(((CallBasicNode) callNode).getVariantExpr()));
    }
  }

  public List<Expression> getPositionalParams(
      CallBasicNode callNode, TranslateExprNodeVisitor exprTranslator, boolean hasVariant) {
    Map<String, Expression> explicitParams = getExplicitParams(callNode, exprTranslator);
    // to perform the call we need to pass these params in the correct order
    List<Expression> params = new ArrayList<>();
    int numTrailingUndefineds = 0;
    for (TemplateType.Parameter calleeParam : callNode.getStaticType().getActualParameters()) {
      Expression explicitParam = explicitParams.remove(calleeParam.getName());
      if (explicitParam == null) {
        numTrailingUndefineds++;
        params.add(Expressions.LITERAL_UNDEFINED);
      } else {
        numTrailingUndefineds = 0;
        params.add(explicitParam);
      }
    }
    checkState(
        explicitParams.isEmpty(), "Expected all params to be consumed, %s remain", explicitParams);
    // rather than foo(x,y, undefined, undefined) we should generate foo(x,y) unless there is a
    // variant, which will be last param.
    if (numTrailingUndefineds > 0 && !hasVariant) {
      params = params.subList(0, params.size() - numTrailingUndefineds);
    }
    return params;
  }

  /**
   * Returns true if the given call can use positional style parameters.
   *
   * <p>This kind of call is possible if:
   *
   * <ul>
   *   <li>The callee supports it, see {@link #hasPositionalSignature}
   *   <li>The caller is a {@link CallBasicNode} with node {@code data=} expression and the calle is
   *       a static template (not a template reference)
   * </ul>
   */
  public boolean canPerformPositionalCall(CallNode callNode) {
    if (callNode.isPassingData()
        || !(callNode instanceof CallBasicNode)
        || !((CallBasicNode) callNode).isStaticCall()) {
      return false;
    }
    return hasPositionalSignature(((CallBasicNode) callNode).getStaticType());
  }

  public static Expression applyEscapingDirectives(Expression call, CallNode callNode) {
    // Apply escaping directives as necessary.
    //
    // The print directive system continues to use JsExpr, as it is a publicly available API and
    // migrating it to CodeChunk would be a major change. Therefore, we convert our CodeChunks
    // to JsExpr and back here.
    for (SoyPrintDirective directive : callNode.getEscapingDirectives()) {
      if (directive instanceof ModernSoyJsSrcPrintDirective) {
        call = ((ModernSoyJsSrcPrintDirective) directive).applyForJsSrc(call, ImmutableList.of());
      } else if (directive instanceof SoyJsSrcPrintDirective) {
        JsExpr callResult = call.singleExprOrName(FormatOptions.JSSRC);
        ImmutableSet.Builder<GoogRequire> requiresBuilder = ImmutableSet.builder();
        call.collectRequires(requiresBuilder::add);
        if (directive instanceof SoyLibraryAssistedJsSrcPrintDirective) {
          for (String name :
              ((SoyLibraryAssistedJsSrcPrintDirective) directive).getRequiredJsLibNames()) {
            requiresBuilder.add(GoogRequire.create(name));
          }
        }
        callResult =
            ((SoyJsSrcPrintDirective) directive).applyForJsSrc(callResult, ImmutableList.of());
        call =
            fromExpr(callResult, requiresBuilder.build())
                .withInitialStatements(call.allInitialStatementsInTopScope());
      } else {
        throw new IllegalStateException(
            String.format(
                "Contextual autoescaping produced a bogus directive: %s", directive.getName()));
      }
    }

    return call;
  }

  /** Represents a callable function symbol. */
  @AutoValue
  public abstract static class Callee {
    Callee() {}

    /** A reference to the positional signature */
    public abstract Optional<Expression> positionalStyle();

    public abstract Expression objectStyle();
  }

  /**
   * @param callNode The call to generate code for.
   * @return The JS expression for the template to call
   */
  public Callee genCallee(CallNode callNode, TranslateExprNodeVisitor exprTranslator) {
    // Build the JS CodeChunk for the callee's name.
    Expression callee;
    if (callNode instanceof CallBasicNode) {
      // Case 1: Basic call.
      CallBasicNode callBasicNode = (CallBasicNode) callNode;
      if (callBasicNode.isStaticCall()) {
        // Skip checks for the common case of synthetic template literals.
        callee =
            Expressions.dottedIdNoRequire(state.templateAliases.get(callBasicNode.getCalleeName()));
      } else {
        callee = exprTranslator.exec(callBasicNode.getCalleeExpr());
      }
    } else {
      // Case 2: Delegate call.
      CallDelegateNode callDelegateNode = (CallDelegateNode) callNode;
      Expression calleeId =
          JsRuntime.SOY_GET_DELTEMPLATE_ID.call(
              stringLiteral(delTemplateNamer.getDelegateName(callDelegateNode)));

      ExprRootNode variantSoyExpr = callDelegateNode.getDelCalleeVariantExpr();
      Expression variant;
      if (variantSoyExpr == null) {
        // Case 2a: Delegate call with empty variant.
        variant = LITERAL_EMPTY_STRING;
      } else {
        // Case 2b: Delegate call with variant expression.
        variant = exprTranslator.exec(variantSoyExpr);
      }

      callee = SOY_GET_DELEGATE_FN.call(calleeId, variant);
    }
    Optional<Expression> positional = Optional.empty();

    if (canPerformPositionalCall(callNode)) {
      positional =
          Optional.of(
              dottedIdNoRequire(
                  state.templateAliases.get(
                          ((TemplateLiteralNode)
                                  ((CallBasicNode) callNode).getCalleeExpr().getRoot())
                              .getResolvedName())
                      + "$"));
    }

    return new AutoValue_GenCallCodeUtils_Callee(positional, callee);
  }

  /**
   * Generates the JS expression for the object to pass in a given call.
   *
   * <p>Important: If there are CallParamContentNode children whose contents are not computable as
   * JS expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param&lt;n&gt;' temporary variables.
   *
   * <p>Here are five example calls:
   *
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   *
   * Their respective objects to pass might be the following:
   *
   * <pre>
   *   opt_data
   *   opt_data.boo.foo
   *   {goo: opt_data.moo}
   *   soy.$$assignDefaults({goo: 'Blah'}, opt_data.boo)
   *   {goo: param65}
   * </pre>
   *
   * Note that in the last case, the param content is not computable as JS expressions, so we assume
   * that code has been generated to define the temporary variable 'param&lt;n&gt;'.
   *
   * @param callNode The call to generate code for.
   * @return The JS expression for the object to pass in the call.
   */
  public Expression genObjToPass(CallNode callNode, TranslateExprNodeVisitor exprTranslator) {

    // ------ Generate the expression for the original data to pass ------
    Optional<Expression> dataToPass = Optional.empty();
    if (callNode.isPassingAllData()) {
      dataToPass = Optional.of(JsRuntime.OPT_DATA);
    } else if (callNode.isPassingData()) {
      dataToPass = Optional.of(exprTranslator.exec(callNode.getDataExpr()));
    }

    // ------ Build an object literal containing the additional params ------
    Map<String, Expression> params = getExplicitParams(callNode, exprTranslator);
    if (callNode.isPassingAllData()) {
      Map<String, Expression> mergedParams = new LinkedHashMap<>();
      mergedParams.putAll(getDefaultParams(callNode));
      mergedParams.putAll(params);
      params = mergedParams;
    }
    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (dataToPass.isPresent()) {
      if (params.isEmpty()) {
        return dataToPass.get().castAsUnknown();
      }
      // No need to cast; assignDefaults already returns {?}.
      return SOY_ASSIGN_DEFAULTS.call(Expressions.objectLiteral(params), dataToPass.get());
    } else {
      if (params.isEmpty()) {
        return LITERAL_NULL;
      }
      // Ignore inconsistencies between Closure Compiler & Soy type systems (eg, proto nullability).
      return Expressions.objectLiteral(params).castAsUnknown();
    }
  }

  private Map<String, Expression> getExplicitParams(
      CallNode callNode, TranslateExprNodeVisitor exprTranslator) {
    Map<String, Expression> params = new LinkedHashMap<>();

    for (CallParamNode child : callNode.getChildren()) {
      Expression value;

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        value = exprTranslator.exec(cpvn.getExpr());
      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        if (isComputableAsJsExprsVisitor.exec(cpcn)) {

          state.outputVarHandler.pushOutputVarForEvalOnly(
              OutputVarHandler.outputStyleForBlock(cpcn));
          List<Expression> contentChunks = state.createJsExprsVisitor().exec(cpcn);
          state.outputVarHandler.popOutputVar();

          value = maybeWrapContent(state.translationContext.codeGenerator(), cpcn, contentChunks);
        } else {
          // This is a param with content that cannot be represented as JS expressions, so we assume
          // that code has been generated to define the temporary variable 'param<n>'. The param
          // will already be wrapped in the appropriate SanitizedContent if needed.
          value = Id.create("param" + cpcn.getId());
        }
      }
      params.put(child.getKey().identifier(), value);
    }

    return params;
  }

  private Map<String, Expression> getDefaultParams(CallNode node) {
    Map<String, Expression> defaultParams = new LinkedHashMap<>();
    for (TemplateParam param : node.getNearestAncestor(TemplateNode.class).getParams()) {
      if (param.hasDefault()) {
        // Just put the parameter value in here, which will be the default if the parameter is
        // unset. The additional JS to figure out of a parameter is the default or not isn't worth
        // it.
        defaultParams.put(
            param.name(), state.translationContext.soyToJsVariableMappings().get(param.refName()));
      }
    }
    return defaultParams;
  }

  @Nullable
  protected Expression maybeWrapWithOutputBuffer(
      CallParamContentNode node, List<Expression> exprs) {
    if (OutputVarHandler.outputStyleForBlock(node) == OutputVarHandler.Style.LAZY) {
      return createHtmlOutputBufferFunction().call(exprs);
    }
    return null;
  }

  /**
   * If the param node had a content kind specified, it was autoescaped in the corresponding
   * context. Hence the result of evaluating the param block is wrapped in a SanitizedContent
   * instance of the appropriate kind.
   *
   * <p>The expression for the constructor of SanitizedContent of the appropriate kind (e.g., "new
   * SanitizedHtml"), or null if the node has no 'kind' attribute. This uses the variant used in
   * internal blocks.
   */
  protected Expression maybeWrapContent(
      CodeChunk.Generator generator, CallParamContentNode node, List<Expression> exprs) {
    Expression value = maybeWrapWithOutputBuffer(node, exprs);
    if (value != null) {
      return value;
    }

    Expression content = Expressions.concatForceString(exprs);
    if (node.getContentKind() == SanitizedContentKind.TEXT) {
      return content;
    }

    return sanitizedContentOrdainerFunction(node.getContentKind()).call(content);
  }
}
