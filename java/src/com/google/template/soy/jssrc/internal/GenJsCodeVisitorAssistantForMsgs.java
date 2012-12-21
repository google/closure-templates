/*
 * Copyright 2012 Google Inc.
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

import com.google.common.base.CaseFormat;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.base.BaseUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.msgs.restricted.IcuSyntaxUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralCaseNode;
import com.google.template.soy.soytree.MsgPluralDefaultNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgPluralRemainderNode;
import com.google.template.soy.soytree.MsgSelectCaseNode;
import com.google.template.soy.soytree.MsgSelectDefaultNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.jssrc.GoogMsgNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Assistant visitor for GenJsCodeVisitor to handle messages.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * @author Kai Huang
 */
class GenJsCodeVisitorAssistantForMsgs extends AbstractSoyNodeVisitor<Void> {


  /** Regex pattern for an underscore-number suffix. */
  private static final Pattern UNDERSCORE_NUMBER_SUFFIX = Pattern.compile("_[0-9]+$");


  /** Master instance of GenJsCodeVisitor. */
  private final GenJsCodeVisitor master;

  /** Instance of JsExprTranslator to use. */
  private final JsExprTranslator jsExprTranslator;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** The GenJsExprsVisitor used for the current template. */
  private final GenJsExprsVisitor genJsExprsVisitor;

  /** The JsCodeBuilder to build the current JS file being generated (during a run). */
  private final JsCodeBuilder jsCodeBuilder;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JsExpr>> localVarTranslations;


  /**
   * @param master The master GenJsCodeVisitor instance.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to use.
   * @param jsCodeBuilder The current JsCodeBuilder.
   * @param localVarTranslations The current local var translations.
   * @param genJsExprsVisitor The current GenJsExprsVisitor.
   */
  GenJsCodeVisitorAssistantForMsgs(
      GenJsCodeVisitor master, JsExprTranslator jsExprTranslator, GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor, JsCodeBuilder jsCodeBuilder,
      Deque<Map<String, JsExpr>> localVarTranslations, GenJsExprsVisitor genJsExprsVisitor) {
    this.master = master;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.jsCodeBuilder = jsCodeBuilder;
    this.localVarTranslations = localVarTranslations;
    this.genJsExprsVisitor = genJsExprsVisitor;
  }


  @Override public Void exec(SoyNode node) {
    throw new AssertionError();
  }


  /**
   * This method must only be called by the master GenJsCodeVisitor.
   */
  void visitForUseByMaster(SoyNode node) {
    visit(node);
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for GoogMsgNode.


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

    boolean isPlrselMsg = node.isPlrselMsg();

    // Build the code for the message text and the individual code bits for each placeholder (i.e.
    // "<placeholderName>: <exprCode>") and plural/select (i.e., "<varName>: <exprCode>").

    GoogMsgCodeGenInfo googMsgCodeGenInfo = new GoogMsgCodeGenInfo(isPlrselMsg);
    genGoogMsgCodeForChildren(node, node, googMsgCodeGenInfo);

    String msgTextCode = BaseUtils.escapeToSoyString(
        googMsgCodeGenInfo.msgTextCodeSb.toString(), false);
    // Note: BaseUtils.escapeToSoyString() builds a Soy string, which is usually a valid JS string.
    // The rare exception is a string containing a Unicode Format character (Unicode category "Cf")
    // because of the JavaScript language quirk that requires all category "Cf" characters to be
    // escaped in JS strings. Therefore, we must call JsSrcUtils.escapeUnicodeFormatChars() on the
    // result.
    msgTextCode = JsSrcUtils.escapeUnicodeFormatChars(msgTextCode);

    // Generate JS comment (JSDoc) block for the goog.getMsg() call.
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

    // Generate goog.getMsg() call.
    jsCodeBuilder.indent().append("var ", node.getGoogMsgVarName(), " = goog.getMsg(");

    if (isPlrselMsg) {
      // For plural/select msgs, we're letting goog.i18n.MessageFormat handle all placeholder
      // replacements, even ones that have nothing to do with plural/select. Therefore, in
      // generating the goog.getMsg() call, we always put the message text on the same line (there
      // are never any placeholders for goog.getMsg() to replace).
      jsCodeBuilder.append(msgTextCode);

    } else {
      if (googMsgCodeGenInfo.placeholderCodeBits.size() == 0) {
        // If no placeholders, we put the message text on the same line.
        jsCodeBuilder.append(msgTextCode);
      } else {
        // If there are placeholders, we put the message text on a new line, indented 4 extra
        // spaces. And we line up the placeholders too.
        jsCodeBuilder.append("\n");
        jsCodeBuilder.indent().append("    ", msgTextCode, ",");
        appendCodeBits(googMsgCodeGenInfo.placeholderCodeBits);
      }
    }

    jsCodeBuilder.append(");\n");

    // For plural/select messages, generate the goog.i18n.MessageFormat call.
    // We don't want to output the result of goog.getMsg() directly. Instead, we send that string to
    // goog.i18n.MessageFormat for post-processing. This post-processing is where we're handling
    // all placeholder replacements, even ones that have nothing to do with plural/select.
    if (isPlrselMsg) {

      // Gather all the code bits.
      List<String> codeBitsForMfCall = googMsgCodeGenInfo.plrselVarCodeBits;
      codeBitsForMfCall.addAll(googMsgCodeGenInfo.placeholderCodeBits);

      // Generate the call.
      jsCodeBuilder.indent().append(
          "var ", node.getRenderedGoogMsgVarName(),
          " = (new goog.i18n.MessageFormat(", node.getGoogMsgVarName(), ")).formatIgnoringPound(");
      appendCodeBits(codeBitsForMfCall);
      jsCodeBuilder.append(");\n");
    }
  }


  /**
   * Private helper class for visitGoogMsgNode().
   * Stores the data require for generating goog.geMsg() code.
   */
  private static class GoogMsgCodeGenInfo {

    /** Whether we're using braced placeholders (only applicable for plural/select msgs. */
    public final boolean doUseBracedPhs;

    /** The StringBuilder holding the generated message text (before escaping and quoting). */
    public StringBuilder msgTextCodeSb;

    /** List of code bits for placeholders. */
    public List<String> placeholderCodeBits;

    /** Set of placeholder names for which we have already generated code bits. */
    public Set<String> seenPlaceholderNames;

    /** List of code bits for plural and select variables. */
    public List<String> plrselVarCodeBits;

    /** Set of plural/select var names for which we have already generated code bits. */
    public Set<String> seenPlrselVarNames;

    public GoogMsgCodeGenInfo(boolean doUseBracedPhs) {
      this.doUseBracedPhs = doUseBracedPhs;
      msgTextCodeSb = new StringBuilder();
      placeholderCodeBits = Lists.newArrayList();
      seenPlaceholderNames = Sets.newHashSet();
      plrselVarCodeBits = Lists.newArrayList();
      seenPlrselVarNames = Sets.newHashSet();
    }
  }


  /**
   * Private helper for visitGoogMsgNode().
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
   * Private helper for visitGoogMsgNode().
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
        String nodeStringForErrorMsg = (child instanceof CommandNode) ?
            "Tag " + ((CommandNode) child).getTagString() : "Node " + child.toString();
        throw SoySyntaxExceptionUtils.createWithNode(
            nodeStringForErrorMsg + " is not allowed to be a direct child of a message.", child);
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
   * Private helper for visitGoogMsgNode().
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
    updatePlrselVarCodeBits(
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
   * Private helper for visitGoogMsgNode().
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
    updatePlrselVarCodeBits(
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
   * Private helper for visitGoogMsgNode().
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

    if (googMsgCodeGenInfo.doUseBracedPhs) {
      // Add placeholder to message text.
      googMsgCodeGenInfo.msgTextCodeSb.append('{').append(placeholderName).append('}');
      // If the placeholder name has not already been seen, then this child must be its
      // representative node. Add the code bit for the placeholder now.
      updatePlaceholderCodeBits(
          googMsgCodeGenInfo, placeholderName, placeholderName,
          genGoogMsgPlaceholderExpr(node).getText());

    } else {
      // For goog.getMsg(), we must change the placeholder name to lower camel-case format.
      String googMsgPlaceholderName = genGoogMsgPlaceholderName(placeholderName);

      // Add placeholder to message text. Note the '$' for goog.getMsg() syntax.
      googMsgCodeGenInfo.msgTextCodeSb.append("{$").append(googMsgPlaceholderName).append('}');
      // If the placeholder name has not already been seen, then this child must be its
      // representative node. Add the code bit for the placeholder now.
      updatePlaceholderCodeBits(
          googMsgCodeGenInfo, placeholderName, googMsgPlaceholderName,
          genGoogMsgPlaceholderExpr(node).getText());
    }
  }


  /**
   * Private helper for visitGoogMsgNode().
   * Updates code bits (and seenNames) for a plural/select var.
   * @param codeBits The list of code bits.
   * @param seenNames Set of seen names.
   * @param name The name.
   * @param googMsgName The enclosing {@code GoogMsgNode} object.
   * @param exprText The corresponding expression text.
   */
  /**
   * Private helper for visitGoogMsgNode().
   * Updates code bits (and seenNames) for a plural/select var.
   * @param googMsgCodeGenInfo The object holding code-gen info.
   * @param plrselVarName The plural or select var name. Should be upper underscore format.
   * @param exprText The JS expression text for the value.
   */
  private static void updatePlrselVarCodeBits(
      GoogMsgCodeGenInfo googMsgCodeGenInfo, String plrselVarName, String exprText) {
    if (googMsgCodeGenInfo.seenPlrselVarNames.contains(plrselVarName)) {
      return;  // already added to code bits previously
    }
    googMsgCodeGenInfo.seenPlrselVarNames.add(plrselVarName);

    // Add the code bit.
    String placeholderCodeBit = "'" + plrselVarName + "': " + exprText;
    googMsgCodeGenInfo.plrselVarCodeBits.add(placeholderCodeBit);
  }


  /**
   * Private helper for visitGoogMsgNode().
   * Updates code bits (and seenNames) for a placeholder.
   * @param googMsgCodeGenInfo The object holding code-gen info.
   * @param placeholderName The placeholder name. Should be upper underscore format.
   * @param googMsgPlaceholderName The placeholder name for the goog msg. Should be in lower camel
   *     case, with optional underscore-number suffix.
   * @param exprText The JS expression text for the value.
   */
  private static void updatePlaceholderCodeBits(
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
   * Private helper for visitGoogMsgNode().
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
  private static String genGoogMsgPlaceholderName(String placeholderName) {

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
   * Private helper for visitGoogMsgNode().
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


  // -----------------------------------------------------------------------------------------------
  // Implementations for other specific nodes.


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


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    master.visitForUseByAssistants(node);
  }

}
