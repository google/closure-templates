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
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;

import junit.framework.TestCase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for GenJsExprsVisitor.
 *
 */
public final class GenJsExprsVisitorTest extends TestCase {

  private static final Injector INJECTOR = Guice.createInjector(new SoyModule());

  private static final Deque<Map<String, JsExpr>> LOCAL_VAR_TRANSLATIONS =
      new ArrayDeque<Map<String, JsExpr>>();
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


  private SoyJsSrcOptions jsSrcOptions;


  @Override protected void setUp() {
    jsSrcOptions = new SoyJsSrcOptions();
    JsSrcTestUtils.simulateNewApiCall(INJECTOR, jsSrcOptions);
  }


  public void testRawText() {

    assertGeneratedJsExprs(
        "I'm feeling lucky!",
        ImmutableList.of(new JsExpr("'I\\'m feeling lucky!'", Integer.MAX_VALUE)));
    // Ensure Unicode gets escaped, since there's no guarantee about the output encoding of the JS.
    assertGeneratedJsExprs(
        "More \u00BB",
        ImmutableList.of(new JsExpr("'More \\u00BB'", Integer.MAX_VALUE)));
  }

  public void testMsgHtmlTag() {

    assertGeneratedJsExprs(
        "{@param url : ?}\n" + "{msg desc=\"\"}<a href=\"{$url}\">Click here</a>{/msg}",
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
        0,
        2);
  }


  public void testPrint() {

    assertGeneratedJsExprs(
        "{@param boo : ?}\n" + "{$boo.foo}",
        ImmutableList.of(new JsExpr("opt_data.boo.foo", Integer.MAX_VALUE)));

    assertGeneratedJsExprs(
        "{@param goo : ?}\n" + "{$goo.moo}",
        ImmutableList.of(new JsExpr("gooData8.moo", Integer.MAX_VALUE)));

    assertGeneratedJsExprs(
        "{@param goo : ?}\n" + "{isNonnull($goo)+1}",
        ImmutableList.of(new JsExpr("(gooData8 != null) + 1", Operator.PLUS.getPrecedence())));
  }


  public void testXid() {

    assertGeneratedJsExprs(
        "{xid selected-option}",
        ImmutableList.of(new JsExpr("xid('selected-option')", Integer.MAX_VALUE)));
    assertGeneratedJsExprs(
        "{xid selected.option}",
        ImmutableList.of(new JsExpr("xid('selected.option')", Integer.MAX_VALUE)));
  }

  public void testCss() {

    assertGeneratedJsExprs(
        "{css selected-option}",
        ImmutableList.of(new JsExpr("goog.getCssName('selected-option')", Integer.MAX_VALUE)));

    assertGeneratedJsExprs(
        "{@param foo : ?}\n" + "{css $foo, bar}",
        ImmutableList.of(new JsExpr("goog.getCssName(opt_data.foo, 'bar')", Integer.MAX_VALUE)));
  }

  public void testIf() {

    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{if $boo}\n"
            + "  Blah\n"
            + "{elseif not isNonnull($goo)}\n"
            + "  Bleh\n"
            + "{else}\n"
            + "  Bluh\n"
            + "{/if}\n";
    String expectedJsExprText =
        "(opt_data.boo) ? 'Blah' : (! (gooData8 != null)) ? 'Bleh' : 'Bluh'";
    assertGeneratedJsExprs(
        soyNodeCode,
        ImmutableList.of(new JsExpr(expectedJsExprText, Operator.CONDITIONAL.getPrecedence())));
  }


  public void testCall() {
    assertGeneratedJsExprs(
        "{call some.func data=\"all\" /}",
        ImmutableList.of(new JsExpr("some.func(opt_data, null, opt_ijData)", Integer.MAX_VALUE)));

    assertGeneratedJsExprs(
        "{@param boo : ?}\n" + "{call some.func data=\"$boo.foo\" /}",
        ImmutableList.of(new JsExpr("some.func(opt_data.boo.foo, null, opt_ijData)", Integer.MAX_VALUE)));

    String soyNodeCode =
        "{@param moo : ?}\n" + "{call some.func}" + "  {param goo: $moo /}" + "{/call}";
    assertGeneratedJsExprs(
        soyNodeCode,
        ImmutableList.of(new JsExpr("some.func({goo: opt_data.moo}, null, opt_ijData)", Integer.MAX_VALUE)));

    soyNodeCode =
        "{@param boo : ?}\n"
            + "{call some.func data=\"$boo\"}"
            + "  {param goo}Blah{/param}"
            + "{/call}";
    assertGeneratedJsExprs(
        soyNodeCode,
        ImmutableList.of(
            new JsExpr(
                "some.func(soy.$$assignDefaults({goo: 'Blah'}, opt_data.boo), null, opt_ijData)",
                Integer.MAX_VALUE)));
  }


  public void testBlocks() {

    String soyNodeCode = "{@param boo : ?}\n" + "{if $boo}\n" + "  Blah {$boo} bleh.\n" + "{/if}\n";
    String expectedJsExprText = "(opt_data.boo) ? 'Blah ' + opt_data.boo + ' bleh.' : ''";
    assertGeneratedJsExprs(
        soyNodeCode,
        ImmutableList.of(new JsExpr(expectedJsExprText, Operator.CONDITIONAL.getPrecedence())));

    soyNodeCode =
        "{@param goo : ?}\n"
            + "{call some.func}"
            + "  {param goo}{lb}{isNonnull($goo)}{rb} is {$goo.moo}{/param}"
            + "{/call}";
    expectedJsExprText =
        "some.func({goo: '{' + (gooData8 != null) + '} is ' + gooData8.moo}, null, opt_ijData)";
    assertGeneratedJsExprs(
        soyNodeCode,
        ImmutableList.of(new JsExpr(expectedJsExprText, Integer.MAX_VALUE)));
  }


  private static void assertGeneratedJsExprs(String soyNodeCode, List<JsExpr> expectedJsExprs) {
    assertGeneratedJsExprs(soyNodeCode, expectedJsExprs, 0);
  }


  /**
   * @param indicesToNode Series of indices for walking down to the node we want to test.
   */
  private static void assertGeneratedJsExprs(
      String soyCode, List<JsExpr> expectedJsExprs, int... indicesToNode) {
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();
    // Required by testPrintGoogMsg.
    new ExtractMsgVariablesVisitor().exec(soyTree);
    SoyNode node = SharedTestUtils.getNode(soyTree, indicesToNode);

    GenJsExprsVisitor gjev =
        INJECTOR
            .getInstance(GenJsExprsVisitorFactory.class)
            .create(LOCAL_VAR_TRANSLATIONS, AliasUtils.IDENTITY_ALIASES, boom);
    List<JsExpr> actualJsExprs = gjev.exec(node);

    assertThat(actualJsExprs).hasSize(expectedJsExprs.size());
    for (int i = 0; i < expectedJsExprs.size(); i++) {
      JsExpr expectedJsExpr = expectedJsExprs.get(i);
      JsExpr actualJsExpr = actualJsExprs.get(i);
      assertThat(actualJsExpr.getText()).isEqualTo(expectedJsExpr.getText());
      assertThat(actualJsExpr.getPrecedence()).isEqualTo(expectedJsExpr.getPrecedence());
    }
  }

}
