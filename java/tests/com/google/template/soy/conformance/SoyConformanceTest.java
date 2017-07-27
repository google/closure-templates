/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.conformance;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.StableSoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test case for {@link SoyConformance}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@RunWith(JUnit4.class)
public class SoyConformanceTest {

  private static final String FOO_BANNED_EVERYWHERE_EXCEPT_COMMENTS =
      "requirement: {\n"
          + "  banned_text_everywhere_except_comments: {\n"
          + "    text: 'foo'\n"
          + "  }\n"
          + "  error_message: 'foo'"
          + "}";

  @Test
  public void testBannedFunction() {
    assertViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'quoteKeysIfJs'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .foo}\n"
            + "{quoteKeysIfJs(['xxx': 'bar', 'yyy': 'baz'])}\n"
            + "{/template}");
  }

  @Test
  public void testTemplateWithSameNameAsBannedFunction() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function: {\n"
            + "    function: 'quoteKeysIfJs'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .quoteKeysIfJs}\n"
            + "This should be allowed.\n"
            + "{/template}");
  }

  @Test
  public void testBannedPrintDirective() {
    assertViolation(
        "requirement: {\n"
            + "  banned_directive: {\n"
            + "    directive: '|noAutoescape'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .foo}\n"
            + "{print 'blah' |noAutoescape}\n"
            + "{/template}");
  }

  @Test
  public void testTemplateWithSameNameAsBannedPrintDirective() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_directive: {\n"
            + "    directive: '|noAutoescape'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .noAutoescape}\n"
            + "This should be allowed.\n"
            + "{/template}");
  }

  @Test
  public void testBannedCommandText() {
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector: {\n"
            + "    selector: 'goog-inline-block'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .foo}\n"
            + "{css goog-inline-block}\n"
            + "{/template}\n");
  }

  @Test
  public void testBannedCommandTextInNonTargetedCommand() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_css_selector: {\n"
            + "    selector: 'goog-inline-block'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .foo}\n"
            + "{print 'goog-inline-block'}\n"
            + "{/template}\n");
  }

  @Test
  public void testSoyDocParamsCauseErrorWhenSoyDocParamsAreBanned() {
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.NoSoyDocParams'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "/** @param foo */\n"
            + "{template .bar}\n"
            + "  {$foo}\n"
            + "{/template}\n");
  }

  @Test
  public void testHeaderParamsDoNotCauseErrorWhenSoyDocParamsAreBanned() {
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.NoSoyDocParams'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .bar}\n"
            + "  {@param foo: string}\n"
            + "  {$foo}\n"
            + "{/template}\n");
  }

  @Test
  public void testMixedParamsCauseErrorWhenSoyDocParamsAreBanned() {
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.NoSoyDocParams'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "/** @param foo */\n"
            + "{template .bar}\n"
            + "  {@param baz: string}\n"
            + "  {$foo}{$baz}\n"
            + "{/template}\n");
  }

  @Test
  public void testWhitelistedFileDoesNotCauseErrors() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'quoteKeysIfJs'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'foo/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                    + "{template .foo}\n"
                    + "{quoteKeysIfJs(['xxx': 'bar', 'yyy': 'baz'])}\n"
                    + "{/template}"),
            SoyFileKind.SRC,
            "foo/bar/baz.soy"));
  }

  @Test
  public void testWhitelistEntriesAreSubstrings() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'quoteKeysIfJs'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'c/foo/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                    + "{template .foo}\n"
                    + "{quoteKeysIfJs(['xxx': 'bar', 'yyy': 'baz'])}\n"
                    + "{/template}"),
            SoyFileKind.SRC,
            "a/b/c/foo/bar/baz.soy"));
  }

  @Test
  public void testWhitelistedSubstringsAreContiguous() {
    assertViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'quoteKeysIfJs'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'foo/c/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
                    + "{template .foo}\n"
                    + "{quoteKeysIfJs(['xxx': 'bar', 'yyy': 'baz'])}\n"
                    + "{/template}"),
            SoyFileKind.SRC,
            "a/b/c/foo/bar/baz.soy"));
  }

  @Test
  public void testBannedTextEverywhereIncludesTemplateBody() {
    assertViolation(
        FOO_BANNED_EVERYWHERE_EXCEPT_COMMENTS,
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .bar}\n"
            + "foo\n"
            + "{/template}");
  }

  @Test
  public void testBannedTextEverywhereIncludesNamespace() {
    assertViolation(
        FOO_BANNED_EVERYWHERE_EXCEPT_COMMENTS,
        "{namespace foo autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .bar}\n"
            + "{/template}");
  }

  @Test
  public void testBannedTextEverywhereIncludesTemplateName() {
    assertViolation(
        FOO_BANNED_EVERYWHERE_EXCEPT_COMMENTS,
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "{template .foo}\n"
            + "{/template}");
  }

  @Test
  public void testBannedTextEverywhereIncludesBetweenComments() {
    assertViolation(
        FOO_BANNED_EVERYWHERE_EXCEPT_COMMENTS,
        "{namespace ns}\n" + "{template .bar}\n" + "/** */ foo /** */" + "{/template}");
  }

  // TODO(brndn): add more testsBannedTextEverywhereIncludesXXX tests.

  @Test
  public void testBannedTextEverywhereDoesNotIncludeComments() {
    assertNoViolation(
        FOO_BANNED_EVERYWHERE_EXCEPT_COMMENTS,
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "// foo.\n"
            + "/** foo */\n"
            + "/**\n"
            + " * foo\n"
            + " */\n"
            + "{template .bar}\n"
            + "Hello, world./** foo. */\n"
            + "/* foo foo foo */\n"
            + "{/template}\n"
            + "/**\n"
            + " * foo\n"
            + " */\n"
            + "{template .baz}\n"
            + "/** foo foo */"
            + "{/template}");
  }

  @Test
  public void testBannedTextEverywhereDoesNotInterpretInputAsRegex() {
    String noStars =
        "requirement: {\n"
            + "  banned_text_everywhere_except_comments: {\n"
            + "    text: '*'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}";
    assertViolation(noStars, "{namespace ns}\n" + "{template .foo}*{/template}\n");
  }

  @Test
  public void testBannedRawText() {
    assertViolation(
        "requirement: {\n"
            + "  banned_raw_text: {\n"
            + "    text: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n" + "{template .bar}\n" + "foo\n" + "{/template}");
  }

  @Test
  public void testBannedRawTextDoesNotIncludeTemplateNames() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_raw_text: {\n"
            + "    text: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n" + "{template .foo}\n" + "bar\n" + "{/template}");
  }

  @Test
  public void bannedHtmlTag() {
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .bar}\n"
            + "  <div>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagDifferentCase() {
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            // Tag name is case insensitive: Div will be converted to div
            + "    tag: 'Div'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .bar}\n"
            // Tag name is case insensitive.
            + "  <DIV>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagMultipleTags() {
    // Banned both iframe and script, using iframe.
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            // Tag name is case-insensitive
            + "    tag: 'IFRAME'\n"
            + "    tag: 'script'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .bar}\n"
            + "<iframe src='#'>test</iframe>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagNoViolation() {
    // Banned script, using iframe.
    assertNoViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'script'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .bar}\n"
            + "<iframe src='foo.js'>test</iframe>\n"
            + "{/template}");

    // Banned both iframe and script, using other tags.
    assertNoViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'iframe'\n"
            + "    tag: 'script'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .bar}\n"
            + "<div><p></p></div>\n"
            + "{/template}");
  }

  @Test
  public void testBanInlineEventHandlers() {
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .foo}\n"
            + "<div onclick='bar'></div>\n"
            + "{/template}");
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .foo}\n"
            + "<div ONCLICK='bar'></div>\n"
            + "{/template}");
  }

  @Test
  public void testRequireStrictHtml() {
    String config =
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.RequireStrictHtml'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}";
    assertNoViolation(
        config, "{namespace ns stricthtml=\"true\"}\n" + "{template .foo}\n" + "{/template}");
    assertViolation(
        config, "{namespace ns stricthtml=\"false\"}\n" + "{template .foo}\n" + "{/template}");
    assertViolation(config, "{namespace ns}\n" + "{template .foo}\n" + "{/template}");
    assertViolation(
        config,
        "{namespace ns stricthtml=\"true\"}\n"
            + "{template .foo stricthtml=\"false\"}\n"
            + "{/template}");
    assertNoViolation(
        config, "{namespace ns}\n" + "{template .foo kind=\"text\"}\n" + "{/template}");
  }

  // regression test, this used to not be detected since we only issued errors if the attribute was
  // defined inside a kind="attributes" block
  @Test
  public void testBanInlineEventHandlers_attributes() {
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template .foo kind=\"attributes\"}\n"
            + "ONCLICK='bar'\n"
            + "{/template}");
  }

  // regression test for a situation involving script tags, the old version wouldn't catch this but
  // i don't know why...i suspect a subtle bug in the way SlicedRawTextNodes are created
  @Test
  public void testBanInlineEventHandlers_script() {
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + " {@param jsUrl : ?}{@param callback : ?}\n"
            + "<script id=\"base-js\" src=\"{$jsUrl |blessStringAsTrustedResourceUrlForLegacy}\" "
            + "async onload=\"{$callback}\"></script>\n"
            + "{/template}");
  }

  @Test
  public void testBanInlineEventHandlersWhitelisting() {
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'foo/bar/baz.soy'"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template .foo}\n"
                    + "<script onload='foo();'></script>\n"
                    + "{/template}"),
            SoyFileKind.SRC,
            "foo/bar/baz.soy"));
  }

  // Regression test for a bug where we used to essentially ignore whitelisting if there were
  // multiple files in a compilation unit
  @Test
  public void testBanInlineEventHandlersIsTooAggressiveWithWhitelists() {
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'foo/bar/baz.soy'"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template .foo}\n"
                    + "<script onload='foo();'></script>\n"
                    + "{/template}"),
            SoyFileKind.SRC,
            "foo/bar/baz.soy"),
        new StableSoyFileSupplier(
            CharSource.wrap("{namespace ns}\n" + "{template .noViolation}{/template}"),
            SoyFileKind.SRC,
            "foo/bar/quux.soy"));
  }

  @Test
  public void testBanInlineEventHandlers_NotJs() {
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + " "
            + "}",
        "{namespace ns autoescape=\"strict\"}\n"
            + "{template .foo}\n"
            + "<div foo='bar'></div>\n"
            + "{/template}");
  }

  private void assertViolation(String textProto, String input) {
    ImmutableList<SoyError> violations = getViolations(textProto, input);
    assertThat(violations).hasSize(1);
  }

  private void assertViolation(String textProto, SoyFileSupplier... soyFileSuppliers) {
    ImmutableList<SoyError> violations = getViolations(textProto, soyFileSuppliers);
    assertThat(violations).hasSize(1);
  }

  private void assertNoViolation(String textProto, String input) {
    ImmutableList<SoyError> violations = getViolations(textProto, input);
    assertThat(violations).isEmpty();
  }

  private void assertNoViolation(String textProto, SoyFileSupplier... soyFileSuppliers) {
    ImmutableList<SoyError> violations = getViolations(textProto, soyFileSuppliers);
    assertThat(violations).isEmpty();
  }

  private ImmutableList<SoyError> getViolations(String textProto, SoyFileSupplier... suppliers) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forSuppliers(suppliers)
        .setConformanceConfig(parseConfigProto(textProto))
        .errorReporter(errorReporter)
        .parse();
    return errorReporter.getErrors();
  }

  private ValidatedConformanceConfig parseConfigProto(String textProto) {
    ConformanceConfig.Builder builder = ConformanceConfig.newBuilder();
    try {
      TextFormat.merge(textProto, builder);
    } catch (ParseException pe) {
      throw new RuntimeException(pe);
    }
    return ValidatedConformanceConfig.create(builder.build());
  }

  private ImmutableList<SoyError> getViolations(String textProto, String input) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(input)
        .setConformanceConfig(parseConfigProto(textProto))
        .errorReporter(errorReporter)
        .parse();
    return errorReporter.getErrors();
  }
}
