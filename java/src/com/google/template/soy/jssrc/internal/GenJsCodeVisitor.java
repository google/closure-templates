/*
 * Copyright 2008 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.template.soy.base.SoyFileKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.SoyJsSrcOptions.CodeStyle;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.IsUsingIjData;
import com.google.template.soy.sharedpasses.ShouldEnsureDataIsDefinedVisitor;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.DebuggerNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Visitor for generating full JS code (i.e. statements) for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> {@link #exec} should be called on a full parse tree. JS source code will be generated for
 * all the Soy files. The return value is a list of strings, each string being the content of one
 * generated JS file (corresponding to one Soy file).
 *
 * @author Kai Huang
 */
class GenJsCodeVisitor extends AbstractSoyNodeVisitor<List<String>> {


  /** Regex pattern to look for dots in a template name. */
  private static final Pattern DOT = Pattern.compile("\\.");

  /** Regex pattern for an integer. */
  private static final Pattern INTEGER = Pattern.compile("-?\\d+");

  /** Namespace to goog.require when useGoogIsRtlForBidiGlobalDir is in force. */
  private static final String GOOG_IS_RTL_NAMESPACE = "goog.i18n.bidi";

  /** Namespace to goog.require when a plural/select message is encountered. */
  private static final String GOOG_MESSAGE_FORMAT_NAMESPACE = "goog.i18n.MessageFormat";


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** Whether any of the Soy code uses injected data. */
  private final boolean isUsingIjData;

  /** Instance of JsExprTranslator to use. */
  private final JsExprTranslator jsExprTranslator;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  private final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** Factory for creating an instance of GenJsExprsVisitor. */
  private final GenJsExprsVisitorFactory genJsExprsVisitorFactory;

  /** The contents of the generated JS files. */
  private List<String> jsFilesContents;

  /** The JsCodeBuilder to build the current JS file being generated (during a run). */
  @VisibleForTesting protected JsCodeBuilder jsCodeBuilder;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  @VisibleForTesting protected Deque<Map<String, JsExpr>> localVarTranslations;

  /** The GenJsExprsVisitor used for the current template. */
  @VisibleForTesting protected GenJsExprsVisitor genJsExprsVisitor;

  /** The assistant visitor for msgs used for the current template (lazily initialized). */
  @VisibleForTesting protected GenJsCodeVisitorAssistantForMsgs assistantForMsgs;


  /**
   * @param jsSrcOptions The options for generating JS source code.
   * @param isUsingIjData Whether any of the Soy code uses injected data.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to use.
   * @param canInitOutputVarVisitor The CanInitOutputVarVisitor to use.
   * @param genJsExprsVisitorFactory Factory for creating an instance of GenJsExprsVisitor.
   */
  @Inject
  GenJsCodeVisitor(
      SoyJsSrcOptions jsSrcOptions, @IsUsingIjData boolean isUsingIjData,
      JsExprTranslator jsExprTranslator, GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CanInitOutputVarVisitor canInitOutputVarVisitor,
      GenJsExprsVisitorFactory genJsExprsVisitorFactory) {
    this.jsSrcOptions = jsSrcOptions;
    this.isUsingIjData = isUsingIjData;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
  }


  @Override public List<String> exec(SoyNode node) {
    jsFilesContents = Lists.newArrayList();
    jsCodeBuilder = null;
    localVarTranslations = null;
    genJsExprsVisitor = null;
    assistantForMsgs = null;
    visit(node);
    return jsFilesContents;
  }


  /**
   * This method must only be called by assistant visitors, in particular
   * GenJsCodeVisitorAssistantForMsgs.
   */
  void visitForUseByAssistants(SoyNode node) {
    visit(node);
  }


  @VisibleForTesting
  @Override protected void visit(SoyNode node) {
    super.visit(node);
  }


  @Override protected void visitChildren(ParentSoyNode<?> node) {

    // If the block is empty or if the first child cannot initilize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      jsCodeBuilder.initOutputVarIfNecessary();
    }

    List<JsExpr> consecChildrenJsExprs = Lists.newArrayList();

    for (SoyNode child : node.getChildren()) {

      if (isComputableAsJsExprsVisitor.exec(child)) {
        consecChildrenJsExprs.addAll(genJsExprsVisitor.exec(child));

      } else {
        // We've reached a child that is not computable as JS expressions.

        // First add the JsExprs from preceding consecutive siblings that are computable as JS
        // expressions (if any).
        if (consecChildrenJsExprs.size() > 0) {
          jsCodeBuilder.addToOutputVar(consecChildrenJsExprs);
          consecChildrenJsExprs.clear();
        }

        // Now append the code for this child.
        visit(child);
      }
    }

    // Add the JsExprs from the last few children (if any).
    if (consecChildrenJsExprs.size() > 0) {
      jsCodeBuilder.addToOutputVar(consecChildrenJsExprs);
      consecChildrenJsExprs.clear();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {

    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.associateMetaInfo(null, soyFile.getFilePath(), null);
      }
    }
  }


  /**
   * Example:
   * <pre>
   * // This file was automatically generated from my-templates.soy.
   * // Please don't edit this file by hand.
   *
   * if (typeof boo == 'undefined') { var boo = {}; }
   * if (typeof boo.foo == 'undefined') { boo.foo = {}; }
   *
   * ...
   * </pre>
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {

    if (node.getSoyFileKind() == SoyFileKind.DEP) {
      return;  // don't generate code for deps
    }

    jsCodeBuilder = new JsCodeBuilder(jsSrcOptions.getCodeStyle());

    jsCodeBuilder.appendLine("// This file was automatically generated from ",
                             node.getFileName(), ".");
    jsCodeBuilder.appendLine("// Please don't edit this file by hand.");

    // If this file is in a delegate package, add a declaration for JS compilers. We don't bother
    // adding an option for this, since it's just a comment and shouldn't be harmful even if the
    // user is not using JS compilers that recognize it.
    if (node.getDelPackageName() != null) {
      jsCodeBuilder.appendLine();
      jsCodeBuilder.appendLine("/**");
      jsCodeBuilder.appendLine(" * @modName {", node.getDelPackageName(), "}");
      jsCodeBuilder.appendLine(" */");
    }

    // Add code to define JS namespaces or add provide/require calls for Closure Library.
    jsCodeBuilder.appendLine();

    if (jsSrcOptions.shouldProvideRequireSoyNamespaces()) {
      addCodeToProvideSoyNamespace(node);
      if (jsSrcOptions.shouldProvideBothSoyNamespacesAndJsFunctions()) {
        addCodeToProvideJsFunctions(node);
      }
      jsCodeBuilder.appendLine();
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireSoyNamespaces(node);

    } else if (jsSrcOptions.shouldProvideRequireJsFunctions()) {
      if (jsSrcOptions.shouldProvideBothSoyNamespacesAndJsFunctions()) {
        addCodeToProvideSoyNamespace(node);
      }
      addCodeToProvideJsFunctions(node);
      jsCodeBuilder.appendLine();
      addCodeToRequireGeneralDeps(node);
      addCodeToRequireJsFunctions(node);

    } else {
      addCodeToDefineJsNamespaces(node);
    }

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      jsCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.associateMetaInfo(null, null, template.getTemplateNameForUserMsgs());
      }
    }

    jsFilesContents.add(jsCodeBuilder.getCode());
    jsCodeBuilder = null;
  }


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to define JS namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToDefineJsNamespaces(SoyFileNode soyFile) {

    SortedSet<String> jsNamespaces = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      String templateName = template.getTemplateName();
      Matcher dotMatcher = DOT.matcher(templateName);
      while (dotMatcher.find()) {
        jsNamespaces.add(templateName.substring(0, dotMatcher.start()));
      }
    }

    for (String jsNamespace : jsNamespaces) {
      boolean hasDot = jsNamespace.indexOf('.') >= 0;
      // If this is a top level namespace and the option to declare top level
      // namespaces is turned off, skip declaring it.
      if (jsSrcOptions.shouldDeclareTopLevelNamespaces() || hasDot) {
        jsCodeBuilder.appendLine("if (typeof ", jsNamespace, " == 'undefined') { ",
                                 (hasDot ? "" : "var "), jsNamespace, " = {}; }");
      }
    }
  }


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to provide Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideSoyNamespace(SoyFileNode soyFile) {
    jsCodeBuilder.appendLine("goog.provide('", soyFile.getNamespace(), "');");
  }


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to provide template JS functions.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideJsFunctions(SoyFileNode soyFile) {

    SortedSet<String> templateNames = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template instanceof TemplateBasicNode && ((TemplateBasicNode) template).isOverride()) {
        continue;  // generated function name already provided
      }
      templateNames.add(template.getTemplateName());
    }
    for (String templateName : templateNames) {
      jsCodeBuilder.appendLine("goog.provide('", templateName, "');");
    }
  }


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require general dependencies.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireGeneralDeps(SoyFileNode soyFile) {

    jsCodeBuilder.appendLine("goog.require('soy');");
    jsCodeBuilder.appendLine("goog.require('soydata');");
    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      jsCodeBuilder.appendLine("goog.require('soy.StringBuilder');");
    }

    if (jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir()) {
      jsCodeBuilder.appendLine("goog.require('", GOOG_IS_RTL_NAMESPACE, "');");
    }

    if ((new HasPlrselMsgVisitor()).exec(soyFile)) {
      jsCodeBuilder.appendLine("goog.require('", GOOG_MESSAGE_FORMAT_NAMESPACE, "');");
    }
  }


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireSoyNamespaces(SoyFileNode soyFile) {

    String prevCalleeNamespace = null;
    for (String calleeNotInFile : (new FindCalleesNotInFileVisitor()).exec(soyFile)) {
      int lastDotIndex = calleeNotInFile.lastIndexOf('.');
      if (lastDotIndex == -1) {
        throw SoySyntaxExceptionUtils.createWithNode(
            "When using the option to provide/require Soy namespaces, found a called template \"" +
                calleeNotInFile + "\" that does not reside in a namespace.",
            soyFile);
      }
      String calleeNamespace = calleeNotInFile.substring(0, lastDotIndex);
      if (calleeNamespace.length() > 0 && !calleeNamespace.equals(prevCalleeNamespace)) {
        jsCodeBuilder.appendLine("goog.require('", calleeNamespace, "');");
        prevCalleeNamespace = calleeNamespace;
      }
    }
  }


  /**
   * Helper for visitSoyFileNode(SoyFileNode) to add code to require template JS functions.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToRequireJsFunctions(SoyFileNode soyFile) {

    for (String calleeNotInFile : (new FindCalleesNotInFileVisitor()).exec(soyFile)) {
      jsCodeBuilder.appendLine("goog.require('", calleeNotInFile, "');");
    }
  }


  /**
   * Example:
   * <pre>
   * my.func = function(opt_data, opt_sb) {
   *   var output = opt_sb || new soy.StringBuilder();
   *   ...
   *   ...
   *   return opt_sb ? '' : output.toString();
   * };
   * </pre>
   */
  @Override protected void visitTemplateNode(TemplateNode node) {

    boolean isCodeStyleStringbuilder = jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER;

    localVarTranslations = new ArrayDeque<Map<String, JsExpr>>();
    genJsExprsVisitor = genJsExprsVisitorFactory.create(localVarTranslations);
    assistantForMsgs = null;

    // ------ Generate JS Doc. ------
    if (jsSrcOptions.shouldGenerateJsdoc()) {
      jsCodeBuilder.appendLine("/**");
      jsCodeBuilder.appendLine(" * @param {Object.<string, *>=} opt_data");
      if (isCodeStyleStringbuilder) {
        jsCodeBuilder.appendLine(" * @param {soy.StringBuilder=} opt_sb");
      } else {
        jsCodeBuilder.appendLine(" * @param {(null|undefined)=} opt_ignored");
      }
      if (isUsingIjData) {
        jsCodeBuilder.appendLine(" * @param {Object.<string, *>=} opt_ijData");
      }
      // For strict autoescaping templates, the result is actually a typesafe wrapper.
      String returnType = (node.getContentKind() == null) ?
          "string" : NodeContentKinds.toJsSanitizedContentTypeName(node.getContentKind());
      jsCodeBuilder.appendLine(" * @return {", returnType, "}");
      jsCodeBuilder.appendLine(" * @notypecheck");
      if (node instanceof TemplateBasicNode &&
          ((TemplateBasicNode) node).isOverride()) {
        jsCodeBuilder.appendLine(" * @suppress {duplicate}");
      }
      jsCodeBuilder.appendLine(" */");
    }

    // ------ Generate function definition up to opening brace. ------
    jsCodeBuilder.appendLine(
        node.getTemplateName(), " = function(opt_data",
        (isCodeStyleStringbuilder ? ", opt_sb" : ", opt_ignored"),
        (isUsingIjData ? ", opt_ijData" : ""), ") {");
    jsCodeBuilder.increaseIndent();

    // ------ Generate function body. ------
    generateFunctionBody(node);

    // ------ Generate function closing brace. ------
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("};");

    // ------ If delegate template, generate a statement to register it. ------
    if (node instanceof TemplateDelegateNode) {
      TemplateDelegateNode nodeAsDelTemplate = (TemplateDelegateNode) node;
      String delTemplateIdExprText =
          "soy.$$getDelTemplateId('" + nodeAsDelTemplate.getDelTemplateName() + "')";
      String delTemplateVariantExprText = "'" + nodeAsDelTemplate.getDelTemplateVariant() + "'";
      jsCodeBuilder.appendLine(
          "soy.$$registerDelegateFn(",
          delTemplateIdExprText, ", ", delTemplateVariantExprText, ", ",
          Integer.toString(nodeAsDelTemplate.getDelPriority()), ", ",
          nodeAsDelTemplate.getTemplateName(), ");");
    }
  }


  /**
   * Generates the function body.
   */
  private void generateFunctionBody(TemplateNode node) {
    boolean isCodeStyleStringbuilder = jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER;

    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());

    // Generate statement to ensure data is defined, if necessary.
    if ((new ShouldEnsureDataIsDefinedVisitor()).exec(node)) {
      jsCodeBuilder.appendLine("opt_data = opt_data || {};");
    }

    JsExpr resultJsExpr;
    if (!isCodeStyleStringbuilder && isComputableAsJsExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as JS
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the JS expressions and return the result.

      List<JsExpr> templateBodyJsExprs = genJsExprsVisitor.exec(node);
      resultJsExpr = JsExprUtils.concatJsExprs(templateBodyJsExprs);
    } else {
      // Case 2: Normal case.

      jsCodeBuilder.pushOutputVar("output");
      if (isCodeStyleStringbuilder) {
        jsCodeBuilder.appendLine("var output = opt_sb || new soy.StringBuilder();");
        jsCodeBuilder.setOutputVarInited();
      }

      visitChildren(node);

      if (isCodeStyleStringbuilder) {
        resultJsExpr = new JsExpr("opt_sb ? '' : output.toString()", Integer.MAX_VALUE);
      } else {
        resultJsExpr = new JsExpr("output", Integer.MAX_VALUE);
      }
      jsCodeBuilder.popOutputVar();
    }

    if (node.getContentKind() != null) {
      if (isCodeStyleStringbuilder) {
        // TODO: In string builder mode, the only way to support strict is if callers know
        // whether their callees are strict and wrap at the call site. This is challenging
        // because most projects compile Javascript one file at a time. Since concat mode
        // is faster in most browsers nowadays, this may not be a priority.
        throw SoySyntaxExceptionUtils.createWithNode(
            "Soy's StringBuilder-based code generation mode does not currently support " +
            "autoescape=\"strict\".",
            node);
      }
      // Templates with autoescape="strict" return the SanitizedContent wrapper for its kind:
      // - Call sites are wrapped in an escaper. Returning SanitizedContent prevents re-escaping.
      // - The topmost call into Soy returns a SanitizedContent. This will make it easy to take
      // the result of one template and feed it to another, and also to confidently assign sanitized
      // HTML content to innerHTML.
      resultJsExpr = JsExprUtils.maybeWrapAsSanitizedContent(
          node.getContentKind(), resultJsExpr);
    }
    jsCodeBuilder.appendLine("return ", resultJsExpr.getText(), ";");

    localVarTranslations.pop();
  }

  @Override protected void visitGoogMsgNode(GoogMsgNode node) {
    if (assistantForMsgs == null) {
      assistantForMsgs = new GenJsCodeVisitorAssistantForMsgs(
          this, jsExprTranslator, genCallCodeUtils, isComputableAsJsExprsVisitor, jsCodeBuilder,
          localVarTranslations, genJsExprsVisitor);
    }
    assistantForMsgs.visitForUseByMaster(node);
  }


  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {
    throw new AssertionError();
  }


  @Override protected void visitPrintNode(PrintNode node) {
    jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));
  }


  /**
   * Example:
   * <pre>
   *   {let $boo: $foo.goo[$moo] /}
   * </pre>
   * might generate
   * <pre>
   *   var boo35 = opt_data.foo.goo[opt_data.moo];
   * </pre>
   */
  @Override protected void visitLetValueNode(LetValueNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    JsExpr valueJsExpr =
        jsExprTranslator.translateToJsExpr(node.getValueExpr(), null, localVarTranslations);
    jsCodeBuilder.appendLine("var ", generatedVarName, " = ", valueJsExpr.getText(), ";");

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new JsExpr(generatedVarName, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {let $boo}
   *     Hello {$name}
   *   {/let}
   * </pre>
   * might generate
   * <pre>
   *   var boo35 = 'Hello ' + opt_data.name;
   * </pre>
   */
  @Override protected void visitLetContentNode(LetContentNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
    jsCodeBuilder.pushOutputVar(generatedVarName);

    visitChildren(node);

    jsCodeBuilder.popOutputVar();
    localVarTranslations.pop();

    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      jsCodeBuilder.appendLine(generatedVarName, " = ", generatedVarName, ".toString();");
    }

    if (node.getContentKind() != null) {
      // If the let node had a content kind specified, it was autoescaped in the corresponding
      // context. Hence the result of evaluating the let block is wrapped in a SanitizedContent
      // instance of the appropriate kind.

      // The expression for the constructor of SanitizedContent of the appropriate kind (e.g.,
      // "soydata.VERY_UNSAFE.ordainSanitizedHtml"), or null if the node has no 'kind' attribute.
      final String sanitizedContentOrdainer =
          NodeContentKinds.toJsSanitizedContentOrdainer(node.getContentKind());

      jsCodeBuilder.appendLine(generatedVarName, " = ", sanitizedContentOrdainer, "(",
          generatedVarName, ");");
    }

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new JsExpr(generatedVarName, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {if $boo.foo &gt; 0}
   *     ...
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   if (opt_data.boo.foo &gt; 0) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitIfNode(IfNode node) {

    if (isComputableAsJsExprsVisitor.exec(node)) {
      jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));
      return;
    }

    // ------ Not computable as JS expressions, so generate full code. ------

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        JsExpr condJsExpr = jsExprTranslator.translateToJsExpr(
            icn.getExprUnion().getExpr(), icn.getExprText(), localVarTranslations);
        if (icn.getCommandName().equals("if")) {
          jsCodeBuilder.appendLine("if (", condJsExpr.getText(), ") {");
        } else {  // "elseif" block
          jsCodeBuilder.appendLine("} else if (", condJsExpr.getText(), ") {");
        }

        jsCodeBuilder.increaseIndent();
        visit(icn);
        jsCodeBuilder.decreaseIndent();

      } else if (child instanceof IfElseNode) {
        IfElseNode ien = (IfElseNode) child;

        jsCodeBuilder.appendLine("} else {");

        jsCodeBuilder.increaseIndent();
        visit(ien);
        jsCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    jsCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {switch $boo}
   *     {case 0}
   *       ...
   *     {case 1, 2}
   *       ...
   *     {default}
   *       ...
   *   {/switch}
   * </pre>
   * might generate
   * <pre>
   *   switch (opt_data.boo) {
   *     case 0:
   *       ...
   *       break;
   *     case 1:
   *     case 2:
   *       ...
   *       break;
   *     default:
   *       ...
   *   }
   * </pre>
   */
  @Override protected void visitSwitchNode(SwitchNode node) {

    JsExpr switchValueJsExpr = jsExprTranslator.translateToJsExpr(
        node.getExpr(), node.getExprText(), localVarTranslations);
    jsCodeBuilder.appendLine("switch (", switchValueJsExpr.getText(), ") {");
    jsCodeBuilder.increaseIndent();

    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;

        for (ExprNode caseExpr : scn.getExprList()) {
          JsExpr caseJsExpr =
              jsExprTranslator.translateToJsExpr(caseExpr, null, localVarTranslations);
          jsCodeBuilder.appendLine("case ", caseJsExpr.getText(), ":");
        }

        jsCodeBuilder.increaseIndent();
        visit(scn);
        jsCodeBuilder.appendLine("break;");
        jsCodeBuilder.decreaseIndent();

      } else if (child instanceof SwitchDefaultNode) {
        SwitchDefaultNode sdn = (SwitchDefaultNode) child;

        jsCodeBuilder.appendLine("default:");

        jsCodeBuilder.increaseIndent();
        visit(sdn);
        jsCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {ifempty}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   var fooList2 = opt_data.boo.foos;
   *   var fooListLen2 = fooList2.length;
   *   if (fooListLen2 > 0) {
   *     ...
   *   } else {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNode(ForeachNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String nodeId = Integer.toString(node.getId());
    String listVarName = baseVarName + "List" + nodeId;
    String listLenVarName = baseVarName + "ListLen" + nodeId;

    // Define list var and list-len var.
    JsExpr dataRefJsExpr = jsExprTranslator.translateToJsExpr(
        node.getExpr(), node.getExprText(), localVarTranslations);
    jsCodeBuilder.appendLine("var ", listVarName, " = ", dataRefJsExpr.getText(), ";");
    jsCodeBuilder.appendLine("var ", listLenVarName, " = ", listVarName, ".length;");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      jsCodeBuilder.appendLine("if (", listLenVarName, " > 0) {");
      jsCodeBuilder.increaseIndent();
    }

    // Generate code for nonempty case.
    visit(node.getChild(0));

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("} else {");
      jsCodeBuilder.increaseIndent();

      // Generate code for empty case.
      visit(node.getChild(1));

      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("}");
    }
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   for (var fooIndex2 = 0; fooIndex2 &lt; fooListLen2; fooIndex2++) {
   *     var fooData2 = fooList2[fooIndex2];
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = Integer.toString(node.getForeachNodeId());
    String listVarName = baseVarName + "List" + foreachNodeId;
    String listLenVarName = baseVarName + "ListLen" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // The start of the JS 'for' loop.
    jsCodeBuilder.appendLine(
        "for (var ", indexVarName, " = 0; ",
        indexVarName, " < ", listLenVarName, "; ",
        indexVarName, "++) {");
    jsCodeBuilder.increaseIndent();
    jsCodeBuilder.appendLine("var ", dataVarName, " = ", listVarName, "[", indexVarName, "];");

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JsExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        baseVarName, new JsExpr(dataVarName, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isFirst",
        new JsExpr(indexVarName + " == 0", Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isLast",
        new JsExpr(indexVarName + " == " + listLenVarName + " - 1",
            Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__index", new JsExpr(indexVarName, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the JS 'for' loop.
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {for $i in range(1, $boo)}
   *     ...
   *   {/for}
   * </pre>
   * might generate
   * <pre>
   *   var iLimit4 = opt_data.boo;
   *   for (var i4 = 1; i4 &lt; iLimit4; i4++) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForNode(ForNode node) {

    String varName = node.getVarName();
    String nodeId = Integer.toString(node.getId());

    // Get the JS expression text for the init/limit/increment values.
    List<ExprRootNode<?>> rangeArgs = Lists.newArrayList(node.getRangeArgs());
    String incrementJsExprText =
        (rangeArgs.size() == 3) ?
        jsExprTranslator.translateToJsExpr(rangeArgs.remove(2), null, localVarTranslations)
            .getText() :
        "1" /* default */;
    String initJsExprText =
        (rangeArgs.size() == 2) ?
        jsExprTranslator.translateToJsExpr(rangeArgs.remove(0), null, localVarTranslations)
            .getText() :
        "0" /* default */;
    String limitJsExprText =
        jsExprTranslator.translateToJsExpr(rangeArgs.get(0), null, localVarTranslations).getText();

    // If any of the JS expressions for init/limit/increment isn't an integer, precompute its value.
    String initCode;
    if (INTEGER.matcher(initJsExprText).matches()) {
      initCode = initJsExprText;
    } else {
      initCode = varName + "Init" + nodeId;
      jsCodeBuilder.appendLine("var ", initCode, " = ", initJsExprText, ";");
    }

    String limitCode;
    if (INTEGER.matcher(limitJsExprText).matches()) {
      limitCode = limitJsExprText;
    } else {
      limitCode = varName + "Limit" + nodeId;
      jsCodeBuilder.appendLine("var ", limitCode, " = ", limitJsExprText, ";");
    }

    String incrementCode;
    if (INTEGER.matcher(incrementJsExprText).matches()) {
      incrementCode = incrementJsExprText;
    } else {
      incrementCode = varName + "Increment" + nodeId;
      jsCodeBuilder.appendLine("var ", incrementCode, " = ", incrementJsExprText, ";");
    }

    // The start of the JS 'for' loop.
    String incrementStmt = incrementCode.equals("1") ?
        varName + nodeId + "++" : varName + nodeId + " += " + incrementCode;
    jsCodeBuilder.appendLine(
        "for (var ",
        varName, nodeId, " = ", initCode, "; ",
        varName, nodeId, " < ", limitCode, "; ",
        incrementStmt,
        ") {");
    jsCodeBuilder.increaseIndent();

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JsExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(varName, new JsExpr(varName + nodeId, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the JS 'for' loop.
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo: 88 /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}
   *       Hello {$name}
   *     {/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   output += some.func(opt_data);
   *   output += some.func(opt_data.boo.foo);
   *   output += some.func({goo: 88});
   *   output += some.func(soy.$$augmentMap(opt_data.boo, {goo: 'Hello ' + opt_data.name});
   * </pre>
   */
  @Override protected void visitCallNode(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      // For 'stringbuilder' code style, pass the current output var to collect the call's output.
      genCallCodeUtils.genAndAppendCallStmt(jsCodeBuilder, node, localVarTranslations);

    } else {
      // For 'concat' code style, we simply add the call's result to the current output var.
      JsExpr callExpr = genCallCodeUtils.genCallExpr(node, localVarTranslations);
      jsCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
    }
  }


  @Override protected void visitCallParamContentNode(CallParamContentNode node) {

    // This node should only be visited when it's not computable as JS expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsJsExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as JS expressions.");
    }

    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
    jsCodeBuilder.pushOutputVar("param" + node.getId());

    visitChildren(node);

    jsCodeBuilder.popOutputVar();
    localVarTranslations.pop();
  }


  /**
   * Example:
   * <pre>
   *   {log}Blah {$boo}.{/log}
   * </pre>
   * might generate
   * <pre>
   *   window.console.log('Blah ' + opt_data.boo + '.');
   * </pre>
   *
   * <p> If the log msg is not computable as JS exprs, then it will be built in a local var
   * logMsg_s##, e.g.
   * <pre>
   *   var logMsg_s14 = ...
   *   window.console.log(logMsg_s14);
   * </pre>
   */
  @Override protected void visitLogNode(LogNode node) {

    if (isComputableAsJsExprsVisitor.execOnChildren(node)) {
      List<JsExpr> logMsgJsExprs = genJsExprsVisitor.execOnChildren(node);
      JsExpr logMsgJsExpr = JsExprUtils.concatJsExprs(logMsgJsExprs);
      jsCodeBuilder.appendLine("window.console.log(", logMsgJsExpr.getText(), ");");

    } else {
      // Must build log msg in a local var logMsg_s##.
      localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
      jsCodeBuilder.pushOutputVar("logMsg_s" + node.getId());

      visitChildren(node);

      jsCodeBuilder.popOutputVar();
      localVarTranslations.pop();

      jsCodeBuilder.appendLine("window.console.log(logMsg_s", Integer.toString(node.getId()), ");");
    }
  }


  /**
   * Example:
   * <pre>
   *   {debugger}
   * </pre>
   * generates
   * <pre>
   *   debugger;
   * </pre>
   */
  @Override protected void visitDebuggerNode(DebuggerNode node) {
    jsCodeBuilder.appendLine("debugger;");
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {

      if (node instanceof BlockNode) {
        localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
        visitChildren((BlockNode) node);
        localVarTranslations.pop();

      } else {
        visitChildren((ParentSoyNode<?>) node);
      }

      return;
    }

    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Simply generate JS expressions for this node and add them to the current output var.
      jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));

    } else {
      // Need to implement visit*Node() for the specific case.
      throw new UnsupportedOperationException();
    }
  }

}
