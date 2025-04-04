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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jssrc.dsl.Expressions.construct;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_GET_MSG;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_I18N_MESSAGE_FORMAT;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.Id;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.SoyJsPluginUtils;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.Statements;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.restricted.ModernSoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.msgs.internal.IcuSyntaxUtils;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.EscapingMode;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Assistant visitor for GenJsCodeVisitor to handle messages.
 *
 * <p>Precondition: MsgNode should not exist in the tree.
 */
public class GenJsCodeVisitorAssistantForMsgs extends AbstractReturningSoyNodeVisitor<Statement> {

  private static final Pattern LINE_BOUNDARY_PATTERN = Pattern.compile("\\s*?(\\n|\\r)\\s*");

  /** Regex pattern for an underscore-number suffix. */
  private static final Pattern UNDERSCORE_NUMBER_SUFFIX = Pattern.compile("_[0-9]+$");

  private final SoyJsSrcOptions jsSrcOptions;
  protected final GenJsTemplateBodyVisitor owner;
  private final GenCallCodeUtils genCallCodeUtils;
  private final IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor;
  private final GenJsExprsVisitor genJsExprsVisitor;
  protected final TranslationContext translationContext;
  private final ErrorReporter errorReporter;
  private final OutputVarHandler outputVars;

  protected GenJsCodeVisitorAssistantForMsgs(
      GenJsTemplateBodyVisitor owner,
      SoyJsSrcOptions jsSrcOptions,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      GenJsExprsVisitor genJsExprsVisitor,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      OutputVarHandler outputVars) {
    this.owner = checkNotNull(owner);
    this.jsSrcOptions = checkNotNull(jsSrcOptions);
    this.genCallCodeUtils = checkNotNull(genCallCodeUtils);
    this.isComputableAsJsExprsVisitor = checkNotNull(isComputableAsJsExprsVisitor);
    this.genJsExprsVisitor = checkNotNull(genJsExprsVisitor);
    this.translationContext = checkNotNull(translationContext);
    this.errorReporter = checkNotNull(errorReporter);
    this.outputVars = checkNotNull(outputVars);
  }

  /**
   * Returns a code chunk representing a translated variable.
   *
   * <p>Example:
   *
   * <pre>
   *   {msg desc="Link to help content."}Learn more{/msg}
   *   {msg desc="Tells user how to access a product."}
   *     Click &lt;a href="{$url}"&gt;here&lt;/a&gt; to access {$productName}.
   *   {/msg}
   * </pre>
   *
   * might return the following code chunk:
   *
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
   */
  public Expression generateMsgGroupVariable(MsgFallbackGroupNode node) {
    Expression msg;
    if (node.numChildren() == 1) {
      translationContext
          .soyToJsVariableMappings()
          .setIsPrimaryMsgInUse(node, Expressions.LITERAL_TRUE);
      msg = generateSingleMsgVariable(node.getChild(0));
    } else { // has fallbackmsg children
      msg = generateMsgGroupVariableWithFallback(node);
    }
    // handle escaping
    for (SoyPrintDirective printDirective : node.getEscapingDirectives()) {
      if (printDirective instanceof ModernSoyJsSrcPrintDirective) {
        msg =
            ((ModernSoyJsSrcPrintDirective) printDirective).applyForJsSrc(msg, ImmutableList.of());
      } else {
        msg =
            SoyJsPluginUtils.applyDirective(
                msg,
                (SoyJsSrcPrintDirective) printDirective,
                /* args= */ ImmutableList.of(),
                node.getSourceLocation(),
                errorReporter);
      }
    }
    return msg;
  }

  /**
   * Returns a code chunk representing a variable declaration for an {@link MsgFallbackGroupNode}
   * that contains fallback(s).
   */
  private Expression generateMsgGroupVariableWithFallback(MsgFallbackGroupNode node) {
    checkState(node.numChildren() == 2);

    // Generate the goog.getMsg calls for all children.
    GoogMsgCodeGenInfo primaryCodeGenInfo =
        genGoogGetMsgCallHelper(buildGoogMsgVarNameHelper(node.getChild(0)), node.getChild(0));
    GoogMsgCodeGenInfo fallbackCodeGenInfo =
        genGoogGetMsgCallHelper(buildGoogMsgVarNameHelper(node.getChild(1)), node.getChild(1));

    // Declare a temporary variable to hold the getMsgWithFallback() call so that we can apply any
    // MessageFormats from any of the fallbacks.  This is also the variable name that we return to
    // the caller.
    // TODO(lukes): we don't really need this variable unless 'isPrimaryMsgInUse' is called,
    // consider adding translation logic for that conditionally.
    Expression selectedMsg =
        translationContext
            .codeGenerator()
            .declarationBuilder("selected_msg")
            .setRhs(
                Expressions.dottedIdNoRequire("goog.getMsgWithFallback")
                    .call(primaryCodeGenInfo.googMsgVar, fallbackCodeGenInfo.googMsgVar))
            .build()
            .ref();

    // TODO(b/33382980): In certain cases in IDOM this generates these variables multiple times
    // (because they are used in separate scopes), clean this up.
    Expression isPrimaryMsgInUse = selectedMsg.doubleEquals(primaryCodeGenInfo.googMsgVar);
    translationContext.soyToJsVariableMappings().setIsPrimaryMsgInUse(node, isPrimaryMsgInUse);
    if (primaryCodeGenInfo.placeholders == null && fallbackCodeGenInfo.placeholders == null) {
      // all placeholders have already been substituted, just return
      return selectedMsg;
    }
    // Generate the goog.i18n.MessageFormat calls for child plural/select messages (if any), each
    // wrapped in an if-block that will only execute if that child is the chosen message.
    Expression initializer;
    if (primaryCodeGenInfo.placeholders != null) {
      initializer =
          Expressions.ifExpression(
                  selectedMsg.doubleEquals(primaryCodeGenInfo.googMsgVar),
                  getMessageFormatCall(primaryCodeGenInfo))
              .setElse(
                  fallbackCodeGenInfo.placeholders == null
                      ? selectedMsg
                      : getMessageFormatCall(fallbackCodeGenInfo))
              .build(translationContext.codeGenerator());
    } else {
      initializer =
          Expressions.ifExpression(
                  selectedMsg.doubleEquals(fallbackCodeGenInfo.googMsgVar),
                  getMessageFormatCall(fallbackCodeGenInfo))
              .setElse(selectedMsg)
              .build(translationContext.codeGenerator());
    }
    return translationContext
        .codeGenerator()
        .declarationBuilder("msg_s")
        .setRhs(initializer)
        .build()
        .ref();
  }

  /**
   * Returns a code chunk representing a variable declaration for an {@link MsgNode} with no
   * fallback messages.
   */
  private Expression generateSingleMsgVariable(MsgNode msgNode) {
    String googMsgVarName = buildGoogMsgVarNameHelper(msgNode);

    // Generate the goog.getMsg call.
    GoogMsgCodeGenInfo googMsgCodeGenInfo = genGoogGetMsgCallHelper(googMsgVarName, msgNode);

    if (!msgNode.isPlrselMsg()) {
      // No postprocessing is needed. Simply use the original goog.getMsg var.
      return googMsgCodeGenInfo.googMsgVar;
    }
    // For plural/select messages, generate the goog.i18n.MessageFormat call.
    // We don't want to output the result of goog.getMsg() directly. Instead, we send that
    // string to goog.i18n.MessageFormat for postprocessing. This postprocessing is where we're
    // handling all placeholder replacements, even ones that have nothing to do with
    // plural/select.
    return translationContext
        .codeGenerator()
        .declarationBuilder("msg_s")
        .setRhs(getMessageFormatCall(googMsgCodeGenInfo))
        .build()
        .ref();
  }

  /** Builds the googMsgVarName for an MsgNode. */
  private String buildGoogMsgVarNameHelper(MsgNode msgNode) {
    // NOTE: MSG_UNNAMED/MSG_EXTERNAL are a special tokens recognized by the jscompiler. MSG_UNNAMED
    // disables the default logic that requires all messages to be uniquely named.
    // and MSG_EXTERNAL causes the jscompiler to not extract these messages.
    String desiredName =
        jsSrcOptions.googMsgsAreExternal()
            ? "MSG_EXTERNAL_" + MsgUtils.computeMsgIdForDualFormat(msgNode)
            : "MSG_UNNAMED";
    return translationContext.nameGenerator().generate(desiredName);
  }

  /**
   * Generates the goog.getMsg call for an MsgNode. The goog.getMsg call (including JsDoc) will be
   * appended to the jsCodeBuilder.
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
    Expression googMsgContent =
        stringLiteral(buildGoogMsgContentStr(msgParts, msgNode.isPlrselMsg()));

    // Build the individual code bits for each placeholder (i.e. "<placeholderName>: <exprCode>")
    // and each plural/select (i.e. "<varName>: <exprCode>").
    GoogMsgPlaceholderCodeGenInfo placeholderInfo =
        new GoogMsgPlaceholderCodeGenInfo(msgNode.isPlrselMsg());
    genGoogMsgCodeForChildren(msgParts, msgNode, placeholderInfo);
    // Generate JS comment (JSDoc) block for the goog.getMsg() call.
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    if (msgNode.getMeaning() != null) {
      jsDocBuilder.addAnnotation("meaning", msgNode.getMeaning());
    }
    // jscomp implicitly removes newlines from multiline descriptions so we can do that here without
    // changing behavior.  It also ensures we don't actually start a line with an '@' character
    // since afaict there is no escaping syntax in jsdoc.
    jsDocBuilder.addAnnotation(
        "desc", LINE_BOUNDARY_PATTERN.matcher(msgNode.getDesc()).replaceAll(" "));
    if (msgNode.getAlternateId().isPresent()) {
      jsDocBuilder.addAnnotation(
          "alternateMessageId", String.valueOf(msgNode.getAlternateId().getAsLong()));
    }

    // Generate goog.getMsg() call.
    VariableDeclaration.Builder builder =
        VariableDeclaration.builder(Id.create(googMsgVarName)).setJsDoc(jsDocBuilder.build());
    if (msgNode.getEscapingMode() == EscapingMode.ESCAPE_HTML) {
      // In HTML, we always pass three arguments to goog.getMsg().
      builder.setRhs(
          GOOG_GET_MSG.call(
              googMsgContent,
              msgNode.isPlrselMsg()
                  ? Expressions.EMPTY_OBJECT_LITERAL
                  : placeholderInfo.placeholders.build(),
              Expressions.objectLiteral(ImmutableMap.of("html", Expressions.LITERAL_TRUE))));
    } else if (msgNode.isPlrselMsg() || placeholderInfo.placeholders.isEmpty()) {
      // For plural/select msgs, we're letting goog.i18n.MessageFormat handle all placeholder
      // replacements, even ones that have nothing to do with plural/select. Therefore, this case
      // is the same as having no placeholder replacements.
      builder.setRhs(GOOG_GET_MSG.call(googMsgContent));
    } else {
      // If there are placeholders, pass them as an arg to goog.getMsg.
      builder.setRhs(GOOG_GET_MSG.call(googMsgContent, placeholderInfo.placeholders.build()));
    }
    Expression placeholders =
        msgNode.isPlrselMsg()
            ? placeholderInfo.pluralsAndSelects.putAll(placeholderInfo.placeholders).build()
            : null;
    return new GoogMsgCodeGenInfo(builder.build().ref(), placeholders);
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

    msgParts = IcuSyntaxUtils.convertMsgPartsToEmbeddedIcuSyntax(msgParts);

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
        throw new AssertionError("unexpected part: " + msgPart);
      }
    }

    return msgStrSb.toString();
  }

  /**
   * Generates the {@code goog.i18n.MessageFormat} postprocessing call for a child plural/select
   * message.
   */
  private static Expression getMessageFormatCall(GoogMsgCodeGenInfo codeGenInfo) {
    return construct(GOOG_I18N_MESSAGE_FORMAT, codeGenInfo.googMsgVar)
        .dotAccess("formatIgnoringPound")
        .call(codeGenInfo.placeholders);
  }

  private static final class GoogMsgCodeGenInfo {
    final Expression googMsgVar;

    /**
     * Placeholders that still need to be applied, if any. This is only relevant in plrsel messages
     * which require a different formatting method to be called.
     */
    @Nullable final Expression placeholders;

    GoogMsgCodeGenInfo(Expression googMsgVar, Expression placeholders) {
      this.googMsgVar = googMsgVar;
      this.placeholders = placeholders;
    }
  }

  /** Stores the data required for generating {@code goog.getMsg()} calls. */
  private static final class GoogMsgPlaceholderCodeGenInfo {

    /** Whether the message is a plural/select message. */
    final boolean isPlrselMsg;

    /** Key-value entries for placeholders. */
    final MapLiteralBuilder placeholders = new MapLiteralBuilder();

    /** Key-value entries for plural and select variables. */
    final MapLiteralBuilder pluralsAndSelects = new MapLiteralBuilder();

    GoogMsgPlaceholderCodeGenInfo(boolean isPlrselMsg) {
      this.isPlrselMsg = isPlrselMsg;
    }
  }

  /**
   * Generates {@code goog.getMsg()} calls for a given parent node and its children.
   *
   * @param parts the msg parts
   * @param msgNode The enclosing MsgNode.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForChildren(
      ImmutableList<SoyMsgPart> parts, MsgNode msgNode, GoogMsgPlaceholderCodeGenInfo codeGenInfo) {

    for (SoyMsgPart child : parts) {
      if (child instanceof SoyMsgRawTextPart) {
        // raw text doesn't have placeholders
      } else if (child instanceof SoyMsgSelectPart) {
        genGoogMsgCodeForSelectNode((SoyMsgSelectPart) child, msgNode, codeGenInfo);
      } else if (child instanceof SoyMsgPlaceholderPart) {
        genGoogMsgCodeForPlaceholder((SoyMsgPlaceholderPart) child, msgNode, codeGenInfo);
      } else if (child instanceof SoyMsgPluralPart) {
        genGoogMsgCodeForPluralNode((SoyMsgPluralPart) child, msgNode, codeGenInfo);
      } else {
        throw new AssertionError("unexpected child: " + child);
      }
    }
  }

  /**
   * Generates code bits for a {@code MsgPluralNode} subtree inside a message.
   *
   * @param pluralPart A node of type {@code SoyMsgPluralPart}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForPluralNode(
      SoyMsgPluralPart pluralPart, MsgNode msgNode, GoogMsgPlaceholderCodeGenInfo codeGenInfo) {
    // we need to always traverse into children in case any of the cases have unique placeholder
    MsgPluralNode reprNode = msgNode.getRepPluralNode(pluralPart.getPluralVarName());
    if (!codeGenInfo.pluralsAndSelects.contains(pluralPart.getPluralVarName())) {
      codeGenInfo.pluralsAndSelects.put(
          pluralPart.getPluralVarName(), translateExpr(reprNode.getExpr()));
    }

    for (SoyMsgPart.Case<SoyMsgPluralCaseSpec> child : pluralPart.getCases()) {
      genGoogMsgCodeForChildren(child.parts(), msgNode, codeGenInfo);
    }
  }

  /**
   * Generates code bits for a {@code SoyMsgSelectPart} part of a message.
   *
   * @param selectPart A node of type {@code MsgSelectNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForSelectNode(
      SoyMsgSelectPart selectPart, MsgNode msgNode, GoogMsgPlaceholderCodeGenInfo codeGenInfo) {
    // we need to always traverse into children in case any of the cases have unique placeholder
    MsgSelectNode reprNode = msgNode.getRepSelectNode(selectPart.getSelectVarName());
    if (!codeGenInfo.pluralsAndSelects.contains(selectPart.getSelectVarName())) {
      codeGenInfo.pluralsAndSelects.put(
          selectPart.getSelectVarName(), translateExpr(reprNode.getExpr()));
    }

    for (SoyMsgPart.Case<String> child : selectPart.getCases()) {
      genGoogMsgCodeForChildren(child.parts(), msgNode, codeGenInfo);
    }
  }

  private Expression translateExpr(ExprNode expr) {
    return owner.createExprTranslator().exec(expr);
  }

  /**
   * Generates code bits for a normal {@code MsgPlaceholderNode} inside a message.
   *
   * @param placeholder A node of type {@code MsgPlaceholderNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForPlaceholder(
      SoyMsgPlaceholderPart placeholder,
      MsgNode msgNode,
      GoogMsgPlaceholderCodeGenInfo codeGenInfo) {
    // For plural/select, the placeholder is an ICU placeholder, i.e. kept in all-caps. But for
    // goog.getMsg(), we must change the placeholder name to lower camel-case format.
    String googMsgPlaceholderName =
        codeGenInfo.isPlrselMsg
            ? placeholder.getPlaceholderName()
            : genGoogMsgPlaceholderName(placeholder.getPlaceholderName());
    if (codeGenInfo.placeholders.contains(googMsgPlaceholderName)) {
      return;
    }
    MsgPlaceholderNode reprNode = msgNode.getRepPlaceholderNode(placeholder.getPlaceholderName());

    codeGenInfo.placeholders.put(googMsgPlaceholderName, genGoogMsgPlaceholder(reprNode));
  }

  /**
   * Converts a Soy placeholder name (in upper underscore format) into a JS variable name (in lower
   * camel case format) used by goog.getMsg(). If the original name has a numeric suffix, it will be
   * preserved with an underscore.
   *
   * <p>For example, the following transformations happen:
   * <li>N : n
   * <li>NUM_PEOPLE : numPeople
   * <li>PERSON_2 : person_2
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

  /** Returns a code chunk for the given placeholder node. */
  protected Expression genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {

    List<Expression> contentChunks = new ArrayList<>();

    for (StandaloneNode contentNode : msgPhNode.getChildren()) {

      if (contentNode instanceof MsgHtmlTagNode
          && !isComputableAsJsExprsVisitor.exec(contentNode)) {
        // This is a MsgHtmlTagNode that is not computable as JS expressions. Visit it to
        // generate code to define the 'htmlTag<n>' variable.
        Statement decl = visit(contentNode);
        contentChunks.add(id("htmlTag" + contentNode.getId()).withInitialStatement(decl));

      } else if (contentNode instanceof CallNode) {
        // If the CallNode has any CallParamContentNode children that are not computable as JS
        // expressions, visit them to generate code to define their respective 'param<n>' variables.
        List<Statement> decls = new ArrayList<>();
        CallNode callNode = (CallNode) contentNode;
        for (CallParamNode grandchild : callNode.getChildren()) {
          if (grandchild instanceof CallParamContentNode
              && !isComputableAsJsExprsVisitor.exec(grandchild)) {
            decls.add(visit(grandchild));
          }
        }

        Expression call =
            genCallCodeUtils
                .genStandalone(callNode, owner.createExprTranslator())
                .withInitialStatements(decls);
        contentChunks.add(call);
      } else {
        List<Expression> chunks = genJsExprsVisitor.exec(contentNode);
        contentChunks.add(Expressions.concat(chunks));
      }
    }

    return Expressions.concat(contentChunks);
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for other specific nodes.

  /**
   * Example:
   *
   * <pre>
   *   &lt;a href="http://www.google.com/search?hl=en
   *     {for $i in range(3)}
   *       &amp;param{$i}={$i}
   *     {/for}
   *   "&gt;
   * might generate
   * </pre>
   *
   * var htmlTag84 = (new soy.StringBuilder()).append('&lt;a href="'); for (var i80 = 1; i80 &lt; 3;
   * i80++) { htmlTag84.append('&amp;param', i80, '=', i80); } htmlTag84.append('"&gt;'); </pre>
   */
  @Override
  protected Statement visitMsgHtmlTagNode(MsgHtmlTagNode node) {

    // This node should only be visited when it's not computable as JS expressions, because this
    // method just generates the code to define the temporary 'htmlTag<n>' variable.
    if (isComputableAsJsExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'htmlTag<n>' when not computable as JS expressions.");
    }

    outputVars.pushOutputVar("htmlTag" + node.getId());
    List<Statement> children = visitChildren(node);
    outputVars.popOutputVar();
    return Statements.of(children);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected Statement visitSoyNode(SoyNode node) {
    return owner.visit(node);
  }

  /**
   * Helper class for building up the input to {@link Expressions#objectLiteral}. TODO(user):
   * consider making this part of the CodeChunk DSL, since all callers seem to do something similar.
   */
  private static final class MapLiteralBuilder {
    final Map<String, Expression> map = new LinkedHashMap<>();

    @CanIgnoreReturnValue
    MapLiteralBuilder put(String key, Expression value) {
      Expression prev = map.put(key, value);
      if (prev != null) {
        throw new IllegalArgumentException("already generated this placeholder");
      }
      return this;
    }

    public boolean contains(String selectVarName) {
      return map.containsKey(selectVarName);
    }

    boolean isEmpty() {
      return map.isEmpty();
    }

    @CanIgnoreReturnValue
    MapLiteralBuilder putAll(MapLiteralBuilder other) {
      checkState(Sets.intersection(map.keySet(), other.map.keySet()).isEmpty());
      map.putAll(other.map);
      return this;
    }

    Expression build() {
      return Expressions.objectLiteralWithQuotedKeys(map);
    }
  }
}
