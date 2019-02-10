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
import static com.google.template.soy.jssrc.dsl.Expression.id;
import static com.google.template.soy.jssrc.dsl.Expression.number;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for GenJsExprsVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class GenJsExprsVisitorTest {

  private static final Joiner JOINER = Joiner.on('\n');

  // Let 'goo' simulate a local variable from a 'foreach' loop.
  private static final ImmutableMap<String, Expression> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.<String, Expression>builder()
          .put("goo", id("gooData8"))
          .put("goo__isFirst", id("gooIndex8").doubleEquals(number(0)))
          .put("goo__isLast", id("gooIndex8").doubleEquals(id("gooListLen8").minus(number(1))))
          .put("goo__index", id("gooIndex8"))
          .build();

  @Test
  public void testRawText() {

    assertGeneratedChunks("I'm feeling lucky!", "'I\\'m feeling lucky!';");
    assertGeneratedChunks("{'<script></script>'}", "'<script><\\/script>';");
    // Ensure Unicode gets escaped, since there's no guarantee about the output encoding of the JS.
    assertGeneratedChunks("More \u00BB", "'More \\u00BB';");
  }

  @Test
  public void testMsgHtmlTag() {

    assertGeneratedJsExprs(JOINER.join(
        "{@param url : ?}",
        "{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}"),
        ImmutableList.of(
            new JsExpr("'<a href=\"'", Integer.MAX_VALUE),
            new JsExpr("opt_data.url", Integer.MAX_VALUE),
            new JsExpr("'\">'", Integer.MAX_VALUE)),
        0,
        0,
        0,
        0);

    assertGeneratedJsExprs(
        "{@param url : ?}\n" + "{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}",
        ImmutableList.of(new JsExpr("'</a>'", Integer.MAX_VALUE)),
        0,
        0,
        2);
  }

  @Test
  public void testPrint() {

    assertGeneratedChunks(JOINER.join("{@param boo : ?}", "{$boo.foo}"), "opt_data.boo.foo;");

    assertGeneratedChunks(JOINER.join("{@param goo : ?}", "{$goo.moo}"), "gooData8.moo;");

    assertGeneratedChunks(
        JOINER.join("{@param goo : ?}", "{length($goo)+1}"), "gooData8.length + 1;");
  }

  @Test
  public void testPrint_nonExpr() {

    String soyNodeCode = JOINER.join("{@param boo : string}", "{map('a': 'b', $boo: 'c')[$boo]}");
    String expectedGenCode =
        JOINER.join(
            "var $tmp = new Map();",
            "$tmp.set(soy.$$checkNotNull(soy.map.$$maybeCoerceKeyToString('a')), 'b');",
            "$tmp.set(soy.$$checkNotNull(soy.map.$$maybeCoerceKeyToString(opt_data.boo)), 'c');",
            "$tmp.get(soy.map.$$maybeCoerceKeyToString(opt_data.boo));");
    assertGeneratedChunks(soyNodeCode, expectedGenCode);
  }

  @Test
  public void testXid() {

    assertGeneratedChunks("{xid('selected-option')}", "xid('selected-option');");
    assertGeneratedChunks("{xid('selected.option')}", "xid('selected.option');");
  }

  @Test
  public void testCss() {
    assertGeneratedChunks("{css('selected-option')}", "goog.getCssName('selected-option');");

    assertGeneratedChunks(
        JOINER.join("{@param foo : ?}", "{css($foo, 'bar')}"),
        "goog.getCssName(opt_data.foo, 'bar');");
  }

  @Test
  public void testIf() {

    String soyNodeCode = JOINER.join(
        "{@param boo : ?}",
        "{@param goo : ?}",
        "{if $boo}",
        "  Blah",
        "{elseif not isNonnull($goo)}",
        "  Bleh",
        "{else}",
        "  Bluh",
        "{/if}");
    String expectedJsExprText =
        JOINER.join(
            "var $tmp;",
            "if (opt_data.boo) {",
            "  $tmp = 'Blah';",
            "} else if (!(gooData8 != null)) {",
            "  $tmp = 'Bleh';",
            "} else {",
            "  $tmp = 'Bluh';",
            "}");
    assertGeneratedChunks(soyNodeCode, expectedJsExprText);
  }

  @Test
  public void testIfNoElse() {

    String soyNodeCode =
        JOINER.join(
            "{@param boo : ?}",
            "{@param goo : ?}",
            "{if $boo}",
            "  Blah",
            "{elseif not isNonnull($goo)}",
            "  Bleh",
            "{/if}");
    String expectedJsExprText =
        JOINER.join(
            "var $tmp;",
            "if (opt_data.boo) {",
            "  $tmp = 'Blah';",
            "} else if (!(gooData8 != null)) {",
            "  $tmp = 'Bleh';",
            "} else {",
            "  $tmp = '';",
            "}");
    assertGeneratedChunks(soyNodeCode, expectedJsExprText);
  }

  @Test
  public void testIfTernary() {

    String soyNodeCode = JOINER.join(
        "{@param boo : ?}",
        "{if $boo}",
        "  Blah",
        "{else}",
        "  Bleh",
        "{/if}");
    String expectedJsExprText = "opt_data.boo ? 'Blah' : 'Bleh';";
    assertGeneratedChunks(soyNodeCode, expectedJsExprText);
  }

  @Test
  public void testCall() {
    assertGeneratedChunks(
        "{call some.func data=\"all\" /}", "some.func(/** @type {?} */ (opt_data), opt_ijData);");

    String soyNodeCode = JOINER.join(
        "{@param boo : ?}",
        "{call some.func data=\"$boo.foo\" /}");
    assertGeneratedChunks(
        soyNodeCode, "some.func(/** @type {?} */ (opt_data.boo.foo), opt_ijData);");

    soyNodeCode = JOINER.join(
        "{@param moo : ?}",
        "{call some.func}",
        "  {param goo: $moo /}",
        "{/call}");
    assertGeneratedChunks(
        soyNodeCode, "some.func(/** @type {?} */ ({goo: opt_data.moo}), opt_ijData);");

    soyNodeCode =
        JOINER.join(
            "{@param boo : ?}",
            "{call some.func data=\"$boo\"}",
            "  {param goo kind=\"text\"}Blah{/param}",
            "{/call}");
    assertGeneratedChunks(
        soyNodeCode,
        "some.func(soy.$$assignDefaults({goo: soydata.$$markUnsanitizedTextForInternalBlocks("
            + "'Blah')}, opt_data.boo), opt_ijData);");
  }

  @Test
  public void testBlocks() {

    String soyNodeCode = JOINER.join(
        "{@param boo : ?}",
        "{if $boo}",
        "  Blah {$boo} bleh.",
        "{/if}");
    String expectedJsExprText = "opt_data.boo ? 'Blah ' " + "+ opt_data.boo + ' bleh.' : '';";
    assertGeneratedChunks(soyNodeCode, expectedJsExprText);

    soyNodeCode =
        JOINER.join(
            "{@param goo : ?}",
            "{call some.func}",
            "  {param goo kind=\"text\"}{lb}{isNonnull($goo)}{rb} is {$goo.moo}{/param}",
            "{/call}");
    expectedJsExprText =
        "some.func(/** @type {?} */ ({goo: soydata.$$markUnsanitizedTextForInternalBlocks("
            + "'{' + (gooData8 != null) + '} is ' + gooData8.moo)}), opt_ijData);";
    assertGeneratedChunks(soyNodeCode, expectedJsExprText);
  }

  private static void assertGeneratedChunks(String soyNodeCode, String... expectedChunks) {
    List<Expression> actualChunks = generateChunks(soyNodeCode, 0);
    assertThat(actualChunks).hasSize(expectedChunks.length);

    for (int i = 0; i < actualChunks.size(); i++) {
      Expression actual = actualChunks.get(i);
      String expected = expectedChunks[i];

      assertThat(actual.getCode()).isEqualTo(expected);
    }
  }

  /** @param indicesToNode Series of indices for walking down to the node we want to test. */
  private static void assertGeneratedJsExprs(
      String soyCode, List<JsExpr> expectedJsExprs, int... indicesToNode) {
    List<Expression> actualChunks = generateChunks(soyCode, indicesToNode);

    List<JsExpr> actualJsExprs = new ArrayList<>();
    for (Expression chunk : actualChunks) {
      actualJsExprs.add(chunk.assertExpr()); // TODO(user): Fix tests to work with CodeChunks
    }

    assertThat(actualJsExprs).hasSize(expectedJsExprs.size());
    for (int i = 0; i < expectedJsExprs.size(); i++) {
      JsExpr expectedJsExpr = expectedJsExprs.get(i);
      JsExpr actualJsExpr = actualJsExprs.get(i);
      assertThat(actualJsExpr.getText()).isEqualTo(expectedJsExpr.getText());
      assertThat(actualJsExpr.getPrecedence()).isEqualTo(expectedJsExpr.getPrecedence());
    }
  }

  private static List<Expression> generateChunks(String soyCode, int... indicesToNode) {
    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();
    SoyNode node = SharedTestUtils.getNode(soyTree, indicesToNode);

    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    GenJsExprsVisitor visitor =
        JsSrcTestUtils.createGenJsExprsVisitorFactory()
            .create(
                TranslationContext.of(
                    SoyToJsVariableMappings.startingWith(LOCAL_VAR_TRANSLATIONS),
                    CodeChunk.Generator.create(nameGenerator),
                    nameGenerator),
                AliasUtils.IDENTITY_ALIASES,
                boom);
    return visitor.exec(node);
  }
}
