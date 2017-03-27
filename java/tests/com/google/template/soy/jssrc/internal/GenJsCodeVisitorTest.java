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
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;
import static com.google.template.soy.jssrc.dsl.CodeChunk.number;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.shared.AutoEscapingType;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
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
  private static final Injector INJECTOR = Guice.createInjector(new SoyModule());

  // Let 'goo' simulate a local variable from a 'foreach' loop.
  private static final ImmutableMap<String, CodeChunk.WithValue> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.<String, CodeChunk.WithValue>builder()
          .put(
              "goo",
              id("gooData8"))
          .put(
              "goo__isFirst",
              id("gooIndex8")
                  .doubleEquals(
                      number(0)))
          .put(
              "goo__isLast",
              id("gooIndex8")
                  .doubleEquals(
                      id("gooListLen8")
                          .minus(
                              number(1))))
          .put(
              "goo__index",
              id("gooIndex8"))
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
    JsSrcTestUtils.simulateNewApiCall(INJECTOR, jsSrcOptions);
    genJsCodeVisitor = INJECTOR.getInstance(GenJsCodeVisitor.class);
    genJsCodeVisitor.templateAliases = TEMPLATE_ALIASES;
  }

  @Test
  public void testSoyFile() {

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {call .goo data=\"all\" /}\n"
            + "  {call boo.woo.hoo data=\"all\" /}\n" // not defined in this file
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    // ------ Not using Closure ------
    String expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "if (typeof boo == 'undefined') { var boo = {}; }\n"
            + "if (typeof boo.foo == 'undefined') { boo.foo = {}; }\n"
            + "\n"
            + "\n";

    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);

    // ------ Using Closure, provide/require Soy namespaces ------
    expectedJsFileContentStart =
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
            + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);

    // ------ Using Closure, provide/require JS functions ------
    expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo.goo');\n"
            + "\n"
            + "goog.require('boo.woo.hoo');\n"
            + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(false);
    jsSrcOptions.setShouldProvideRequireJsFunctions(true);
    jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);

    // ------ Using Closure, provide both Soy namespaces and JS functions ------
    expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
            + "// Please don't edit this file by hand.\n"
            + "\n"
            + "/**\n"
            + " * @fileoverview Templates in namespace boo.foo.\n"
            + " * @public\n"
            + " */\n"
            + "\n"
            + "goog.provide('boo.foo');\n"
            + "goog.provide('boo.foo.goo');\n"
            + "\n"
            + "goog.require('boo.woo');\n"
            + "\n";

    jsSrcOptions.setShouldProvideRequireJsFunctions(false);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(true);
    jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testOnlyOneRequireStatementPerNamespace() {

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
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
            + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testOnlyOneRequireStatementPerPluginNamespace() {

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
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
            + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testSoyFileWithRequirecssOnNamespace() {

    String testFileContent =
        ""
            + "{namespace boo.foo autoescape=\"deprecated-noncontextual\"\n"
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
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  @Test
  public void testSoyFileInDelegatePackage() {
    String testFileContent =
        "{delpackage MySecretFeature}\n"
            + "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
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
            + "\n"
            + "\n"
            + "boo.foo.__deltemplate_s2_34da4ced = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return '' + soy.$$getDelegateFn(soy.$$getDelTemplateId('myDelegates.soo'), "
            + "'', false)(null, null, opt_ijData);\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.__deltemplate_s2_34da4ced.soyTemplateName = "
            + "'boo.foo.__deltemplate_s2_34da4ced';\n"
            + "}\n"
            + "soy.$$registerDelegateFn(soy.$$getDelTemplateId('myDelegates.goo'), '', 1,"
            + " boo.foo.__deltemplate_s2_34da4ced);\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(parse.fileSet(), parse.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContent);
  }

  @Test
  public void testDelegateVariantProvideRequiresJsDocAnnotations() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
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
            + "\n"
            + "\n"
            + "boo.foo.__deltemplate_s2_784ed7a8 = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return '' + soy.$$getDelegateFn(soy.$$getDelTemplateId('myDelegates.moo'), "
            + "'moomoo', false)(null, null, opt_ijData);\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.__deltemplate_s2_784ed7a8.soyTemplateName = "
            + "'boo.foo.__deltemplate_s2_784ed7a8';\n"
            + "}\n"
            + "soy.$$registerDelegateFn(soy.$$getDelTemplateId('myDelegates.goo'), 'googoo', 0,"
            + " boo.foo.__deltemplate_s2_784ed7a8);\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(parse.fileSet(), parse.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContent);
  }

  @Test
  public void testTemplate() {

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  Blah\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    // ------ Code style 'concat' ------
    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return 'Blah';\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testTemplateThatShouldEnsureDataIsDefined() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param? moo */\n"
            + "{template .goo}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  opt_data = opt_data || {};\n"
            + "  return '' + opt_data.moo;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);

    // ------ Should not generate extra statement for injected and local var data refs. ------
    testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {let $moo: 90 /}\n"
            + "  {$moo}{$ij.moo}\n"
            + "{/template}\n";

    template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var output = '';\n"
            + "  var moo__soy4 = 90;\n"
            + "  output += moo__soy4 + opt_ijData.moo;\n"
            + "  return output;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor = INJECTOR.getInstance(GenJsCodeVisitor.class);
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();
    genJsCodeVisitor.templateAliases = TEMPLATE_ALIASES;

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testTemplateWithShouldGenerateJsdoc() {

    jsSrcOptions.setShouldGenerateJsdoc(true);

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  Blah\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    // ------ Code style 'concat' with shouldGenerateJsdoc ------
    String expectedJsCode =
        ""
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {(null|undefined)=} opt_ignored\n"
            + " * @param {Object<string, *>=} opt_ijData\n"
            + " * @return {string}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return 'Blah';\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testStrictTemplateShouldGenerateSanitizedContentReturnValue() {

    jsSrcOptions.setShouldGenerateJsdoc(true);

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo autoescape=\"strict\" kind=\"js\"}\n"
            + "  alert('Hello World');\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    // ------ Code style 'concat' with shouldGenerateJsdoc ------
    String expectedJsCode =
        ""
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {(null|undefined)=} opt_ignored\n"
            + " * @param {Object<string, *>=} opt_ijData\n"
            + " * @return {!goog.soy.data.SanitizedJs}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedJs('alert(\\'Hello World\\');');\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testDelTemplate() {

    String testFileContent =
        ""
            + // note: no delpackage => priority 0
            "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test delegate template. */\n"
            + "{deltemplate myDelegates.goo}\n"
            + "  Blah\n"
            + "{/deltemplate}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    // ------ Code style 'concat'. ------
    String expectedJsCode =
        ""
            + "goog.provide('boo.foo');\n"
            + "\n"
            + "goog.require('soy');\n"
            + "\n"
            + "\n"
            + "boo.foo.__deltemplate_s2_ad618961 = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return 'Blah';\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.__deltemplate_s2_ad618961.soyTemplateName = "
            + "'boo.foo.__deltemplate_s2_ad618961';\n"
            + "}\n"
            + "soy.$$registerDelegateFn(soy.$$getDelTemplateId('myDelegates.goo'), '', 0,"
            + " boo.foo.__deltemplate_s2_ad618961);\n";

    genJsCodeVisitor.jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    // Setup the GenJsCodeVisitor's state before the template is visited.
    String file =
        genJsCodeVisitor
            .gen(parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get())
            .get(0);
    assertThat(file).endsWith(expectedJsCode);
  }

  @Test
  public void testDelTemplateWithVariant() {

    String testFileContent =
        ""
            + "{delpackage MySecretFeature}\n"
            + // note: delpackage => priority 1
            "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test delegate template with variant. */\n"
            + "{deltemplate myDelegates.goo variant=\"'moo'\"}\n"
            + "  Blah\n"
            + "{/deltemplate}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    // ------ Code style 'concat'. ------
    String expectedJsCode =
        ""
            + "boo.foo.__deltemplate_s2_b66e4cb3 = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return 'Blah';\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.__deltemplate_s2_b66e4cb3.soyTemplateName = "
            + "'boo.foo.__deltemplate_s2_b66e4cb3';\n"
            + "}\n"
            + "soy.$$registerDelegateFn("
            + "soy.$$getDelTemplateId('myDelegates.goo'), 'moo', 1,"
            + " boo.foo.__deltemplate_s2_b66e4cb3);\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testRawText() {
    assertGeneratedJsCode(
        "I'm feeling lucky!\n",
        "output += 'I\\'m feeling lucky!';\n");

    assertGeneratedJsCode(
        "{lb}^_^{rb}{sp}{\\n}\n",
        "output += '{^_^} \\n';\n");
  }

  @Test
  public void testGoogMsg() {

    String soyCode =
        "{@param user : ?}\n"
            + "{@param url : ?}\n"
            + "{msg desc=\"Tells the user to click a link.\"}\n"
            + "  Hello {$user.userName}, please click <a href=\"{$url}\">here</a>.\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc Tells the user to click a link. */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'Hello {$userName}, please click {$startLink}here{$endLink}.', "
            + "{'userName': opt_data.user.userName, "
            + "'startLink': '<a href=\"' + opt_data.url + '\">', "
            + "'endLink': '</a>'});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    soyCode =
        ""
            + "{msg meaning=\"boo\" desc=\"foo\" hidden=\"true\"}\n"
            + "  Blah\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @meaning boo\n"
            + " *  @desc foo\n"
            + " *  @hidden */\n"
            + "var MSG_UNNAMED = goog.getMsg('Blah');\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    soyCode =
        "{@param boo : ?}\n"
            + "{@param a : ?}\n"
            + "{msg desc=\"A span with generated id.\"}\n"
            + "  <span id=\"{for $i in range(3)}{$i}{/for}\">\n"
            + "  {call some.func data=\"$boo\"}\n"
            + "    {param goo}\n"
            + "      {for $i in range(4)}{$i}{/for}\n"
            + "    {/param}\n"
            + "  {/call}\n"
            + "  {$a + 2}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "var htmlTag15 = '<span id=\"';\n"
            + "for (var i10 = 0; i10 < 3; i10++) {\n"
            + "  htmlTag15 += i10;\n"
            + "}\n"
            + "htmlTag15 += '\">';\n"
            + "var param19 = '';\n"
            + "for (var i21 = 0; i21 < 4; i21++) {\n"
            + "  param19 += i21;\n"
            + "}\n"
            + "/** @desc A span with generated id. */\n"
            + "var MSG_UNNAMED = goog.getMsg('{$startSpan}{$xxx_1}{$xxx_2}', "
            + "{'startSpan': htmlTag15, "
            + "'xxx_1': some.func(soy.$$assignDefaults({goo: param19}, opt_data.boo), null, "
            + "opt_ijData),"
            + " 'xxx_2': opt_data.a + 2});\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    soyCode =
        "{msg desc=\"foo\"}\n"
            + "  More \u00BB\n"
            + "{/msg}\n";
    // Make sure JS code doesn't have literal unicode characters, since they
    // don't always get interpreted properly.
    expectedJsCode =
        ""
            + "/** @desc foo */\n"
            + "var MSG_UNNAMED = goog.getMsg('More \\u00BB');\n"
            + "output += MSG_UNNAMED;\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }

  @Test
  public void testGoogMsgWithFallback() {

    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
            + "  Archive\n"
            + "{fallbackmsg desc=\"\"}\n"
            + "  Archive\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @meaning verb\n"
            + " *  @desc Used as a verb. */\n"
            + "var MSG_UNNAMED = goog.getMsg('Archive');\n"
            + "/** @desc  */\n"
            + "var MSG_UNNAMED$$1 = goog.getMsg('Archive');\n"
            + "var msg_s = goog.getMsgWithFallback(MSG_UNNAMED, MSG_UNNAMED$$1);\n"
            + "output += msg_s;\n";
    // Note: Using getGeneratedJsCode() directly so that ids are not replaced with ###.
    assertThat(getGeneratedJsCode(soyCode, ExplodingErrorReporter.get())).isEqualTo(expectedJsCode);
  }

  @Test
  public void testSoyV1GlobalPlaceholderCompatibility() {
    // Test that placeholders for global variables have the same form
    // as they appeared in Soy V1 so that teams with internationalized
    // strings don't have to re-translate strings with new placeholders.

    // global, all caps, underbars between words.  Placeholder should
    // be lower-case camel case version
    String soyCode =
        "{msg desc=\"\"}\n"
            + "Unable to reach {PRODUCT_NAME_HTML}. Eeeek!\n"
            + "{/msg}\n";
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
  public void testPrintGoogMsg() {

    String soyCode =
        "{@param userName : ?}\n"
            + "{@param url : ?}\n"
            + "{msg desc=\"Tells the user to click a link.\"}\n"
            + "  Hello {$userName}, please click <a href=\"{$url}\">here</a>.\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc Tells the user to click a link. */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'Hello {$userName}, please click {$startLink}here{$endLink}.', "
            + "{'userName': opt_data.userName, "
            + "'startLink': '<a href=\"' + opt_data.url + '\">', "
            + "'endLink': '</a>'});\n"
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
            + "  {let $beta}Boo!{/let}\n"
            + "  {let $gamma}\n"
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
            + "  var beta__soy10 = 'Boo!';\n"
            + "  var gamma__soy13 = '';\n"
            + "  var i15Limit = alpha__soy8;\n"
            + "  for (var i15 = 0; i15 < i15Limit; i15++) {\n"
            + "    gamma__soy13 += i15 + beta__soy10;\n"
            + "  }\n"
            + "  var delta__soy22 = 'Boop!';\n"
            + "  delta__soy22 = soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks("
            + "delta__soy22);\n"
            + "  output += alpha__soy8 + beta__soy10 + gamma__soy13 + delta__soy22;\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  // Regression test for a bug where the logic for ordaining strict letcontent blocks failed to
  // propagate the necessary requires for the ordainer functions.
  @Test
  public void testStrictLetAddsAppropriateRequires() {
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    String soyNodeCode = "{let $text kind=\"text\"}foo{/let}{let $html kind=\"html\"}foo{/let}\n";
    ParseResult parseResult =
        SoyFileSetParserBuilder.forTemplateContents(AutoEscapingType.STRICT, soyNodeCode).parse();
    String jsFilesContents =
        genJsCodeVisitor
            .gen(parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get())
            .get(0);
    assertThat(jsFilesContents).contains("goog.require('soydata')");
    assertThat(jsFilesContents).contains("goog.require('soydata.VERY_UNSAFE')");
  }

  @Test
  public void testIf() {
    String soyNodeCode;
    String expectedJsCode;

    soyNodeCode = JOINER.join(
        "{@param boo : ?}",
        "{if $boo}",
        "  Blah",
        "{else}",
        "  Bluh",
        "{/if}");
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
            "var $tmp = null;",
            "if (opt_data.boo) {",
            "  $tmp = 'Blah';",
            "} else if (!(('' + gooData8).indexOf('goo') != -1)) {",
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
            + "  for (var i9 = 0; i9 < 4; i9++) {\n"
            + "    output += i9 + 1 + '<br>';\n"
            + "  }\n"
            + "} else if (!(('' + gooData8).indexOf('goo') != -1)) {\n"
            + "  output += 'Bleh';\n"
            + "} else {\n"
            + "  output += 'Bluh';\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testSwitch() {

    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{switch $boo}\n"
            + "  {case 0}\n"
            + "    Blah\n"
            + "  {case 1, $goo + 1, 2}\n"
            + "    Bleh\n"
            + "  {default}\n"
            + "    Bluh\n"
            + "{/switch}\n";
    String expectedJsCode =
        "var $tmp = opt_data.boo;\n"
            + "switch (goog.isObject($tmp) ? $tmp.toString() : $tmp) {\n"
            + "  case 0:\n"
            + "    output += 'Blah';\n"
            + "    break;\n"
            + "  case 1:\n"
            + "  case gooData8 + 1:\n"
            + "  case 2:\n"
            + "    output += 'Bleh';\n"
            + "    break;\n"
            + "  default:\n"
            + "    output += 'Bluh';\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testSwitch_withNullCoalescing() {
    String soyNodeCode =
        "{@param alpha : ?}\n"
            + "{@param beta : ?}\n"
            + "{switch $alpha ?: $beta}\n"
            + "  {default}\n"
            + "    Bluh\n"
            + "{/switch}\n";
    String expectedJsCode =
        "var $tmp = ($$temp = opt_data.alpha) == null ? opt_data.beta : $$temp;\n"
            + "switch (goog.isObject($tmp) ? $tmp.toString() : $tmp) {\n"
            + "  default:\n"
            + "    output += 'Bluh';\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testForeach() {
    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{foreach $foo in $boo.foos}\n"
            + "  {if not isFirst($foo)}\n"
            + "    <br>\n"
            + "  {/if}\n"
            + "  {$foo} is fool no. {index($foo)}\n"
            + "  {if isLast($foo)}\n"
            + "    <br>The end.\n"
            + "  {/if}\n"
            + "{ifempty}\n"
            + "  No fools here.\n"
            + "{/foreach}\n";
    String expectedJsCode =
        ""
            + "var foo19List = opt_data.boo.foos;\n"
            + "var foo19ListLen = foo19List.length;\n"
            + "if (foo19ListLen > 0) {\n"
            + "  for (var foo19Index = 0; foo19Index < foo19ListLen; foo19Index++) {\n"
            + "    var foo19Data = foo19List[foo19Index];\n"
            + "    output += (!(foo19Index == 0) ? '<br>' : '') + foo19Data + ' is fool no. '"
            + " + foo19Index + (foo19Index == foo19ListLen - 1 ? '<br>The end.' : '');\n"
            + "  }\n"
            + "} else {\n"
            + "  output += 'No fools here.';\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // TODO(user): Test a foreach-loop with initializing statements
  }

  @Test
  public void testFor() {

    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{for $i in range(8, 16, 2)}\n"
            + "  {$boo[$i] + $goo[$i]}\n"
            + "{/for}\n";
    String expectedJsCode =
        ""
            + "for (var i6 = 8; i6 < 16; i6 += 2) {\n"
            + "  output += opt_data.boo[i6] + gooData8[i6];\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        ""
            + "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{@param foo : ?}\n"
            + "{for $i in range($boo-$goo, $boo+$goo, $foo)}\n"
            + "  {$i + 1}{sp}\n"
            + "{/for}\n";
    expectedJsCode =
        ""
            + "var i7Limit = opt_data.boo + gooData8;\n"
            + "var i7Increment = opt_data.foo;\n"
            + "for (var i7 = opt_data.boo - gooData8; i7 < i7Limit; i7 += i7Increment) {\n"
            + "  output += i7 + 1 + ' ';\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        ""
            + "{let $boo: ['a': [], 'b': [10, 20, 30]] /}\n"
            + "{for $i in range($boo.b[0], $boo.b[1], $boo.b[2])}\n"
            + "  {$i}\n"
            + "{/for}\n";
    expectedJsCode =
        ""
            + "var boo__soy4 = {a: [], b: [10, 20, 30]};\n"
            + "var i6Limit = boo__soy4.b[1];\n"
            + "var i6Increment = boo__soy4.b[2];\n"
            + "for (var i6 = boo__soy4.b[0]; i6 < i6Limit; i6 += i6Increment) {\n"
            + "  output += i6;\n"
            + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // TODO(user): Test a for-loop with initializing statements
  }

  @Test
  public void testBasicCall() {

    assertGeneratedJsCode(
        "{call some.func data=\"all\" /}\n",
        "output += some.func(opt_data, null, opt_ijData);\n");

    String soyNodeCode =
        "{@param moo : ?}\n" + "{call some.func}\n" + "  {param goo : $moo /}\n" + "{/call}\n";
    assertGeneratedJsCode(
        soyNodeCode,
        "output += some.func({goo: opt_data.moo}, null, opt_ijData);\n");

    soyNodeCode =
        "{@param boo : ?}\n"
            + "{call some.func data=\"$boo\"}\n"
            + "  {param goo}\n"
            + "    {for $i in range(7)}\n"
            + "      {$i}\n"
            + "    {/for}\n"
            + "  {/param}\n"
            + "{/call}\n";
    String expectedJsCode =
        ""
            + "var param6 = '';\n"
            + "for (var i8 = 0; i8 < 7; i8++) {\n"
            + "  param6 += i8;\n"
            + "}\n"
            + "output += some.func(soy.$$assignDefaults({goo: param6}, opt_data.boo), null, "
            + "opt_ijData);\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testDelegateCall() {

    assertGeneratedJsCode(
        "{@param boo : ?}\n" + "{delcall my.delegate data=\"$boo.foo\" /}\n",
        "output += soy.$$getDelegateFn(soy.$$getDelTemplateId('my.delegate'), '', false)"
            + "(opt_data.boo.foo, null, opt_ijData);\n");

    assertGeneratedJsCode(
        "{@param boo : ?}\n"
            + "{@param voo : ?}\n"
            + "{delcall my.delegate variant=\"$voo\" data=\"$boo.foo\" /}\n",
        "output += soy.$$getDelegateFn("
            + "soy.$$getDelTemplateId('my.delegate'), opt_data.voo, false)"
            + "(opt_data.boo.foo, null, opt_ijData);\n");

    assertGeneratedJsCode(
        "{@param boo : ?}\n"
            + "{delcall my.delegate data=\"$boo.foo\" allowemptydefault=\"true\" /}\n",
        "output += soy.$$getDelegateFn(soy.$$getDelTemplateId('my.delegate'), '', true)"
            + "(opt_data.boo.foo, null, opt_ijData);\n");
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

    assertGeneratedJsCode("{xid some-id}\n", "output += xid('some-id');\n");

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo}\n"
            + "  {xid some-id}\n"
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).contains("goog.require('xid');");
  }

  // -----------------------------------------------------------------------------------------------
  // Tests for plural/select messages.

  @Test
  public void testMsgWithPlural() {

    // A simple plural message with offset and remainder().
    String soyNodeCode =
        "{@param num_people: ?}\n"
            + "{@param person : ?}\n"
            + "{@param place : ?}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}"
            + "          I see {$person} and {remainder($num_people)} other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc A sample plural message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{NUM_PEOPLE,plural,offset:1 "
            + "=0{I see no one in {PLACE}.}"
            + "=1{I see {PERSON} in {PLACE}.}"
            + "=2{I see {PERSON} and one other person in {PLACE}.}"
            + "other{          I see {PERSON} and {XXX} other people in {PLACE}.}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'NUM_PEOPLE': opt_data.num_people, "
            + "'PLACE': opt_data.place, "
            + "'PERSON': opt_data.person, "
            + "'XXX': opt_data.num_people - 1});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // A simple plural message with no offset.
    soyNodeCode =
        "{@param num_people: ?}\n"
            + "{@param person : ?}\n"
            + "{@param place : ?}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {default}I see {$num_people} persons in {$place}, including {$person}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample plural message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{NUM_PEOPLE_1,plural,"
            + "=0{I see no one in {PLACE}.}"
            + "=1{I see {PERSON} in {PLACE}.}"
            + "other{I see {NUM_PEOPLE_2} persons in {PLACE}, including {PERSON}.}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'NUM_PEOPLE_1': opt_data.num_people, "
            + "'PLACE': opt_data.place, "
            + "'PERSON': opt_data.person, "
            + "'NUM_PEOPLE_2': opt_data.num_people});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Same message as above, but with (a) 0 offset explicitly specified and (b) a plural
    // expression that is a function call, specifically length(...).
    soyNodeCode =
        "{@param persons : ?}\n"
            + "{@param place : ?}\n"
            + "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural length($persons) offset=\"0\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$persons[0]} in {$place}.\n"
            + "      {default}"
            + "          I see {length($persons)} persons in {$place}, including {$persons[0]}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample plural message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{NUM,plural,"
            + "=0{I see no one in {PLACE}.}"
            + "=1{I see {XXX_1} in {PLACE}.}"
            + "other{          I see {XXX_2} persons in {PLACE}, including {XXX_1}.}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'NUM': (opt_data.persons.length), "
            + "'PLACE': opt_data.place, "
            + "'XXX_1': opt_data.persons[0], "
            + "'XXX_2': (opt_data.persons.length)});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // With the plural variable used both as a placeholder and in remainder().
    soyNodeCode =
        "{@param persons : ?}\n"
            + "{@param num_people : ?}\n"
            + "{msg desc=\"A sample plural with offset\"}\n"
            + "  {plural $num_people offset=\"2\"}\n"
            + "    {case 0}No people.\n"
            + "    {case 1}There is one person: {$persons[0]}.\n"
            + "    {case 2}There are two persons: {$persons[0]} and {$persons[1]}.\n"
            + "    {default}There are {$num_people} persons: "
            + "{$persons[0]}, {$persons[1]} and {remainder($num_people)} others.\n"
            + "  {/plural}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample plural with offset */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{NUM_PEOPLE_1,plural,offset:2 "
            + "=0{No people.}"
            + "=1{There is one person: {XXX_1}.}"
            + "=2{There are two persons: {XXX_1} and {XXX_2}.}"
            + "other{There are {NUM_PEOPLE_2} persons: {XXX_1}, {XXX_2} and {XXX_3} others.}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'NUM_PEOPLE_1': opt_data.num_people, "
            + "'XXX_1': opt_data.persons[0], "
            + "'XXX_2': opt_data.persons[1], "
            + "'NUM_PEOPLE_2': opt_data.num_people, "
            + "'XXX_3': opt_data.num_people - 2});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testMsgWithSelect() {

    // Simple select message: Gender with 'female' and other.
    String soyNodeCode =
        "{@param gender : ?}\n"
            + "{@param person : ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc A sample gender message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{GENDER,select,"
            + "female{{PERSON} added you to her circle.}"
            + "other{{PERSON} added you to his circle.}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'GENDER': opt_data.gender, "
            + "'PERSON': opt_data.person});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Simple select message: Gender with 'female', 'male', 'neuter' and other.
    soyNodeCode =
        "{@param gender : ?}\n"
            + "{@param person : ?}\n"
            + "{msg desc=\"A sample gender message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}{$person} added you to her circle.\n"
            + "    {case 'male'}{$person} added you to his circle.\n"
            + "    {case 'neuter'}{$person} added you to its circle.\n"
            + "    {default}{$person} added you to his circle.\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample gender message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{GENDER,select,"
            + "female{{PERSON} added you to her circle.}"
            + "male{{PERSON} added you to his circle.}"
            + "neuter{{PERSON} added you to its circle.}"
            + "other{{PERSON} added you to his circle.}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'GENDER': opt_data.gender, "
            + "'PERSON': opt_data.person});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }

  @Test
  public void testPlrselMsgWithFallback() {

    String soyCode =
        ""
            + "{@param userGender : ?}\n"
            + "{@param targetGender : ?}\n"
            + "{@param targetName : ?}\n"
            + "{msg genders=\"$userGender, $targetGender\" desc=\"A message with genders.\"}\n"
            + "  Join {$targetName}'s community.\n"
            + "{fallbackmsg desc=\"A message without genders.\"}\n"
            + "  Join {$targetName}'s community.\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc A message with genders. */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{USER_GENDER,select,"
            + "female{{TARGET_GENDER,select,"
            + "female{Join {TARGET_NAME}\\'s community.}"
            + "male{Join {TARGET_NAME}\\'s community.}"
            + "other{Join {TARGET_NAME}\\'s community.}"
            + "}}"
            + "male{{TARGET_GENDER,select,"
            + "female{Join {TARGET_NAME}\\'s community.}"
            + "male{Join {TARGET_NAME}\\'s community.}"
            + "other{Join {TARGET_NAME}\\'s community.}"
            + "}}"
            + "other{{TARGET_GENDER,select,"
            + "female{Join {TARGET_NAME}\\'s community.}"
            + "male{Join {TARGET_NAME}\\'s community.}"
            + "other{Join {TARGET_NAME}\\'s community.}"
            + "}}"
            + "}');\n"
            + "/** @desc A message without genders. */\n"
            + "var MSG_UNNAMED$$1 = goog.getMsg("
            + "'Join {$targetName}\\'s community.', "
            + "{'targetName': opt_data.targetName});\n"
            + "var msg_s = goog.getMsgWithFallback(MSG_UNNAMED, MSG_UNNAMED$$1);\n"
            + "if (msg_s == MSG_UNNAMED) {\n"
            + "  msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'USER_GENDER': opt_data.userGender, "
            + "'TARGET_GENDER': opt_data.targetGender, "
            + "'TARGET_NAME': opt_data.targetName});\n"
            + "}\n"
            + "output += msg_s;\n";
    // Note: Using getGeneratedJsCode() directly so that ids are not replaced with ###.
    assertThat(getGeneratedJsCode(soyCode, ExplodingErrorReporter.get())).isEqualTo(expectedJsCode);
  }

  @Test
  public void testMsgWithNestedSelectPlural() {

    // Select nested inside select.
    String soyNodeCode =
        "{@param gender : ?}\n"
            + "{@param gender2 : ?}\n"
            + "{@param person1 : ?}\n"
            + "{@param person2 : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}\n"
            + "      {select $gender2}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to her circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender2}\n"
            + "        {case 'female'}{$person1} added {$person2} and her friends to his circle.\n"
            + "        {default}{$person1} added {$person2} and his friends to his circle.\n"
            + "      {/select}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    String expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{GENDER,select,"
            + "female{"
            + "{GENDER_2,select,"
            + "female{{PERSON_1} added {PERSON_2} and her friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and his friends to her circle.}"
            + "}"
            + "}"
            + "other{"
            + "{GENDER_2,select,"
            + "female{{PERSON_1} added {PERSON_2} and her friends to his circle.}"
            + "other{{PERSON_1} added {PERSON_2} and his friends to his circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'GENDER': opt_data.gender, "
            + "'GENDER_2': opt_data.gender2, "
            + "'PERSON_1': opt_data.person1, "
            + "'PERSON_2': opt_data.person2});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Plural nested inside select.
    soyNodeCode =
        "{@param gender : ?}\n"
            + "{@param person : ?}\n"
            + "{@param num_people : ?}\n"
            + "{msg desc=\"A sample nested message\"}\n"
            + "  {select $gender}\n"
            + "    {case 'female'}\n"
            + "      {plural $num_people}\n"
            + "        {case 1}{$person} added one person to her circle.\n"
            + "        {default}{$person} added {$num_people} to her circle.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $num_people}\n"
            + "        {case 1}{$person} added one person to his circle.\n"
            + "        {default}{$person} added {$num_people} to his circle.\n"
            + "      {/plural}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    expectedJsCode =
        ""
            + "/** @desc A sample nested message */\n"
            + "var MSG_UNNAMED = goog.getMsg("
            + "'{GENDER,select,"
            + "female{"
            + "{NUM_PEOPLE_1,plural,"
            + "=1{{PERSON} added one person to her circle.}"
            + "other{{PERSON} added {NUM_PEOPLE_2} to her circle.}"
            + "}"
            + "}"
            + "other{"
            + "{NUM_PEOPLE_1,plural,"
            + "=1{{PERSON} added one person to his circle.}"
            + "other{{PERSON} added {NUM_PEOPLE_2} to his circle.}"
            + "}"
            + "}"
            + "}');\n"
            + "var msg_s = new goog.i18n.MessageFormat(MSG_UNNAMED).formatIgnoringPound("
            + "{'GENDER': opt_data.gender, "
            + "'NUM_PEOPLE_1': opt_data.num_people, "
            + "'PERSON': opt_data.person, "
            + "'NUM_PEOPLE_2': opt_data.num_people});\n"
            + "output += msg_s;\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    // Plural inside plural should be invalid.
    soyNodeCode =
        "{msg desc=\"A sample nested message\"}\n"
            + "  {plural $n_friends}\n"
            + "    {case 1}\n"
            + "      {plural $n_circles}\n"
            + "        {case 1}You have one friend in one circle.\n"
            + "        {default}You have one friend in {$n_circles} circles.\n"
            + "      {/plural}\n"
            + "    {default}\n"
            + "      {plural $n_circles}\n"
            + "        {case 1}You have {$n_friends} friends in one circle.\n"
            + "        {default}You have {$n_friends} friends in {$n_circles} circles.\n"
            + "      {/plural}\n"
            + "  {/plural}\n"
            + "{/msg}\n";
    assertFailsInGeneratingJsCode(soyNodeCode, null);

    // Select inside plural should be invalid.
    soyNodeCode =
        "{msg desc=\"A sample nested message\"}\n"
            + "  {plural $n_friends}\n"
            + "    {case 1}\n"
            + "      {select $gender}\n"
            + "        {case 'female'}{$person} has one person in her circle.\n"
            + "        {default}{$person} has one person in his circle.\n"
            + "      {/select}\n"
            + "    {default}\n"
            + "      {select $gender}\n"
            + "        {case 'female'}{$person} has {$n_friends} persons in her circle.\n"
            + "        {default}{$person} has {$n_friends} persons in his circle.\n"
            + "      {/select}\n"
            + "  {/plural}\n"
            + "{/msg}\n";
    assertFailsInGeneratingJsCode(soyNodeCode, null);
  }

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
            + "    {case 'user-centric'}\n"
            + "      {select $user.gender}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {case 'friend-centric'}\n"
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
            + "user-centric{"
            + "{GENDER_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "friend-centric{"
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
            + "    {case 'user-centric'}\n"
            + "      {select $gender.user}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {case 'friend-centric'}\n"
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
            + "user-centric{"
            + "{USER,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "friend-centric{"
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
            + "    {case 'user-centric'}\n"
            + "      {select $gender[0]}\n"
            + "        {case 'female'}{$person1} added {$person2} and friends to her circle.\n"
            + "        {default}{$person1} added {$person2} and friends to his circle.\n"
            + "      {/select}\n"
            + "    {case 'friend-centric'}\n"
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
            + "user-centric{"
            + "{STATUS_1,select,"
            + "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
            + "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
            + "}"
            + "}"
            + "friend-centric{"
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
            + "  {sp}<span class=\"{css sharebox-id-email-number}\">{$num}</span>{sp}\n"
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
            + "      Notify{sp}<span class=\"{css sharebox-id-email-number}\">{$num}</span>{sp}\n"
            + "person via email &rsaquo;\n"
            + "    {default}\n"
            + "Notify{sp}\n<span class=\"{css sharebox-id-email-number}\">"
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

  @Test
  public void testMsgWithInvalidPlrsel() {

    // FAIL: Remainder variable different from plural variable.
    String soyNodeCode =
        "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"1\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($n)} other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    assertFailsInGeneratingJsCode(soyNodeCode, null);

    // FAIL: Remainder in a plural variable with no offset.
    soyNodeCode =
        "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    assertFailsInGeneratingJsCode(soyNodeCode, null);

    // FAIL: Remainder in a plural variable with offset=0.
    soyNodeCode =
        "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $num_people offset=\"0\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($num_people)} other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    assertFailsInGeneratingJsCode(soyNodeCode, null);

    // FAIL: Remainder variable and plural variable are different but have the same leaf name.
    soyNodeCode =
        "  {msg desc=\"A sample plural message\"}\n"
            + "    {plural $users.num offset=\"0\"}\n"
            + "      {case 0}I see no one in {$place}.\n"
            + "      {case 1}I see {$person} in {$place}.\n"
            + "      {case 2}I see {$person} and one other person in {$place}.\n"
            + "      {default}I see {$person} and {remainder($friends.num)} other people in {$place}.\n"
            + "    {/plural}\n"
            + "  {/msg}\n";
    assertFailsInGeneratingJsCode(soyNodeCode, null);
  }

  @Test
  public void testStrictMode() {

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo autoescape=\"strict\" kind=\"html\"}\n"
            + "  Blah\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    // ------ Code style 'concat' ------
    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml('Blah');\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  // -----------------------------------------------------------------------------------------------
  // Header params.

  @Test
  public void testHeaderParamsGeneratesJsRecordType() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@param moo : string}\n"
            + "  {@param goo : string|null}\n"
            + "  {$moo}\n"
            + "  {$goo}\n"
            + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    jsSrcOptions.setShouldGenerateJsdoc(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());

    // Ensure that the use of header params generates a record type for opt_data.
    assertThat(jsFilesContents.get(0))
        .contains(
            Joiner.on('\n')
                .join(
                    "@param {{",
                    " *  moo: (!goog.soy.data.SanitizedContent|string),",
                    " *  goo: (!goog.soy.data.SanitizedContent|null|string|undefined)",
                    " * }} opt_data"));
    assertThat(jsFilesContents.get(0))
        .contains(
            Joiner.on('\n')
                .join(
                    "@typedef {{",
                    " *  moo: (!goog.soy.data.SanitizedContent|string),",
                    " *  goo: (!goog.soy.data.SanitizedContent|null|string|undefined)",
                    " * }}",
                    " */",
                    "boo.foo.goo.Params;"));
  }

  @Test
  public void testHeaderParamRequiresAssertsAndSanitizedData() {
    ImmutableSet<String> symbols =
        getRequiredSymbols(
            "{namespace boo.foo}\n"
                + "\n"
                + "{template .goo}\n"
                + "  {@param moo : string}\n"
                + "  {$moo}\n"
                + "{/template}\n");

    assertThat(symbols).contains("soy.asserts");
    assertThat(symbols).contains("goog.soy.data.SanitizedContent");
  }

  @Test
  public void testHeaderParamAggregateTypesPropagateRequiresFromMembers() {
    assertThat(
            getRequiredSymbols(
                "{namespace boo.foo}\n"
                    + "\n"
                    + "{template .goo}\n"
                    + "  {@param moo : list<html>}\n"
                    + "  {$moo}\n"
                    + "{/template}\n"))
        .contains("goog.soy.data.SanitizedHtml");
    assertThat(
            getRequiredSymbols(
                "{namespace boo.foo}\n"
                    + "\n"
                    + "{template .goo}\n"
                    + "  {@param moo : map<string, html>}\n"
                    + "  {$moo}\n"
                    + "{/template}\n"))
        .contains("goog.soy.data.SanitizedHtml");
    assertThat(
            getRequiredSymbols(
                "{namespace boo.foo}\n"
                    + "\n"
                    + "{template .goo}\n"
                    + "  {@param moo : html|css}\n"
                    + "  {$moo}\n"
                    + "{/template}\n"))
        .containsAllOf("goog.soy.data.SanitizedHtml", "goog.soy.data.SanitizedCss");
  }

  @Test
  public void testHeaderParamIntType() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@param moo : int}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var moo = soy.asserts.assertType(goog.isNumber(opt_data.moo), 'moo', opt_data.moo, 'number');\n"
            + "  return '' + moo;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testHeaderParamStringType() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@param moo : string}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var moo = soy.asserts.assertType(goog.isString(opt_data.moo) || opt_data.moo instanceof goog.soy.data.SanitizedContent, 'moo', opt_data.moo, '!goog.soy.data.SanitizedContent|string');\n"
            + "  return '' + moo;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testHeaderParamBoolType() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@param moo : bool}\n"
            + "  {@param? noo : bool}\n"
            + "  {$moo ? 1 : 0}{$noo ? 1 : 0}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var moo = soy.asserts.assertType(goog.isBoolean(opt_data.moo) || opt_data.moo === 1 || opt_data.moo === 0, 'moo', opt_data.moo, 'boolean');\n"
            + "  var noo = soy.asserts.assertType(opt_data.noo == null || (goog.isBoolean(opt_data.noo) || opt_data.noo === 1 || opt_data.noo === 0), 'noo', opt_data.noo, 'boolean|null|undefined');\n"
            + "  return '' + (moo ? 1 : 0) + (noo ? 1 : 0);\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testHeaderParamSanitizedType() {
    String testFileContent =
        "{namespace boo.foo}\n"
            + "\n"
            + "{template .goo}\n"
            + "  {@param html: html}\n"
            + "  {$html}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var html = soy.asserts.assertType(goog.soy.data.SanitizedHtml.isCompatibleWith(opt_data.html), 'html', opt_data.html, '!goog.html.SafeHtml|!goog.soy.data.SanitizedHtml|!goog.soy.data.UnsanitizedText|string');\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml(html);\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testHeaderParamUnionType() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@param moo : string|list<int>}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var moo = soy.asserts.assertType(goog.isArray(opt_data.moo) || (goog.isString(opt_data.moo) || opt_data.moo instanceof goog.soy.data.SanitizedContent), 'moo', opt_data.moo, '!Array<number>|!goog.soy.data.SanitizedContent|string');\n"
            + "  return '' + moo;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testHeaderParamReservedWord() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@param export : int}\n"
            + "  {$export}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var param$export = soy.asserts.assertType(goog.isNumber(opt_data.export), 'export', opt_data.export, 'number');\n"
            + "  return '' + param$export;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testInjectedHeaderParamStringType() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** */\n"
            + "{template .goo}\n"
            + "  {@inject moo : string}\n"
            + "  {$moo}\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  var moo = soy.asserts.assertType(goog.isString(opt_ijData.moo) || opt_ijData.moo instanceof goog.soy.data.SanitizedContent, 'moo', opt_ijData.moo, '!goog.soy.data.SanitizedContent|string');\n"
            + "  return '' + moo;\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n"
            + "";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor = INJECTOR.getInstance(GenJsCodeVisitor.class);
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();
    genJsCodeVisitor.templateAliases = TEMPLATE_ALIASES;

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testPrivateTemplateHasPrivateJsDocAnnotationInGencode() {
    jsSrcOptions.setShouldGenerateJsdoc(true);

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo visibility=\"private\"}\n"
            + "  Blah\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {(null|undefined)=} opt_ignored\n"
            + " * @param {Object<string, *>=} opt_ijData\n"
            + " * @return {string}\n"
            + " * @suppress {checkTypes}\n"
            + " * @private\n"
            + " */\n"
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return 'Blah';\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testLegacyPrivateTemplateDoesNotHavePrivateJsDocAnnotationInGencode() {
    jsSrcOptions.setShouldGenerateJsdoc(true);

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** Test template. */\n"
            + "{template .goo private=\"true\"}\n"
            + "  Blah\n"
            + "{/template}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent).parse().fileSet());

    String expectedJsCode =
        ""
            + "/**\n"
            + " * @param {Object<string, *>=} opt_data\n"
            + " * @param {(null|undefined)=} opt_ignored\n"
            + " * @param {Object<string, *>=} opt_ijData\n"
            + " * @return {string}\n"
            + " * @suppress {checkTypes}\n"
            + " */\n"
            + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return 'Blah';\n"
            + "};\n"
            + "if (goog.DEBUG) {\n"
            + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  @Test
  public void testGoogModuleGeneration() {
    jsSrcOptions.setShouldDeclareTopLevelNamespaces(false);
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
            + "goog.require('soydata.VERY_UNSAFE');\n"
            + "var $import1 = goog.require('boo.bar');\n"
            + "var $templateAlias1 = $import1.one;\n"
            + "var $templateAlias2 = $import1.two;\n"
            + "\n"
            + "\n"
            + "function $goo(opt_data, opt_ignored, opt_ijData) {\n"
            + "  return soydata.VERY_UNSAFE.ordainSanitizedHtml("
            + "$templateAlias1(null, null, opt_ijData) + "
            + "$templateAlias2(null, null, opt_ijData));\n"
            + "}\n"
            + "exports.goo = $goo;\n"
            + "if (goog.DEBUG) {\n"
            + "  $goo.soyTemplateName = 'boo.foo.goo';\n"
            + "}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    assertThat(
        genJsCodeVisitor
            .gen(parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get())
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
    String genCode = getGeneratedJsCode(soyCode, ExplodingErrorReporter.get());
    assertThat(genCode).isEqualTo(expectedJsCode);
  }

  /**
   * Asserts that a soy code throws a SoySyntaxException.
   *
   * @param soyCode The invalid Soy code.
   * @param expectedErrorMsg If not null, this is checked against the exception message.
   */
  private void assertFailsInGeneratingJsCode(String soyCode, @Nullable String expectedErrorMsg) {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    String genCode = getGeneratedJsCode(soyCode, errorReporter);
    if (errorReporter.getErrorMessages().isEmpty()) {
      throw new AssertionError(
          "Expected:\n" + soyCode + "\n to fail. But instead generated:\n" + genCode);
    }
    if (expectedErrorMsg != null && !errorReporter.getErrorMessages().contains(expectedErrorMsg)) {
      throw new AssertionError(
          "Expected:\n"
              + soyCode
              + "\n to fail with error:\""
              + expectedErrorMsg
              + "\". But instead failed with:"
              + errorReporter.getErrorMessages());
    }
  }

  /**
   * Generates JavaScript code from the given soy code.
   *
   * @param soyCode The Soy code.
   */
  private String getGeneratedJsCode(String soyCode, ErrorReporter errorReporter) {
    Checkpoint checkPoint = errorReporter.checkpoint();
    ParseResult parseResult =
        SoyFileSetParserBuilder.forTemplateContents(soyCode)
            .errorReporter(errorReporter)
            .allowUnboundGlobals(true)
            .parse();
    new ExtractMsgVariablesVisitor().exec(parseResult.fileSet());
    if (errorReporter.errorsSince(checkPoint)) {
      return null;
    }
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
        INJECTOR
            .getInstance(GenJsExprsVisitorFactory.class)
            .create(translationContext, TEMPLATE_ALIASES, errorReporter);
    genJsCodeVisitor.assistantForMsgs = null; // will be created when used

    for (SoyNode child : templateNode.getChildren()) {
      genJsCodeVisitor.visitForTesting(child, errorReporter);
    }

    return genJsCodeVisitor.jsCodeBuilder.getCode();
  }

  private ImmutableSet<String> getRequiredSymbols(String soyFile) {
    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(soyFile).parse();

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    jsSrcOptions.setShouldGenerateJsdoc(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    Pattern googRequire = Pattern.compile("goog.require\\('(.*)'\\);");
    ImmutableSet.Builder<String> requires = ImmutableSet.builder();
    for (String file : jsFilesContents) {
      Matcher matcher = googRequire.matcher(file);
      while (matcher.find()) {
        requires.add(matcher.group(1));
      }
    }
    return requires.build();
  }
}
