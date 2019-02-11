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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link GenJsCodeVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class GenJsCodeVisitorTest {
  private static final Joiner JOINER = Joiner.on('\n');

  // Let 'goo' simulate a local variable from a 'foreach' loop.
  private static final ImmutableMap<String, Expression> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.<String, Expression>builder()
          .put("goo", id("gooData8"))
          .put("goo__isFirst", id("gooIndex8").doubleEquals(number(0)))
          .put("goo__isLast", id("gooIndex8").doubleEquals(id("gooListLen8").minus(number(1))))
          .put("goo__index", id("gooIndex8"))
          .build();

  private static final TemplateAliases TEMPLATE_ALIASES = AliasUtils.IDENTITY_ALIASES;

  private static final SoyFunction NOOP_REQUIRE_SOY_FUNCTION =
      new SoyLibraryAssistedJsSrcFunction() {
        @Override
        public String getName() {
          return "noopRequire";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(0);
        }

        @Override
        public ImmutableSet<String> getRequiredJsLibNames() {
          return ImmutableSet.of("for.function", "also.for.function");
        }

        @Override
        public JsExpr computeForJsSrc(List<JsExpr> args) {
          return new JsExpr("", Integer.MAX_VALUE);
        }
      };

  private SoyJsSrcOptions jsSrcOptions;
  private GenJsCodeVisitor genJsCodeVisitor;

  @Before
  public void setUp() {
    jsSrcOptions = new SoyJsSrcOptions();
    genJsCodeVisitor =
        JsSrcMain.createVisitor(
            jsSrcOptions, new SoyTypeRegistry(), BidiGlobalDir.LTR, ErrorReporter.exploding());
    genJsCodeVisitor.templateAliases = TEMPLATE_ALIASES;
  }

  @Test
  public void testSoyFile() {

    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "  {call boo.woo.hoo data=\"all\" /}\n" // not defined in this file
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    // ------ Using Closure, provide both Soy namespaces and JS functions ------
    String expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.require('boo.woo');\n"
            + "goog.requireType('soy');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "\n";

    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testOnlyOneRequireStatementPerNamespace() {

    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {call boo.woo.aaa data=\"all\" /}\n"
            + "  {call boo.woo.aaa.bbb data=\"all\" /}\n"
            + "  {call boo.woo.bbb data=\"all\" /}\n"
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    // ------ Using Closure, provide/require Soy namespaces ------
    String expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.require('boo.woo');\n"
            + "goog.require('boo.woo.aaa');\n"
            + "goog.requireType('soy');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testOnlyOneRequireStatementPerPluginNamespace() {

    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {call for.function.aaa data=\"all\" /}\n"
            + "  {noopRequire()}\n"
            + "{/template}\n";

    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .addSoyFunction(NOOP_REQUIRE_SOY_FUNCTION)
            .parse();

    // ------ Using Closure, provide/require Soy namespaces and required JS for function ------
    String expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.require('also.for.function');\n"
            + "goog.require('for.function');\n"
            + "goog.requireType('soy');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testSoyFileWithRequirecssOnNamespace() {

    String testFileContent =
        ""
            + "{namespace boo.foo\n"
            + "    requirecss=\"\n"
            + "        ddd.eee.fff.ggg,\n"
            + "        aaa.bbb.ccc\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  blah\n"
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    String expectedJsFileContentStart =
        ""
            + "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @requirecss {aaa.bbb.ccc}\n"
            + " * @requirecss {ddd.eee.fff.ggg}\n"
            + " * @public\n"
            + " */\n"
            + "\n";

    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testSoyFileInDelegatePackage() {
    String testFileContent =
        "{delpackage MySecretFeature}\n"
            + "{namespace boo.foo}\n"
            + "\n"
            + "/** Test delegate template. */\n"
            + "{deltemplate myDelegates.goo}\n"
            + "  {delcall myDelegates.soo /}\n"
            + "{/deltemplate}\n";

    ParseResult parse = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    String expectedJsFileContent =
        ""
            + "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @modName {MySecretFeature}\n"
            + " * @hassoydeltemplate {myDelegates.goo}\n"
            + " * @hassoydelcall {myDelegates.soo}\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.require('soy');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "\n"
            + "\n"
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData_deprecated\n"
            + " * @return {!goog.soy.data.SanitizedHtml}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "boo.foo.__deltemplate_MySecretFeature_myDelegates_goo_ = function("
            + "opt_data, opt_ijData, opt_ijData_deprecated) {\n"
            + "  opt_ijData = /** @type {!soy.IjData} */ (opt_ijData_deprecated || opt_ijData);\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml(soy.$$getDelegateFn("
            + "soy.$$getDelTemplateId('myDelegates.soo'), '', false)(null, opt_ijData));\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.__deltemplate_MySecretFeature_myDelegates_goo_.soyTemplateName = "
            + "'boo.foo.__deltemplate_MySecretFeature_myDelegates_goo_';\n"
            + "}\n"
            + "soy.$$registerDelegateFn(soy.$$getDelTemplateId('myDelegates.goo'), '', 1,"
            + " boo.foo.__deltemplate_MySecretFeature_myDelegates_goo_);\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(parse.fileSet(), parse.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContent);
  }

  @Test
  public void testDelegateVariantProvideRequiresJsDocAnnotations() {
    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "/** Test delegate template. */\n"
            + "{deltemplate myDelegates.goo variant=\"'googoo'\"}\n"
            + "  {delcall myDelegates.moo variant=\"'moomoo'\" /}\n"
            + "{/deltemplate}\n";

    ParseResult parse = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();
    String expectedJsFileContent =
        ""
            + "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @hassoydeltemplate {myDelegates.goo}\n"
            + " * @hassoydelcall {myDelegates.moo}\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.require('soy');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "\n"
            + "\n"
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData_deprecated\n"
            + " * @return {!goog.soy.data.SanitizedHtml}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "boo.foo.__deltemplate__myDelegates_goo_googoo = function("
            + "opt_data, opt_ijData, opt_ijData_deprecated) {\n"
            + "  opt_ijData = /** @type {!soy.IjData} */ (opt_ijData_deprecated || opt_ijData);\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml(soy.$$getDelegateFn("
            + "soy.$$getDelTemplateId('myDelegates.moo'), 'moomoo', false)(null, opt_ijData));\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.__deltemplate__myDelegates_goo_googoo.soyTemplateName = "
            + "'boo.foo.__deltemplate__myDelegates_goo_googoo';\n"
            + "}\n"
            + "soy.$$registerDelegateFn(soy.$$getDelTemplateId('myDelegates.goo'), 'googoo', 0,"
            + " boo.foo.__deltemplate__myDelegates_goo_googoo);\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(parse.fileSet(), parse.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContent);
  }

  @Test
  public void testRawText() {
    assertGeneratedJsCode("I'm feeling lucky!\n", "output += 'I\\'m feeling lucky!';\n");

    assertGeneratedJsCode("{lb}^_^{rb}{sp}{\\n}\n", "output += '{^_^} \\n';\n");
  }

  @Test
  public void testSoyV1GlobalPlaceholderCompatibility() {
    // Test that placeholders for global variables have the same form
    // as they appeared in Soy V1 so that teams with internationalized
    // strings don't have to re-translate strings with new placeholders.

    // global, all caps, underbars between words.  Placeholder should
    // be lower-case camel case version
    String soyCode =
        "{msg desc=\"\"}\n" + "Unable to reach {PRODUCT_NAME_HTML}. Eeeek!\n" + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'Unable to reach {$productNameHtml}. Eeeek!', "
            + "{'productNameHtml': PRODUCT_NAME_HTML});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, all caps, leading or trailing underbars.  Placeholder should
    // be lower-case camel case version
    soyCode =
        "{msg desc=\"\"}\n"
            + "{window.field}{window._AField}{_window_.forest}{window.size.x}"
            + "{window.size._xx_xx_}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$field}{$aField}{$forest}{$x}{$xxXx}', "
            + "{'field': window.field, "
            + "'aField': window._AField, "
            + "'forest': _window_.forest, "
            + "'x': window.size.x, "
            + "'xxXx': window.size._xx_xx_});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, property name.  Placeholder should be lower case
    // camel case version of last component.
    soyCode = "{msg desc=\"\"}\n" + "{window.FOO.BAR} {window.ORIGINAL_SERVER_NAME}\n" + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$bar} {$originalServerName}', "
            + "{'bar': window.FOO.BAR, "
            + "'originalServerName': window.ORIGINAL_SERVER_NAME});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, camel case name.  Placeholder should be same.
    soyCode = "{msg desc=\"\"}\n" + " {camelCaseName}{global.camelCase}. \n" + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$camelCaseName}{$camelCase}.', "
            + "{'camelCaseName': camelCaseName, "
            + "'camelCase': global.camelCase});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    //  Upper case camel case name becomes lower case in placeholder.
    soyCode = "{msg desc=\"\"}\n" + "Unable to reach {CamelCaseName}. Eeeek!\n" + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'Unable to reach {$camelCaseName}. Eeeek!', "
            + "{'camelCaseName': CamelCaseName});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // Leading and trailing underbars are stripped when creating placeholders.
    soyCode =
        "{msg desc=\"Not actually shown to the user.\"}\n"
            + "{_underbar} {_wunderBar_}\n"
            + "{_ThunderBar_} {underCar__}\n"
            + "{window.__car__}{window.__AnotherBar__}{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc Not actually shown to the user. */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$underbar} {$wunderBar}{$thunderBar}"
            + " {$underCar}{$car}{$anotherBar}', "
            + "{'underbar': _underbar, "
            + "'wunderBar': _wunderBar_, "
            + "'thunderBar': _ThunderBar_, "
            + "'underCar': underCar__, "
            + "'car': window.__car__, "
            + "'anotherBar': window.__AnotherBar__});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }

  @Test
  public void testSoyV1LocalPlaceholderCompatibility() {
    // Test that placeholders for local variables (passed into the template)
    // have the same form as they appeared in Soy V1 so that teams
    // with internationalized strings don't have to re-translate strings
    //  with new placeholders.

    // local, all caps, underbars between words.  Placeholder should
    // be lower-case camel case version
    String soyCode =
        "{@param PRODUCT_NAME_HTML : ?}\n"
            + "{msg desc=\"\"}\n"
            + "Unable to reach {$PRODUCT_NAME_HTML}. Eeeek!\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'Unable to reach {$productNameHtml}. Eeeek!', "
            + "{'productNameHtml': opt_data.PRODUCT_NAME_HTML});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // local, property name.  Placeholder should be lower case
    // camel case version of last component.
    soyCode =
        "{@param myvar : ?}\n"
            + "{@param window : ?}\n"
            + "{msg desc=\"\"}\n"
            + "{$myvar.foo.bar}{$myvar.ORIGINAL_SERVER}"
            + "{$window.size._xx_xx_}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$bar}{$originalServer}{$xxXx}', "
            + "{'bar': opt_data.myvar.foo.bar, "
            + "'originalServer': opt_data.myvar.ORIGINAL_SERVER, "
            + "'xxXx': opt_data.window.size._xx_xx_});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, property name, with underbars.  Placeholder should be lower case
    // camel case version of last component, with underbars stripped.
    soyCode =
        "{@param myvar : ?}\n"
            + "{msg desc=\"\"}\n"
            + "{$myvar.foo._bar}{$myvar.foo.trail_}{$myvar.foo._bar_bar_bar_}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$bar}{$trail}{$barBarBar}', "
            + "{'bar': opt_data.myvar.foo._bar, "
            + "'trail': opt_data.myvar.foo.trail_, "
            + "'barBarBar': opt_data.myvar.foo._bar_bar_bar_});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // local, camel case name.  Placeholder should be same, in lower case.
    soyCode =
        "{@param productName : ?}\n"
            + "{@param OtherProductName : ?}\n"
            + "{msg desc=\"\"}\n"
            + " {$productName}{$OtherProductName}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{$productName}{$otherProductName}', "
            + "{'productName': opt_data.productName, "
            + "'otherProductName': opt_data.OtherProductName});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }


  @Test
  public void testPrint() {
    assertGeneratedJsCode("{@param boo : ?}\n{$boo.foo}\n", "output += opt_data.boo.foo;\n");
  }

  @Test
  public void testLet() {

    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{if $boo}\n"
            + // wrapping in if-block makes using assertGeneratedJsCode easier
            "  {let $alpha: $boo.foo /}\n"
            + "  {let $beta kind=\"text\"}Boo!{/let}\n"
            + "  {let $gamma kind=\"text\"}\n"
            + "    {for $i in range($alpha)}\n"
            + "      {$i}{$beta}\n"
            + "    {/for}\n"
            + "  {/let}\n"
            + "  {let $delta kind=\"html\"}Boop!{/let}\n"
            + "  {$alpha}{$beta}{$gamma}{$delta}\n"
            + "{/if}\n";

    String expectedJsCode =
        ""
            + "if (opt_data.boo) {\n"
            + "  var alpha__soy8 = opt_data.boo.foo;\n"
            + "  var beta__soy11 = 'Boo!';\n"
            + "  var beta__wrapped11 = "
            + "soydata.$$markUnsanitizedTextForInternalBlocks(beta__soy11);\n"
            + "  var gamma__soy21 = '';\n"
            + "  var i14ListLen = Math.max(0, Math.ceil((alpha__soy8 - 0) / 1));\n"
            + "  for (var i14Index = 0; i14Index < i14ListLen; i14Index++) {\n"
            + "    var i14Data = 0 + i14Index * 1;\n"
            + "    gamma__soy21 += i14Data + beta__wrapped11;\n"
            + "  }\n"
            + "  var gamma__wrapped21 = "
            + "soydata.$$markUnsanitizedTextForInternalBlocks(gamma__soy21);\n"
            + "  var delta__soy24 = 'Boop!';\n"
            + "  var delta__wrapped24 = soydata.VERY_UNSAFE"
            + ".$$ordainSanitizedHtmlForInternalBlocks(delta__soy24);\n"
            + "  output += alpha__soy8 + beta__wrapped11 + gamma__wrapped21 + delta__wrapped24;\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  // Regression test for a bug where the logic for ordaining strict letcontent blocks failed to
  // propagate the necessary requires for the ordainer functions.
  @Test
  public void testStrictLetAddsAppropriateRequires() {
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    String soyNodeCode = "{let $text kind=\"text\"}foo{/let}{let $html kind=\"html\"}foo{/let}\n";
    ParseResult parseResult = SoyFileSetParserBuilder.forTemplateContents(soyNodeCode).parse();
    String jsFilesContents =
        genJsCodeVisitor
            .gen(parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding())
            .get(0);
    assertThat(jsFilesContents).contains("goog.require('soydata')");
    assertThat(jsFilesContents).contains("goog.require('soydata.VERY_UNSAFE')");
  }

  @Test
  public void testIf() {
    String soyNodeCode;
    String expectedJsCode;

    soyNodeCode =
        JOINER.join("{@param boo : ?}", "{if $boo}", "  Blah", "{else}", "  Bluh", "{/if}");
    expectedJsCode = "output += opt_data.boo ? 'Blah' : 'Bluh';\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        JOINER.join(
            "{@param boo : ?}",
            "{@param goo : ?}",
            "{if $boo}",
            "  Blah",
            "{elseif not strContains($goo, 'goo')}",
            "  Bleh",
            "{else}",
            "  Bluh",
            "{/if}");
    expectedJsCode =
        JOINER.join(
            "var $tmp;",
            "if (opt_data.boo) {",
            "  $tmp = 'Blah';",
            "} else if (!soy.$$strContains('' + gooData8, 'goo')) {",
            "  $tmp = 'Bleh';",
            "} else {",
            "  $tmp = 'Bluh';",
            "}",
            "output += $tmp;",
            "");
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        JOINER.join(
            "{@param boo : ?}",
            "{@param goo : ?}",
            "{if $boo.foo > 0}",
            "  {for $i in range(4)}",
            "    {$i+1}<br>",
            "  {/for}",
            "{elseif not strContains($goo, 'goo')}",
            "  Bleh",
            "{else}",
            "  Bluh",
            "{/if}");
    expectedJsCode =
        ""
            + "if (opt_data.boo.foo > 0) {\n"
            + "  var i9ListLen = Math.max(0, Math.ceil((4 - 0) / 1));\n"
            + "  for (var i9Index = 0; i9Index < i9ListLen; i9Index++) {\n"
            + "    var i9Data = 0 + i9Index * 1;\n"
            + "    output += i9Data + 1 + '<br>';\n"
            + "  }\n"
            + "} else if (!soy.$$strContains('' + gooData8, 'goo')) {\n"
            + "  output += 'Bleh';\n"
            + "} else {\n"
            + "  output += 'Bluh';\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testBasicCall() {

    assertGeneratedJsCode(
        "{call some.func data=\"all\" /}\n",
        "output += some.func(/** @type {?} */ (opt_data), opt_ijData);\n");

    String soyNodeCode =
        "{@param moo : ?}\n" + "{call some.func}\n" + "  {param goo : $moo /}\n" + "{/call}\n";
    assertGeneratedJsCode(
        soyNodeCode, "output += some.func(/** @type {?} */ ({goo: opt_data.moo}), opt_ijData);\n");

    soyNodeCode =
        "{@param boo : ?}\n"
            + "{call some.func data=\"$boo\"}\n"
            + "  {param goo kind=\"text\"}\n"
            + "    {for $i in range(7)}\n"
            + "      {$i}\n"
            + "    {/for}\n"
            + "  {/param}\n"
            + "{/call}\n";
    String expectedJsCode =
        ""
            + "var param12 = '';\n"
            + "var i6ListLen = Math.max(0, Math.ceil((7 - 0) / 1));\n"
            + "for (var i6Index = 0; i6Index < i6ListLen; i6Index++) {\n"
            + "  var i6Data = 0 + i6Index * 1;\n"
            + "  param12 += i6Data;\n"
            + "}\n"
            + "output += some.func(soy.$$assignDefaults("
            + "{goo: soydata.$$markUnsanitizedTextForInternalBlocks(param12)}, opt_data.boo), "
            + "opt_ijData);\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testDelegateCall() {

    assertGeneratedJsCode(
        "{@param boo : ?}\n" + "{delcall my.delegate data=\"$boo.foo\" /}\n",
        "output += soy.$$getDelegateFn(soy.$$getDelTemplateId('my.delegate'), '', false)"
            + "(/** @type {?} */ (opt_data.boo.foo), opt_ijData);\n");

    assertGeneratedJsCode(
        "{@param boo : ?}\n"
            + "{@param voo : ?}\n"
            + "{delcall my.delegate variant=\"$voo\" data=\"$boo.foo\" /}\n",
        "output += soy.$$getDelegateFn("
            + "soy.$$getDelTemplateId('my.delegate'), opt_data.voo, false)"
            + "(/** @type {?} */ (opt_data.boo.foo), opt_ijData);\n");

    assertGeneratedJsCode(
        "{@param boo : ?}\n"
            + "{delcall my.delegate data=\"$boo.foo\" allowemptydefault=\"true\" /}\n",
        "output += soy.$$getDelegateFn(soy.$$getDelTemplateId('my.delegate'), '', true)"
            + "(/** @type {?} */ (opt_data.boo.foo), opt_ijData);\n");
  }

  @Test
  public void testLog() {

    assertGeneratedJsCode(
        "{@param boo : ?}\n" + "{log}Blah {$boo}.{/log}\n",
        "window.console.log('Blah ' + opt_data.boo + '.');\n");

    assertGeneratedJsCode(
        ""
            + "{@param foo : ?}\n"
            + "{@param boo : ?}\n"
            + "{@param moo : ?}\n"
            + "{if true}\n"
            + "  {$foo}\n"
            + "  {log}Blah {$boo}.{/log}\n"
            + "  {$moo}\n"
            + "{/if}\n",
        ""
            + "if (true) {\n"
            + "  output += opt_data.foo;\n"
            + "  window.console.log('Blah ' + opt_data.boo + '.');\n"
            + "  output += opt_data.moo;\n"
            + "}\n");
  }

  @Test
  public void testDebugger() {
    assertGeneratedJsCode("{debugger}\n", "debugger;\n");

    assertGeneratedJsCode(
        "{@param foo : ?}\n"
            + "{@param moo : ?}\n"
            + ""
            + "{if true}\n"
            + "  {$foo}\n"
            + "  {debugger}\n"
            + "  {$moo}\n"
            + "{/if}\n",
        ""
            + "if (true) {\n"
            + "  output += opt_data.foo;\n"
            + "  debugger;\n"
            + "  output += opt_data.moo;\n"
            + "}\n");
  }

  @Test
  public void testXid() {

    assertGeneratedJsCode("{xid('some-id')}\n", "output += xid('some-id');\n");

    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {xid('some-id')}\n"
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding());
    assertThat(jsFilesContents.get(0)).contains("goog.require('xid');");
  }

  // -----------------------------------------------------------------------------------------------
  // Tests for plural/select messages.

  @Test
  public void testMsgWithCollidingPlrsel() {

    // Nested selects, inside a select, with variables all resolving to the same base name "gender".
    String soyNodeCode =
        "{@param format : ?}\n"
            + "{@param user : ?}\n"
            + "{@param person1 : ?}\n"
            + "{@param person2 : ?}\n"
            + "{@param friend : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $format}\n"
            + "    {case 'user_centric'}\n"
            + "      {select $user.gender}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {case 'friend_centric'}\n"
            + "      {select $friend.gender}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $user.gender}\n"
            + "        {case 'female'}{$person1} added {$person2} and some friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and some friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{FORMAT,select,"
            + "user_centric{"
            + "{GENDER_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "friend_centric{"
            + "{GENDER_2,select,"
            + "female{{PERSON_1} added {PERSON_2} and her friends to circle.}"
            + "other{{PERSON_1} added {PERSON_2} and his friends to circle.}"
            + "}"
            + "}"
            + "other{"
            + "{GENDER_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'FORMAT': opt_data.format, "
            + "'GENDER_1': opt_data.user.gender, "
            + "'GENDER_2': opt_data.friend.gender, "
            + "'PERSON_1': opt_data.person1, "
            + "'PERSON_2': opt_data.person2});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Selects nested inside a select, with conflicting top-level names.
    // (This doesn't have conflict.  It is just another test.)
    soyNodeCode =
        "{@param format : ?}\n"
            + "{@param gender : ?}\n"
            + "{@param person1 : ?}\n"
            + "{@param person2 : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $format}\n"
            + "    {case 'user_centric'}\n"
            + "      {select $gender.user}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {case 'friend_centric'}\n"
            + "      {select $gender.friend}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender.user}\n"
            + "        {case 'female'}{$person1} added {$person2} and some friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and some friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{FORMAT,select,"
            + "user_centric{"
            + "{USER,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "friend_centric{"
            + "{FRIEND,select,"
            + "female{{PERSON_1} added {PERSON_2} and her friends to circle.}"
            + "other{{PERSON_1} added {PERSON_2} and his friends to circle.}"
            + "}"
            + "}"
            + "other{"
            + "{USER,select,"
            + "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'FORMAT': opt_data.format, "
            + "'USER': opt_data.gender.user, "
            + "'FRIEND': opt_data.gender.friend, "
            + "'PERSON_1': opt_data.person1, "
            + "'PERSON_2': opt_data.person2});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Similar message as the previous, but the variables are complex, falling
    // back to default naming.
    soyNodeCode =
        "{@param format : ?}\n"
            + "{@param gender : ?}\n"
            + "{@param person1 : ?}\n"
            + "{@param person2 : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $format}\n"
            + "    {case 'user_centric'}\n"
            + "      {select $gender[0]}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {case 'friend_centric'}\n"
            + "      {select $gender[1]}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender[0]}\n"
            + "        {case 'female'}{$person1} added {$person2} and some friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and some friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{FORMAT,select,"
            + "user_centric{"
            + "{STATUS_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "friend_centric{"
            + "{STATUS_2,select,"
            + "female{{PERSON_1} added {PERSON_2} and her friends to circle.}"
            + "other{{PERSON_1} added {PERSON_2} and his friends to circle.}"
            + "}"
            + "}"
            + "other{"
            + "{STATUS_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'FORMAT': opt_data.format, "
            + "'STATUS_1': opt_data.gender[0], "
            + "'STATUS_2': opt_data.gender[1], "
            + "'PERSON_1': opt_data.person1, "
            + "'PERSON_2': opt_data.person2});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Plurals, nested inside a select, with plural name fallbacks.
    soyNodeCode =
        "{@param person : ?}\n"
            + "{@param values : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $values.gender[0]}\n"
            + "    {case 'female'}\n"
            + "      {plural $values.people[0]}\n"
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$person} added {$values.people[0]} to her circle.\n"
            + "      {/plural}\n"
            + "    {case 'male'}\n"
            + "      {plural $values.people[1]}\n"
            + "        {case 1}{$person} added one person to his circle.\n"
            + "        {default}{$person} added {$values.people[1]} to his circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $values.people[1]}\n"
            + "        {case 1}{$person} added one person to his/her circle.\n"
            + "        {default}{$person} added {$values.people[1]} to his/her circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{STATUS,select,"
            + "female{"
            + "{NUM_1,plural,"
            + "=1{{PERSON} added one person to her circle.}"
            + "other{{PERSON} added {XXX_1} to her circle.}"
            + "}"
            + "}"
            + "male{"
            + "{NUM_2,plural,"
            + "=1{{PERSON} added one person to his circle.}"
            + "other{{PERSON} added {XXX_2} to his circle.}"
            + "}"
            + "}"
            + "other{"
            + "{NUM_2,plural,"
            + "=1{{PERSON} added one person to his/her circle.}"
            + "other{{PERSON} added {XXX_2} to his/her circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'STATUS': opt_data.values.gender[0], "
            + "'NUM_1': opt_data.values.people[0], "
            + "'NUM_2': opt_data.values.people[1], "
            + "'PERSON': opt_data.person, "
            + "'XXX_1': opt_data.values.people[0], "
            + "'XXX_2': opt_data.values.people[1]});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Plurals nested inside select, with conflicts between select var name, plural var names
    // and placeholder names.
    soyNodeCode =
        "{@param gender : ?}\n"
            + "{@param person : ?}\n"
            + "{@param number : ?}\n"
            + "{@param user : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender.person}\n"
            + "    {case 'female'}\n"
            + "      {plural $number.person}\n"
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$user.person} added {$number.person} to her circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $number.person}\n"
            + "        {case 1}{$person} added one person to his/her circle.\n"
            + "        {default}{$user.person} added {$number.person} to his/her circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{PERSON_1,select,"
            + "female{"
            + "{PERSON_2,plural,"
            + "=1{{PERSON_3} added one person to her circle.}"
            + "other{{PERSON_4} added {PERSON_5} to her circle.}"
            + "}"
            + "}"
            + "other{"
            + "{PERSON_2,plural,"
            + "=1{{PERSON_3} added one person to his/her circle.}"
            + "other{{PERSON_4} added {PERSON_5} to his/her circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'PERSON_1': opt_data.gender.person, "
            + "'PERSON_2': opt_data.number.person, "
            + "'PERSON_3': opt_data.person, "
            + "'PERSON_4': opt_data.user.person, "
            + "'PERSON_5': opt_data.number.person});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Same as before, except that plural in one branch has offset, the other one doesn't.
    // The result is the same.
    soyNodeCode =
        "{@param gender : ?}\n"
            + "{@param person : ?}\n"
            + "{@param number : ?}\n"
            + "{@param user : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender.person}\n"
            + "    {case 'female'}\n"
            + "      {plural $number.person offset=\"1\"}\n"
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$user.person} added {remainder($number.person)} people to her "
            + "circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $number.person}\n"
            + "        {case 1}{$person} added one person to his/her circle.\n"
            + "        {default}{$user.person} added {$number.person} people to his/her circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{PERSON_1,select,"
            + "female{"
            + "{PERSON_2,plural,offset:1 "
            + "=1{{PERSON_4} added one person to her circle.}"
            + "other{{PERSON_5} added {XXX} people to her circle.}"
            + "}"
            + "}"
            + "other{"
            + "{PERSON_3,plural,"
            + "=1{{PERSON_4} added one person to his/her circle.}"
            + "other{{PERSON_5} added {PERSON_6} people to his/her circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'PERSON_1': opt_data.gender.person, "
            + "'PERSON_2': opt_data.number.person, "
            + "'PERSON_3': opt_data.number.person, "
            + "'PERSON_4': opt_data.person, "
            + "'PERSON_5': opt_data.user.person, "
            + "'XXX': opt_data.number.person - 1, "
            + "'PERSON_6': opt_data.number.person});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Select inside select with same variable
    soyNodeCode =
        "{@param friend : ?}\n"
            + "{@param person1 : ?}\n"
            + "{@param person2 : ?}\n"
            + "{@param user : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $user.gender}\n"
            + "    {case 'female'}\n"
            + "      {select $user.gender}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $friend.gender}\n"
            + "        {case 'female'}{$person1} added {$person2} and some friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and some friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{GENDER_1,select,"
            + "female{"
            + "{GENDER_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "other{"
            + "{GENDER_2,select,"
            + "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'GENDER_1': opt_data.user.gender, "
            + "'GENDER_2': opt_data.friend.gender, "
            + "'PERSON_1': opt_data.person1, "
            + "'PERSON_2': opt_data.person2});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testMsgWithPlrselHtml() {

    String soyNodeCode =
        "{@param num : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  Notify \n"
            + "  {sp}<span class=\"{css('sharebox-id-email-number')}\">{$num}</span>{sp}\n"
            + " people via email &rsaquo;"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc  */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'Notify {$startSpan}{$num}{$endSpan} people via email &rsaquo;', "
            + "{'startSpan': '<span class=\"'"
            + " + goog.getCssName('sharebox-id-email-number') + '\">', "
            + "'num': opt_data.num, "
            + "'endSpan': '</span>'});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        "{@param num : ?}\n"
            + "{msg desc=\"[ICU Syntax]\"}\n"
            + "  {plural $num}\n"
            + "    {case 0}"
            + "      Notify people via email &rsaquo;\n"
            + "    {case 1}"
            + "      Notify{sp}<span class=\"{css('sharebox-id-email-number')}\">{$num}</span> "
            + "person via email &rsaquo;\n"
            + "    {default}\n"
            + "Notify{sp}\n<span class=\"{css('sharebox-id-email-number')}\">"
            + "{$num}</span>{sp}\npeople via email &rsaquo;\n"
            + "  {/plural}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc [ICU Syntax] */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{NUM_1,plural,=0{      Notify people via email &rsaquo;}"
            + "=1{      Notify {START_SPAN_1}{NUM_2}{END_SPAN} person via email &rsaquo;}"
            + "other{Notify {START_SPAN_2}{NUM_2}{END_SPAN} people via email &rsaquo;}}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'NUM_1': opt_data.num, "
            + "'START_SPAN_1': '<span class=\"' + goog.getCssName('sharebox-id-email-number') "
            + "+ '\">', "
            + "'NUM_2': opt_data.num, "
            + "'END_SPAN': '</span>', "
            + "'START_SPAN_2': '<span class=\"' + goog.getCssName('sharebox-id-email-number')"
            + " + '\">'});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  // -----------------------------------------------------------------------------------------------
  // Header params.

  @Test
  public void testPrivateTemplateHasPrivateJsDocAnnotationInGencode() {
    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo visibility=\"private\"}\n"
            + "  Blah\n"
            + "{/template}\n";

    String expectedJsCode =
        ""
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData_deprecated\n"
            + " * @return {!goog.soy.data.SanitizedHtml}\n"
            + " * @suppress {checkTypes}\n"
            + " * @private\n"
            + " */\n"
            + "boo.foo.goo = function(opt_data, opt_ijData, opt_ijData_deprecated) {\n"
            + "  opt_ijData = /** @type {!soy.IjData} */ (opt_ijData_deprecated || opt_ijData);\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml('Blah');\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();
    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(parseResult.fileSet());
    genJsCodeVisitor.visitForTesting(template, parseResult.registry(), ErrorReporter.exploding());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testGoogModuleGeneration() {
    jsSrcOptions.setShouldGenerateGoogModules(true);

    String testFileContent =
        ""
            + "{namespace boo.foo}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {call boo.bar.one /}\n"
            + "  {call boo.bar.two /}\n"
            + "{/template}\n";

    String expectedJsCode =
        ""
            + "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.module('boo.foo');\n"
            + "\n"
            + "goog.requireType('soy');\n"
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "var $import1 = goog.require('boo.bar');\n"
            + "var $templateAlias1 = $import1.one;\n"
            + "var $templateAlias2 = $import1.two;\n"
            + "\n"
            + "\n"
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData\n"
            + " * @param {soy.IjData|Object<string, *>=} opt_ijData_deprecated\n"
            + " * @return {!goog.soy.data.SanitizedHtml}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "var $goo = function(opt_data, opt_ijData, opt_ijData_deprecated) {\n"
            + "  opt_ijData = /** @type {!soy.IjData} */ (opt_ijData_deprecated || opt_ijData);\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml("
            + "$templateAlias1(null, opt_ijData) + "
            + "$templateAlias2(null, opt_ijData));\n"
            + "};\n"
            + "exports.goo = $goo;\n"
            + "if (goog.DEBUG) {\n"
            + "  $goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    assertThat(
            genJsCodeVisitor
                .gen(parseResult.fileSet(), parseResult.registry(), ErrorReporter.exploding())
                .get(0))
        .isEqualTo(expectedJsCode);
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  /**
   * @param soyCode The soy code.
   * @param expectedJsCode JavaScript code expected to be generated from the Soy code.
   */
  private void assertGeneratedJsCode(String soyCode, String expectedJsCode) {
    ParseResult parseResult =
        SoyFileSetParserBuilder.forTemplateContents(soyCode)
            .allowUnboundGlobals(true)
            .parse();
    TemplateNode templateNode = parseResult.fileSet().getChild(0).getChild(0);

    // Setup the GenJsCodeVisitor's state before the node is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();
    genJsCodeVisitor.jsCodeBuilder.pushOutputVar("output");
    genJsCodeVisitor.jsCodeBuilder.setOutputVarInited();
    UniqueNameGenerator nameGenerator = JsSrcNameGenerators.forLocalVariables();
    CodeChunk.Generator codeGenerator = CodeChunk.Generator.create(nameGenerator);
    TranslationContext translationContext =
        TranslationContext.of(
            SoyToJsVariableMappings.startingWith(LOCAL_VAR_TRANSLATIONS),
            codeGenerator,
            nameGenerator);
    genJsCodeVisitor.templateTranslationContext = translationContext;
    genJsCodeVisitor.genJsExprsVisitor =
        JsSrcTestUtils.createGenJsExprsVisitorFactory()
            .create(translationContext, TEMPLATE_ALIASES, ErrorReporter.exploding());
    genJsCodeVisitor.assistantForMsgs = null; // will be created when used

    for (SoyNode child : templateNode.getChildren()) {
      genJsCodeVisitor.visitForTesting(child, parseResult.registry(), ErrorReporter.exploding());
    }

    String genCode = genJsCodeVisitor.jsCodeBuilder.getCode();
    assertThat(genCode).isEqualTo(expectedJsCode);
  }
}
