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
import static com.google.template.soy.jssrc.dsl.Statement.forOf;
import static com.google.template.soy.jssrc.internal.JsRuntime.GOOG_STRING_UNESCAPE_ENTITIES;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.DoWhile;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Statement;
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
import com.google.template.soy.soytree.VeLogNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
  private final Map<String, MsgPlaceholderNode> placeholderNames = new LinkedHashMap<>();

  /**
   * Wrapper character around placeholder placeholders.  This is used to locate placeholder names in
   * the translated result so we can instead run the idom instructions in their MsgPlaceholderNodes.
   * The value is an arbitrary but short character that cannot appear in translated messages.
   */
  private static final String PLACEHOLDER_WRAPPER = "\u0001";

  /** A JS regex literal that matches our placeholder placeholders. */
  private static final String PLACEHOLDER_REGEX = "/\\x01\\d+\\x01/g";

  private final String staticDecl;
  private final GenIncrementalDomCodeVisitor idomMaster;

  AssistantForHtmlMsgs(
      GenIncrementalDomCodeVisitor master,
      SoyJsSrcOptions jsSrcOptions,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsJsExprsVisitor isComputableAsJsExprsVisitor,
      TemplateAliases functionAliases,
      GenJsExprsVisitor genJsExprsVisitor,
      TranslationContext translationContext,
      ErrorReporter errorReporter,
      String staticDecl) {
    super(
        master,
        jsSrcOptions,
        genCallCodeUtils,
        isComputableAsJsExprsVisitor,
        functionAliases,
        genJsExprsVisitor,
        translationContext,
        errorReporter);
    this.staticDecl = staticDecl;
    this.idomMaster = master;
  }

  @Override
  public Expression generateMsgGroupVariable(MsgFallbackGroupNode node) {
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


    // The raw translated text, with placeholder placeholders.
    Expression translationVar = super.generateMsgGroupVariable(node);

    // If there are no placeholders, we don't need anything special (but we still need to unescape).
    if (placeholderNames.isEmpty()) {
      Expression unescape = GOOG_STRING_UNESCAPE_ENTITIES.call(translationVar);
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

    // We assume at this point that the statics array has been populated with something like
    // [['hi', '\\x01\foo'], ['ho', '\\x02\foo']]

    // Consider this block the body of the for loop
    ImmutableList.Builder<Statement> body = ImmutableList.builder();
    String itemId = "i" + node.getId();
    // Get a handle to the ith item of the static array
    Expression item = Expression.id("i" + node.getId());

    // The first element contains some text that we call using itext
    body.add(
        item.bracketAccess(Expression.number(0))
            .and(
                INCREMENTAL_DOM_TEXT.call(item.bracketAccess(Expression.number(0))),
                translationContext.codeGenerator())
            .asStatement());

    // The second element contains a placeholder string. We then execute a switch statement
    // to decide which branch to execute.
    SwitchBuilder switchBuilder = Statement.switchValue(item.bracketAccess(Expression.number(1)));
    for (Map.Entry<String, MsgPlaceholderNode> ph : placeholderNames.entrySet()) {
      Statement value = idomMaster.visitForUseByAssistantsAsCodeChunk(ph.getValue());
      MsgPlaceholderNode phNode = ph.getValue();
      if (phNode.getParent() instanceof VeLogNode) {
        VeLogNode parent = (VeLogNode) phNode.getParent();
        if (parent.getChild(0) == phNode) {
          GenIncrementalDomCodeVisitor.VeLogStateHolder state = idomMaster.openVeLogNode(parent);
          // It is a compiler failure to have a logOnly in a message node.
          Preconditions.checkState(state.logOnlyConditional == null);
          value = Statement.of(state.enterStatement, value);
        }
        if (parent.getChild(parent.numChildren() - 1) == phNode) {
          value = Statement.of(value, idomMaster.exitVeLogNode(parent, null));
        }
      }
      switchBuilder.addCase(Expression.stringLiteral(ph.getKey()), value);
    }
    body.add(switchBuilder.build());
    // End of for loop

    Statement loop =
        forOf(
            itemId,
            Expression.id(staticDecl).bracketAccess(translationVar),
            Statement.of(body.build()));

    return Statement.of(staticsInitializer(node, translationVar), loop);
  }

  /**
   * In all cases, messages that are broken up into placeholders typically contain the same parts.
   * That is, a message that looks like <div>FooBar{$foo}</div> in all invocations will be broken up
   * into {DIV_PLACEHOLDER}{CONSTANT_MESSAGE}{PRINT $FOO}{END_DIV_PLACEHOLDER}. In order to avoid
   * parsing and splitting up this over and over again, we execute the splitting logic once and save
   * the results so that future usages are fast.
   *
   * <p>Concretely speaking, this is basically an uninitialized array that lives in the module
   * scope. See staticsDecl.
   */
  private Statement staticsInitializer(MsgFallbackGroupNode node, Expression translationVar) {

    // All of these helper variables must have uniquely-suffixed names because {msg}s can be nested.

    // The mutable (tracking index of last match) regex to find the placeholder placeholders.
    VariableDeclaration regexVar =
        VariableDeclaration.builder("partRe_" + node.getId())
            .setRhs(Expression.regexLiteral(PLACEHOLDER_REGEX))
            .build();
    // The current placeholder from the regex.
    VariableDeclaration matchVar =
        VariableDeclaration.builder("match_" + node.getId()).setMutable().build();
    // The index of the end of the previous placeholder, where the next raw text run starts.
    VariableDeclaration lastIndexVar =
        VariableDeclaration.builder("lastIndex_" + node.getId())
            .setMutable()
            .setRhs(Expression.number(0))
            .build();
    // A counter to increment and update the statics array.
    VariableDeclaration counter =
        VariableDeclaration.builder("counter_" + node.getId())
            .setMutable()
            .setRhs(Expression.number(0))
            .build();

    List<Statement> doBody = new ArrayList<>();
    // Execute the regex on the string to get the next matching pair.
    // match_XXX = partRe_XXX.exec(MSG_EXTERNAL_XXX) || undefined;
    doBody.add(
        matchVar
            .ref()
            .assign(
                regexVar
                    .ref()
                    .dotAccess("exec")
                    .call(translationVar)
                    // Replace null with undefined.  This is necessary to make substring() treat
                    // falsy as an omitted
                    // parameter, so that it goes until the end of the string.  Otherwise, the
                    // non-numeric parameter
                    // would be coerced to zero.
                    .or(Expression.id("undefined"), translationContext.codeGenerator()))
            .asStatement());

    // Emit the (possibly-empty) run of raw text since the last placeholder, until this placeholder,
    // or until the end of the source string.
    Expression endIndex =
        matchVar.ref().and(matchVar.ref().dotAccess("index"), translationContext.codeGenerator());
    // Incremental DOM usually unescapes all strings at compile time. However, for messages
    // we need to do so at runtime so that entities like `&lt`; becomes <
    Expression unescape =
        GOOG_STRING_UNESCAPE_ENTITIES.call(
            // This unescapes the string up from the beginning of the text content to the next
            // placeholder
            translationVar.dotAccess("substring").call(lastIndexVar.ref(), endIndex));

    // First start off by initializing the statics declaration array (it is undefined before)
    doBody.add(
        Expression.id(staticDecl)
            .bracketAccess(translationVar)
            .bracketAccess(counter.ref())
            .assign(
                Expression.arrayLiteral(
                    ImmutableList.of(
                        unescape,
                        matchVar
                            .ref()
                            .and(
                                matchVar.ref().bracketAccess(Expression.number(0)),
                                translationContext.codeGenerator()))))
            .asStatement());

    // counter++
    doBody.add(counter.ref().assign(counter.ref().plus(Expression.number(1))).asStatement());
    // Update the beginning of the string to parse to the end of the current one.
    doBody.add(lastIndexVar.ref().assign(regexVar.ref().dotAccess("lastIndex")).asStatement());

    Statement statement =
        Statement.of(
            Expression.id(staticDecl)
                .bracketAccess(translationVar)
                .assign(Expression.arrayLiteral(ImmutableList.of()))
                .asStatement(),
            Statement.of(translationVar.initialStatements()),
            regexVar,
            lastIndexVar,
            counter,
            matchVar,
            DoWhile.builder().setCondition(matchVar.ref()).setBody(Statement.of(doBody)).build());
    statement =
        Statement.ifStatement(
                Expression.not(Expression.id(staticDecl).bracketAccess(translationVar)), statement)
            .build();
    return statement;
  }

  @Override
  protected Expression genGoogMsgPlaceholder(MsgPlaceholderNode msgPhNode) {
    // Mark the node so we know what instructions to emit.
    String name = PLACEHOLDER_WRAPPER + placeholderNames.size() + PLACEHOLDER_WRAPPER;
    placeholderNames.put(name, msgPhNode);
    // Return the marker string to insert into the translated text.
    return Expression.stringLiteral(name);
  }
}
