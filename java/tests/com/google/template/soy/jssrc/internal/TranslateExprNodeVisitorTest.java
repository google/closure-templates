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
import static com.google.template.soy.exprtree.Operator.OR;
import static com.google.template.soy.exprtree.Operator.PLUS;
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.number;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.assertThatSoyExpr;
import static com.google.template.soy.jssrc.internal.JsSrcSubject.assertThatSoyFile;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.LoggingConfig;
import com.google.template.soy.logging.ValidatedLoggingConfig;
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
  private static final ImmutableMap<String, Expression> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.<String, Expression>builder()
          .put("goo", id("gooData8"))
          .put("goo__isFirst", id("gooIndex8").doubleEquals(number(0)))
          .put("goo__isLast", id("gooIndex8").doubleEquals(id("gooListLen8").minus(number(1))))
          .put("goo__index", id("gooIndex8"))
          .build();

  @Test
  public void testStringLiteral() {
    assertThatSoyExpr("'waldo'").generatesCode("'waldo';");
    // Ensure Unicode gets escaped, since there's no guarantee about the output encoding of the JS.
    assertThatSoyExpr("'\u05E9\u05DC\u05D5\u05DD'")
        .generatesCode("'\\u05E9\\u05DC\\u05D5\\u05DD';");
  }

  @Test
  public void testListLiteral() {
    assertThatSoyExpr("['blah', 123, $foo]").generatesCode("['blah', 123, opt_data.foo];");
    assertThatSoyExpr("[]").generatesCode("[];");
  }

  @Test
  public void testRecordLiteral() {
    assertThatSoyExpr("record()").generatesCode("{};");

    assertThatSoyExpr("record(aaa: 123, bbb: 'blah')").generatesCode("{aaa: 123, bbb: 'blah'};");
    assertThatSoyExpr("record(aaa: $foo, bbb: 'blah')")
        .generatesCode("{aaa: opt_data.foo, bbb: 'blah'};");

    assertThatSoyExpr("record(aaa: record(bbb: 'blah'))").generatesCode("{aaa: {bbb: 'blah'}};");
  }

  @Test
  public void testDataRef() {
    assertThatSoyExpr("$boo").generatesCode("opt_data.boo;");
    assertThatSoyExpr("$boo.goo").generatesCode("opt_data.boo.goo;");
    assertThatSoyExpr("$goo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooData8;");
    assertThatSoyExpr("$goo.boo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooData8.boo;");
    assertThatSoyExpr("$boo[0][1].foo[2]")
        .generatesCode(
            "opt_data.boo[/** @type {?} */ (0)][/** @type {?} */ (1)].foo[/** @type {?} */ (2)];");
    assertThatSoyExpr("$boo[0][1]")
        .generatesCode("opt_data.boo[/** @type {?} */ (0)][/** @type {?} */ (1)];");
    assertThatSoyExpr("$boo[/** @type {?} */ ($foo)][/** @type {?} */ ($goo+1)]")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode(
            "opt_data.boo[/** @type {?} */ (opt_data.foo)][/** @type {?} */ (gooData8 + 1)];");
    assertThatSoyExpr("$class").generatesCode("opt_data.class;");
    assertThatSoyExpr("$boo.yield").generatesCode("opt_data.boo.yield;");
  }

  @Test
  public void testGlobal() {
    assertThatSoyExpr("MOO_2").generatesCode("MOO_2;");
    assertThatSoyExpr("aaa.BBB").generatesCode("aaa.BBB;");
  }

  @Test
  public void testOperators() {
    assertThatSoyExpr("not $boo or true and $goo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("!opt_data.boo || true && soy.$$coerceToBoolean(gooData8);")
        .withPrecedence(OR);

    assertThatSoyExpr("( (8-4) + (2-1) )").generatesCode("8 - 4 + (2 - 1);").withPrecedence(PLUS);

    assertThatSoyExpr("$foo ?: 0")
        .generatesCode("var $tmp = opt_data.foo;", "$tmp != null ? $tmp : 0;");
  }

  @Test
  public void testNullCoalescingNested() {
    assertThatSoyExpr("$boo ?: -1")
        .generatesCode("var $tmp = opt_data.boo;", "$tmp != null ? $tmp : -1;");

    assertThatSoyExpr("$a ?: $b ?: $c")
        .generatesCode(
            "var $tmp$$2;",
            "var $tmp$$1 = opt_data.a;",
            "if ($tmp$$1 != null) {",
            "  $tmp$$2 = $tmp$$1;",
            "} else {",
            "  var $tmp = opt_data.b;",
            "  $tmp$$2 = $tmp != null ? $tmp : opt_data.c;",
            "}");

    assertThatSoyExpr("$a ?: $b ? $c : $d")
        .generatesCode(
            "var $tmp = opt_data.a;",
            "$tmp != null ? $tmp : opt_data.b ? opt_data.c : opt_data.d;");

    assertThatSoyExpr("$a ? $b ?: $c : $d")
        .generatesCode(
            "var $tmp$$1;",
            "if (opt_data.a) {",
            "  var $tmp = opt_data.b;",
            "  $tmp$$1 = $tmp != null ? $tmp : opt_data.c;",
            "} else {",
            "  $tmp$$1 = opt_data.d;",
            "}");

    assertThatSoyExpr("$a ? $b : $c ?: $d")
        .generatesCode(
            "var $tmp$$1;",
            "if (opt_data.a) {",
            "  $tmp$$1 = opt_data.b;",
            "} else {",
            "  var $tmp = opt_data.c;",
            "  $tmp$$1 = $tmp != null ? $tmp : opt_data.d;",
            "}");

    assertThatSoyExpr("($a ?: $b) ?: $c")
        .generatesCode(
            "var $tmp = opt_data.a;",
            "var $tmp$$1 = $tmp != null ? $tmp : opt_data.b;",
            "$tmp$$1 != null ? $tmp$$1 : opt_data.c;");

    assertThatSoyExpr("$a ?: ($b ?: $c)")
        .generatesCode(
            "var $tmp$$2;",
            "var $tmp$$1 = opt_data.a;",
            "if ($tmp$$1 != null) {",
            "  $tmp$$2 = $tmp$$1;",
            "} else {",
            "  var $tmp = opt_data.b;",
            "  $tmp$$2 = $tmp != null ? $tmp : opt_data.c;",
            "}");

    assertThatSoyExpr("($a ?: $b) ? $c : $d")
        .generatesCode(
            "var $tmp = opt_data.a;",
            "($tmp != null ? $tmp : opt_data.b) ? opt_data.c : opt_data.d;");
  }

  @Test
  public void testForeachFunctions() {
    assertThatSoyExpr("isFirst($goo) ? 1 : 0")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooIndex8 == 0 ? 1 : 0;")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("not isLast($goo) ? 1 : 0")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("!(gooIndex8 == gooListLen8 - 1) ? 1 : 0;")
        .withPrecedence(CONDITIONAL);

    assertThatSoyExpr("index($goo) + 1")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("gooIndex8 + 1;")
        .withPrecedence(PLUS);
  }

  @Test
  public void testCheckNotNull() {
    assertThatSoyExpr("checkNotNull($goo) ? 1 : 0")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("/** @type {?} */ (soy.$$checkNotNull(gooData8)) ? 1 : 0;")
        .withPrecedence(CONDITIONAL);
  }

  @Test
  public void testCss() {
    assertThatSoyExpr("css('foo')").generatesCode("goog.getCssName('foo');");
    assertThatSoyExpr("css($base, 'bar')").generatesCode("goog.getCssName(opt_data.base, 'bar');");
    assertThatSoyExpr("css($goo, 'bar')")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("goog.getCssName(gooData8, 'bar');");
  }

  @Test
  public void testXid() {
    assertThatSoyExpr("xid('foo')").generatesCode("xid('foo');");
  }

  @Test
  public void testV1Expression() {
    String soyFile =
        ""
            + "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {@param goo: ?}\n"
            + "  {v1Expression('$goo.length()')}\n"
            + "{/template}";
    String expectedJs =
        "/**\n"
            + " * @param {!ns.foo.Params} opt_data\n"
            + " * @param {(?goog.soy.IjData|?Object<string, *>)=} opt_ijData\n"
            + " * @param {(?goog.soy.IjData|?Object<string, *>)=} opt_ijData_deprecated\n"
            + " * @return {!goog.soy.data.SanitizedHtml}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "ns.foo = function(opt_data, opt_ijData, opt_ijData_deprecated) {\n"
            + "  opt_ijData = /** @type {!goog.soy.IjData} */ (opt_ijData_deprecated ||"
            + " opt_ijData);\n"
            + "  /** @type {?} */\n"
            + "  var goo = opt_data.goo;\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml((goo.length()));\n"
            + "};\n"
            + "/**\n"
            + " * @typedef {{\n"
            + " *  goo: ?,\n"
            + " * }}\n"
            + " */\n"
            + "ns.foo.Params;\n"
            + "if (goog.DEBUG) {\n"
            + "  ns.foo.soyTemplateName = 'ns.foo';\n"
            + "}\n";

    assertThatSoyFile(soyFile).generatesTemplateThat().isEqualTo(expectedJs);
  }

  @Test
  public void testVeLiteral() {
    assertThatSoyExpr("ve(MyVe)")
        .withLoggingConfig(
            ValidatedLoggingConfig.create(
                LoggingConfig.newBuilder()
                    .addElement(LoggableElement.newBuilder().setId(8675309).setName("MyVe"))
                    .build()))
        .generatesCode(
            "goog.DEBUG "
                + "? new soy.velog.$$VisualElement(8675309, 'MyVe') "
                + ": new soy.velog.$$VisualElement(8675309);");
  }
}
