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

import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_EMPTY_STRING;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_FALSE;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_NULL;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.LITERAL_TRUE;
import static com.google.template.soy.jssrc.dsl.CodeChunk.WithValue.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.fromExpr;
import static com.google.template.soy.jssrc.dsl.CodeChunk.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_ASSIGN_DEFAULTS;
import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_GET_DELEGATE_FN;
import static com.google.template.soy.jssrc.internal.JsRuntime.sanitizedContentOrdainerFunctionForInternalBlocks;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.internal.SoyToJsVariableMappings.VarKey;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/**
 * Generates JS code for {call}s and {delcall}s.
 *
 */
public class GenCallCodeUtils {

  /** All registered JS print directives. */
  private final Map<String, SoyJsSrcPrintDirective> soyJsSrcDirectivesMap;

  /** Instance of JsExprTranslator to use. */
  private final JsExprTranslator jsExprTranslator;

  /** Instance of DelTemplateNamer to use. */
  private final DelTemplateNamer delTemplateNamer;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** Factory for creating an instance of GenJsExprsVisitor. */
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;

  /**
   * @param soyJsSrcDirectivesMap Map of jssrc print directives to their names.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param delTemplateNamer Renamer for delegate templates.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to be used.
   * @param genJsExprsVisitorFactory Factory for creating an instance of GenJsExprsVisitor.
   */
  @Inject
  protected GenCallCodeUtils(
      Map<String, SoyJsSrcPrintDirective> soyJsSrcDirectivesMap,
      JsExprTranslator jsExprTranslator,
      DelTemplateNamer delTemplateNamer,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory) {
    this.jsExprTranslator = jsExprTranslator;
    this.delTemplateNamer = delTemplateNamer;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
    this.soyJsSrcDirectivesMap = soyJsSrcDirectivesMap;
  }

  /**
   * Generates the JS expression for a given call.
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * JS expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param&lt;n&gt;' temporary variables.
   *
   * <p> Here are five example calls:
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
   * Their respective generated calls might be the following:
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$assignDefaults({goo: 'Blah'}, opt_data.boo))
   *   some.func({goo: param65})
   * </pre>
   * Note that in the last case, the param content is not computable as JS expressions, so we assume
   * that code has been generated to define the temporary variable 'param&lt;n&gt;'.
   *
   * @param callNode The call to generate code for.
   * @param templateAliases A mapping of fully qualified calls to a variable in scope.
   * @return The JS expression for the call.
   */
  public CodeChunk.WithValue gen(
      CallNode callNode,
      TemplateAliases templateAliases,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {

    // Build the JS CodeChunk for the callee's name.
    CodeChunk.WithValue callee;
    if (callNode instanceof CallBasicNode) {
      // Case 1: Basic call.
      // TODO(lukes): add the logic for the goog.require here.  The simplest strategy requires a
      // TemplateRegistry to detect external templates.
      callee =
          CodeChunk.dottedIdNoRequire(
              templateAliases.get(((CallBasicNode) callNode).getCalleeName()));
    } else {
      // Case 2: Delegate call.
      CallDelegateNode callDelegateNode = (CallDelegateNode) callNode;
      CodeChunk.WithValue calleeId =
          JsRuntime.SOY_GET_DELTEMPLATE_ID.call(
              stringLiteral(delTemplateNamer.getDelegateName(callDelegateNode)));

      ExprRootNode variantSoyExpr = callDelegateNode.getDelCalleeVariantExpr();
      CodeChunk.WithValue variant;
      if (variantSoyExpr == null) {
        // Case 2a: Delegate call with empty variant.
        variant = LITERAL_EMPTY_STRING;
      } else {
        // Case 2b: Delegate call with variant expression.
        variant = jsExprTranslator.translateToCodeChunk(
            variantSoyExpr,
            translationContext,
            errorReporter);
      }

      callee =
          SOY_GET_DELEGATE_FN.call(
              calleeId,
              variant,
              callDelegateNode.allowEmptyDefault() ? LITERAL_TRUE : LITERAL_FALSE);
    }

    // Generate the data object to pass to callee
    CodeChunk.WithValue objToPass =
        genObjToPass(callNode, templateAliases, translationContext, errorReporter);

    // Generate the main call expression.
    CodeChunk.WithValue call = callee.call(objToPass, LITERAL_NULL, JsRuntime.OPT_IJ_DATA);
    if (callNode.getEscapingDirectiveNames().isEmpty()) {
      return call;
    }

    // Apply escaping directives as necessary.
    //
    // The print directive system continues to use JsExpr, as it is a publicly available API and
    // migrating it to CodeChunk would be a major change. Therefore, we convert our CodeChunks
    // to JsExpr and back here.
    JsExpr callResult = call.singleExprOrName();
    RequiresCollector.IntoImmutableSet collector = new RequiresCollector.IntoImmutableSet();
    call.collectRequires(collector);
    for (String directiveName : callNode.getEscapingDirectiveNames()) {
      SoyJsSrcPrintDirective directive = soyJsSrcDirectivesMap.get(directiveName);
      Preconditions.checkNotNull(
          directive, "Contextual autoescaping produced a bogus directive: %s", directiveName);
      callResult = directive.applyForJsSrc(callResult, ImmutableList.<JsExpr>of());
      if (directive instanceof SoyLibraryAssistedJsSrcPrintDirective) {
        for (String name :
            ((SoyLibraryAssistedJsSrcPrintDirective) directive).getRequiredJsLibNames()) {
          collector.add(GoogRequire.create(name));
        }
      }
    }

    return fromExpr(callResult, collector.get()).withInitialStatements(call.initialStatements());
  }


  /**
   * Generates the JS expression for the object to pass in a given call.
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * JS expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param&lt;n&gt;' temporary variables.
   *
   * <p> Here are five example calls:
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
   * Their respective objects to pass might be the following:
   * <pre>
   *   opt_data
   *   opt_data.boo.foo
   *   {goo: opt_data.moo}
   *   soy.$$assignDefaults({goo: 'Blah'}, opt_data.boo)
   *   {goo: param65}
   * </pre>
   * Note that in the last case, the param content is not computable as JS expressions, so we assume
   * that code has been generated to define the temporary variable 'param&lt;n&gt;'.
   *
   * @param callNode The call to generate code for.
   * @param templateAliases A mapping of fully qualified calls to a variable in scope.
   * @return The JS expression for the object to pass in the call.
   */
  private CodeChunk.WithValue genObjToPass(
      CallNode callNode,
      TemplateAliases templateAliases,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {

    // ------ Generate the expression for the original data to pass ------
    CodeChunk.WithValue dataToPass;
    if (callNode.isPassingAllData()) {
      dataToPass = JsRuntime.OPT_DATA;
    } else if (callNode.isPassingData()) {
      dataToPass =
          jsExprTranslator.translateToCodeChunk(
              callNode.getDataExpr(), translationContext, errorReporter);
    } else {
      dataToPass = LITERAL_NULL;
    }

    // ------ Case 1: No additional params ------
    if (callNode.numChildren() == 0) {
      return dataToPass;
    }

    // ------ Build an object literal containing the additional params ------
    ImmutableList.Builder<CodeChunk.WithValue> keys = ImmutableList.builder();
    ImmutableList.Builder<CodeChunk.WithValue> values = ImmutableList.builder();

    for (CallParamNode child : callNode.getChildren()) {
      keys.add(id(child.getKey().identifier()));

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        CodeChunk.WithValue value =
            jsExprTranslator.translateToCodeChunk(
                cpvn.getExpr(), translationContext, errorReporter);
        values.add(value);
      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        CodeChunk.WithValue content;
        if (isComputableAsJsExprsVisitor.exec(cpcn)) {
          List<CodeChunk.WithValue> chunks = genJsExprsVisitorFactory
              .create(translationContext, templateAliases, errorReporter)
              .exec(cpcn);
          content = CodeChunkUtils.concatChunksForceString(chunks);
        } else {
          // This is a param with content that cannot be represented as JS expressions, so we assume
          // that code has been generated to define the temporary variable 'param<n>'.
          content =
              translationContext.variableMappings().getIdentifier(VarKey.createOutputVar(cpcn));
        }

        content = maybeWrapContent(translationContext.codeGenerator(), cpcn, content);
        values.add(content);
      }
    }

    CodeChunk.WithValue params = CodeChunk.mapLiteral(keys.build(), values.build());

    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (callNode.isPassingData()) {
      CodeChunk.WithValue allData = SOY_ASSIGN_DEFAULTS.call(params, dataToPass);
      return allData;
    } else {
      return params;
    }
  }

  /**
   * If the param node had a content kind specified, it was autoescaped in the
   * corresponding context. Hence the result of evaluating the param block is wrapped
   * in a SanitizedContent instance of the appropriate kind.
   * <p>
   * The expression for the constructor of SanitizedContent of the appropriate kind (e.g.,
   * "new SanitizedHtml"), or null if the node has no 'kind' attribute.  This uses the
   * variant used in internal blocks.
   * </p>
   */
  protected CodeChunk.WithValue maybeWrapContent(
      CodeChunk.Generator generator, CallParamContentNode node, CodeChunk.WithValue content) {
    if (node.getContentKind() == null) {
      return content;
    }

    // Use the internal blocks wrapper, to maintain falsiness of empty string
    return sanitizedContentOrdainerFunctionForInternalBlocks(node.getContentKind()).call(content);
  }
}
