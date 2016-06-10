/*
 * Copyright 2015 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoytreeUtils;

import junit.framework.TestCase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link TranslateToJsExprVisitor}.
 *
 */
public final class TranslateToJsExprVisitorTest extends TestCase {

  private static final Deque<Map<String, JsExpr>> LOCAL_VAR_TRANSLATIONS = new ArrayDeque<>();
  static {
    Map<String, JsExpr> frame = Maps.newHashMap();
    // Let 'goo' simulate a local variable from a 'foreach' loop.
    frame.put("goo", new JsExpr("gooData8", Integer.MAX_VALUE));
    frame.put("goo__isFirst", new JsExpr("gooIndex8 == 0", Operator.EQUAL.getPrecedence()));
    frame.put("goo__isLast", new JsExpr("gooIndex8 == gooListLen8 - 1",
                                        Operator.EQUAL.getPrecedence()));
    frame.put("goo__index", new JsExpr("gooIndex8", Integer.MAX_VALUE));
    LOCAL_VAR_TRANSLATIONS.push(frame);
  }


  public void testStringLiteral() {
    assertTranslation(
        "'waldo'",
        new JsExpr("'waldo'", Integer.MAX_VALUE));
    // Ensure Unicode gets escaped, since there's no guarantee about the output encoding of the JS.
    assertTranslation(
        "'\u05E9\u05DC\u05D5\u05DD'",
        new JsExpr("'\\u05E9\\u05DC\\u05D5\\u05DD'", Integer.MAX_VALUE));
  }


  public void testListLiteral() {

    assertTranslation(
        "['blah', 123, $foo]",
        new JsExpr("['blah', 123, opt_data.foo]", Integer.MAX_VALUE));
    assertTranslation("[]", new JsExpr("[]", Integer.MAX_VALUE));
  }


  public void testMapLiteral() {

    // ------ Unquoted keys. ------
    assertTranslation("[:]", new JsExpr("{}", Integer.MAX_VALUE));
    assertTranslation(
        "['aaa': 123, 'bbb': 'blah']",
        new JsExpr("{aaa: 123, bbb: 'blah'}", Integer.MAX_VALUE));
    assertTranslation(
        "['aaa': $foo, 'bbb': 'blah']",
        new JsExpr("{aaa: opt_data.foo, bbb: 'blah'}", Integer.MAX_VALUE));

    // ------ Quoted keys. ------
    assertTranslation("quoteKeysIfJs([:])", new JsExpr("{}", Integer.MAX_VALUE));
    assertTranslation(
        "quoteKeysIfJs( ['aaa': $foo, 'bbb': 'blah'] )",
        new JsExpr("{'aaa': opt_data.foo, 'bbb': 'blah'}", Integer.MAX_VALUE));
    assertTranslation(
        "quoteKeysIfJs(['aaa': 123, $boo: $foo])",
        new JsExpr(
            "(function() { var map_s = {'aaa': 123};" +
                " map_s[soy.$$checkMapKey(opt_data.boo)] = opt_data.foo;" +
                " return map_s; })()",
            Integer.MAX_VALUE));
    assertTranslation(
        "quoteKeysIfJs([$boo: $foo, $goo[0]: 123])",
        new JsExpr(
            "(function() { var map_s = {};" +
                " map_s[soy.$$checkMapKey(opt_data.boo)] = opt_data.foo;" +
                " map_s[soy.$$checkMapKey(gooData8[0])] = 123;" +
                " return map_s; })()",
            Integer.MAX_VALUE));

    // ------ Errors. ------

    // Non-string key is error.
    assertSoyErrorKinds(
        "[0: 123, 1: 'oops']",
        "Keys in map literals cannot be constants (found constant '0').",
        "Keys in map literals cannot be constants (found constant '1').");

    SoyJsSrcOptions jsSrcOptionsWithoutCompiler = new SoyJsSrcOptions();
    SoyJsSrcOptions jsSrcOptionsWithCompiler = new SoyJsSrcOptions();
    jsSrcOptionsWithCompiler.setShouldGenerateJsdoc(true);

    // Non-identifier key without quoteKeysIfJs() is error only when using Closure Compiler.
    assertTranslation(
        "['0': 123, '1': $foo]",
        new JsExpr("{'0': 123, '1': opt_data.foo}", Integer.MAX_VALUE),
        jsSrcOptionsWithoutCompiler);
    assertSoyErrorKinds(
        "['0': 123, '1': '123']",
        jsSrcOptionsWithCompiler,
        "Map literal with non-identifier key '0' must be wrapped in quoteKeysIfJs().",
        "Map literal with non-identifier key '1' must be wrapped in quoteKeysIfJs().");

    // Expression key without quoteKeysIfJs() is error only when using Closure Compiler.
    assertTranslation(
        "['aaa': 123, $boo: $foo]",
        new JsExpr(
            "(function() { var map_s = {aaa: 123};" +
                " map_s[soy.$$checkMapKey(opt_data.boo)] = opt_data.foo;" +
                " return map_s; })()",
            Integer.MAX_VALUE),
        jsSrcOptionsWithoutCompiler);
    assertSoyErrorKinds(
        "['aaa': 123, $boo: $foo]",
        jsSrcOptionsWithCompiler,
        "Expression key '$boo' in map literal must be wrapped in quoteKeysIfJs().");
  }


  public void testDataRef() {

    assertTranslation(
        "$boo", new JsExpr("opt_data.boo", Integer.MAX_VALUE));
    assertTranslation(
        "$boo.goo", new JsExpr("opt_data.boo.goo", Integer.MAX_VALUE));
    assertTranslation(
        "$goo", new JsExpr("gooData8", Integer.MAX_VALUE));
    assertTranslation(
        "$goo.boo", new JsExpr("gooData8.boo", Integer.MAX_VALUE));
    assertTranslation(
        "$boo[0][1].foo[2]", new JsExpr("opt_data.boo[0][1].foo[2]", Integer.MAX_VALUE));
    assertTranslation(
        "$boo[0][1]", new JsExpr("opt_data.boo[0][1]", Integer.MAX_VALUE));
    assertTranslation(
        "$boo[$foo][$goo+1]",
        new JsExpr("opt_data.boo[opt_data.foo][gooData8 + 1]", Integer.MAX_VALUE));
    assertTranslation(
        "$class", new JsExpr("opt_data['class']", Integer.MAX_VALUE));
    assertTranslation(
        "$boo.yield", new JsExpr("opt_data.boo['yield']", Integer.MAX_VALUE));

    assertTranslation(
        "$boo?.goo",
        new JsExpr(
            "(opt_data.boo == null) ? null : opt_data.boo.goo",
            Operator.CONDITIONAL.getPrecedence()));
    assertTranslation(
        "$goo?.boo",
        new JsExpr(
            "(gooData8 == null) ? null : gooData8.boo",
            Operator.CONDITIONAL.getPrecedence()));
    assertTranslation(
        "$boo?[0]?[1]",
        new JsExpr(
            "(opt_data.boo == null) ? null : (opt_data.boo[0] == null) ? null : opt_data.boo[0][1]",
            Operator.CONDITIONAL.getPrecedence()));
  }


  public void testGlobal() {

    assertTranslation("MOO_2",
        new JsExpr("MOO_2", Integer.MAX_VALUE));
    assertTranslation("aaa.BBB",
        new JsExpr("aaa.BBB", Integer.MAX_VALUE));
  }

  public void testOperators() {

    assertTranslation("not $boo or true and $goo",
        new JsExpr("! opt_data.boo || true && gooData8", Operator.OR.getPrecedence()));
    assertTranslation("( (8-4) + (2-1) )",
        new JsExpr("8 - 4 + (2 - 1)", Operator.PLUS.getPrecedence()));

    assertTranslation("$foo ?: 0",
        new JsExpr("($$temp = opt_data.foo) == null ? 0 : $$temp",
            Operator.NULL_COALESCING.getPrecedence()));
  }

  public void testNullCoalescingNested() {
    assertTranslation("$boo ?: -1", nullCoalesing("($$temp = opt_data.boo) == null ? -1 : $$temp"));
    assertTranslation("$a ?: $b ?: $c",
        nullCoalesing(
            "($$temp = opt_data.a) == null "
                + "? ($$temp = opt_data.b) == null ? opt_data.c : $$temp : $$temp"));
    assertTranslation("$a ?: $b ? $c : $d",
        nullCoalesing(
            "($$temp = opt_data.a) == null ? opt_data.b ? opt_data.c : opt_data.d : $$temp"));
    assertTranslation("$a ? $b ?: $c : $d",
        nullCoalesing(
            "opt_data.a ? (($$temp = opt_data.b) == null ? opt_data.c : $$temp) : opt_data.d"));
    assertTranslation("$a ? $b : $c ?: $d",
        nullCoalesing(
            "opt_data.a ? opt_data.b : ($$temp = opt_data.c) == null ? opt_data.d : $$temp"));
    assertTranslation("($a ?: $b) ?: $c",
        nullCoalesing(
            "($$temp = ($$temp = opt_data.a) == null ? opt_data.b : $$temp) == null "
                + "? opt_data.c : $$temp"));
    assertTranslation("$a ?: ($b ?: $c)",
        nullCoalesing(
            "($$temp = opt_data.a) == null "
                + "? ($$temp = opt_data.b) == null ? opt_data.c : $$temp : $$temp"));
    assertTranslation("($a ?: $b) ? $c : $d", nullCoalesing(
        "(($$temp = opt_data.a) == null ? opt_data.b : $$temp) ? opt_data.c : opt_data.d"));
  }


  private JsExpr nullCoalesing(String text) {
    return new JsExpr(text, Operator.NULL_COALESCING.getPrecedence());
  }

  public void testGeneralFunctions() {
    assertTranslation(
        "isFirst($goo) ? 1 : 0",
        new JsExpr("gooIndex8 == 0 ? 1 : 0", Operator.CONDITIONAL.getPrecedence()));
    assertTranslation(
        "not isLast($goo) ? 1 : 0",
        new JsExpr(
            "! (gooIndex8 == gooListLen8 - 1) ? 1 : 0", Operator.CONDITIONAL.getPrecedence()));
    assertTranslation(
        "index($goo) + 1", new JsExpr("gooIndex8 + 1", Operator.PLUS.getPrecedence()));
  }


  /**
   * Checks that the given Soy expression translates to the given JsExpr.
   * @param soyExpr The Soy expression to test.
   * @param expectedJsExpr The expected translated JsExpr.
   */
  private void assertTranslation(String soyExpr, JsExpr expectedJsExpr) {
    assertTranslation(soyExpr, expectedJsExpr, new SoyJsSrcOptions());
  }


  /**
   * Checks that the given Soy expression translates to the given JsExpr.
   * @param soyExpr The Soy expression to test.
   * @param expectedJsExpr The expected translated JsExpr.
   * @param jsSrcOptions The JsSrc compiler options.
   */
  private void assertTranslation(
      String soyExpr, JsExpr expectedJsExpr, SoyJsSrcOptions jsSrcOptions) {
    String templateBody = SharedTestUtils.untypedTemplateBodyForExpression(soyExpr);
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                    + "/***/\n"
                    + "{template .aaa}\n"
                    + templateBody
                    + "{/template}\n")
            .allowUnboundGlobals(true)
            .parse()
            .fileSet();
    List<PrintNode> printNodes = SoytreeUtils.getAllNodesOfType(soyTree, PrintNode.class);
    assertThat(printNodes).hasSize(1);
    ExprNode exprNode = printNodes.get(0).getExprUnion().getExpr();
    JsExpr actualJsExpr = new TranslateToJsExprVisitor(
        jsSrcOptions,
        LOCAL_VAR_TRANSLATIONS,
        ExplodingErrorReporter.get())
        .exec(exprNode);
    assertThat(actualJsExpr.getText()).isEqualTo(expectedJsExpr.getText());
    assertThat(actualJsExpr.getPrecedence()).isEqualTo(expectedJsExpr.getPrecedence());
  }


  /**
   * Checks that the given Soy expression causes a SoySyntaxException during translation, optionally
   * checking the exception's error message.
   * @param soyExpr The Soy expression to test.
   * @param expectedErrorMsgSubstrings An expected substring of the expected exception's message.
   */
  private void assertSoyErrorKinds(String soyExpr, String... expectedErrorMsgSubstrings) {
    assertSoyErrorKinds(soyExpr, new SoyJsSrcOptions(), expectedErrorMsgSubstrings);
  }


  /**
   * Checks that the given Soy expression causes a SoySyntaxException during translation, optionally
   * checking the exception's error message.
   * @param soyExpr The Soy expression to test.
   * @param expectedErrorMsgSubstrings An expected substring of the expected exception's message.
   * @param jsSrcOptions The JsSrc compiler options.
   */
  private void assertSoyErrorKinds(
      String soyExpr, SoyJsSrcOptions jsSrcOptions, String... expectedErrorMsgSubstrings) {
    ExprNode exprNode = new ExpressionParser(
        soyExpr, SourceLocation.UNKNOWN, SoyParsingContext.exploding())
        .parseExpression();
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    new TranslateToJsExprVisitor(
        jsSrcOptions,
        LOCAL_VAR_TRANSLATIONS,
        errorReporter)
        .exec(exprNode);
    ImmutableList<String> errorMessages = errorReporter.getErrorMessages();
    assertThat(errorMessages).hasSize(expectedErrorMsgSubstrings.length);
    for (int i = 0; i < expectedErrorMsgSubstrings.length; ++i) {
      assertThat(errorMessages.get(i)).contains(expectedErrorMsgSubstrings[i]);
    }
  }
}
