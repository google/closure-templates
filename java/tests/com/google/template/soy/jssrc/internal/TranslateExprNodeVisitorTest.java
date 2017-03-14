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

import static com.google.template.soy.exprtree.Operator.CONDITIONAL;
import static com.google.template.soy.exprtree.Operator.NULL_COALESCING;
import static com.google.template.soy.exprtree.Operator.OR;
import static com.google.template.soy.exprtree.Operator.PLUS;
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.number;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.assertThatSoyExpr;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.assertThatSoyFile;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link TranslateExprNodeVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class TranslateExprNodeVisitorTest {

  // Let 'goo' simulate a local variable from a 'foreach' loop.
  private static final ImmutableMap<String, CodeChunk.WithValue> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.<String, CodeChunk.WithValue>builder()
          .put("goo", id("gooData8"))
          .put("goo__isFirst", id("gooIndex8").doubleEquals(number(0)))
          .put("goo__isLast", id("gooIndex8").doubleEquals(id("gooListLen8").minus(number(1))))
          .put("goo__index", id("gooIndex8"))
          .build();

  @Test
  public void testStringLiteral() {
    assertThatSoyExpr("'waldo'").generatesCode("'waldo'");
    // Ensure Unicode gets escaped, since there's no guarantee about the output encoding of the JS.
    assertThatSoyExpr("'\u05E9\u05DC\u05D5\u05DD'").generatesCode("'\\u05E9\\u05DC\\u05D5\\u05DD'");
  }

  @Test
  public void testListLiteral() {
    assertThatSoyExpr("['blah', 123, $foo]").generatesCode("['blah', 123, opt_data.foo]");
    assertThatSoyExpr("[]").generatesCode("[]");
  }

  @Test
  public void testMapLiteral() {
    // ------ Unquoted keys. ------
    assertThatSoyExpr("[:]").generatesCode("{}");

    assertThatSoyExpr("['aaa': 123, 'bbb': 'blah']").generatesCode("{aaa: 123, bbb: 'blah'}");
    assertThatSoyExpr("['aaa': $foo, 'bbb': 'blah']")
        .generatesCode("{aaa: opt_data.foo, bbb: 'blah'}");

    assertThatSoyExpr("['aaa': ['bbb': 'blah']]").generatesCode("{aaa: {bbb: 'blah'}}");

    // ------ Quoted keys. ------
    assertThatSoyExpr("quoteKeysIfJs([:])").generatesCode("{}");
    assertThatSoyExpr("quoteKeysIfJs( ['aaa': $foo, 'bbb': 'blah'] )")
        .generatesCode("{'aaa': opt_data.foo, 'bbb': 'blah'}");

    assertThatSoyExpr("quoteKeysIfJs(['aaa': 123, $boo: $foo])")
        .generatesCode(
            "var $tmp = {'aaa': 123};", "$tmp[soy.$$checkMapKey(opt_data.boo)] = opt_data.foo;");

    assertThatSoyExpr("quoteKeysIfJs([$boo: $foo, $goo[0]: 123])")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode(
            "var $tmp = {};",
            "$tmp[soy.$$checkMapKey(opt_data.boo)] = opt_data.foo;",
            "$tmp[soy.$$checkMapKey(gooData8[0])] = 123;");

    assertThatSoyExpr("quoteKeysIfJs(['aaa': ['bbb': 'blah']])")
        .generatesCode("{'aaa': {bbb: 'blah'}}");

    // ------ Errors. ------

    // Non-string key is error.
    assertThatSoyExpr("[0: 123, 1: 'oops']")
        .causesErrors(
            "Keys in map literals cannot be constants (found constant '0').",
            "Keys in map literals cannot be constants (found constant '1').");

    SoyJsSrcOptions noCompiler = new SoyJsSrcOptions();
    SoyJsSrcOptions withCompiler = new SoyJsSrcOptions();
    withCompiler.setShouldGenerateJsdoc(true);

    // Non-identifier key without quoteKeysIfJs() is error only when using Closure Compiler.
    assertThatSoyExpr("['0': 123, '1': $foo]")
        .withJsSrcOptions(noCompiler)
        .generatesCode("{'0': 123, '1': opt_data.foo}");

    assertThatSoyExpr("['0': 123, '1': '123']")
        .withJsSrcOptions(withCompiler)
        .causesErrors(
            "Map literal with non-identifier key '0' must be wrapped in quoteKeysIfJs().",
            "Map literal with non-identifier key '1' must be wrapped in quoteKeysIfJs().");

    // Expression key without quoteKeysIfJs() is error only when using Closure Compiler.
    assertThatSoyExpr("['aaa': 123, $boo: $foo]")
        .withJsSrcOptions(noCompiler)
        .generatesCode(
            "var $tmp = {aaa: 123};", "$tmp[soy.$$checkMapKey(opt_data.boo)] = opt_data.foo;");

    assertThatSoyExpr("['aaa': 123, $boo: $foo, $moo: $goo]")
        .withJsSrcOptions(withCompiler)
        .causesErrors(
            "Expression key '$boo' in map literal must be wrapped in quoteKeysIfJs().",
            "Expression key '$moo' in map literal must be wrapped in quoteKeysIfJs().");
  }

  @Test
  public void testDataRef() {
    assertThatSoyExpr("$boo").generatesCode("opt_data.boo");
    assertThatSoyExpr("$boo.goo").generatesCode("opt_data.boo.goo");
    assertThatSoyExpr("$goo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooData8");
    assertThatSoyExpr("$goo.boo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooData8.boo");
    assertThatSoyExpr("$boo[0][1].foo[2]").generatesCode("opt_data.boo[0][1].foo[2]");
    assertThatSoyExpr("$boo[0][1]").generatesCode("opt_data.boo[0][1]");
    assertThatSoyExpr("$boo[$foo][$goo+1]")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("opt_data.boo[opt_data.foo][gooData8 + 1]");
    assertThatSoyExpr("$class").generatesCode("opt_data.class");
    assertThatSoyExpr("$boo.yield").generatesCode("opt_data.boo.yield");

    assertThatSoyExpr("$boo?.goo")
        .generatesCode("opt_data.boo == null ? null : opt_data.boo.goo")
        .withPrecedence(CONDITIONAL);
    assertThatSoyExpr("$goo?.boo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooData8 == null ? null : gooData8.boo")
        .withPrecedence(CONDITIONAL);

    // TODO(user): the gencode currently re-evaluates nested null-safe accesses,
    // such as opt_data.boo and opt_data.boo[0] below. The correct (and simpler) solution
    // is to emit conditional statements, but we can't generate non-expressions outside of
    // test code yet.
    assertThatSoyExpr("$boo?[0]?[1]")
        .generatesCode(
            "opt_data.boo == null"
                + " ? null : opt_data.boo[0] == null ? null : opt_data.boo[0][1]")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a?.b.c")
        .generatesCode("opt_data.a == null ? null : opt_data.a.b.c")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a?.b[1]")
        .generatesCode("opt_data.a == null ? null : opt_data.a.b[1]")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a?[1].b")
        .generatesCode("opt_data.a == null ? null : opt_data.a[1].b")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a?[1][2]")
        .generatesCode("opt_data.a == null ? null : opt_data.a[1][2]")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a.b?.c")
        .generatesCode("opt_data.a.b == null ? null : opt_data.a.b.c")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a.b?[1]")
        .generatesCode("opt_data.a.b == null ? null : opt_data.a.b[1]")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a[1]?.b")
        .generatesCode("opt_data.a[1] == null ? null : opt_data.a[1].b")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a[1]?[2]")
        .generatesCode("opt_data.a[1] == null ? null : opt_data.a[1][2]")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("$a.b?.c.d?.e")
        .generatesCode(
            "opt_data.a.b == null "
                + "? null : opt_data.a.b.c.d == null ? null : opt_data.a.b.c.d.e")
        .withPrecedence(CONDITIONAL);
  }

  @Test
  public void testGlobal() {
    assertThatSoyExpr("MOO_2").generatesCode("MOO_2");
    assertThatSoyExpr("aaa.BBB").generatesCode("aaa.BBB");
  }

  @Test
  public void testOperators() {
    assertThatSoyExpr("not $boo or true and $goo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("!opt_data.boo || true && gooData8")
        .withPrecedence(OR);

    assertThatSoyExpr("( (8-4) + (2-1) )").generatesCode("8 - 4 + (2 - 1)").withPrecedence(PLUS);

    assertThatSoyExpr("$foo ?: 0")
        .generatesCode("($$temp = opt_data.foo) == null ? 0 : $$temp")
        .withPrecedence(NULL_COALESCING);
  }

  @Test
  public void testNullCoalescingNested() {
    assertThatSoyExpr("$boo ?: -1")
        .generatesCode("($$temp = opt_data.boo) == null ? -1 : $$temp")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("$a ?: $b ?: $c")
        .generatesCode(
            "($$temp = opt_data.a) == null "
                + "? (($$temp = opt_data.b) == null ? opt_data.c : $$temp) : $$temp")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("$a ?: $b ? $c : $d")
        .generatesCode(
            "($$temp = opt_data.a) == null ? (opt_data.b ? opt_data.c : opt_data.d) : $$temp")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("$a ? $b ?: $c : $d")
        .generatesCode(
            "opt_data.a ? (($$temp = opt_data.b) == null " + "? opt_data.c : $$temp) : opt_data.d")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("$a ? $b : $c ?: $d")
        .generatesCode(
            "opt_data.a ? opt_data.b : ($$temp = opt_data.c) == null ? opt_data.d : $$temp")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("($a ?: $b) ?: $c")
        .generatesCode(
            "($$temp = ($$temp = opt_data.a) == null ? opt_data.b : $$temp) == null "
                + "? opt_data.c : $$temp")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("$a ?: ($b ?: $c)")
        .generatesCode(
            "($$temp = opt_data.a) == null "
                + "? (($$temp = opt_data.b) == null ? opt_data.c : $$temp) : $$temp")
        .withPrecedence(NULL_COALESCING);

    assertThatSoyExpr("($a ?: $b) ? $c : $d")
        .generatesCode(
            "(($$temp = opt_data.a) == null ? opt_data.b : $$temp) " + "? opt_data.c : opt_data.d")
        .withPrecedence(NULL_COALESCING);
  }

  @Test
  public void testBuiltinFunctions() {
    assertThatSoyExpr("isFirst($goo) ? 1 : 0")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooIndex8 == 0 ? 1 : 0")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("not isLast($goo) ? 1 : 0")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("!(gooIndex8 == gooListLen8 - 1) ? 1 : 0")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("index($goo) + 1")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooIndex8 + 1")
        .withPrecedence(PLUS);

    assertThatSoyExpr("['abc': $goo]")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("{abc: gooData8}");

    assertThatSoyExpr("quoteKeysIfJs(['abc': $goo])")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("{'abc': gooData8}");

    assertThatSoyExpr("checkNotNull($goo) ? 1 : 0")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("soy.$$checkNotNull(gooData8) ? 1 : 0")
        .withPrecedence(CONDITIONAL);
  }

  @Test
  public void testBuiltinFunctions_v1Expression() {
    String soyFile =
        ""
            + "{namespace ns}\n"
            + "{template foo deprecatedV1=\"true\"}\n"
            + "  {v1Expression('$goo.length()')}\n"
            + "{/template}";
    String expectedJs =
        ""
            + "foo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml(opt_data.goo.length());\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  foo.soyTemplateName = 'foo';\n"
            + "}\n";

    assertThatSoyFile(soyFile)
        .withDeclaredSyntaxVersion(SyntaxVersion.V1_0)
        .generatesTemplateThat()
        .isEqualTo(expectedJs);
  }
}
