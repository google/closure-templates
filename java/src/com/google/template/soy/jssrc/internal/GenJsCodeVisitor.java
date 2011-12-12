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
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.SoyJsSrcOptions.CodeStyle;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.msgs.restricted.IcuSyntaxUtils;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.IsUsingIjData;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgPluralRemainderNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoytreeUtils;
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
import java.util.Set;
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

  /** Regex pattern for an underscore-number suffix. */
  private static final Pattern UNDERSCORE_NUMBER_SUFFIX = Pattern.compile("_[0-9]+$");

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

  /** The GenJsExprsVisitor used by this instance. */
  @VisibleForTesting protected GenJsExprsVisitor genJsExprsVisitor;

  /** The JsCodeBuilder to build the current JS file being generated (during a run). */
  @VisibleForTesting protected JsCodeBuilder jsCodeBuilder;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  @VisibleForTesting protected Deque<Map<String, JsExpr>> localVarTranslations;


  /**
   * @param jsSrcOptions The options for generating JS source code.
   * @param isUsingIjData Whether any of the Soy code uses injected data.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to be used.
   * @param canInitOutputVarVisitor The CanInitOutputVarVisitor to be used.
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
    visit(node);
    return jsFilesContents;
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
        throw sse.setFilePath(soyFile.getFilePath());
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
        throw sse.setTemplateName(template.getTemplateNameForUserMsgs());
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
    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      jsCodeBuilder.appendLine("goog.require('soy.StringBuilder');");
    }

    if (jsSrcOptions.getUseGoogIsRtlForBidiGlobalDir()) {
      jsCodeBuilder.appendLine("goog.require('", GOOG_IS_RTL_NAMESPACE, "');");
    }

    if ((new HasPluralSelectMsgVisitor()).exec(soyFile)) {
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
        throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
            "When using the option to provide/require Soy namespaces, found a called template \"" +
            calleeNotInFile + "\" that does not reside in a namespace.", null, soyFile);
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

    if (jsSrcOptions.shouldGenerateJsdoc()) {
      jsCodeBuilder.appendLine("/**");
      jsCodeBuilder.appendLine(" * @param {Object.<string, *>=} opt_data");
      if (isCodeStyleStringbuilder) {
        jsCodeBuilder.appendLine(" * @param {soy.StringBuilder=} opt_sb");
      } else if (isUsingIjData) {
        jsCodeBuilder.appendLine(" * @param {*=} opt_ignored");
      }
      if (isUsingIjData) {
        jsCodeBuilder.appendLine(" * @param {Object.<string, *>=} opt_ijData");
      }
      jsCodeBuilder.appendLine(" * @return {string}");
      jsCodeBuilder.appendLine(" * @notypecheck");
      jsCodeBuilder.appendLine(" */");
    }

    jsCodeBuilder.appendLine(
        node.getTemplateName(), " = function(opt_data",
        (isCodeStyleStringbuilder ? ", opt_sb" : isUsingIjData ? ", opt_ignored" : ""),
        (isUsingIjData ? ", opt_ijData" : ""), ") {");
    jsCodeBuilder.increaseIndent();
    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());

    if (!isCodeStyleStringbuilder && isComputableAsJsExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as JS
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the JS expressions and return the result.

      List<JsExpr> templateBodyJsExprs = genJsExprsVisitor.exec(node);
      JsExpr templateBodyJsExpr = JsExprUtils.concatJsExprs(templateBodyJsExprs);
      jsCodeBuilder.appendLine("return ", templateBodyJsExpr.getText(), ";");

    } else {
      // Case 2: Normal case.

      jsCodeBuilder.pushOutputVar("output");
      if (isCodeStyleStringbuilder) {
        jsCodeBuilder.appendLine("var output = opt_sb || new soy.StringBuilder();");
        jsCodeBuilder.setOutputVarInited();
      }

      visitChildren(node);

      if (isCodeStyleStringbuilder) {
        jsCodeBuilder.appendLine("return opt_sb ? '' : output.toString();");
      } else {
        jsCodeBuilder.appendLine("return output;");
      }
      jsCodeBuilder.popOutputVar();
    }

    localVarTranslations.pop();
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("};");

    // ------ If delegate template, add a statement to register it. ------
    if (node instanceof TemplateDelegateNode) {
      TemplateDelegateNode nodeAsDelTemplate = (TemplateDelegateNode) node;
      jsCodeBuilder.appendLine(
          "soy.$$registerDelegateFn(soy.$$getDelegateId('",
          nodeAsDelTemplate.getDelTemplateName(), "'), ",
          Integer.toString(nodeAsDelTemplate.getDelPriority()), ", ",
          nodeAsDelTemplate.getTemplateName(), ");");
    }
  }


  /**
   * Example:
   * <xmp>
   *   {msg desc="Link to help content."}Learn more{/msg}
   *   {msg desc="Tells user how to access a product." hidden="true"}
   *     Click <a href="}{$url}">here</a> to access {$productName}.
   *   {/msg}
   * </xmp>
   * might generate
   * <xmp>
   *   /** @desc Link to help content. *{@literal /}
   *   var MSG_UNNAMED_9 = goog.getMsg('Learn more');
   *   /** @desc Tells user how to access a product.
   *    *  @hidden *{@literal /}
   *   var MSG_UNNAMED_10 = goog.getMsg(
   *       'Click {$startLink}here{$endLink} to access {$productName}.',
   *       {startLink: '<a href="' + opt_data.url + '">',
   *        endLink: '</a>',
   *        productName: opt_data.productName});
   * </xmp>
   */
  @Override protected void visitGoogMsgNode(GoogMsgNode node) {

    // Build the code for the message text and the individual code bits for each placeholder (i.e.
    // "<placeholderName>: <exprCode>") and plural/select (i.e., "<var name>: <exprCode>").

    GoogMsgCodeGenInfo googMsgCodeGenInfo = new GoogMsgCodeGenInfo();
    genGoogMsgCodeForChildren(node, node, googMsgCodeGenInfo);

    String msgTextCode = BaseUtils.escapeToSoyString(
        googMsgCodeGenInfo.msgTextCodeSb.toString(), false);
    // Note: BaseUtils.escapeToSoyString() builds a Soy string, which is usually a valid JS string.
    // The rare exception is a string containing a Unicode Format character (Unicode category "Cf")
    // because of the JavaScript language quirk that requires all category "Cf" characters to be
    // escaped in JS strings. Therefore, we must call JsSrcUtils.escapeUnicodeFormatChars() on the
    // result.
    msgTextCode = JsSrcUtils.escapeUnicodeFormatChars(msgTextCode);

    // Finally, generate the code for the whole message definition.
    jsCodeBuilder.indent().append("/** ");
    if (node.getMeaning() != null) {
      jsCodeBuilder.append("@meaning ", node.getMeaning(), "\n");
      jsCodeBuilder.indent().append(" *  ");
    }
    jsCodeBuilder.append("@desc ", node.getDesc());
    if (node.isHidden()) {
      jsCodeBuilder.append("\n");
      jsCodeBuilder.indent().append(" *  @hidden");
    }
    jsCodeBuilder.append(" */\n");

    String googMsgName = node.getGoogMsgVarName();
    jsCodeBuilder.indent().append("var ", googMsgName, " = goog.getMsg(");

    if (googMsgCodeGenInfo.placeholderCodeBits.size() == 0) {
      // If no placeholders, we put the message text on the same line.
      jsCodeBuilder.append(msgTextCode);
    } else {
      // If there are placeholders, we put the message text on a new line, indented 4 extra spaces.
      // And we line up the placeholders too.
      jsCodeBuilder.append("\n");
      jsCodeBuilder.indent().append("    ", msgTextCode, ",");
      appendCodeBits(googMsgCodeGenInfo.placeholderCodeBits);
    }

    jsCodeBuilder.append(");\n");

    // For messages with plural/select commands inside, we don't want to output
    // the message.  Instead, the return value of the i18n function should be returned.
    if (googMsgCodeGenInfo.pluralSelectVarCodeBits.size() > 0) {
      // Pass the ICU message to goog.i18n.MessageFormat and capture the formatted
      // string in another variable.
      jsCodeBuilder.indent().append(
          "var ", node.getRenderedGoogMsgVarName(),
          " = (new goog.i18n.MessageFormat(", googMsgName, ")).formatIgnoringPound(");
      appendCodeBits(googMsgCodeGenInfo.pluralSelectVarCodeBits);
      jsCodeBuilder.append(");\n");
    }
  }


  /**
   * Private helper class for visitGoogMsgNode(GoogMsgNode).
   * Stores the data require for generating goog.geMsg() code.
   */
  private class GoogMsgCodeGenInfo {

    /**
     * The StringBuilder object holding the generated message text string
     * (before escaping and quoting).
     */
    public StringBuilder msgTextCodeSb;

    /** List of code bits for placeholders. */
    public List<String> placeholderCodeBits;

    /** Set of placeholder names for which we have already generated code bits. */
    public Set<String> seenPlaceholderNames;

    /** List of code bits for plural and select variables. */
    public List<String> pluralSelectVarCodeBits;

    /** Set of plural/select var names for which we have already generated code bits. */
    public Set<String> seenPluralSelectVarNames;

    public GoogMsgCodeGenInfo() {
      msgTextCodeSb = new StringBuilder();
      placeholderCodeBits = Lists.newArrayList();
      seenPlaceholderNames = Sets.newHashSet();
      pluralSelectVarCodeBits = Lists.newArrayList();
      seenPluralSelectVarNames = Sets.newHashSet();
    }
  }


  /**
   * Private helper for visitGoogMsgNode(GoogMsgNode).
   * Appends given code bits to Js code builder.
   * @param codeBits Code bits.
   */
  private void appendCodeBits(List<String> codeBits) {
    boolean isFirst = true;
    for (String codeBit : codeBits) {
      if (isFirst) {
        jsCodeBuilder.append("\n");
        jsCodeBuilder.indent().append("    {");
        isFirst = false;
      } else {
        jsCodeBuilder.append(",\n");
        jsCodeBuilder.indent().append("     ");
      }
      jsCodeBuilder.append(codeBit);
    }
    jsCodeBuilder.append("}");
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Generates goog.getMsg() code for a given parent node and its children.
   *
   * @param parentNode A parent node of one of these types: {@code GoogMsgNode},
   *     {@code MsgPluralCaseNode}, {@code MsgPluralDefaultNode},
   *     {@code MsgSelectCaseNode}, {@code MsgSelectDefaultNode}.
   * @param googMsgNode The enclosing {@code GoogMsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeForChildren(
      BlockNode parentNode, GoogMsgNode googMsgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    StringBuilder msgTextCodeSb = googMsgCodeGenInfo.msgTextCodeSb;
    if (parentNode instanceof MsgPluralCaseNode) {
      msgTextCodeSb.append(IcuSyntaxUtils.getPluralCaseOpenString(
          ((MsgPluralCaseNode) parentNode).getCaseNumber()));
    } else if (parentNode instanceof MsgPluralDefaultNode) {
      msgTextCodeSb.append(IcuSyntaxUtils.getPluralCaseOpenString(null));
    } else if (parentNode instanceof MsgSelectCaseNode) {
      msgTextCodeSb.append(IcuSyntaxUtils.getSelectCaseOpenString(
          ((MsgSelectCaseNode) parentNode).getCaseValue()));
    } else if (parentNode instanceof MsgSelectDefaultNode) {
      msgTextCodeSb.append(IcuSyntaxUtils.getSelectCaseOpenString(null));
    }

    for (StandaloneNode child : parentNode.getChildren()) {
      if (child instanceof RawTextNode) {
        msgTextCodeSb.append(((RawTextNode) child).getRawText());
      } else if (child instanceof MsgPlaceholderNode) {
        genGoogMsgCodeForMsgPlaceholderNode((MsgPlaceholderNode) child,
            googMsgNode, googMsgCodeGenInfo);
      } else if (child instanceof MsgPluralNode) {
        genGoogMsgCodeForPluralNode((MsgPluralNode) child, googMsgNode, googMsgCodeGenInfo);
      } else if (child instanceof MsgSelectNode) {
        genGoogMsgCodeForSelectNode((MsgSelectNode) child, googMsgNode, googMsgCodeGenInfo);
      } else if (child instanceof MsgPluralRemainderNode) {
        msgTextCodeSb.append(IcuSyntaxUtils.getPluralRemainderString());
      } else {
        String nodeStringForErrorMsg = (parentNode instanceof CommandNode) ?
            "Tag " + ((CommandNode) parentNode).getTagString() : "Node " + parentNode.toString();
        throw new SoySyntaxException(
            nodeStringForErrorMsg + " is not allowed to be a direct child of a 'msg' tag.");
      }
    }

    if (parentNode instanceof MsgPluralCaseNode || parentNode instanceof MsgPluralDefaultNode) {
      msgTextCodeSb.append(IcuSyntaxUtils.getPluralCaseCloseString());
    } else if (parentNode instanceof MsgSelectCaseNode ||
               parentNode instanceof MsgSelectDefaultNode) {
      msgTextCodeSb.append(IcuSyntaxUtils.getSelectCaseCloseString());
    }
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Generates code for a {@code MsgPluralNode} inside a message.
   * @param pluralNode A node of type {@code MsgPluralNode}.
   * @param googMsgNode The enclosing {@code GoogMsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeForPluralNode(
      MsgPluralNode pluralNode, GoogMsgNode googMsgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    String pluralVarName = googMsgNode.getPluralVarName(pluralNode);

    StringBuilder msgTextCodeSb = googMsgCodeGenInfo.msgTextCodeSb;
    msgTextCodeSb.append(IcuSyntaxUtils.getPluralOpenString(pluralVarName, pluralNode.getOffset()));
    updatePluralSelectVarCodeBits(
        googMsgCodeGenInfo,
        pluralVarName,
        jsExprTranslator.translateToJsExpr(
            pluralNode.getExpr(), null, localVarTranslations).getText());
    for (CaseOrDefaultNode child : pluralNode.getChildren()) {
      genGoogMsgCodeForChildren(child, googMsgNode, googMsgCodeGenInfo);
    }
    msgTextCodeSb.append(IcuSyntaxUtils.getPluralCloseString());
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Generates code for a {@code MsgSelectNode} inside a message.
   * @param selectNode A node of type {@code MsgSelectNode}.
   * @param googMsgNode The enclosing {@code GoogMsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeForSelectNode(
      MsgSelectNode selectNode, GoogMsgNode googMsgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    String selectVarName = googMsgNode.getSelectVarName(selectNode);

    StringBuilder msgTextCodeSb = googMsgCodeGenInfo.msgTextCodeSb;
    msgTextCodeSb.append(IcuSyntaxUtils.getSelectOpenString(selectVarName));
    updatePluralSelectVarCodeBits(
        googMsgCodeGenInfo,
        selectVarName,
        jsExprTranslator.translateToJsExpr(
            selectNode.getExpr(), null, localVarTranslations).getText());
    for (CaseOrDefaultNode child : selectNode.getChildren()) {
      genGoogMsgCodeForChildren(child, googMsgNode, googMsgCodeGenInfo);
    }
    msgTextCodeSb.append(IcuSyntaxUtils.getSelectCloseString());
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Generates code for a normal {@code MsgPlaceholderNode} inside a message.
   * @param node A node of type {@code MsgPlaceholderNode}.
   * @param googMsgNode The enclosing {@code GoogMsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeForMsgPlaceholderNode(
      MsgPlaceholderNode node, GoogMsgNode googMsgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    String placeholderName = googMsgNode.getPlaceholderName(node);
    String googMsgPlaceholderName = genGoogMsgPlaceholderName(placeholderName);

    // Add placeholder to message text.
    googMsgCodeGenInfo.msgTextCodeSb.append("{$").append(googMsgPlaceholderName).append("}");
    // If the placeholder name has not already been seen, then this child must be its
    // representative node. Add the code bit for the placeholder now.
    updatePlaceholderCodeBits(
        googMsgCodeGenInfo, placeholderName, googMsgPlaceholderName,
        genGoogMsgPlaceholderExpr(node).getText());
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Updates code bits (and seenNames) for a plural/select var.
   * @param codeBits The list of code bits.
   * @param seenNames Set of seen names.
   * @param name The name.
   * @param googMsgName The enclosing {@code GoogMsgNode} object.
   * @param exprText The corresponding expression text.
   */
  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Updates code bits (and seenNames) for a plural/select var.
   * @param googMsgCodeGenInfo The object holding code-gen info.
   * @param pluralSelectVarName The plural or select var name. Should be upper underscore format.
   * @param exprText The JS expression text for the value.
   */
  private void updatePluralSelectVarCodeBits(
      GoogMsgCodeGenInfo googMsgCodeGenInfo, String pluralSelectVarName, String exprText) {
    if (googMsgCodeGenInfo.seenPluralSelectVarNames.contains(pluralSelectVarName)) {
      return;  // already added to code bits previously
    }
    googMsgCodeGenInfo.seenPluralSelectVarNames.add(pluralSelectVarName);

    // Add the code bit.
    String placeholderCodeBit = "'" + pluralSelectVarName + "': " + exprText;
    googMsgCodeGenInfo.pluralSelectVarCodeBits.add(placeholderCodeBit);
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Updates code bits (and seenNames) for a placeholder.
   * @param googMsgCodeGenInfo The object holding code-gen info.
   * @param placeholderName The placeholder name. Should be upper underscore format.
   * @param googMsgPlaceholderName The placeholder name for the goog msg. Should be in lower camel
   *     case, with optional underscore-number suffix.
   * @param exprText The JS expression text for the value.
   */
  private void updatePlaceholderCodeBits(
      GoogMsgCodeGenInfo googMsgCodeGenInfo, String placeholderName, String googMsgPlaceholderName,
      String exprText) {
    if (googMsgCodeGenInfo.seenPlaceholderNames.contains(placeholderName)) {
      return;  // already added to code bits previously
    }
    googMsgCodeGenInfo.seenPlaceholderNames.add(placeholderName);

    // Add the code bit.
    String placeholderCodeBit = "'" + googMsgPlaceholderName + "': " + exprText;
    googMsgCodeGenInfo.placeholderCodeBits.add(placeholderCodeBit);
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Converts a Soy placeholder name (in upper-underscore format) into a JS variable name (in
   * lower-camelcase format) used by goog.getMsg().  If the original name has a numeric suffix, that
   * will be preserved with an underscore.
   *
   * For example, the following transformations happen:
   * <li> N : n
   * <li> NUM_PEOPLE : numPeople
   * <li> PERSON_2 : person_2
   * <li>GENDER_OF_THE_MAIN_PERSON_3 : genderOfTheMainPerson_3
   *
   * @param placeholderName The placeholder name to convert.
   * @return The generated goog.getMsg name for the given (standard) Soy name.
   */
  private String genGoogMsgPlaceholderName(String placeholderName) {

    Matcher suffixMatcher = UNDERSCORE_NUMBER_SUFFIX.matcher(placeholderName);
    if (suffixMatcher.find()) {
      String base = placeholderName.substring(0, suffixMatcher.start());
      String suffix = suffixMatcher.group();
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, base) + suffix;
    } else {
      return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, placeholderName);
    }
  }


  /**
   * Private helper for {@code visitGoogMsgNode(GoogMsgNode)}.
   * Generates the JS expr for a given placeholder.
   *
   * @param msgPhNode The placeholder to generate the JS expr for.
   * @return The JS expr for the given placeholder.
   */
  private JsExpr genGoogMsgPlaceholderExpr(MsgPlaceholderNode msgPhNode) {

    List<JsExpr> contentJsExprs = Lists.newArrayList();

    for (StandaloneNode contentNode : msgPhNode.getChildren()) {

      if (contentNode instanceof MsgHtmlTagNode &&
          !isComputableAsJsExprsVisitor.exec(contentNode)) {
        // This is a MsgHtmlTagNode that is not computable as JS expressions. Visit it to
        // generate code to define the 'htmlTag<n>' variable.
        visit(contentNode);
        contentJsExprs.add(new JsExpr("htmlTag" + contentNode.getId(), Integer.MAX_VALUE));

      } else if (contentNode instanceof CallNode) {
        // If the CallNode has any CallParamContentNode children (i.e. this GoogMsgNode's
        // grandchildren) that are not computable as JS expressions, visit them to generate code
        // to define their respective 'param<n>' variables.
        CallNode callNode = (CallNode) contentNode;
        for (CallParamNode grandchild : callNode.getChildren()) {
          if (grandchild instanceof CallParamContentNode &&
              !isComputableAsJsExprsVisitor.exec(grandchild)) {
            visit(grandchild);
          }
        }
        contentJsExprs.add(genCallCodeUtils.genCallExpr(callNode, localVarTranslations));

      } else {
        contentJsExprs.addAll(genJsExprsVisitor.exec(contentNode));
      }
    }

    return JsExprUtils.concatJsExprs(contentJsExprs);
  }


  /**
   * Example:
   * <xmp>
   *   <a href="http://www.google.com/search?hl=en
   *     {for $i in range(3)}
   *       &amp;param{$i}={$i}
   *     {/for}
   *   ">
   * </xmp>
   * might generate
   * <xmp>
   *   var htmlTag84 = (new soy.StringBuilder()).append('<a href="');
   *   for (var i80 = 1; i80 &lt; 3; i80++) {
   *     htmlTag84.append('&amp;param', i80, '=', i80);
   *   }
   *   htmlTag84.append('">');
   * </xmp>
   */
  @Override protected void visitMsgHtmlTagNode(MsgHtmlTagNode node) {

    // This node should only be visited when it's not computable as JS expressions, because this
    // method just generates the code to define the temporary 'htmlTag<n>' variable.
    if (isComputableAsJsExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'htmlTag<n>' when not computable as JS expressions.");
    }

    jsCodeBuilder.pushOutputVar("htmlTag" + node.getId());
    visitChildren(node);
    jsCodeBuilder.popOutputVar();
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
   *   output += some.func(soy.$$augmentData(opt_data.boo, {goo: 'Hello ' + opt_data.name});
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
      JsExpr objToPass = genCallCodeUtils.genObjToPass(node, localVarTranslations);
      String calleeExprText = (node instanceof CallBasicNode) ?
          ((CallBasicNode) node).getCalleeName() :
          "soy.$$getDelegateFn(soy.$$getDelegateId('" +
              ((CallDelegateNode) node).getDelCalleeName() + "'))";
      jsCodeBuilder.indent()
          .append(calleeExprText, "(", objToPass.getText(), ", ").appendOutputVarName()
          .append(isUsingIjData ? ", opt_ijData" : "").append(");\n");

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
