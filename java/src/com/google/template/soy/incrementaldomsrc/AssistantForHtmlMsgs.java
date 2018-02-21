/*
 * Copyright 2016 Google Inc.
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
package com.google.template.soy.incrementaldomsrc;

import static com.google.template.soy.incrementaldomsrc.IncrementalDomRuntime.INCREMENTAL_DOM_TEXT;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_STRING_UNESCAPE_ENTITIES;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.DoWhile;
import com.google.template.soy.jssrc.dsl.SwitchBuilder;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.jssrc.internal.GenCallCodeUtils;
import com.google.template.soy.jssrc.internal.GenJsCodeVisitorAssistantForMsgs;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor;
import com.google.template.soy.jssrc.internal.IsComputableAsJsExprsVisitor;
import com.google.template.soy.jssrc.internal.TemplateAliases;
import com.google.template.soy.jssrc.internal.TranslationContext;
import com.google.template.soy.soytree.HtmlContext;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates <code>{msg}</code> commands in HTML context into idom instructions.
 *
 * This class is not reusable.
 *
 * This will pass all interpolated values as special placeholder strings. It will then extract these
 * placeholders from the translated message and execute the idom commands instead.
 */
final class AssistantForHtmlMsgs extends GenJsCodeVisitorAssistantForMsgs {

  /**
   * Maps dynamic nodes within the translated message to placeholder values to pass to goog.getMsg()
   * and substitute for idom commands.
   */
  private final Map<String, MsgPlaceholderNode> placeholderNames = Maps.newHashMap();

  /**
   * Wrapper character around placeholder placeholders.  This is used to locate placeholder names in
   * the translated result so we can instead run the idom instructions in their MsgPlaceholderNodes.
   * The value is an arbitrary but short character that cannot appear in translated messages.
   */
  private static final String PLACEHOLDER_WRAPPER = "\u0001";

  /** A JS regex literal that matches our placeholder placeholders. */
  private static final String PLACEHOLDER_REGEX = "/\\x01\\d+\\x01/g";

  AssistantForHtmlMsgs(
      GenIncrementalDomCodeVisitor master,
      SoyJsSrcOptions jsSrcOptions,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      TemplateAliases functionAliases,
      GenJsExprsVisitor genJsExprsVisitor,
      TranslationContext translationContext,
      ErrorReporter errorReporter) {
    super(
        master,
        jsSrcOptions,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        functionAliases,
        genJsExprsVisitor,
        translationContext,
        errorReporter);
  }

  @Override
  public CodeChunk.WithValue generateMsgGroupVariable(MsgFallbackGroupNode node) {
    throw new IllegalStateException(
        "This class should only be used for via the new idom entry-point.");
  }

  /**
   * Returns a code chunk of idom instructions that output the contents of a translated message as
   * HTML. For example:
   *
   * <pre>
   *   {msg desc="Says hello to a person."}Hello {$name}!{/msg}
   * </pre>
   *
   * compiles to
   *
   * <pre>
   *   /** @desc Says hello to a person. *{@literal /}
   *   var MSG_EXTERNAL_6936162475751860807 = goog.getMsg(
   *       'Hello {$name}!',
   *       {'name': '\u00010\u0001'});
   *   var lastIndex_1153 = 0, partRe_1153 = /\x01\d+\x01/g, match_1153;
   *   do {
   *     match_1153 = partRe_1153.exec(MSG_EXTERNAL_6936162475751860807) || undefined;
   *     incrementalDom.text(goog.string.unescapeEntities(
   *         MSG_EXTERNAL_6936162475751860807.substring(
   *           lastIndex_1153, match_1153 && match_1153.index)));
   *     lastIndex_1153 = partRe_1153.lastIndex;
   *     switch (match_1153 && match_1153[0]) {
   *       case '\u00010\u0001':
   *         var dyn8 = opt_data.name;
   *         if (typeof dyn8 == 'function') dyn8();
   *         else if (dyn8 != null) incrementalDom.text(dyn8);
   *         break;
   *     }
   *   } while (match_1153);
   * </pre>
   *
   * Each interpolated MsgPlaceholderNode (either for HTML tags or for print statements) compiles to
   * a separate {@code case} statement.
   */
  CodeChunk generateMsgGroupCode(MsgFallbackGroupNode node) {
    Preconditions.checkState(placeholderNames.isEmpty(), "This class is not reusable.");
    // Non-HTML {msg}s should be extracted into LetContentNodes and handled by jssrc.
    Preconditions.checkArgument(node.getHtmlContext() == HtmlContext.HTML_PCDATA,
        "AssistantForHtmlMsgs is only for HTML {msg}s.");

    // All of these helper variables must have uniquely-suffixed names because {msg}s can be nested.

    // It'd be nice to move this codegen to a Soy template...

    // The raw translated text, with placeholder placeholders.
    CodeChunk.WithValue translationVar = super.generateMsgGroupVariable(node);

    // If there are no placeholders, we don't need anything special (but we still need to unescape).
    if (placeholderNames.isEmpty()) {
      CodeChunk.WithValue unescape = GOOG_STRING_UNESCAPE_ENTITIES.call(translationVar);
      return INCREMENTAL_DOM_TEXT.call(unescape);
    }

    // The translationVar may be non-trivial if escaping directives are applied to it, if so bounce
    // it into a fresh variable.
    if (!translationVar.isCheap()) {
      translationVar =
          translationContext
              .codeGenerator()
              .declarationBuilder()
              .setRhs(translationVar)
              .build()
              .ref();
    }

    // Declare everything.

    // The mutable (tracking index of last match) regex to find the placeholder placeholders.
    VariableDeclaration regexVar =
        VariableDeclaration.builder("partRe_" + node.getId())
            .setRhs(CodeChunk.regexLiteral(PLACEHOLDER_REGEX))
            .build();
    // The current placeholder from the regex.
    VariableDeclaration matchVar = VariableDeclaration.builder("match_" + node.getId()).build();
    // The index of the end of the previous placeholder, where the next raw text run starts.
    VariableDeclaration lastIndexVar =
        VariableDeclaration.builder("lastIndex_" + node.getId())
            .setRhs(CodeChunk.number(0))
            .build();

    List<CodeChunk> doBody = new ArrayList<>();
    // match_XXX = partRe_XXX.exec(MSG_EXTERNAL_XXX) || undefined;
    doBody.add(
        matchVar
            .ref()
            .assign(
                regexVar
                    .ref()
                    .dotAccess("exec")
                    .call(translationVar)
                    .or(CodeChunk.id("undefined"), translationContext.codeGenerator())));
    // Replace null with undefined.  This is necessary to make substring() treat falsy as an omitted
    // parameter, so that it goes until the end of the string.  Otherwise, the non-numeric parameter
    // would be coerced to zero.

    // Emit the (possibly-empty) run of raw text since the last placeholder, until this placeholder,
    // or until the end of the source string.
    CodeChunk.WithValue endIndex =
        matchVar.ref().and(matchVar.ref().dotAccess("index"), translationContext.codeGenerator());
    CodeChunk.WithValue unescape =
        GOOG_STRING_UNESCAPE_ENTITIES.call(
            translationVar.dotAccess("substring").call(lastIndexVar.ref(), endIndex));

    doBody.add(INCREMENTAL_DOM_TEXT.call(unescape));
    doBody.add(lastIndexVar.ref().assign(regexVar.ref().dotAccess("lastIndex")));
    // switch (match_XXX && match_XXX[0]) { ... }
    SwitchBuilder switchBuilder =
        CodeChunk.switch_(
            matchVar
                .ref()
                .and(
                    matchVar.ref().bracketAccess(CodeChunk.number(0)),
                    translationContext.codeGenerator()));

    for (Map.Entry<String, MsgPlaceholderNode> ph : placeholderNames.entrySet()) {
      switchBuilder.case_(
          CodeChunk.stringLiteral(ph.getKey()),
          master.visitForUseByAssistantsAsCodeChunk(ph.getValue()));
    }
    doBody.add(switchBuilder.build());
    return CodeChunk.statements(
        CodeChunk.statements(translationVar.initialStatements()),
        regexVar,
        lastIndexVar,
        matchVar,
        DoWhile.builder()
            .setCondition(matchVar.ref())
            .setBody(CodeChunk.statements(doBody))
            .build());
  }

  @Override
  protected CodeChunk.WithValue genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {
    // Mark the node so we know what instructions to emit.
    String name = PLACEHOLDER_WRAPPER + placeholderNames.size() + PLACEHOLDER_WRAPPER;
    placeholderNames.put(name, msgPhNode);
    // Return the marker string to insert into the translated text.
    return CodeChunk.stringLiteral(name);
  }
}
