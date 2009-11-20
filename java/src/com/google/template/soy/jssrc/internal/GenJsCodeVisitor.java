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
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachIfemptyNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.MsgPlaceholderNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.SoyCommandNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
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


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

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
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to be used.
   * @param canInitOutputVarVisitor The CanInitOutputVarVisitor to be used.
   * @param genJsExprsVisitorFactory Factory for creating an instance of GenJsExprsVisitor.
   */
  @Inject
  GenJsCodeVisitor(SoyJsSrcOptions jsSrcOptions, JsExprTranslator jsExprTranslator,
                   GenCallCodeUtils genCallCodeUtils,
                   IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
                   CanInitOutputVarVisitor canInitOutputVarVisitor,
                   GenJsExprsVisitorFactory genJsExprsVisitorFactory) {
    this.jsSrcOptions = jsSrcOptions;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genJsExprsVisitorFactory = genJsExprsVisitorFactory;
  }


  @Override protected void setup() {
    jsFilesContents = Lists.newArrayList();
    jsCodeBuilder = null;
    localVarTranslations = null;
  }


  @VisibleForTesting
  @Override protected void visit(SoyNode node) {
    super.visit(node);
  }


  @Override protected List<String> getResult() {
    return jsFilesContents;
  }


  @Override protected void visitChildren(ParentSoyNode<? extends SoyNode> node) {

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
  // Implementations for concrete classes.


  @Override protected void visitInternal(SoyFileSetNode node) {

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
  @Override protected void visitInternal(SoyFileNode node) {

    jsCodeBuilder = new JsCodeBuilder(jsSrcOptions.getCodeStyle());

    jsCodeBuilder.appendLine("// This file was automatically generated from ",
                             node.getFileName(), ".");
    jsCodeBuilder.appendLine("// Please don't edit this file by hand.");

    // Add code to define JS namespaces or add provide/require calls for Closure Library.
    jsCodeBuilder.appendLine();
    if (jsSrcOptions.shouldProvideRequireSoyNamespaces()) {
      addCodeToProvideRequireSoyNamespaces(node);
    } else if (jsSrcOptions.shouldProvideRequireJsFunctions()) {
      addCodeToProvideRequireJsFunctions(node);
    } else {
      addCodeToDefineJsNamespaces(node);
    }

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      jsCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.setTemplateName(template.getTemplateName());
      }
    }

    jsFilesContents.add(jsCodeBuilder.getCode());
    jsCodeBuilder = null;
  }


  /**
   * Helper for visitInternal(SoyFileNode) to add code to define JS namespaces.
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
   * Helper for visitInternal(SoyFileNode) to add code to provide/require Soy namespaces.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideRequireSoyNamespaces(SoyFileNode soyFile) {

    jsCodeBuilder.appendLine("goog.provide('", soyFile.getNamespace(), "');");

    jsCodeBuilder.appendLine();

    jsCodeBuilder.appendLine("goog.require('soy');");
    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      jsCodeBuilder.appendLine("goog.require('soy.StringBuilder');");
    }
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
   * Helper for visitInternal(SoyFileNode) to add code to provide/require template JS functions.
   * @param soyFile The node we're visiting.
   */
  private void addCodeToProvideRequireJsFunctions(SoyFileNode soyFile) {

    SortedSet<String> templateNames = Sets.newTreeSet();
    for (TemplateNode template : soyFile.getChildren()) {
      if (template.isOverride()) {
        continue;  // generated function name already provided
      }
      templateNames.add(template.getTemplateName());
    }
    for (String templateName : templateNames) {
      jsCodeBuilder.appendLine("goog.provide('", templateName, "');");
    }

    jsCodeBuilder.appendLine();

    jsCodeBuilder.appendLine("goog.require('soy');");
    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      jsCodeBuilder.appendLine("goog.require('soy.StringBuilder');");
    }
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
   *   if (!opt_sb) return output.toString();
   * };
   * </pre>
   */
  @Override protected void visitInternal(TemplateNode node) {

    boolean isCodeStyleStringbuilder = jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER;

    localVarTranslations = new ArrayDeque<Map<String, JsExpr>>();
    genJsExprsVisitor = genJsExprsVisitorFactory.create(localVarTranslations);

    if (jsSrcOptions.shouldGenerateJsdoc()) {
      jsCodeBuilder.appendLine("/**");
      jsCodeBuilder.appendLine(" * @param {Object.<string, *>} opt_data");
      if (isCodeStyleStringbuilder) {
        jsCodeBuilder.appendLine(" * @param {soy.StringBuilder} opt_sb");
        jsCodeBuilder.appendLine(" * @return {string|undefined}");
      } else {
        jsCodeBuilder.appendLine(" * @return {string}");
      }
      jsCodeBuilder.appendLine(" */");
    }

    if (isCodeStyleStringbuilder) {
      jsCodeBuilder.appendLine(node.getTemplateName(), " = function(opt_data, opt_sb) {");
    } else {
      jsCodeBuilder.appendLine(node.getTemplateName(), " = function(opt_data) {");
    }
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
        jsCodeBuilder.appendLine("if (!opt_sb) return output.toString();");
      } else {
        jsCodeBuilder.appendLine("return output;");
      }
      jsCodeBuilder.popOutputVar();
    }

    localVarTranslations.pop();
    jsCodeBuilder.decreaseIndent();
    jsCodeBuilder.appendLine("};");
  }


  /**
   * Example:
   * <pre>{@literal
   *   {msg desc="Link to help content."}Learn more{/msg}
   *   {msg desc="Tells user how to access a product." hidden="true"}
   *     Click <a href="{$url}">here</a> to access {$productName}.
   *   {/msg}
   * }</pre>
   * might generate
   * <pre>
   *   /** @desc Link to help content. *{@literal /}
   *   var MSG_UNNAMED_9 = goog.getMsg('Learn more');
   *   /** @desc Tells user how to access a product.
   *    *  @hidden *{@literal /}
   *   var MSG_UNNAMED_10 = goog.getMsg(
   *       'Click {$startLink}here{$endLink} to access {$productName}.',
   *       {startLink: {@literal '<a href="' + opt_data.url + '">'},
   *        endLink: {@literal '</a>'},
   *        productName: opt_data.productName});
   * </pre>
   */
  @Override protected void visitInternal(GoogMsgNode node) {

    // Build the code for the message text and the individual code bits for each placeholder (i.e.
    // "<placeholderName>: <exprCode>").
    StringBuilder msgTextCodeSb = new StringBuilder();
    List<String> placeholderCodeBits = new ArrayList<String>();
    Set<String> seenPlaceholderNames = new HashSet<String>();

    for (SoyNode child : node.getChildren()) {

      if (child instanceof RawTextNode) {
        // Only need to add to message text.
        msgTextCodeSb.append(((RawTextNode) child).getRawText());

      } else if (child instanceof MsgPlaceholderNode) {
        String placeholderName = node.getPlaceholderName((MsgPlaceholderNode) child);
        String googMsgPlaceholderName = genGoogMsgPlaceholderName(placeholderName);

        // Add placeholder to message text.
        msgTextCodeSb.append("{$").append(googMsgPlaceholderName).append("}");

        // If the placeholder name has not already been seen, then this child must be its
        // representative node. Add the code bit for the placeholder now.
        if (!seenPlaceholderNames.contains(placeholderName)) {
          seenPlaceholderNames.add(placeholderName);
          String placeholderCodeBit =
              "'" + googMsgPlaceholderName + "': " +
              genGoogMsgPlaceholderExpr((MsgPlaceholderNode) child).getText();
          placeholderCodeBits.add(placeholderCodeBit);
        }

      } else {
        String nodeStringForErrorMsg =
            (node instanceof SoyCommandNode) ? "Tag " + ((SoyCommandNode) node).getTagString() :
            "Node " + node.toString();
        throw new SoySyntaxException(
            nodeStringForErrorMsg + " is not allowed to be a direct child of a 'msg' tag.");
      }
    }

    String msgTextCode = BaseUtils.escapeToSoyString(msgTextCodeSb.toString(), false);
    // Note: BaseUtils.escapeToSoyString() builds a Soy string, which is usually a valid JS string.
    // The rare exception is a string containing a Unicode Format character (Unicode category "Cf")
    // because of the JavaScript language quirk that requires all category "Cf" characters to be
    // escaped in JS strings. Therefore, we must call JsSrcUtils.escapeUnicodeFormatChars() on the
    // result.
    msgTextCode = JsSrcUtils.escapeUnicodeFormatChars(msgTextCode);

    // Finally, generate the code for the whole message definition.
    jsCodeBuilder.indent().append("/** @desc ", node.getDesc());
    if (node.isHidden()) {
      jsCodeBuilder.append("\n");
      jsCodeBuilder.indent().append(" *  @hidden");
    }
    jsCodeBuilder.append(" */\n");

    jsCodeBuilder.indent().append("var ", node.getGoogMsgName(), " = goog.getMsg(");

    if (placeholderCodeBits.size() == 0) {
      // If no placeholders, we put the message text on the same line.
      jsCodeBuilder.append(msgTextCode);
    } else {
      // If there are placeholders, we put the message text on a new line, indented 4 extra spaces.
      // And we line up the placeholders too.
      jsCodeBuilder.append("\n");
      jsCodeBuilder.indent().append("    ", msgTextCode);
      boolean isFirst = true;
      for (String placeholderCodeBit : placeholderCodeBits) {
        jsCodeBuilder.append(",\n");
        if (isFirst) {
          isFirst = false;
          jsCodeBuilder.indent().append("    {");
        } else {
          jsCodeBuilder.indent().append("     ");
        }
        jsCodeBuilder.append(placeholderCodeBit);
      }
      jsCodeBuilder.append("}");
    }

    jsCodeBuilder.append(");\n");
  }


  /**
   * Private helper for {@code visitInternal(GoogMsgNode)} to convert a placeholder name into a
   * goog.getMsg placeholder name.
   *
   * A (standard) placeholder name has upper-underscore format. A goog.getMsg placeholder name must
   * be lower-camelcase, possibly with an underscore-number suffix.
   *
   * @param placeholderName The placeholder name to convert.
   * @return The generated goog.getMsg placeholder name for the given (standard) placeholder name.
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
   * Private helper for {@code visitInternal(GoogMsgNode)} to generate the JS expr for a given
   * placeholder.
   *
   * @param msgPlaceholderNode The placeholder to generate the JS expr for.
   * @return The JS expr for the given placeholder.
   */
  private JsExpr genGoogMsgPlaceholderExpr(MsgPlaceholderNode msgPlaceholderNode) {

    if (msgPlaceholderNode instanceof MsgHtmlTagNode &&
        !isComputableAsJsExprsVisitor.exec(msgPlaceholderNode)) {
      // This is a MsgHtmlTagNode that is not computable as JS expressions. Visit it to
      // generate code to define the 'htmlTag<n>' variable.
      visit(msgPlaceholderNode);
      return new JsExpr("htmlTag" + msgPlaceholderNode.getId(), Integer.MAX_VALUE);

    } else if (msgPlaceholderNode instanceof CallNode) {
      // If the CallNode has any CallParamContentNode children (i.e. this GoogMsgNode's
      // grandchildren) that are not computable as JS expressions, visit them to generate code
      // to define their respective 'param<n>' variables.
      CallNode callNode = (CallNode) msgPlaceholderNode;
      for (CallParamNode grandchild : callNode.getChildren()) {
        if (grandchild instanceof CallParamContentNode &&
            !isComputableAsJsExprsVisitor.exec(grandchild)) {
          visit(grandchild);
        }
      }
      return genCallCodeUtils.genCallExpr(callNode, localVarTranslations);

    } else {
      return JsExprUtils.concatJsExprs(genJsExprsVisitor.exec(msgPlaceholderNode));
    }
  }


  /**
   * Example:
   * <pre>{@literal
   *   <a href="http://www.google.com/search?hl=en
   *     {for $i in range(3)}
   *       &amp;param{$i}={$i}
   *     {/foreach}
   *   ">
   * }</pre>
   * might generate
   * <pre>{@literal
   *   var htmlTag84 = (new soy.StringBuilder()).append('<a href="');
   *   for (var i80 = 1; i80 &lt; 3; i80++) {
   *     htmlTag84.append('&amp;param', i80, '=', i80);
   *   }
   *   htmlTag84.append('">');
   * }</pre>
   */
  @Override protected void visitInternal(MsgHtmlTagNode node) {

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


  @Override protected void visitInternal(PrintNode node) {
    jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));
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
  @Override protected void visitInternal(IfNode node) {

    if (isComputableAsJsExprsVisitor.exec(node)) {
      jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));
      return;
    }

    // ------ Not computable as JS expressions, so generate full code. ------

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        JsExpr condJsExpr = jsExprTranslator.translateToJsExpr(
            icn.getExpr(), icn.getExprText(), localVarTranslations);
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
  @Override protected void visitInternal(SwitchNode node) {

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
  @Override protected void visitInternal(ForeachNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String nodeId = node.getId();
    String listVarName = baseVarName + "List" + nodeId;
    String listLenVarName = baseVarName + "ListLen" + nodeId;

    // Define list var and list-len var.
    JsExpr dataRefJsExpr = jsExprTranslator.translateToJsExpr(
        node.getDataRef(), node.getDataRefText(), localVarTranslations);
    jsCodeBuilder.appendLine("var ", listVarName, " = ", dataRefJsExpr.getText(), ";");
    jsCodeBuilder.appendLine("var ", listLenVarName, " = ", listVarName, ".length;");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      jsCodeBuilder.appendLine("if (", listLenVarName, " > 0) {");
      jsCodeBuilder.increaseIndent();
    }

    // Generate code for nonempty case.
    visit((ForeachNonemptyNode) node.getChild(0));

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      jsCodeBuilder.decreaseIndent();
      jsCodeBuilder.appendLine("} else {");
      jsCodeBuilder.increaseIndent();

      // Generate code for empty case.
      visit((ForeachIfemptyNode) node.getChild(1));

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
  @Override protected void visitInternal(ForeachNonemptyNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = node.getForeachNodeId();
    String listVarName = baseVarName + "List" + foreachNodeId;
    String listLenVarName = baseVarName + "ListLen" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // The start of the JS 'for' loop.
    jsCodeBuilder.appendLine("for (var ", indexVarName, " = 0; ",
                             indexVarName, " < ", listLenVarName, "; ",
                             indexVarName, "++) {");
    jsCodeBuilder.increaseIndent();
    jsCodeBuilder.appendLine("var ", dataVarName, " = ", listVarName, "[", indexVarName, "];");

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, JsExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        baseVarName, new JsExpr(dataVarName, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isFirst", new JsExpr(indexVarName + " == 0",
                                               Operator.EQUAL.getPrecedence()));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isLast", new JsExpr(indexVarName + " == " + listLenVarName + " - 1",
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
  @Override protected void visitInternal(ForNode node) {

    String varName = node.getLocalVarName();
    String nodeId = node.getId();

    // Get the JS expression text for the init/limit/increment values.
    List<ExprRootNode<ExprNode>> rangeArgs = Lists.newArrayList(node.getRangeArgs());
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
    String incrementStmt =
        (incrementCode.equals("1")) ? varName + nodeId + "++"
                                    : varName + nodeId + " += " + incrementCode;
    jsCodeBuilder.appendLine("for (var ",
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
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="88" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}
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
  @Override protected void visitInternal(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as JS
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsJsExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    if (jsSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      // For 'stringbuilder' code style, pass the current output var to collect the call's output.
      JsExpr objToPass = genCallCodeUtils.genObjToPass(
          node,
          localVarTranslations);
      jsCodeBuilder.indent().append(node.getCalleeName(), "(", objToPass.getText(), ", ")
          .appendOutputVarName().append(");\n");

    } else {
      // For 'concat' code style, we simply add the call's result to the current output var.
      JsExpr callExpr = genCallCodeUtils.genCallExpr(node, localVarTranslations);
      jsCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
    }
  }


  @Override protected void visitInternal(CallParamContentNode node) {

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
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {

    if (isComputableAsJsExprsVisitor.exec(node)) {
      // Simply generate JS expressions for this node and add them to the current output var.
      jsCodeBuilder.addToOutputVar(genJsExprsVisitor.exec(node));

    } else {
      // Need to implement visitInternal() for the specific case.
      throw new UnsupportedOperationException();
    }
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    localVarTranslations.push(Maps.<String, JsExpr>newHashMap());
    visitChildren(node);
    localVarTranslations.pop();
  }

}
