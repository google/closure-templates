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

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_FALSE;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.Expression.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.Expression.fromExpr;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.ASSERT_TEMPLATE;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ASSIGN_DEFAULTS;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_GET_DELEGATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunctionForInternalBlocks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates JS code for {call}s and {delcall}s.
 *
 */
public class GenCallCodeUtils {

  /** Instance of DelTemplateNamer to use. */
  private final DelTemplateNamer delTemplateNamer;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** Factory for creating an instance of GenJsExprsVisitor. */
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;

  protected GenCallCodeUtils(
      DelTemplateNamer delTemplateNamer,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory) {
    this.delTemplateNamer = delTemplateNamer;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
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
   * @param templateAliases A mapping of fully qualified calls to a variable in scope.
   * @return The JS expression for the call.
   */
  public Expression gen(
      CallNode callNode,
      TemplateAliases templateAliases,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      TranslateExprNodeVisitor exprTranslator) {

    // Build the JS CodeChunk for the callee's name.
    Expression callee = genCallee(callNode, templateAliases, exprTranslator);

    // Generate the data object to pass to callee
    Expression objToPass =
        genObjToPass(callNode, templateAliases, translationContext, errorReporter, exprTranslator);

    Expression call = genMainCall(callee, objToPass, callNode);
    if (callNode.getEscapingDirectives().isEmpty()) {
      return call;
    }
    return applyEscapingDirectives(call, callNode);
  }

  protected Expression genMainCall(Expression callee, Expression objToPass, CallNode call) {
    return callee.call(objToPass, JsRuntime.OPT_IJ_DATA);
  }

  public static Expression applyEscapingDirectives(Expression call, CallNode callNode) {
    // Apply escaping directives as necessary.
    //
    // The print directive system continues to use JsExpr, as it is a publicly available API and
    // migrating it to CodeChunk would be a major change. Therefore, we convert our CodeChunks
    // to JsExpr and back here.
    JsExpr callResult = call.singleExprOrName();
    ImmutableSet.Builder<GoogRequire> requiresBuilder = ImmutableSet.builder();
    call.collectRequires(requiresBuilder::add);
    for (SoyPrintDirective directive : callNode.getEscapingDirectives()) {
      checkState(
          directive instanceof SoyJsSrcPrintDirective,
          "Contextual autoescaping produced a bogus directive: %s",
          directive.getName());
      callResult =
          ((SoyJsSrcPrintDirective) directive).applyForJsSrc(callResult, ImmutableList.of());
      if (directive instanceof SoyLibraryAssistedJsSrcPrintDirective) {
        for (String name :
            ((SoyLibraryAssistedJsSrcPrintDirective) directive).getRequiredJsLibNames()) {
          requiresBuilder.add(GoogRequire.create(name));
        }
      }
    }

    return fromExpr(callResult, requiresBuilder.build())
        .withInitialStatements(call.initialStatements());
  }

  /**
   * @param callNode The call to generate code for.
   * @param templateAliases A mapping of fully qualified calls to a variable in scope.
   * @param exprTranslator
   * @return The JS expression for the template to call
   */
  public Expression genCallee(
      CallNode callNode, TemplateAliases templateAliases, TranslateExprNodeVisitor exprTranslator) {
    // Build the JS CodeChunk for the callee's name.
    Expression callee;
    if (callNode instanceof CallBasicNode) {
      // Case 1: Basic call.
      CallBasicNode callBasicNode = (CallBasicNode) callNode;
      Expression calleeExpression = exprTranslator.exec(callBasicNode.getCalleeExpr());
      // Skip checks for the common case of static calls.
      callee =
          callBasicNode.isStaticCall() ? calleeExpression : ASSERT_TEMPLATE.call(calleeExpression);
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

      callee =
          SOY_GET_DELEGATE_FN.call(
              calleeId,
              variant,
              callDelegateNode.allowEmptyDefault() ? LITERAL_TRUE : LITERAL_FALSE);
    }
    return callee;
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
   * @param templateAliases A mapping of fully qualified calls to a variable in scope.
   * @return The JS expression for the object to pass in the call.
   */
  public Expression genObjToPass(
      CallNode callNode,
      TemplateAliases templateAliases,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      TranslateExprNodeVisitor exprTranslator) {

    // ------ Generate the expression for the original data to pass ------
    Expression dataToPass;
    if (callNode.isPassingAllData()) {
      dataToPass = JsRuntime.OPT_DATA;
    } else if (callNode.isPassingData()) {
      dataToPass = exprTranslator.exec(callNode.getDataExpr());
    } else if (callNode.numChildren() == 0) {
      // If we're passing neither children nor indirect data, we can immediately return null.
      return LITERAL_NULL;
    } else {
      dataToPass = LITERAL_NULL;
    }

    Map<String, Expression> paramDefaults = getDefaultParams(callNode, translationContext);
    // ------ Case 1: No additional params ------
    if (callNode.numChildren() == 0) {
      if (!paramDefaults.isEmpty()) {
        dataToPass = SOY_ASSIGN_DEFAULTS.call(dataToPass, Expression.objectLiteral(paramDefaults));
      }
      // Ignore inconsistencies between Closure Compiler & Soy type systems (eg, proto nullability).
      return dataToPass.castAs("?");
    }

    // ------ Build an object literal containing the additional params ------
    Map<String, Expression> params = paramDefaults;

    for (CallParamNode child : callNode.getChildren()) {
      Expression value;

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        value = exprTranslator.exec(cpvn.getExpr());
      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        if (isComputableAsJsExprsVisitor.exec(cpcn)) {
          List<Expression> chunks =
              genJsExprsVisitorFactory
                  .create(translationContext, templateAliases, errorReporter)
                  .exec(cpcn);
          value = CodeChunkUtils.concatChunksForceString(chunks);
        } else {
          // This is a param with content that cannot be represented as JS expressions, so we assume
          // that code has been generated to define the temporary variable 'param<n>'.
          value = id("param" + cpcn.getId());
        }

        value = maybeWrapContent(translationContext.codeGenerator(), cpcn, value);
      }
      params.put(child.getKey().identifier(), value);
    }

    Expression paramsExp = Expression.objectLiteral(params);

    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (callNode.isPassingData()) {
      Expression allData = SOY_ASSIGN_DEFAULTS.call(paramsExp, dataToPass);
      // No need to cast; assignDefaults already returns {?}.
      return allData;
    } else {
      // Ignore inconsistencies between Closure Compiler & Soy type systems (eg, proto nullability).
      return paramsExp.castAs("?");
    }
  }

  private Map<String, Expression> getDefaultParams(
      CallNode node, TranslationContext translationContext) {
    Map<String, Expression> defaultParams = new LinkedHashMap<>();
    if (!node.isPassingAllData()) {
      return defaultParams;
    }
    for (TemplateParam param : node.getNearestAncestor(TemplateNode.class).getParams()) {
      if (param.hasDefault()) {
        // Just put the parameter value in here, which will be the default if the parameter is
        // unset. The additional JS to figure out of a parameter is the default or not isn't worth
        // it.
        defaultParams.put(
            param.name(), translationContext.soyToJsVariableMappings().get(param.name()));
      }
    }
    return defaultParams;
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
      CodeChunk.Generator generator, CallParamContentNode node, Expression content) {
    if (node.getContentKind() == SanitizedContentKind.TEXT) {
      return content;
    }

    // Use the internal blocks wrapper, to maintain falsiness of empty string
    return sanitizedContentOrdainerFunctionForInternalBlocks(node.getContentKind()).call(content);
  }
}
