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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.JsExprUtils;
import com.google.template.soy.msgs.internal.IcuSyntaxUtils;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.shared.internal.CodeBuilder;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CaseOrDefaultNode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.CommandNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

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
 */
public class GenJsCodeVisitorAssistantForMsgs extends AbstractSoyNodeVisitor<Void> {


  /** Regex pattern for an underscore-number suffix. */
  private static final Pattern UNDERSCORE_NUMBER_SUFFIX = Pattern.compile("_[0-9]+$");


  /** The options for generating JS source code. */
  private final SoyJsSrcOptions jsSrcOptions;

  /** Master instance of GenJsCodeVisitor. */
  protected final GenJsCodeVisitor master;

  /** Instance of JsExprTranslator to use. */
  private final JsExprTranslator jsExprTranslator;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsJsExprsVisitor used by this instance. */
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;

  /** The GenJsExprsVisitor used for the current template. */
  private final GenJsExprsVisitor genJsExprsVisitor;

  /** The JsCodeBuilder to build the current JS file being generated (during a run). */
  protected final CodeBuilder<JsExpr> jsCodeBuilder;

  /** The current stack of replacement JS expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, JsExpr>> localVarTranslations;

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name.
   */
  private final TemplateAliases templateAliases;

  private final ErrorReporter errorReporter;

  /**
   * @param master The master GenJsCodeVisitor instance.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to use.
   * @param jsCodeBuilder The current JsCodeBuilder.
   * @param localVarTranslations The current local var translations.
   * @param genJsExprsVisitor The current GenJsExprsVisitor.
   */
  protected GenJsCodeVisitorAssistantForMsgs(
      GenJsCodeVisitor master,
      SoyJsSrcOptions jsSrcOptions,
      JsExprTranslator jsExprTranslator,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      CodeBuilder<JsExpr> jsCodeBuilder,
      Deque<Map<String, JsExpr>> localVarTranslations,
      TemplateAliases functionAliases,
      GenJsExprsVisitor genJsExprsVisitor,
      ErrorReporter errorReporter) {
    this.master = master;
    this.jsSrcOptions = jsSrcOptions;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.jsCodeBuilder = jsCodeBuilder;
    this.localVarTranslations = localVarTranslations;
    this.templateAliases = functionAliases;
    this.genJsExprsVisitor = genJsExprsVisitor;
    this.errorReporter = errorReporter;
  }


  @Override public Void exec(SoyNode node) {
    throw new AssertionError();
  }

  /**
   * Generates Javascript statements that declare a translated variable, returning the variable name
   * for the caller to output (as an expression).
   * MsgFallbackGroupNodes can only appear in let blocks, which seems like unnecesary overhead since
   * this already generates a perfectly usable variable.  However, this design makes incremental DOM
   * codegen, which needs to apply complex transforms on the translated variable, much simpler.
   * Example:
   * <pre>
   *   {msg desc="Link to help content."}Learn more{/msg}
   *   {msg desc="Tells user how to access a product." hidden="true"}
   *     Click &lt;a href="}{$url}"&gt;here&lt;/a&gt; to access {$productName}.
   *   {/msg}
   * </pre>
   * might generate
   * <pre>
   *   /** @desc Link to help content. *{@literal /}
   *   var MSG_UNNAMED_9 = goog.getMsg('Learn more');
   *   var msg_s9 = MSG_UNNAMED_9;
   *   /** @desc Tells user how to access a product.
   *    *  @hidden *{@literal /}
   *   var MSG_UNNAMED_10 = goog.getMsg(
   *       'Click {$startLink}here{$endLink} to access {$productName}.',
   *       {startLink: '&lt;a href="' + opt_data.url + '"&gt;',
   *        endLink: '&lt;/a&gt;',
   *        productName: opt_data.productName});
   * </pre>
   * and return {@code "MSG_UNNAMED_10"}.
   */
  public String generateMsgGroupVariable(MsgFallbackGroupNode node) {
    String tmpVarName = "msg_s" + node.getId();
    if (node.numChildren() == 1) {
      return generateSingleMsgVariable(node.getChild(0), tmpVarName);
    } else {  // has fallbackmsg children
      generateMsgGroupVariable(node, tmpVarName);
      return tmpVarName;
    }
  }

  /**
   * Generates an initialized variable declaration for an {@link MsgNode} with no fallback messages.
   * @return The variable name, which will be the actual MSG_BLAH variable if no temporary variables
   * are needed for additional formatting.
   */
  private String generateSingleMsgVariable(MsgNode msgNode, String tmpVarName) {
    String googMsgVarName = buildGoogMsgVarNameHelper(msgNode);

    // Generate the goog.getMsg call.
    GoogMsgCodeGenInfo googMsgCodeGenInfo = genGoogGetMsgCallHelper(googMsgVarName, msgNode);

    if (!msgNode.isPlrselMsg()) {
      // No postprocessing is needed. Simply use the original goog.getMsg var.
      return googMsgVarName;
    }
    // For plural/select messages, generate the goog.i18n.MessageFormat call.
    // We don't want to output the result of goog.getMsg() directly. Instead, we send that
    // string to goog.i18n.MessageFormat for postprocessing. This postprocessing is where we're
    // handling all placeholder replacements, even ones that have nothing to do with
    // plural/select.
    jsCodeBuilder.appendLineStart("var ", tmpVarName, " = ");
    appendMessageFormatCall(googMsgCodeGenInfo);
    jsCodeBuilder.appendLineEnd(";");
    return tmpVarName;
  }

  /**
   * Generates an initialized variable declaration for an {@link MsgFallbackGroupNode} that contains
   * fallback(s).
   */
  private void generateMsgGroupVariable(MsgFallbackGroupNode node, String tmpVarName) {
    List<GoogMsgCodeGenInfo> childGoogMsgCodeGenInfos =
        Lists.newArrayListWithCapacity(node.numChildren());

    // Generate the goog.getMsg calls for all children.
    for (MsgNode msgNode : node.getChildren()) {
      String googMsgVarName = buildGoogMsgVarNameHelper(msgNode);
      childGoogMsgCodeGenInfos.add(genGoogGetMsgCallHelper(googMsgVarName, msgNode));
    }

    // Declare a temporary variable to hold the getMsgWithFallback() call so that we can apply any
    // MessageFormats from any of the fallbacks.  This is also the variable name that we return to
    // the caller.
    jsCodeBuilder.appendLineStart("var ", tmpVarName, " = goog.getMsgWithFallback(");
    boolean isFirst = true;
    for (GoogMsgCodeGenInfo childGoogMsgCodeGenInfo : childGoogMsgCodeGenInfos) {
      if (isFirst) {
        isFirst = false;
      } else {
        jsCodeBuilder.append(", ");
      }
      jsCodeBuilder.append(childGoogMsgCodeGenInfo.googMsgVarName);
    }
    jsCodeBuilder.appendLineEnd(");");

    // Generate the goog.i18n.MessageFormat calls for child plural/select messages (if any), each
    // wrapped in an if-block that will only execute if that child is the chosen message.
    for (GoogMsgCodeGenInfo childGoogMsgCodeGenInfo : childGoogMsgCodeGenInfos) {
      if (childGoogMsgCodeGenInfo.isPlrselMsg) {
        jsCodeBuilder.appendLine("if (", tmpVarName, " == ",
            childGoogMsgCodeGenInfo.googMsgVarName, ") {");
        jsCodeBuilder.increaseIndent();
        jsCodeBuilder.appendLineStart(tmpVarName, " = ");
        appendMessageFormatCall(childGoogMsgCodeGenInfo);
        jsCodeBuilder.appendLineEnd(";");
        jsCodeBuilder.decreaseIndent();
        jsCodeBuilder.appendLine("}");
      }
    }
  }

  /** Builds the googMsgVarName for an MsgNode. */
  private String buildGoogMsgVarNameHelper(MsgNode msgNode) {
    return jsSrcOptions.googMsgsAreExternal()
        ? "MSG_EXTERNAL_" + MsgUtils.computeMsgIdForDualFormat(msgNode)
        : "MSG_UNNAMED_" + msgNode.getId();
  }


  /**
   * Generates the goog.getMsg call for an MsgNode.
   * The goog.getMsg call (including JsDoc) will be appended to the jsCodeBuilder.
   *
   * @return The GoogMsgCodeGenInfo object created in the process, which may be needed for
   *     generating postprocessing code (if the message is plural/select).
   */
  private GoogMsgCodeGenInfo genGoogGetMsgCallHelper(String googMsgVarName, MsgNode msgNode) {

    // Build the code for the message content.
    // TODO: We could build the msg parts once and save it as a field on the MsgNode or save it some
    // other way, but it would increase memory usage a little bit. It's probably not a big deal,
    // since it's not per-locale, but I'm not going to do this right now since we're trying to
    // decrease memory usage right now. The same memoization possibility also applies to the msg
    // parts with embedded ICU syntax (created in helper buildGoogMsgContentStr()).
    ImmutableList<SoyMsgPart> msgParts = MsgUtils.buildMsgParts(msgNode);
    String googMsgContentStr = buildGoogMsgContentStr(msgParts, msgNode.isPlrselMsg());
    // Escape non-ASCII characters since browsers are inconsistent in how they interpret utf-8 in
    // JS source files.
    String googMsgContentStrCode = BaseUtils.escapeToSoyString(googMsgContentStr, true);

    // Build the individual code bits for each placeholder (i.e. "<placeholderName>: <exprCode>")
    // and each plural/select (i.e. "<varName>: <exprCode>").
    GoogMsgCodeGenInfo googMsgCodeGenInfo =
        new GoogMsgCodeGenInfo(googMsgVarName, msgNode.isPlrselMsg());
    genGoogMsgCodeBitsForChildren(msgNode, msgNode, googMsgCodeGenInfo);

    // Generate JS comment (JSDoc) block for the goog.getMsg() call.
    jsCodeBuilder.appendLineStart("/** ");
    if (msgNode.getMeaning() != null) {
      jsCodeBuilder.appendLineEnd("@meaning ", msgNode.getMeaning());
      jsCodeBuilder.appendLineStart(" *  ");
    }
    jsCodeBuilder.append("@desc ", msgNode.getDesc());
    if (msgNode.isHidden()) {
      jsCodeBuilder.appendLineEnd();
      jsCodeBuilder.appendLineStart(" *  @hidden");
    }
    jsCodeBuilder.appendLineEnd(" */");

    // Generate goog.getMsg() call.
    jsCodeBuilder.appendLineStart("var ", googMsgCodeGenInfo.googMsgVarName, " = goog.getMsg(");

    if (msgNode.isPlrselMsg()) {
      // For plural/select msgs, we're letting goog.i18n.MessageFormat handle all placeholder
      // replacements, even ones that have nothing to do with plural/select. Therefore, in
      // generating the goog.getMsg() call, we always put the message text on the same line (there
      // are never any placeholders for goog.getMsg() to replace).
      jsCodeBuilder.appendLineEnd(googMsgContentStrCode, ");");

    } else {
      if (googMsgCodeGenInfo.placeholderCodeBits.isEmpty()) {
        // If no placeholders, we put the message text on the same line.
        jsCodeBuilder.appendLineEnd(googMsgContentStrCode, ");");
      } else {
        // If there are placeholders, we put the message text on a new line, indented 4 extra
        // spaces. And we line up the placeholders too.
        jsCodeBuilder.appendLineEnd();
        jsCodeBuilder.appendLine("    ", googMsgContentStrCode, ",");
        appendObjectLiteral(googMsgCodeGenInfo.placeholderCodeBits);
        jsCodeBuilder.appendLineEnd(");");
      }
    }

    return googMsgCodeGenInfo;
  }


  /**
   * Builds the message content string for a goog.getMsg() call.
   *
   * @param msgParts The parts of the message.
   * @param doUseBracedPhs Whether to use braced placeholders.
   * @return The message content string for a goog.getMsg() call.
   */
  private static String buildGoogMsgContentStr(
      ImmutableList<SoyMsgPart> msgParts, boolean doUseBracedPhs) {

    // Note: For source messages, disallow ICU syntax chars that need escaping in raw text.
    msgParts = IcuSyntaxUtils.convertMsgPartsToEmbeddedIcuSyntax(msgParts, false);

    StringBuilder msgStrSb = new StringBuilder();

    for (SoyMsgPart msgPart : msgParts) {

      if (msgPart instanceof SoyMsgRawTextPart) {
        msgStrSb.append(((SoyMsgRawTextPart) msgPart).getRawText());

      } else if (msgPart instanceof SoyMsgPlaceholderPart) {
        String placeholderName = ((SoyMsgPlaceholderPart) msgPart).getPlaceholderName();
        if (doUseBracedPhs) {
          // Add placeholder to message text.
          msgStrSb.append("{").append(placeholderName).append("}");

        } else {
          // For goog.getMsg(), we must change the placeholder name to lower camel-case format.
          String googMsgPlaceholderName = genGoogMsgPlaceholderName(placeholderName);
          // Add placeholder to message text. Note the '$' for goog.getMsg() syntax.
          msgStrSb.append("{$").append(googMsgPlaceholderName).append("}");
        }

      } else {
        throw new AssertionError();
      }
    }

    return msgStrSb.toString();
  }


  /**
   * Generates the goog.i18n.MessageFormat postprocessing expression for a child
   * plural/select message. The expression is appended to the jsCodeBuilder.
   *
   * @param googMsgCodeGenInfo The GoogMsgCodeGenInfo object created by genGoogGetMsgCallHelper().
   */
  private void appendMessageFormatCall(GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    // Gather all the code bits.
    List<String> codeBitsForMfCall = googMsgCodeGenInfo.plrselVarCodeBits;
    codeBitsForMfCall.addAll(googMsgCodeGenInfo.placeholderCodeBits);

    // Generate the goog.i18n.MessageFormat call.
    jsCodeBuilder.appendLineEnd(
        "(new goog.i18n.MessageFormat(", googMsgCodeGenInfo.googMsgVarName,
        ")).formatIgnoringPound(");
    appendObjectLiteral(codeBitsForMfCall);
    jsCodeBuilder.append(")");
  }


  /** Stores the data require for generating goog.geMsg() code. */
  private static class GoogMsgCodeGenInfo {

    /** The name of the goog.getMsg msg var, i.e. MSG_EXTERNAL_### or MSG_UNNAMED_###. */
    final String googMsgVarName;

    /** Whether the msg is a plural/select msg. */
    final boolean isPlrselMsg;

    /** List of code bits for placeholders. */
    final List<String> placeholderCodeBits;

    /** Set of placeholder names for which we have already generated code bits. */
    final Set<String> seenPlaceholderNames;

    /** List of code bits for plural and select variables. */
    final List<String> plrselVarCodeBits;

    /** Set of plural/select var names for which we have already generated code bits. */
    final Set<String> seenPlrselVarNames;

    public GoogMsgCodeGenInfo(String googMsgVarName, boolean isPlrselMsg) {
      this.googMsgVarName = googMsgVarName;
      this.isPlrselMsg = isPlrselMsg;
      placeholderCodeBits = Lists.newArrayList();
      seenPlaceholderNames = Sets.newHashSet();
      plrselVarCodeBits = Lists.newArrayList();
      seenPlrselVarNames = Sets.newHashSet();
    }
  }


  /**
   * Appends an object with the given properties to the JS code builder.
   * @param properties Object property declarations (e.g. {@code 'name': value}).
   */
  private void appendObjectLiteral(List<String> properties) {
    boolean isFirst = true;
    for (String property : properties) {
      if (isFirst) {
        jsCodeBuilder.appendLineStart("    {");
        isFirst = false;
      } else {
        jsCodeBuilder.appendLineEnd(",");
        jsCodeBuilder.appendLineStart("     ");
      }
      jsCodeBuilder.append(property);
    }
    jsCodeBuilder.append("}");
  }


  /**
   * Generates goog.getMsg() code bits for a given parent node and its children.
   *
   * @param parentNode A parent node of one of these types: {@code MsgNode},
   *     {@code MsgPluralCaseNode}, {@code MsgPluralDefaultNode},
   *     {@code MsgSelectCaseNode}, {@code MsgSelectDefaultNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeBitsForChildren(
      BlockNode parentNode, MsgNode msgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    for (StandaloneNode child : parentNode.getChildren()) {
      if (child instanceof RawTextNode) {
        // nothing to do
      } else if (child instanceof MsgPlaceholderNode) {
        genGoogMsgCodeBitsForPlaceholder(
            (MsgPlaceholderNode) child, msgNode, googMsgCodeGenInfo);
      } else if (child instanceof MsgPluralNode) {
        genGoogMsgCodeBitsForPluralNode((MsgPluralNode) child, msgNode, googMsgCodeGenInfo);
      } else if (child instanceof MsgSelectNode) {
        genGoogMsgCodeBitsForSelectNode((MsgSelectNode) child, msgNode, googMsgCodeGenInfo);
      } else {
        String nodeStringForErrorMsg =
            (child instanceof CommandNode)
                ? "Tag " + ((CommandNode) child).getTagString()
                : "Node " + child;
        throw new AssertionError(
            nodeStringForErrorMsg
                + " is not allowed to be a direct child of a 'msg' tag. At :"
                + child.getSourceLocation());
      }
    }
  }


  /**
   * Generates code bits for a {@code MsgPluralNode} subtree inside a message.
   * @param pluralNode A node of type {@code MsgPluralNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeBitsForPluralNode(
      MsgPluralNode pluralNode, MsgNode msgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    updatePlrselVarCodeBits(
        googMsgCodeGenInfo,
        msgNode.getPluralVarName(pluralNode),
        jsExprTranslator
            .translateToJsExpr(pluralNode.getExpr(), localVarTranslations, errorReporter)
            .getText());

    for (CaseOrDefaultNode child : pluralNode.getChildren()) {
      genGoogMsgCodeBitsForChildren(child, msgNode, googMsgCodeGenInfo);
    }
  }


  /**
   * Generates code bits for a {@code MsgSelectNode} subtree inside a message.
   * @param selectNode A node of type {@code MsgSelectNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeBitsForSelectNode(
      MsgSelectNode selectNode, MsgNode msgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    updatePlrselVarCodeBits(
        googMsgCodeGenInfo,
        msgNode.getSelectVarName(selectNode),
        jsExprTranslator
            .translateToJsExpr(selectNode.getExpr(), localVarTranslations, errorReporter)
            .getText());

    for (CaseOrDefaultNode child : selectNode.getChildren()) {
      genGoogMsgCodeBitsForChildren(child, msgNode, googMsgCodeGenInfo);
    }
  }


  /**
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
   * Generates code bits for a normal {@code MsgPlaceholderNode} inside a message.
   * @param node A node of type {@code MsgPlaceholderNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder
   *     names, plural variable names, and select variable names to be used
   *     for message code generation.
   */
  private void genGoogMsgCodeBitsForPlaceholder(
      MsgPlaceholderNode node, MsgNode msgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    String placeholderName = msgNode.getPlaceholderName(node);
    if (googMsgCodeGenInfo.seenPlaceholderNames.contains(placeholderName)) {
      return;  // already added to code bits previously
    }
    googMsgCodeGenInfo.seenPlaceholderNames.add(placeholderName);

    // For plural/select, the placeholder is an ICU placeholder, i.e. kept in all-caps. But for
    // goog.getMsg(), we must change the placeholder name to lower camel-case format.
    String googMsgPlaceholderName = googMsgCodeGenInfo.isPlrselMsg ?
        placeholderName : genGoogMsgPlaceholderName(placeholderName);
    // Add the code bit for the placeholder.
    String placeholderCodeBit =
        "'" + googMsgPlaceholderName + "': " + genGoogMsgPlaceholderExpr(node).getText();
    googMsgCodeGenInfo.placeholderCodeBits.add(placeholderCodeBit);
  }


  /**
   * <p>
   * Converts a Soy placeholder name (in upper underscore format) into a JS variable name (in lower
   * camel case format) used by goog.getMsg(). If the original name has a numeric suffix, it will
   * be preserved with an underscore.
   * </p>
   * <p>
   * For example, the following transformations happen:
   * <li> N : n
   * <li> NUM_PEOPLE : numPeople
   * <li> PERSON_2 : person_2
   * <li>GENDER_OF_THE_MAIN_PERSON_3 : genderOfTheMainPerson_3
   * </p>
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
   * Generates the JS expr for a given placeholder.
   *
   * @param msgPhNode The placeholder to generate the JS expr for.
   * @return The JS expr for the given placeholder.
   */
  protected JsExpr genGoogMsgPlaceholderExpr(MsgPlaceholderNode msgPhNode) {

    List<JsExpr> contentJsExprs = Lists.newArrayList();

    for (StandaloneNode contentNode : msgPhNode.getChildren()) {

      if (contentNode instanceof MsgHtmlTagNode &&
          !isComputableAsJsExprsVisitor.exec(contentNode)) {
        // This is a MsgHtmlTagNode that is not computable as JS expressions. Visit it to
        // generate code to define the 'htmlTag<n>' variable.
        visit(contentNode);
        contentJsExprs.add(new JsExpr("htmlTag" + contentNode.getId(), Integer.MAX_VALUE));

      } else if (contentNode instanceof CallNode) {
        // If the CallNode has any CallParamContentNode children that are not computable as JS
        // expressions, visit them to generate code to define their respective 'param<n>' variables.

        CallNode callNode = (CallNode) contentNode;
        for (CallParamNode grandchild : callNode.getChildren()) {
          if (grandchild instanceof CallParamContentNode &&
              !isComputableAsJsExprsVisitor.exec(grandchild)) {
            visit(grandchild);
          }
        }
        contentJsExprs.add(
            genCallCodeUtils.genCallExpr(
                callNode, localVarTranslations, templateAliases, errorReporter));

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
   * <pre>
   *   &lt;a href="http://www.google.com/search?hl=en
   *     {for $i in range(3)}
   *       &amp;param{$i}={$i}
   *     {/for}
   *   "&gt;
   * might generate
   * </pre>
   *   var htmlTag84 = (new soy.StringBuilder()).append('&lt;a href="');
   *   for (var i80 = 1; i80 &lt; 3; i80++) {
   *     htmlTag84.append('&amp;param', i80, '=', i80);
   *   }
   *   htmlTag84.append('"&gt;');
   * </pre>
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
