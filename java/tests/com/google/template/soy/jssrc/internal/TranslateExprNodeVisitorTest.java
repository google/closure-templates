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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.logging.AnnotatedLoggableElement;
import com.google.template.soy.logging.LoggableElement;
import com.google.template.soy.logging.testing.LoggingConfigs;
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
          .put("$goo", id("gooData8"))
          .put("$goo__isFirst", id("gooIndex8").doubleEquals(number(0)))
          .put("$goo__isLast", id("gooIndex8").doubleEquals(id("gooListLen8").minus(number(1))))
          .put("$goo__index", id("gooIndex8"))
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
    assertThatSoyExpr("['blah', 123, $foo]")
        .generatesCode("soy.$$makeArray('blah', 123, opt_data.foo);");
    assertThatSoyExpr("[]").generatesCode("[];");
  }

  @Test
  public void testRecordLiteral() {
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
    assertThatSoyExpr("aax.BBB").generatesCode("aax.BBB;");
  }

  @Test
  public void testOperators() {
    assertThatSoyExpr("not $boo or true and $goo")
        .withInitialLocalVarTranslations(LOCAL_VAR_TRANSLATIONS)
        .generatesCode("!opt_data.boo || true && soy.$$coerceToBoolean(gooData8);")
        .withPrecedence(OR);

    assertThatSoyExpr("( (8-4) + (2-1) )").generatesCode("8 - 4 + (2 - 1);").withPrecedence(PLUS);

    assertThatSoyExpr("$foo ?: 0")
        .generatesCode("const $tmp = opt_data.foo;", "$tmp != null ? $tmp : 0;");
  }

  @Test
  public void testNullCoalescingNested() {
    assertThatSoyExpr("$boo ?: -1")
        .generatesCode("const $tmp = opt_data.boo;", "$tmp != null ? $tmp : -1;");

    assertThatSoyExpr("$a ?: $b ?: $c")
        .generatesCode(
            "let $tmp$$2;",
            "const $tmp$$1 = opt_data.a;",
            "if ($tmp$$1 != null) {",
            "  $tmp$$2 = $tmp$$1;",
            "} else {",
            "  const $tmp = opt_data.b;",
            "  $tmp$$2 = $tmp != null ? $tmp : opt_data.c;",
            "}");

    assertThatSoyExpr("$a ?: $b ? $c : $d")
        .generatesCode(
            "const $tmp = opt_data.a;",
            "$tmp != null ? $tmp : opt_data.b ? opt_data.c : opt_data.d;");

    assertThatSoyExpr("$a ? $b ?: $c : $d")
        .generatesCode(
            "let $tmp$$1;",
            "if (opt_data.a) {",
            "  const $tmp = opt_data.b;",
            "  $tmp$$1 = $tmp != null ? $tmp : opt_data.c;",
            "} else {",
            "  $tmp$$1 = opt_data.d;",
            "}");

    assertThatSoyExpr("$a ? $b : $c ?: $d")
        .generatesCode(
            "let $tmp$$1;",
            "if (opt_data.a) {",
            "  $tmp$$1 = opt_data.b;",
            "} else {",
            "  const $tmp = opt_data.c;",
            "  $tmp$$1 = $tmp != null ? $tmp : opt_data.d;",
            "}");

    assertThatSoyExpr("($a ?: $b) ?: $c")
        .generatesCode(
            "const $tmp = opt_data.a;",
            "const $tmp$$1 = $tmp != null ? $tmp : opt_data.b;",
            "$tmp$$1 != null ? $tmp$$1 : opt_data.c;");

    assertThatSoyExpr("$a ?: ($b ?: $c)")
        .generatesCode(
            "let $tmp$$2;",
            "const $tmp$$1 = opt_data.a;",
            "if ($tmp$$1 != null) {",
            "  $tmp$$2 = $tmp$$1;",
            "} else {",
            "  const $tmp = opt_data.b;",
            "  $tmp$$2 = $tmp != null ? $tmp : opt_data.c;",
            "}");

    assertThatSoyExpr("($a ?: $b) ? $c : $d")
        .generatesCode(
            "const $tmp = opt_data.a;",
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
  public void testVeLiteral() {
    assertThatSoyExpr("ve(MyVe)")
        .withLoggingConfig(
            LoggingConfigs.createLoggingConfig(
                LoggableElement.newBuilder().setId(8675309).setName("MyVe").build()))
        .generatesCode(
            "goog.DEBUG ? new (goog.module.get('soy.velog').$$VisualElement)(8675309, undefined,"
                + " 'MyVe') : new (goog.module.get('soy.velog').$$VisualElement)(8675309,"
                + " undefined);");
  }

  @Test
  public void testVeLiteralWithMetadata() {
    assertThatSoyExpr("ve(MyMetadataVe)")
        .withLoggingConfig(
            LoggingConfigs.createLoggingConfig(
                AnnotatedLoggableElement.newBuilder()
                    .setElement(LoggableElement.newBuilder().setId(2383).setName("MyMetadataVe"))
                    .setHasMetadata(true)
                    .setClassName("MyTestLoggingConfig")
                    .setJsPackage("root.this.is.a.package.logging_config")
                    .build()))
        .generatesCode(
            "goog.DEBUG ? new (goog.module.get('soy.velog').$$VisualElement)(2383,"
                + " goog.module.get('root.this.is.a.package.logging_config').MyTestLoggingConfig.v2383(),"
                + " 'MyMetadataVe') : new (goog.module.get('soy.velog').$$VisualElement)(2383,"
                + " goog.module.get('root.this.is.a.package.logging_config').MyTestLoggingConfig"
                + ".v2383());");
  }

  @Test
  public void tesUnknownJsGlobal() {
    assertThatSoyExpr("unknownJsGlobal('foo.Bar')")
        .generatesCode("/** @suppress {missingRequire} */", "const $tmp = foo.Bar;", "$tmp;");
  }
}
