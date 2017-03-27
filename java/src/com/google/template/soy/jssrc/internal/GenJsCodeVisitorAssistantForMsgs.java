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

import static com.google.template.soy.jssrc.dsl.CodeChunk.declare;
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.ifStatement;
import static com.google.template.soy.jssrc.dsl.CodeChunk.mapLiteral;
import static com.google.template.soy.jssrc.dsl.CodeChunk.new_;
import static com.google.template.soy.jssrc.dsl.CodeChunk.stringLiteral;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_GET_MSG;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_GET_MSG_WITH_FALLBACK;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_I18N_MESSAGE_FORMAT;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.Declaration;
import com.google.template.soy.msgs.internal.IcuSyntaxUtils;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

  /**
   * Used for looking up the local name for a given template call to a fully qualified template
   * name.
   */
  private final TemplateAliases templateAliases;

  protected final TranslationContext translationContext;

  private final ErrorReporter errorReporter;

  /**
   * @param master The master GenJsCodeVisitor instance.
   * @param jsExprTranslator Instance of JsExprTranslator to use.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsJsExprsVisitor The IsComputableAsJsExprsVisitor to use.
   * @param genJsExprsVisitor The current GenJsExprsVisitor.
   */
  protected GenJsCodeVisitorAssistantForMsgs(
      GenJsCodeVisitor master,
      SoyJsSrcOptions jsSrcOptions,
      JsExprTranslator jsExprTranslator,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      TemplateAliases functionAliases,
      GenJsExprsVisitor genJsExprsVisitor,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {
    this.master = master;
    this.jsSrcOptions = jsSrcOptions;
    this.jsExprTranslator = jsExprTranslator;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsJsExprsVisitor = isComputableAsJsExprsVisitor;
    this.templateAliases = functionAliases;
    this.genJsExprsVisitor = genJsExprsVisitor;
    this.translationContext = translationContext;
    this.errorReporter = errorReporter;
  }


  @Override public Void exec(SoyNode node) {
    throw new AssertionError();
  }

  /** The JsCodeBuilder to build the current JS file being generated (during a run). */
  protected JsCodeBuilder jsCodeBuilder() {
    return master.jsCodeBuilder;
  }

  /**
   * Returns a code chunk that declares a translated variable.
   *
   * <p>Example:
   *
   * <pre>
   *   {msg desc="Link to help content."}Learn more{/msg}
   *   {msg desc="Tells user how to access a product." hidden="true"}
   *     Click &lt;a href="{$url}"&gt;here&lt;/a&gt; to access {$productName}.
   *   {/msg}
   * </pre>
   *
   * might generate
   *
   * <pre>
   *   /** @desc Link to help content. *{@literal /}
   *   var MSG_UNNAMED_9 = goog.getMsg('Learn more');
   *   /** @desc Tells user how to access a product.
   *    *  @hidden *{@literal /}
   *   var MSG_UNNAMED_10 = goog.getMsg(
   *       'Click {$startLink}here{$endLink} to access {$productName}.',
   *       {startLink: '&lt;a href="' + opt_data.url + '"&gt;',
   *        endLink: '&lt;/a&gt;',
   *        productName: opt_data.productName});
   * </pre>
   */
  public CodeChunk.WithValue generateMsgGroupVariable(MsgFallbackGroupNode node) {
    return node.hasFallbackMsg()
        ? generateMsgGroupVariableWithFallbackMsgs(node)
        : generateSingleMsgVariable(node.getChild(0));
  }

  /** Returns a code chunk representing a {@link MsgNode} with no fallback messages. */
  private CodeChunk.WithValue generateSingleMsgVariable(MsgNode msgNode) {
    String varName = getGoogMsgVarName(msgNode);
    GoogMsgCodeGenInfo googMsgCodeGenInfo = genGoogGetMsgCallHelper(varName, msgNode);
    if (!msgNode.isPlrselMsg()) {
      // No postprocessing is needed. Simply use the original goog.getMsg var.
      return id(varName);
    }

    // For plural/select messages, return a code chunk that sets up the message variable
    // with the correct goog.i18n.MessageFormat calls.
    return translationContext
        .codeGenerator()
        .declare(googMsgCodeGenInfo.getMessageFormatCall())
        .ref();
  }

  /**
   * Returns a code chunk representing a {@link MsgFallbackGroupNode}, initialized with calls to
   * {@code goog.getMsgWithFallback} (and if there are plurals or selects, {@code
   * goog.i18n.MessageFormat}).
   */
  private CodeChunk.WithValue generateMsgGroupVariableWithFallbackMsgs(MsgFallbackGroupNode node) {
    List<GoogMsgCodeGenInfo> childGenInfos = new ArrayList<>(node.numChildren());

    // Generate the goog.getMsg calls for all children.
    for (MsgNode msgNode : node.getChildren()) {
      String googMsgVarName = getGoogMsgVarName(msgNode);
      childGenInfos.add(genGoogGetMsgCallHelper(googMsgVarName, msgNode));
    }

    ImmutableList.Builder<CodeChunk.WithValue> args = ImmutableList.builder();
    for (GoogMsgCodeGenInfo childGoogMsgCodeGenInfo : childGenInfos) {
      args.add(CodeChunk.id(childGoogMsgCodeGenInfo.googMsgVarName));
    }
    // Declare a temporary variable to hold the getMsgWithFallback() call so that we can apply any
    // MessageFormats from any of the fallbacks.
    Declaration decl =
        translationContext.codeGenerator().declare(GOOG_GET_MSG_WITH_FALLBACK.call(args.build()));

    ImmutableList.Builder<CodeChunk> initialStatements = ImmutableList.builder();

    // Generate the goog.i18n.MessageFormat calls for child plural/select messages (if any), each
    // wrapped in an if-block that will only execute if that child is the chosen message.
    for (GoogMsgCodeGenInfo child : childGenInfos) {
      if (child.isPlrselMsg) {
        initialStatements.add(
            ifStatement(
                    decl.ref().doubleEquals(id(child.googMsgVarName)),
                    decl.ref().assign(child.getMessageFormatCall()))
                .build());
      }
    }

    return decl.ref().withInitialStatements(initialStatements.build());
  }

  /** Returns the variable name for the given {@link MsgNode}. */
  private String getGoogMsgVarName(MsgNode msgNode) {
    // MSG_UNNAMED/MSG_EXTERNAL are special tokens recognized by JSCompiler.
    // MSG_UNNAMED disables the default logic that requires all messages to be uniquely named,
    // and MSG_EXTERNAL tells JSCompiler that the content of the message comes from outside
    // JSCompiler.
    String desiredName =
        jsSrcOptions.googMsgsAreExternal()
            ? "MSG_EXTERNAL_" + MsgUtils.computeMsgIdForDualFormat(msgNode)
            : "MSG_UNNAMED";
    return translationContext.nameGenerator().generateName(desiredName);
  }


  /**
   * Generates the {@code goog.getMsg} call for an MsgNode. The call (including JsDoc) will be
   * appended to the JsCodeBuilder.
   *
   * @return The {@link GoogMsgCodeGenInfo} created in the process, which may be needed for
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
    CodeChunk.WithValue googMsgContent =
        stringLiteral(buildGoogMsgContentStr(msgParts, msgNode.isPlrselMsg()));

    // Build the individual code bits for each placeholder (i.e. "<placeholderName>: <exprCode>")
    // and each plural/select (i.e. "<varName>: <exprCode>").
    GoogMsgCodeGenInfo googMsgCodeGenInfo =
        new GoogMsgCodeGenInfo(googMsgVarName, msgNode.isPlrselMsg());
    genGoogMsgCodeForChildren(msgNode, msgNode, googMsgCodeGenInfo);

    // Generate JS comment (JSDoc) block for the goog.getMsg() call.
    jsCodeBuilder().appendLineStart("/** ");
    if (msgNode.getMeaning() != null) {
      jsCodeBuilder().appendLineEnd("@meaning ", msgNode.getMeaning());
      jsCodeBuilder().appendLineStart(" *  ");
    }
    jsCodeBuilder().append("@desc ", msgNode.getDesc());
    if (msgNode.isHidden()) {
      jsCodeBuilder().appendLineEnd();
      jsCodeBuilder().appendLineStart(" *  @hidden");
    }
    jsCodeBuilder().appendLineEnd(" */");

    // Generate goog.getMsg() call.
    if (msgNode.isPlrselMsg() || googMsgCodeGenInfo.placeholders.isEmpty()) {
      // For plural/select msgs, we're letting goog.i18n.MessageFormat handle all placeholder
      // replacements, even ones that have nothing to do with plural/select. Therefore, this case
      // is the same as having no placeholder replacements.
      jsCodeBuilder()
          .append(declare(googMsgCodeGenInfo.googMsgVarName, GOOG_GET_MSG.call(googMsgContent)));
    } else {
      // If there are placeholders, pass them as an arg to goog.getMsg.
      jsCodeBuilder()
          .append(
              declare(
                  googMsgCodeGenInfo.googMsgVarName,
                  GOOG_GET_MSG.call(googMsgContent, googMsgCodeGenInfo.placeholders.build())));
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

  /** Stores the data required for generating {@code goog.getMsg()} calls. */
  private static final class GoogMsgCodeGenInfo {

    /** The name of the {@code goog.getMsg()} msg var, i.e. MSG_EXTERNAL_### or MSG_UNNAMED_###. */
    final String googMsgVarName;

    /** Whether the message is a plural/select message. */
    final boolean isPlrselMsg;

    /** Key-value entries for placeholders. */
    final MapLiteralBuilder placeholders = new MapLiteralBuilder();

    /** Key-value entries for plural and select variables. */
    final MapLiteralBuilder pluralsAndSelects = new MapLiteralBuilder();

    GoogMsgCodeGenInfo(String googMsgVarName, boolean isPlrselMsg) {
      this.googMsgVarName = googMsgVarName;
      this.isPlrselMsg = isPlrselMsg;
    }

    /**
     * Returns a code chunk representing a {@code goog.i18n.MessageFormat.formatIgnoringPound} call
     * on the message variable represented by this object.
     */
    CodeChunk.WithValue getMessageFormatCall() {
      pluralsAndSelects.putAll(placeholders);
      return new_(GOOG_I18N_MESSAGE_FORMAT)
          .call(id(googMsgVarName))
          .dotAccess("formatIgnoringPound")
          .call(pluralsAndSelects.build());
    }
  }

  /**
   * Generates {@code goog.getMsg()} calls for a given parent node and its children.
   *
   * @param parentNode A parent node of one of these types: {@link MsgNode}, {@link
   *     com.google.template.soy.soytree.MsgPluralCaseNode}, {@link
   *     com.google.template.soy.soytree.MsgPluralDefaultNode}, {@link
   *     com.google.template.soy.soytree.MsgSelectCaseNode} {@link
   *     com.google.template.soy.soytree.MsgSelectDefaultNode}.
   * @param msgNode The enclosing MsgNode.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForChildren(
      BlockNode parentNode, MsgNode msgNode, GoogMsgCodeGenInfo codeGenInfo) {

    for (StandaloneNode child : parentNode.getChildren()) {
      if (child instanceof RawTextNode) {
        // nothing to do
      } else if (child instanceof MsgPlaceholderNode) {
        genGoogMsgCodeForPlaceholder((MsgPlaceholderNode) child, msgNode, codeGenInfo);
      } else if (child instanceof MsgPluralNode) {
        genGoogMsgCodeForPluralNode((MsgPluralNode) child, msgNode, codeGenInfo);
      } else if (child instanceof MsgSelectNode) {
        genGoogMsgCodeForSelectNode((MsgSelectNode) child, msgNode, codeGenInfo);
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
   *
   * @param pluralNode A node of type {@code MsgPluralNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param googMsgCodeGenInfo Data structure holding information on placeholder names, plural
   *     variable names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForPluralNode(
      MsgPluralNode pluralNode, MsgNode msgNode, GoogMsgCodeGenInfo googMsgCodeGenInfo) {

    googMsgCodeGenInfo.pluralsAndSelects.put(
        stringLiteral(msgNode.getPluralVarName(pluralNode)),
        jsExprTranslator.translateToCodeChunk(
            pluralNode.getExpr(), translationContext, errorReporter));

    for (CaseOrDefaultNode child : pluralNode.getChildren()) {
      genGoogMsgCodeForChildren(child, msgNode, googMsgCodeGenInfo);
    }
  }


  /**
   * Generates code bits for a {@code MsgSelectNode} subtree inside a message.
   *
   * @param selectNode A node of type {@code MsgSelectNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForSelectNode(
      MsgSelectNode selectNode, MsgNode msgNode, GoogMsgCodeGenInfo codeGenInfo) {

    codeGenInfo.pluralsAndSelects.put(
        stringLiteral(msgNode.getSelectVarName(selectNode)),
        jsExprTranslator.translateToCodeChunk(
            selectNode.getExpr(), translationContext, errorReporter));

    for (CaseOrDefaultNode child : selectNode.getChildren()) {
      genGoogMsgCodeForChildren(child, msgNode, codeGenInfo);
    }
  }


  /**
   * Generates code bits for a normal {@code MsgPlaceholderNode} inside a message.
   *
   * @param node A node of type {@code MsgPlaceholderNode}.
   * @param msgNode The enclosing {@code MsgNode} object.
   * @param codeGenInfo Data structure holding information on placeholder names, plural variable
   *     names, and select variable names to be used for message code generation.
   */
  private void genGoogMsgCodeForPlaceholder(
      MsgPlaceholderNode node, MsgNode msgNode, GoogMsgCodeGenInfo codeGenInfo) {

    String placeholderName = msgNode.getPlaceholderName(node);

    // For plural/select, the placeholder is an ICU placeholder, i.e. kept in all-caps. But for
    // goog.getMsg(), we must change the placeholder name to lower camel-case format.
    String googMsgPlaceholderName =
        codeGenInfo.isPlrselMsg ? placeholderName : genGoogMsgPlaceholderName(placeholderName);

    codeGenInfo.placeholders.put(
        stringLiteral(googMsgPlaceholderName), genGoogMsgPlaceholder(node));
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


  /** Returns a code chunk for the given placeholder node. */
  protected CodeChunk.WithValue genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {
    List<CodeChunk.WithValue> contentChunks = new ArrayList<>(msgPhNode.numChildren());

    for (StandaloneNode contentNode : msgPhNode.getChildren()) {

      if (contentNode instanceof MsgHtmlTagNode &&
          !isComputableAsJsExprsVisitor.exec(contentNode)) {
        // This is a MsgHtmlTagNode that is not computable as JS expressions. Visit it to
        // generate code to define the 'htmlTag<n>' variable.
        visit(contentNode);
        contentChunks.add(id("htmlTag" + contentNode.getId()));

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

        CodeChunk.WithValue call =
            genCallCodeUtils.gen(callNode, templateAliases, translationContext, errorReporter);
        contentChunks.add(call);
      } else {
        List<CodeChunk.WithValue> chunks = genJsExprsVisitor.exec(contentNode);
        contentChunks.add(CodeChunkUtils.concatChunks(chunks));
      }
    }

    return CodeChunkUtils.concatChunks(contentChunks);
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

    jsCodeBuilder().pushOutputVar("htmlTag" + node.getId());
    visitChildren(node);
    jsCodeBuilder().popOutputVar();
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    master.visitForUseByAssistants(node);
  }

  /**
   * Helper class for building up the input to {@link CodeChunk#mapLiteral}. TODO(brndn): consider
   * making this part of the CodeChunk DSL, since all callers seem to do something similar.
   */
  private static final class MapLiteralBuilder {
    final ImmutableList.Builder<CodeChunk.WithValue> keys = ImmutableList.builder();
    final ImmutableList.Builder<CodeChunk.WithValue> values = ImmutableList.builder();
    final Set<CodeChunk.WithValue> knownKeys = new HashSet<>();

    MapLiteralBuilder put(CodeChunk.WithValue key, CodeChunk.WithValue value) {
      // No-op if the key already exists. This happens whenever a placeholder is repeated
      // in a message (different branches of an {if}, {select}, etc.)
      if (knownKeys.add(key)) {
        keys.add(key);
        values.add(value);
      }
      return this;
    }

    boolean isEmpty() {
      return knownKeys.isEmpty();
    }

    MapLiteralBuilder putAll(MapLiteralBuilder other) {
      ImmutableList<CodeChunk.WithValue> keys = other.keys.build();
      ImmutableList<CodeChunk.WithValue> values = other.values.build();
      Preconditions.checkState(keys.size() == values.size());
      Preconditions.checkState(Sets.intersection(knownKeys, other.knownKeys).isEmpty());
      for (int i = 0; i < keys.size(); ++i) {
        put(keys.get(i), values.get(i));
      }
      return this;
    }

    CodeChunk.WithValue build() {
      return mapLiteral(keys.build(), values.build());
    }
  }
}
