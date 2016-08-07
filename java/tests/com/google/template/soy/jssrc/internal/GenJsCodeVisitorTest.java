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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.SoyModule;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyObjectType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.primitive.StringType;

import junit.framework.TestCase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Unit tests for {@link GenJsCodeVisitor}.
 *
 */
public final class GenJsCodeVisitorTest extends TestCase {
  private static final Injector INJECTOR = Guice.createInjector(new SoyModule());

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


  @Override protected void setUp() {
    jsSrcOptions = new SoyJsSrcOptions();
    JsSrcTestUtils.simulateNewApiCall(INJECTOR, jsSrcOptions);
    genJsCodeVisitor = INJECTOR.getInstance(GenJsCodeVisitor.class);
    genJsCodeVisitor.templateAliases = TEMPLATE_ALIASES;
  }


  public void testSoyFile() {

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
        + "\n"
        + "/** Test template. */\n"
        + "{template .goo}\n"
        + "  {call .goo data=\"all\" /}\n"
        + "  {call boo.woo.hoo data=\"all\" /}\n" +  // not defined in this file
        "{/template}\n";

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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "goog.require('boo.woo');\n"
        + "\n"
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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "goog.require('boo.woo.hoo');\n"
        + "\n"
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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "goog.require('boo.woo');\n"
        + "\n"
        + "\n";

    jsSrcOptions.setShouldProvideRequireJsFunctions(false);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(true);
    jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "goog.require('boo.woo');\n"
        + "goog.require('boo.woo.aaa');\n"
        + "\n"
        + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "goog.require('also.for.function');\n"
        + "goog.require('for.function');\n"
        + "\n"
        + "\n";

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).startsWith(expectedJsFileContentStart);
  }

  public void testSoyFileWithRequirecssOnNamespace() {

    String testFileContent = ""
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

    String expectedJsFileContentStart = ""
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


  public void testSoyFileNoNamespaceWithProvideNamespaceOption() {

    String testFileContent =
        "/** Test template. */\n"
        + "{template boo.foo.goo autoescape=\"deprecated-noncontextual\" deprecatedV1=\"true\"}\n"
        + "  {call boo.foo.goo data=\"all\" /}\n"
        + "  {call boo.woo.hoo data=\"all\" /}\n" +  // not defined in this file
        "{/template}\n";

    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .declaredSyntaxVersion(SyntaxVersion.V1_0)
            .parse();

    // ------ Using Closure, provide both Soy namespaces and JS functions ------
    String expectedJsFileContentStart =
        "// This file was automatically generated from no-path.\n"
        + "// Please don't edit this file by hand.\n"
        + "\n"
        + "/**\n"
        + " * @fileoverview\n"
        + " * @public\n"
        + " */\n"
        + "\n"
        + "goog.provide('boo.foo.goo');\n"
        + "\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "goog.require('boo.woo.hoo');\n"
        + "\n"
        + "\n";

    jsSrcOptions.setShouldProvideRequireJsFunctions(true);
    jsSrcOptions.setShouldProvideRequireSoyNamespaces(false);
    jsSrcOptions.setShouldProvideBothSoyNamespacesAndJsFunctions(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0))
        .startsWith(expectedJsFileContentStart);
  }


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

    String expectedJsFileContent = ""
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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
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


  public void testDelegateVariantProvideRequiresJsDocAnnotations() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
        + "\n"
        + "/** Test delegate template. */\n"
        + "{deltemplate myDelegates.goo variant=\"'googoo'\"}\n"
        + "  {delcall myDelegates.moo variant=\"'moomoo'\" /}\n"
        + "{/deltemplate}\n";

    ParseResult parse = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();
    String expectedJsFileContent = ""
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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    // ------ Code style 'concat' ------
    String expectedJsCode = ""
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  var output = '';\n"
        + "  var moo__soy3 = 90;\n"
        + "  output += moo__soy3 + opt_ijData.moo;\n"
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    // ------ Code style 'concat' with shouldGenerateJsdoc ------
    String expectedJsCode = ""
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    // ------ Code style 'concat' with shouldGenerateJsdoc ------
    String expectedJsCode = ""
        + "/**\n"
        + " * @param {Object<string, *>=} opt_data\n"
        + " * @param {(null|undefined)=} opt_ignored\n"
        + " * @param {Object<string, *>=} opt_ijData\n"
        + " * @return {!soydata.SanitizedJs}\n"
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


  public void testDelTemplate() {

    String testFileContent = ""
        + // note: no delpackage => priority 0
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
        + "\n"
        + "/** Test delegate template. */\n"
        + "{deltemplate myDelegates.goo}\n"
        + "  Blah\n"
        + "{/deltemplate}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    // ------ Code style 'concat'. ------
    String expectedJsCode = ""
        + "boo.foo.__deltemplate_s2_ad618961 = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  return 'Blah';\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.__deltemplate_s2_ad618961.soyTemplateName = "
        + "'boo.foo.__deltemplate_s2_ad618961';\n"
        + "}\n"
        + "soy.$$registerDelegateFn(soy.$$getDelTemplateId('myDelegates.goo'), '', 0,"
        + " boo.foo.__deltemplate_s2_ad618961);\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }


  public void testDelTemplateWithVariant() {

    String testFileContent = ""
        + "{delpackage MySecretFeature}\n" +  // note: delpackage => priority 1
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
        + "\n"
        + "/** Test delegate template with variant. */\n"
        + "{deltemplate myDelegates.goo variant=\"'moo'\"}\n"
        + "  Blah\n"
        + "{/deltemplate}\n";

    TemplateNode template =
        (TemplateNode)
            SharedTestUtils.getNode(
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    // ------ Code style 'concat'. ------
    String expectedJsCode = ""
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


  public void testRawText() {
    assertGeneratedJsCode(
        "I'm feeling lucky!\n",
        "output += 'I\\'m feeling lucky!';\n");

    assertGeneratedJsCode(
        "{lb}^_^{rb}{sp}{\\n}\n",
        "output += '{^_^} \\n';\n");
  }


  public void testGoogMsg() {

    String soyCode =
        "{@param user : ?}\n"
            + "{@param url : ?}\n"
            + "{msg desc=\"Tells the user to click a link.\"}\n"
            + "  Hello {$user.userName}, please click <a href=\"{$url}\">here</a>.\n"
            + "{/msg}\n";
    String expectedJsCode = ""
        + "/** @desc Tells the user to click a link. */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    'Hello {$userName}, please click {$startLink}here{$endLink}.',\n"
        + "    {'userName': opt_data.user.userName,\n"
        + "     'startLink': '<a href=\"' + opt_data.url + '\">',\n"
        + "     'endLink': '</a>'});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    soyCode =
        "{msg meaning=\"boo\" desc=\"foo\" hidden=\"true\"}\n"
        + "  Blah\n"
        + "{/msg}\n";
    expectedJsCode = ""
        + "/** @meaning boo\n"
        + " *  @desc foo\n"
        + " *  @hidden */\n"
        + "var MSG_UNNAMED_### = goog.getMsg('Blah');\n";
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
    expectedJsCode = ""
        + "var htmlTag11 = '<span id=\"';\n"
        + "for (var i6 = 0; i6 < 3; i6++) {\n"
        + "  htmlTag11 += i6;\n"
        + "}\n"
        + "htmlTag11 += '\">';\n"
        + "var param13 = '';\n"
        + "for (var i14 = 0; i14 < 4; i14++) {\n"
        + "  param13 += i14;\n"
        + "}\n"
        + "/** @desc A span with generated id. */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$startSpan}{$xxx_1}{$xxx_2}',\n"
        + "    {'startSpan': htmlTag11,\n"
        + "     'xxx_1': some.func(soy.$$assignDefaults({goo: param13}, opt_data.boo), "
        + "null, opt_ijData),\n"
        + "     'xxx_2': opt_data.a + 2});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    soyCode =
        "{msg desc=\"foo\"}\n"
        + "  More \u00BB\n"
        + "{/msg}\n";
    // Make sure JS code doesn't have literal unicode characters, since they
    // don't always get interpreted properly.
    expectedJsCode = ""
        + "/** @desc foo */\n"
        + "var MSG_UNNAMED_### = goog.getMsg('More \\u00BB');\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }


  public void testGoogMsgWithFallback() {

    String soyCode =
        "{msg meaning=\"verb\" desc=\"Used as a verb.\"}\n"
        + "  Archive\n"
        + "{fallbackmsg desc=\"\"}\n"
        + "  Archive\n"
        + "{/msg}\n";
    String expectedJsCode = ""
        + "/** @meaning verb\n"
        + " *  @desc Used as a verb. */\n"
        + "var MSG_UNNAMED_4 = goog.getMsg('Archive');\n"
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_6 = goog.getMsg('Archive');\n"
        + "var msg_s3 = goog.getMsgWithFallback(MSG_UNNAMED_4, MSG_UNNAMED_6);\n";
    // Note: Using getGeneratedJsCode() directly so that ids are not replaced with ###.
    assertThat(getGeneratedJsCode(soyCode, ExplodingErrorReporter.get())).isEqualTo(expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    'Unable to reach {$productNameHtml}. Eeeek!',\n"
        + "    {'productNameHtml': PRODUCT_NAME_HTML});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, all caps, leading or trailing underbars.  Placeholder should
    // be lower-case camel case version
    soyCode =
        "{msg desc=\"\"}\n"
        + "{window.field}{window._AField}{_window_.forest}{window.size.x}"
        + "{window.size._xx_xx_}\n"
        + "{/msg}\n";
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$field}{$aField}{$forest}{$x}{$xxXx}',\n"
        + "    {'field': window.field,\n"
        + "     'aField': window._AField,\n"
        + "     'forest': _window_.forest,\n"
        + "     'x': window.size.x,\n"
        + "     'xxXx': window.size._xx_xx_});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, property name.  Placeholder should be lower case
    // camel case version of last component.
    soyCode =
        "{msg desc=\"\"}\n"
        + "{window.FOO.BAR} {window.ORIGINAL_SERVER_NAME}\n"
        + "{/msg}\n";
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$bar} {$originalServerName}',\n"
        + "    {'bar': window.FOO.BAR,\n"
        + "     'originalServerName': window.ORIGINAL_SERVER_NAME});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, camel case name.  Placeholder should be same.
    soyCode =
        "{msg desc=\"\"}\n"
        + " {camelCaseName}{global.camelCase}. \n"
        + "{/msg}\n";
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$camelCaseName}{$camelCase}.',\n"
        + "    {'camelCaseName': camelCaseName,\n"
        + "     'camelCase': global.camelCase});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    //  Upper case camel case name becomes lower case in placeholder.
    soyCode =
        "{msg desc=\"\"}\n"
        + "Unable to reach {CamelCaseName}. Eeeek!\n"
        + "{/msg}\n";
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    'Unable to reach {$camelCaseName}. Eeeek!',\n"
        + "    {'camelCaseName': CamelCaseName});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // Leading and trailing underbars are stripped when creating placeholders.
    soyCode =
        "{msg desc=\"Not actually shown to the user.\"}\n"
        + "{_underbar} {_wunderBar_}\n"
        + "{_ThunderBar_} {underCar__}\n"
        + "{window.__car__}{window.__AnotherBar__}{/msg}\n";
    expectedJsCode = ""
        + "/** @desc Not actually shown to the user. */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$underbar} {$wunderBar}{$thunderBar}"
        + " {$underCar}{$car}{$anotherBar}',\n"
        + "    {'underbar': _underbar,\n"
        + "     'wunderBar': _wunderBar_,\n"
        + "     'thunderBar': _ThunderBar_,\n"
        + "     'underCar': underCar__,\n"
        + "     'car': window.__car__,\n"
        + "     'anotherBar': window.__AnotherBar__});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    'Unable to reach {$productNameHtml}. Eeeek!',\n"
        + "    {'productNameHtml': opt_data.PRODUCT_NAME_HTML});\n";
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
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$bar}{$originalServer}{$xxXx}',\n"
        + "    {'bar': opt_data.myvar.foo.bar,\n"
        + "     'originalServer': opt_data.myvar.ORIGINAL_SERVER,\n"
        + "     'xxXx': opt_data.window.size._xx_xx_});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // global, property name, with underbars.  Placeholder should be lower case
    // camel case version of last component, with underbars stripped.
    soyCode =
        "{@param myvar : ?}\n"
            + "{msg desc=\"\"}\n"
            + "{$myvar.foo._bar}{$myvar.foo.trail_}{$myvar.foo._bar_bar_bar_}\n"
            + "{/msg}\n";
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$bar}{$trail}{$barBarBar}',\n"
        + "    {'bar': opt_data.myvar.foo._bar,\n"
        + "     'trail': opt_data.myvar.foo.trail_,\n"
        + "     'barBarBar': opt_data.myvar.foo._bar_bar_bar_});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);

    // local, camel case name.  Placeholder should be same, in lower case.
    soyCode =
        "{@param productName : ?}\n"
            + "{@param OtherProductName : ?}\n"
            + "{msg desc=\"\"}\n"
            + " {$productName}{$OtherProductName}\n"
            + "{/msg}\n";
    expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    '{$productName}{$otherProductName}',\n"
        + "    {'productName': opt_data.productName,\n"
        + "     'otherProductName': opt_data.OtherProductName});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }


  public void testPrintGoogMsg() {

    String soyCode =
        "{@param userName : ?}\n"
            + "{@param url : ?}\n"
            + "{msg desc=\"Tells the user to click a link.\"}\n"
            + "  Hello {$userName}, please click <a href=\"{$url}\">here</a>.\n"
            + "{/msg}\n";
    String expectedJsCode = ""
        + "/** @desc Tells the user to click a link. */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    'Hello {$userName}, please click {$startLink}here{$endLink}.',\n"
        + "    {'userName': opt_data.userName,\n"
        + "     'startLink': '<a href=\"' + opt_data.url + '\">',\n"
        + "     'endLink': '</a>'});\n";
    assertGeneratedJsCode(soyCode, expectedJsCode);
  }


  public void testPrint() {
    assertGeneratedJsCode("{@param boo : ?}\n{$boo.foo}\n", "output += opt_data.boo.foo;\n");
  }


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

    String expectedJsCode = ""
        + "if (opt_data.boo) {\n"
        + "  var alpha__soy5 = opt_data.boo.foo;\n"
        + "  var beta__soy6 = 'Boo!';\n"
        + "  var gamma__soy8 = '';\n"
        + "  var iLimit9 = alpha__soy5;\n"
        + "  for (var i9 = 0; i9 < iLimit9; i9++) {\n"
        + "    gamma__soy8 += i9 + beta__soy6;\n"
        + "  }\n"
        + "  var delta__soy12 = 'Boop!';\n"
        + "  delta__soy12 = "
        + "soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks(delta__soy12);\n"
        + "  output += alpha__soy5 + beta__soy6 + gamma__soy8 + delta__soy12;\n"
        + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


  public void testIf() {
    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{if $boo}\n"
            + "  Blah\n"
            + "{elseif not strContains($goo, 'goo')}\n"
            + "  Bleh\n"
            + "{else}\n"
            + "  Bluh\n"
            + "{/if}\n";
    String expectedJsCode = ""
        + "output += (opt_data.boo) ? 'Blah' : "
        + "(! (('' + gooData8).indexOf('goo') != -1)) ? 'Bleh' : 'Bluh';\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{if $boo.foo > 0}\n"
            + "  {for $i in range(4)}\n"
            + "    {$i+1}<br>\n"
            + "  {/for}\n"
            + "{elseif not strContains($goo, 'goo')}\n"
            + "  Bleh\n"
            + "{else}\n"
            + "  Bluh\n"
            + "{/if}\n";
    expectedJsCode = ""
        + "if (opt_data.boo.foo > 0) {\n"
        + "  for (var i5 = 0; i5 < 4; i5++) {\n"
        + "    output += i5 + 1 + '<br>';\n"
        + "  }\n"
        + "} else if (! (('' + gooData8).indexOf('goo') != -1)) {\n"
        + "  output += 'Bleh';\n"
        + "} else {\n"
        + "  output += 'Bluh';\n"
        + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "switch ((goog.isObject($$temp = opt_data.boo)) ? $$temp.toString() : $$temp) {\n"
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

  public void testSwitch_withNullCoalescing() {
    String soyNodeCode =
        "{@param alpha : ?}\n"
            + "{@param beta : ?}\n"
            + "{switch $alpha ?: $beta}\n"
            + "  {default}\n"
            + "    Bluh\n"
            + "{/switch}\n";
    String expectedJsCode = ""
        + "switch ((goog.isObject("
        + "$$temp = ($$temp = opt_data.alpha) == null ? opt_data.beta : $$temp)) "
        + "? $$temp.toString() : $$temp) {\n"
        + "  default:\n"
        + "    output += 'Bluh';\n"
        + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


  public void testForeach() {
    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{foreach $foo in $boo.foos}\n"
            + "  {if not isFirst($foo)}\n"
            + "    <br>\n"
            + "  {/if}\n"
            + "  {$foo}s are fools.\n"
            + "{ifempty}\n"
            + "  No fools here.\n"
            + "{/foreach}\n";
    String expectedJsCode = ""
        + "var fooList9 = opt_data.boo.foos;\n"
        + "var fooListLen9 = fooList9.length;\n"
        + "if (fooListLen9 > 0) {\n"
        + "  for (var fooIndex9 = 0; fooIndex9 < fooListLen9; fooIndex9++) {\n"
        + "    var fooData9 = fooList9[fooIndex9];\n"
        + "    output += ((! (fooIndex9 == 0)) ? '<br>' : '') + fooData9 + 's are fools.';\n"
        + "  }\n"
        + "} else {\n"
        + "  output += 'No fools here.';\n"
        + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


  public void testFor() {

    String soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{for $i in range(8, 16, 2)}\n"
            + "  {$boo[$i] + $goo[$i]}\n"
            + "{/for}\n";
    String expectedJsCode = ""
        + "for (var i3 = 8; i3 < 16; i3 += 2) {\n"
        + "  output += opt_data.boo[i3] + gooData8[i3];\n"
        + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);

    soyNodeCode =
        "{@param boo : ?}\n"
            + "{@param goo : ?}\n"
            + "{@param foo : ?}\n"
            + "{for $i in range($boo-$goo, $boo+$goo, $foo)}\n"
            + "  {$i + 1}{sp}\n"
            + "{/for}\n";
    expectedJsCode = ""
        + "var iInit3 = opt_data.boo - gooData8;\n"
        + "var iLimit3 = opt_data.boo + gooData8;\n"
        + "var iIncrement3 = opt_data.foo;\n"
        + "for (var i3 = iInit3; i3 < iLimit3; i3 += iIncrement3) {\n"
        + "  output += i3 + 1 + ' ';\n"
        + "}\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "var param3 = '';\n"
        + "for (var i4 = 0; i4 < 7; i4++) {\n"
        + "  param3 += i4;\n"
        + "}\n"
        + "output += some.func(soy.$$assignDefaults({goo: param3}, opt_data.boo), null, "
        + "opt_ijData);\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "/** @desc A sample plural message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{NUM_PEOPLE,plural,offset:1 "
        +         "=0{I see no one in {PLACE}.}"
        +         "=1{I see {PERSON} in {PLACE}.}"
        +         "=2{I see {PERSON} and one other person in {PLACE}.}"
        +         "other{          I see {PERSON} and {XXX} other people in {PLACE}.}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'NUM_PEOPLE': opt_data.num_people,\n"
        + "     'PLACE': opt_data.place,\n"
        + "     'PERSON': opt_data.person,\n"
        + "     'XXX': opt_data.num_people - 1});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample plural message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{NUM_PEOPLE_1,plural,"
        +        "=0{I see no one in {PLACE}.}"
        +        "=1{I see {PERSON} in {PLACE}.}"
        +        "other{I see {NUM_PEOPLE_2} persons in {PLACE}, including {PERSON}.}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'NUM_PEOPLE_1': opt_data.num_people,\n"
        + "     'PLACE': opt_data.place,\n"
        + "     'PERSON': opt_data.person,\n"
        + "     'NUM_PEOPLE_2': opt_data.num_people});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample plural message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{NUM,plural,"
        +        "=0{I see no one in {PLACE}.}"
        +        "=1{I see {XXX_1} in {PLACE}.}"
        +        "other{          I see {XXX_2} persons in {PLACE}, including {XXX_1}.}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'NUM': opt_data.persons.length,\n"
        + "     'PLACE': opt_data.place,\n"
        + "     'XXX_1': opt_data.persons[0],\n"
        + "     'XXX_2': opt_data.persons.length});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample plural with offset */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{NUM_PEOPLE_1,plural,offset:2 "
        +        "=0{No people.}"
        +        "=1{There is one person: {XXX_1}.}"
        +        "=2{There are two persons: {XXX_1} and {XXX_2}.}"
        +        "other{There are {NUM_PEOPLE_2} persons: {XXX_1}, {XXX_2} and {XXX_3} others.}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'NUM_PEOPLE_1': opt_data.num_people,\n"
        + "     'XXX_1': opt_data.persons[0],\n"
        + "     'XXX_2': opt_data.persons[1],\n"
        + "     'NUM_PEOPLE_2': opt_data.num_people,\n"
        + "     'XXX_3': opt_data.num_people - 2});\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "/** @desc A sample gender message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{GENDER,select,"
        +         "female{{PERSON} added you to her circle.}"
        +         "other{{PERSON} added you to his circle.}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'GENDER': opt_data.gender,\n"
        + "     'PERSON': opt_data.person});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample gender message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{GENDER,select,"
        +        "female{{PERSON} added you to her circle.}"
        +        "male{{PERSON} added you to his circle.}"
        +        "neuter{{PERSON} added you to its circle.}"
        +        "other{{PERSON} added you to his circle.}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'GENDER': opt_data.gender,\n"
        + "     'PERSON': opt_data.person});\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "/** @desc A message with genders. */\n"
        + "var MSG_UNNAMED_4 = goog.getMsg("
        +     "'{USER_GENDER,select,"
        +         "female{{TARGET_GENDER,select,"
        +             "female{Join {TARGET_NAME}\\'s community.}"
        +             "male{Join {TARGET_NAME}\\'s community.}"
        +             "other{Join {TARGET_NAME}\\'s community.}"
        +         "}}"
        +         "male{{TARGET_GENDER,select,"
        +             "female{Join {TARGET_NAME}\\'s community.}"
        +             "male{Join {TARGET_NAME}\\'s community.}"
        +             "other{Join {TARGET_NAME}\\'s community.}"
        +         "}}"
        +         "other{{TARGET_GENDER,select,"
        +             "female{Join {TARGET_NAME}\\'s community.}"
        +             "male{Join {TARGET_NAME}\\'s community.}"
        +             "other{Join {TARGET_NAME}\\'s community.}"
        +         "}}"
        +     "}');\n"
        + "/** @desc A message without genders. */\n"
        + "var MSG_UNNAMED_9 = goog.getMsg(\n"
        + "    'Join {$targetName}\\'s community.',\n"
        + "    {'targetName': opt_data.targetName});\n"
        + "var msg_s3 = goog.getMsgWithFallback(MSG_UNNAMED_4, MSG_UNNAMED_9);\n"
        + "if (msg_s3 == MSG_UNNAMED_4) {\n"
        + "  msg_s3 = (new goog.i18n.MessageFormat(MSG_UNNAMED_4)).formatIgnoringPound(\n"
        + "      {'USER_GENDER': opt_data.userGender,\n"
        + "       'TARGET_GENDER': opt_data.targetGender,\n"
        + "       'TARGET_NAME': opt_data.targetName});\n"
        + "}\n";
    // Note: Using getGeneratedJsCode() directly so that ids are not replaced with ###.
    assertThat(getGeneratedJsCode(soyCode, ExplodingErrorReporter.get())).isEqualTo(expectedJsCode);
  }


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
    String expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{GENDER,select,"
        +        "female{"
        +          "{GENDER_2,select,"
        +            "female{{PERSON_1} added {PERSON_2} and her friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and his friends to her circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{GENDER_2,select,"
        +            "female{{PERSON_1} added {PERSON_2} and her friends to his circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and his friends to his circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'GENDER': opt_data.gender,\n"
        + "     'GENDER_2': opt_data.gender2,\n"
        + "     'PERSON_1': opt_data.person1,\n"
        + "     'PERSON_2': opt_data.person2});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{GENDER,select,"
        +        "female{"
        +          "{NUM_PEOPLE_1,plural,"
        +            "=1{{PERSON} added one person to her circle.}"
        +            "other{{PERSON} added {NUM_PEOPLE_2} to her circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{NUM_PEOPLE_1,plural,"
        +            "=1{{PERSON} added one person to his circle.}"
        +            "other{{PERSON} added {NUM_PEOPLE_2} to his circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'GENDER': opt_data.gender,\n"
        + "     'NUM_PEOPLE_1': opt_data.num_people,\n"
        + "     'PERSON': opt_data.person,\n"
        + "     'NUM_PEOPLE_2': opt_data.num_people});\n";
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
    String expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{FORMAT,select,"
        +        "user-centric{"
        +          "{GENDER_1,select,"
        +            "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
        +          "}"
        +        "}"
        +        "friend-centric{"
        +          "{GENDER_2,select,"
        +            "female{{PERSON_1} added {PERSON_2} and her friends to circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and his friends to circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{GENDER_1,select,"
        +            "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'FORMAT': opt_data.format,\n"
        + "     'GENDER_1': opt_data.user.gender,\n"
        + "     'GENDER_2': opt_data.friend.gender,\n"
        + "     'PERSON_1': opt_data.person1,\n"
        + "     'PERSON_2': opt_data.person2});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{FORMAT,select,"
        +        "user-centric{"
        +          "{USER,select,"
        +            "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
        +          "}"
        +        "}"
        +        "friend-centric{"
        +          "{FRIEND,select,"
        +            "female{{PERSON_1} added {PERSON_2} and her friends to circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and his friends to circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{USER,select,"
        +            "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'FORMAT': opt_data.format,\n"
        + "     'USER': opt_data.gender.user,\n"
        + "     'FRIEND': opt_data.gender.friend,\n"
        + "     'PERSON_1': opt_data.person1,\n"
        + "     'PERSON_2': opt_data.person2});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{FORMAT,select,"
        +        "user-centric{"
        +          "{STATUS_1,select,"
        +            "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
        +          "}"
        +        "}"
        +        "friend-centric{"
        +          "{STATUS_2,select,"
        +            "female{{PERSON_1} added {PERSON_2} and her friends to circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and his friends to circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{STATUS_1,select,"
        +            "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'FORMAT': opt_data.format,\n"
        + "     'STATUS_1': opt_data.gender[0],\n"
        + "     'STATUS_2': opt_data.gender[1],\n"
        + "     'PERSON_1': opt_data.person1,\n"
        + "     'PERSON_2': opt_data.person2});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{STATUS,select,"
        +        "female{"
        +          "{NUM_1,plural,"
        +            "=1{{PERSON} added one person to her circle.}"
        +            "other{{PERSON} added {XXX_1} to her circle.}"
        +          "}"
        +        "}"
        +        "male{"
        +          "{NUM_2,plural,"
        +            "=1{{PERSON} added one person to his circle.}"
        +            "other{{PERSON} added {XXX_2} to his circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{NUM_2,plural,"
        +            "=1{{PERSON} added one person to his/her circle.}"
        +            "other{{PERSON} added {XXX_2} to his/her circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'STATUS': opt_data.values.gender[0],\n"
        + "     'NUM_1': opt_data.values.people[0],\n"
        + "     'NUM_2': opt_data.values.people[1],\n"
        + "     'PERSON': opt_data.person,\n"
        + "     'XXX_1': opt_data.values.people[0],\n"
        + "     'XXX_2': opt_data.values.people[1]});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{PERSON_1,select,"
        +        "female{"
        +          "{PERSON_2,plural,"
        +            "=1{{PERSON_3} added one person to her circle.}"
        +             "other{{PERSON_4} added {PERSON_5} to her circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{PERSON_2,plural,"
        +            "=1{{PERSON_3} added one person to his/her circle.}"
        +            "other{{PERSON_4} added {PERSON_5} to his/her circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'PERSON_1': opt_data.gender.person,\n"
        + "     'PERSON_2': opt_data.number.person,\n"
        + "     'PERSON_3': opt_data.person,\n"
        + "     'PERSON_4': opt_data.user.person,\n"
        + "     'PERSON_5': opt_data.number.person});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{PERSON_1,select,"
        +        "female{"
        +          "{PERSON_2,plural,offset:1 "
        +            "=1{{PERSON_4} added one person to her circle.}"
        +            "other{{PERSON_5} added {XXX} people to her circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{PERSON_3,plural,"
        +            "=1{{PERSON_4} added one person to his/her circle.}"
        +            "other{{PERSON_5} added {PERSON_6} people to his/her circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'PERSON_1': opt_data.gender.person,\n"
        + "     'PERSON_2': opt_data.number.person,\n"
        + "     'PERSON_3': opt_data.number.person,\n"
        + "     'PERSON_4': opt_data.person,\n"
        + "     'PERSON_5': opt_data.user.person,\n"
        + "     'XXX': opt_data.number.person - 1,\n"
        + "     'PERSON_6': opt_data.number.person});\n";
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
    expectedJsCode = ""
        + "/** @desc A sample nested message */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{GENDER_1,select,"
        +        "female{"
        +          "{GENDER_1,select,"
        +            "female{{PERSON_1} added {PERSON_2} and friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and friends to his circle.}"
        +          "}"
        +        "}"
        +        "other{"
        +          "{GENDER_2,select,"
        +            "female{{PERSON_1} added {PERSON_2} and some friends to her circle.}"
        +            "other{{PERSON_1} added {PERSON_2} and some friends to his circle.}"
        +          "}"
        +        "}"
        +      "}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        +     "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'GENDER_1': opt_data.user.gender,\n"
        + "     'GENDER_2': opt_data.friend.gender,\n"
        + "     'PERSON_1': opt_data.person1,\n"
        + "     'PERSON_2': opt_data.person2});\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


  public void testMsgWithPlrselHtml() {

    String soyNodeCode =
        "{@param num : ?}\n"
            + "{msg desc=\"\"}\n"
            + "  Notify \n"
            + "  {sp}<span class=\"{css sharebox-id-email-number}\">{$num}</span>{sp}\n"
            + " people via email &rsaquo;"
            + "{/msg}\n";
    String expectedJsCode = ""
        + "/** @desc  */\n"
        + "var MSG_UNNAMED_### = goog.getMsg(\n"
        + "    'Notify {$startSpan}{$num}{$endSpan} people via email &rsaquo;',\n"
        + "    {'startSpan': '<span class=\"'"
        + " + goog.getCssName('sharebox-id-email-number') + '\">',\n"
        + "     'num': opt_data.num,\n"
        + "     'endSpan': '</span>'});\n";
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
    expectedJsCode = ""
        + "/** @desc [ICU Syntax] */\n"
        + "var MSG_UNNAMED_### = goog.getMsg("
        +     "'{NUM_1,plural,=0{      Notify people via email &rsaquo;}"
        + "=1{      Notify {START_SPAN_1}{NUM_2}{END_SPAN} person via email &rsaquo;}"
        + "other{Notify {START_SPAN_2}{NUM_2}{END_SPAN} people via email &rsaquo;}}');\n"
        + "var msg_s### = (new goog.i18n.MessageFormat("
        + "MSG_UNNAMED_###)).formatIgnoringPound(\n"
        + "    {'NUM_1': opt_data.num,\n"
        + "     'START_SPAN_1': '<span class=\"' + goog.getCssName('sharebox-id-email-number') "
        + "+ '\">',\n"
        + "     'NUM_2': opt_data.num,\n"
        + "     'END_SPAN': '</span>',\n"
        + "     'START_SPAN_2': '<span class=\"' + goog.getCssName('sharebox-id-email-number')"
        + " + '\">'});\n";
    assertGeneratedJsCode(soyNodeCode, expectedJsCode);
  }


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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    // ------ Code style 'concat' ------
    String expectedJsCode = ""
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
    assertThat(jsFilesContents.get(0)).contains(
        "@param {{\n *    moo: string,\n *    goo: (null|string|undefined)\n * }} opt_data");
  }

  public void testHeaderParamRequiresAsserts() {
    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
        + "\n"
        + "/** */\n"
        + "{template .goo}\n"
        + "  {@param moo : string}\n"
        + "  {$moo}\n"
        + "{/template}\n";

    ParseResult parseResult = SoyFileSetParserBuilder.forFileContents(testFileContent).parse();

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());

    // Ensure that the use of header params causes goog.asserts to be required.
    assertThat(jsFilesContents.get(0)).contains("goog.require('goog.asserts')");
  }

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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  var moo = goog.asserts.assertNumber(opt_data.moo, "
        + "\"expected parameter 'moo' of type int.\");\n"
        + "  return '' + moo;\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
        + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  soy.asserts.assertType(goog.isString(opt_data.moo) || "
        + "(opt_data.moo instanceof goog.soy.data.SanitizedContent), "
        + "'moo', "
        + "opt_data.moo, "
        + "'string|goog.soy.data.SanitizedContent'"
        + ");\n"
        + "  var moo = /** @type {string|goog.soy.data.SanitizedContent} */ (opt_data.moo);\n"
        + "  return '' + moo;\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
        + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  soy.asserts.assertType(goog.isBoolean(opt_data.moo) || opt_data.moo === 1 || "
        + "opt_data.moo === 0, "
        + "'moo', "
        + "opt_data.moo, "
        + "'boolean'"
        + ");\n"
        + "  var moo = /** @type {boolean} */ (!!opt_data.moo);\n"
        + "  soy.asserts.assertType(opt_data.noo == null || goog.isBoolean(opt_data.noo) || "
        + "opt_data.noo === 1 || opt_data.noo === 0, "
        + "'noo', "
        + "opt_data.noo, "
        + "'boolean|null|undefined'"
        + ");\n"
        + "  var noo = /** @type {boolean|null|undefined} */ (opt_data.noo);\n"
        + "  return '' + (moo ? 1 : 0) + (noo ? 1 : 0);\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
        + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  soy.asserts.assertType((opt_data.moo instanceof goog.soy.data.SanitizedContent) || "
        + "goog.isArray(opt_data.moo) || goog.isString(opt_data.moo), "
        + "'moo', "
        + "opt_data.moo, "
        + "'!Array<number>|string'"
        + ");\n"
        + "  var moo = /** @type {!Array<number>|string} */ (opt_data.moo);\n"
        + "  return '' + moo;\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
        + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  var param$export = goog.asserts.assertNumber(opt_data['export'], "
        + "\"expected parameter 'export' of type int.\");\n"
        + "  return '' + param$export;\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
        + "}\n";

    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    genJsCodeVisitor.visitForTesting(template, ExplodingErrorReporter.get());
    assertThat(genJsCodeVisitor.jsCodeBuilder.getCode()).isEqualTo(expectedJsCode);
  }

  public void testHeaderParamFieldImport() {

    // Fake type provider which specifies an import symbol for access to the
    // field named 'bar'.
    final SoyObjectType exampleType = new SoyObjectType() {

      @Override public Kind getKind() {
        return SoyType.Kind.OBJECT;
      }

      @Override public boolean isAssignableFrom(SoyType srcType) {
        return false;
      }

      @Override public boolean isInstance(SoyValue value) {
        return false;
      }

      @Override public String getName() {
        return "example.type";
      }

      @Override public String getNameForBackend(SoyBackendKind backend) {
        return "js.example.type";
      }

      @Override public SoyType getFieldType(String fieldName) {
        if (fieldName.equals("bar")) {
          return StringType.getInstance();
        }
        return null;
      }

      @Override
      public ImmutableSet<String> getFieldNames() {
        return ImmutableSet.of("bar");
      }

      @Override public String getFieldAccessExpr(
          String fieldExpr, String fieldName, SoyBackendKind backend) {
        if (fieldName.equals("bar")) {
          return fieldExpr + ".getBar()";
        }
        return null;
      }

      @Override public ImmutableSet<String> getFieldAccessImports(
          String fieldName, SoyBackendKind backend) {
        if (fieldName.equals("bar")) {
          return ImmutableSet.of("example.type.field.bar");
        }
        return ImmutableSet.of();
      }
    };
    SoyTypeRegistry typeRegistry = new SoyTypeRegistry(ImmutableSet.<SoyTypeProvider>of(
        new SoyTypeProvider() {
          @Override
          public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
            if (typeName.equals("example.type")) {
              return exampleType;
            }
            return null;
          }}));

    String testFileContent =
        "{namespace boo.foo autoescape=\"deprecated-noncontextual\"}\n"
        + "\n"
        + "/** */\n"
        + "{template .goo}\n"
        + "  {@param moo : example.type}\n"
        + "  {$moo.bar}\n"
        + "{/template}\n";

    ParseResult parseResult =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .declaredSyntaxVersion(SyntaxVersion.V2_0)
            .typeRegistry(typeRegistry)
            .parse();

    jsSrcOptions.setShouldProvideRequireSoyNamespaces(true);
    // Setup the GenJsCodeVisitor's state before the template is visited.
    genJsCodeVisitor.jsCodeBuilder = new JsCodeBuilder();

    // Verify that the import symbol got required.
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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('goog.asserts');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy.asserts');\n"
        + "\n"
        + "goog.require('example.type.field.bar');\n"
        + "goog.require('js.example.type');\n"
        + "\n"
        + "\n"
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  var moo = goog.asserts.assertInstanceof(opt_data.moo, js.example.type, "
        + "\"expected parameter 'moo' of type js.example.type.\");\n"
        + "  return '' + moo.getBar();\n"
        + "};\n"
        + "if (goog.DEBUG) {\n"
        + "  boo.foo.goo.soyTemplateName = 'boo.foo.goo';\n"
        + "}\n";

    List<String> jsFilesContents =
        genJsCodeVisitor.gen(
            parseResult.fileSet(), parseResult.registry(), ExplodingErrorReporter.get());
    assertThat(jsFilesContents.get(0)).isEqualTo(expectedJsFileContentStart);
  }

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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
        + "boo.foo.goo = function(opt_data, opt_ignored, opt_ijData) {\n"
        + "  soy.asserts.assertType(goog.isString(opt_ijData.moo) || "
        + "(opt_ijData.moo instanceof goog.soy.data.SanitizedContent), "
        + "'moo', "
        + "opt_ijData.moo, "
        + "'string|goog.soy.data.SanitizedContent'"
        + ");\n"
        + "  var moo = /** @type {string|goog.soy.data.SanitizedContent} */ (opt_ijData.moo);\n"
        + "  return '' + moo;\n"
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
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
                SoyFileSetParserBuilder.forFileContents(testFileContent)
                    .parse()
                    .fileSet());

    String expectedJsCode = ""
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

  public void testGoogModuleGeneration() {
    jsSrcOptions.setShouldDeclareTopLevelNamespaces(false);
    jsSrcOptions.setShouldGenerateGoogModules(true);

    String testFileContent = ""
        + "{namespace boo.foo}\n"
        + "\n"
        + "/** Test template. */\n"
        + "{template .goo}\n"
        + "  {call boo.bar.one /}\n"
        + "  {call boo.bar.two /}\n"
        + "{/template}\n";

    String expectedJsCode = ""
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
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soy');\n"
        + "/** @suppress {extraRequire} */\n"
        + "goog.require('soydata');\n"
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
    // replace msg ids for ease of matching
    String genCode =
        getGeneratedJsCode(soyCode, ExplodingErrorReporter.get())
            .replaceAll("MSG_UNNAMED_[0-9]+", "MSG_UNNAMED_###")  // goog.getMsg() variable.
            .replaceAll("msg_[0-9]+__soy[0-9]+", "msg_###__soy###")  // Wrapper {let} variable.
            .replaceAll("msg_s[0-9]+", "msg_s###");  // Temporary variable for fallback call.
    assertThat(genCode).isEqualTo(expectedJsCode);
  }

  /**
   * Asserts that a soy code throws a SoySyntaxException.
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
    SoyNode node = SharedTestUtils.getNode(parseResult.fileSet(), 0);

    // Setup the GenJsCodeVisitor's state before the node is visited.
    JsCodeBuilder jsCodeBuilder = new JsCodeBuilder();
    genJsCodeVisitor.jsCodeBuilder = jsCodeBuilder;
    genJsCodeVisitor.highLevelJsCodeBuilder = new HighLevelJsCodeBuilderImpl(jsCodeBuilder);
    genJsCodeVisitor.jsCodeBuilder.pushOutputVar("output");
    genJsCodeVisitor.jsCodeBuilder.setOutputVarInited();
    genJsCodeVisitor.localVarTranslations = LOCAL_VAR_TRANSLATIONS;
    genJsCodeVisitor.genJsExprsVisitor =
        INJECTOR
            .getInstance(GenJsExprsVisitorFactory.class)
            .create(LOCAL_VAR_TRANSLATIONS, TEMPLATE_ALIASES, errorReporter);
    genJsCodeVisitor.assistantForMsgs = null;  // will be created when used

    genJsCodeVisitor.visitForTesting(node, errorReporter);

    return genJsCodeVisitor.jsCodeBuilder.getCode();
  }
}
