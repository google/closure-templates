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
import com.google.common.collect.Iterables;
import com.google.common.io.CharSource;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.StableSoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link SoyConformance}. */
@RunWith(JUnit4.class)
public class SoyConformanceTest {

  @Test
  public void testBannedFunction() {
    assertViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
            + "{/template}");
  }

  @Test
  public void testTemplateWithSameNameAsBannedFunction() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function: {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template checkNotNull}\n"
            + "This should be allowed.\n"
            + "{/template}");
  }

  @Test
  public void testBannedExtern() {
    assertViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: \"{ext} from 'path/to/externs.soy'\"\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "import {ext} from 'path/to/externs.soy';\n"
                    + "{template foo}\n"
                    + "  {ext()}\n"
                    + "{/template}"),
            SourceFilePath.forTest("foo/bar/baz.soy")),
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns.externs}\n"
                    + "{export extern ext: () => ?}\n"
                    + "  {jsimpl namespace=\"ns.functions\" function=\"fn2\" /}\n"
                    + "{/extern}"),
            SourceFilePath.forTest("path/to/externs.soy")));
  }

  @Test
  public void testBannedPrintDirective() {
    assertViolation(
        "requirement: {\n"
            + "  banned_directive: {\n"
            + "    directive: '|escapeUri'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "{print 'blah' |escapeUri}\n" + "{/template}");
  }

  @Test
  public void testTemplateWithSameNameAsBannedPrintDirective() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_directive: {\n"
            + "    directive: '|filterUri'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template filterUri}\n"
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
        "{namespace ns}\n" + "{template foo}\n" + "{css('goog-inline-block')}\n" + "{/template}\n");
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
        "{namespace ns}\n"
            + "{template foo}\n"
            + "{print 'goog-inline-block'}\n"
            + "{/template}\n");
  }

  @Test
  public void testBannedClassSelectorMdcPrefix() {
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector: {\n"
            + "    selector: 'mdc-'\n"
            + "    when_prefix: true\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "{css('mdc-button')}\n" + "{/template}\n");
  }

  @Test
  public void testBannedClassSelectorMdcSubstring() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_css_selector: {\n"
            + "    selector: 'mdc-'\n"
            + "    when_prefix: true\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "{css('other-prefix-mdc-button')}\n"
            + "{/template}\n");
  }

  @Test
  public void testExemptedFileDoesNotCauseErrors() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  exempt: 'foo/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template foo}\n"
                    + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
                    + "{/template}"),
            SourceFilePath.forTest("foo/bar/baz.soy")));
  }

  @Test
  public void testExemptedEntriesAreSubstrings() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  exempt: 'c/foo/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template foo}\n"
                    + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
                    + "{/template}"),
            SourceFilePath.forTest("a/b/c/foo/bar/baz.soy")));
  }

  @Test
  public void testExemptedSubstringsAreContiguous() {
    assertViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foooo'"
            + "  exempt: 'foo/c/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template foo}\n"
                    + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
                    + "{/template}"),
            SourceFilePath.forTest("a/b/c/foo/bar/baz.soy")));
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
        "{namespace ns}\n" + "{template bar}\n" + "foo\n" + "{/template}");
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
        "{namespace ns}\n" + "{template foo}\n" + "bar\n" + "{/template}");
  }

  @Test
  public void testBannedRawTextDoesNotIncludeComments() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_raw_text: {\n"
            + "    text: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<!-- foo -->\n" + "{/template}");
  }

  @Test
  public void testBannedRawTextIgnoresHtmlAttributes() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_raw_text: {\n"
            + "    text: 'foo'\n"
            + "    except_in_html_attribute: 'link'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<a link=\"foo\"></a>\n" + "{/template}");
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
        "{namespace ns}\n" + "{template bar}\n" + "  <div>test</div>\n" + "{/template}");
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
        "{namespace ns}\n"
            + "{template bar}\n"
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
        "{namespace ns}\n"
            + "{template bar}\n"
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
        "{namespace ns}\n"
            + "{template bar}\n"
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
        "{namespace ns}\n" + "{template bar}\n" + "<div><p></p></div>\n" + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithAttribute() {
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "    when_attribute_possibly_present: 'contenteditable'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <div contenteditable>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithAttributeDifferentCase() {
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "    when_attribute_possibly_present: 'contenteditable'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <div CONTENTEDITABLE>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithNonStaticAttribute() {
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "    when_attribute_possibly_present: 'contenteditable'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template bar}\n"
            + "{@param baz: bool}"
            + "  <div {if $baz}contenteditable{/if}>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithMultipleAttributes() {
    assertViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "    when_attribute_possibly_present: 'contenteditable'\n"
            + "    when_attribute_possibly_present: 'style'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <div contenteditable style='color:red;'>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithAttributeNoViolation() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "    when_attribute_possibly_present: 'contenteditable'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template bar}\n" + "  <div>test</div>\n" + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithMultipleAttributesNoViolation() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'div'\n"
            + "    when_attribute_possibly_present: 'contenteditable'\n"
            + "    when_attribute_possibly_present: 'style'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <div contenteditable>test</div>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithBannedAndRequiredAttribute() {
    String requirement =
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'script'\n"
            + "    when_attribute_possibly_missing: 'src'\n"
            + "    when_attribute_possibly_present: 'defer'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}";

    assertNoViolation(
        requirement,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script src=\"foo.js\"></script>\n"
            + "{/template}");
    assertViolation(
        requirement,
        "{namespace ns}\n" + "{template bar}\n" + "  <script defer></script>\n" + "{/template}");
    assertViolation(
        requirement,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script src=\"foo.js\" defer></script>\n"
            + "{/template}");
    assertViolation(
        requirement,
        "{namespace ns}\n" + "{template bar}\n" + "  <script></script>\n" + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithRequiredAttribute() {
    String requirement =
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'script'\n"
            + "    when_attribute_possibly_missing: 'src'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}";

    // Fail if a script is set without a src
    assertViolation(
        requirement,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script>console.log('js is run here');</script>\n"
            + "{/template}");

    // As long as the src attribute is set there are no violations
    assertNoViolation(
        requirement,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script src=\"foo.js\">console.log('js is ignored here');</script>\n"
            + "{/template}");
    assertNoViolation(
        requirement,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script src=\"\">console.log('js is ignored here');</script>\n"
            + "{/template}");
    assertNoViolation(
        requirement,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script src>console.log('js is ignored here');</script>\n"
            + "{/template}");
  }

  @Test
  public void bannedHtmlTagWithExemptedAttributes() {
    String check =
        "requirement: {\n"
            + "  banned_html_tag: {\n"
            + "    tag: 'script'\n"
            + "    exempt_attribute: {\n"
            + "      name: 'type'\n"
            + "      value: 'application/ld+json'\n"
            + "    }\n"
            + "    exempt_attribute: {\n"
            + "      name: 'keyA'\n"
            + "    }\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}";

    assertNoViolation(
        check,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script type=\"application/ld+json\"></script>\n"
            + "  <script keyA></script>\n"
            + "  <script keyA src=\"foo.js\"></script>\n"
            + "  <script keyA=\"\"></script>\n"
            + "  <script keyA=\"anything\"></script>\n"
            + "{/template}");

    assertViolation(
        check, "{namespace ns}\n" + "{template bar}\n" + "  <script></script>\n" + "{/template}");

    assertViolation(
        check,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script src =\"foo.js\"></script>\n"
            + "{/template}");

    assertViolation(
        check,
        "{namespace ns}\n"
            + "{template bar}\n"
            + "  <script type=\"module\"></script>\n"
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
        "{namespace ns}\n" + "{template foo}\n" + "<div onclick='bar'></div>\n" + "{/template}");
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<div ONCLICK='bar'></div>\n" + "{/template}");
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<div on='bar'></div>\n" + "{/template}");
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
    assertNoViolation(config, "{namespace ns}\n" + "{template foo}\n" + "{/template}");
    assertViolation(
        config, "{namespace ns}\n" + "{template foo stricthtml=\"false\"}\n" + "{/template}");
    assertNoViolation(
        config, "{namespace ns}\n" + "{template foo kind=\"text\"}\n" + "{/template}");
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
            + "{template foo kind=\"attributes\"}\n"
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
            + "{template foo}\n"
            + " {@param jsUrl : ?}{@param callback : ?}\n"
            + "<script id=\"base-js\" src=\"{$jsUrl}\" async onload=\"{$callback}\"></script>\n"
            + "{/template}");
  }

  @Test
  public void testBanInlineEventHandlersExemptions() {
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  exempt: 'foo/bar/baz.soy'"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template foo}\n"
                    + "<script onload='foo();'></script>\n"
                    + "{/template}"),
            SourceFilePath.forTest("foo/bar/baz.soy")));
  }

  // Regression test for a bug where we used to essentially ignore exemptions if there were
  // multiple files in a compilation unit
  @Test
  public void testBanInlineEventHandlersIsTooAggressiveWithExemptions() {
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  exempt: 'foo/bar/baz.soy'"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template foo}\n"
                    + "<script onload='foo();'></script>\n"
                    + "{/template}"),
            SourceFilePath.forTest("foo/bar/baz.soy")),
        new StableSoyFileSupplier(
            CharSource.wrap("{namespace ns2}\n" + "{template noViolation}{/template}"),
            SourceFilePath.forTest("foo/bar/quux.soy")));
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
        "{namespace ns}\n" + "{template foo}\n" + "<div foo='bar'></div>\n" + "{/template}");
  }

  @Test
  public void testBanFragmentNavigation() {
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanFragmentNavigation'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<a href='#foo'></a>" + "{/template}");
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanFragmentNavigation'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<a href='./#foo'></a>" + "{/template}");
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanFragmentNavigation'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template foo}\n" + "<a href='#'></a>" + "{/template}");
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanFragmentNavigation'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "<some-custom-tag href='#'></some-custom-tag>"
            + "{/template}");
  }

  @Test
  public void testBannedCssSelector() {
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector {\n"
            + "    selector: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}{template bar}{css('foo')}{/template}");
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector {\n"
            + "    selector: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}{template bar}{css('foo')}{/template}");
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector {\n"
            + "    selector: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}{template bar}{@param foo : ?}{css($foo, 'foo')}{/template}");
  }

  @Test
  public void testBanXidForCssObfuscation_valid() {
    assertNoViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "\n"
            + "{template foo}\n"
            + "  {@param foo: string}\n"
            + "  {let $baz: css('baz') /}\n"
            + "  <div class=\"{css('foo')} {$baz}\n"
            + "              {if $foo == xid('bar')}{css('bar')}{/if}\n"
            + "              {$foo == xid('baz') ? css('a') : css('b')}\n"
            + "              {call shouldIgnoreThis}{param foo: xid('foo') /}{/call}\"\n"
            + "       data-foo=\"{xid('foo')}\">\n"
            + "    {call bar}\n"
            + "      {param fooClasses: css('foo') /}\n"
            + "      {param barClasses kind=\"text\"}\n"
            + "        {css('bar')}\n"
            + "      {/param}\n"
            + "      {param complexClass kind=\"text\"}\n"
            + "        {call shouldIgnoreThis}{param foo: xid('foo') /}{/call}\n"
            + "      {/param}\n"
            + "      {param foo: xid('foo') /}\n"
            + "      {param bar kind=\"text\"}{xid('bar')}{/param}\n"
            + "    {/call}\n"
            + "  </div>\n"
            + "{/template}\n"
            + "\n"
            + "{template bar}\n"
            + "  {@param fooClasses: string}\n"
            + "  {@param barClasses: string}\n"
            + "  {@param complexClass: string}\n"
            + "  {@param foo: string}\n"
            + "  {@param bar: string}\n"
            + "  {$fooClasses + $barClasses + $complexClass + $foo + $bar}\n"
            + "{/template}\n"
            + "\n"
            + "{template shouldIgnoreThis}\n"
            + "  {@param foo: string}\n"
            + "  {if $foo == xid('foo')}{css('bar')}{/if}\n"
            + "{/template}\n");
  }

  @Test
  public void testBanXidForCssObfuscation_inClassAttribute() {
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  <div class=\"{css('foo')} {xid('bar')}\"></div>\n"
            + "{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {let $bar kind=\"text\"}{xid('bar')}{/let}\n"
            + "  <div class=\"{css('foo')} {$bar}\"></div>\n"
            + "{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {let $baz: xid('baz') /}\n"
            + "  {let $bar kind=\"text\"}{$baz}{/let}\n"
            + "  <div class=\"{css('foo')} {$bar}\"></div>\n"
            + "{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {@param foo: bool}\n"
            + "  <div class=\"{css('foo')} {$foo ? css('bar') : xid('baz')}\"></div>\n"
            + "{/template}");
  }

  @Test
  public void testBanXidForCssObfuscation_inPotentialClassParam() {
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {call bar}\n"
            + "    {param classes kind=\"text\"}{css('foo')} {xid('bar')}{/param}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template bar}{@param classes: string}<div class=\"{$classes}\"></div>{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {call bar}\n"
            + "    {param class: xid('foo') /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template bar}{@param class: string}<div class=\"{$class}\"></div>{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {let $bar: xid('bar') /}\n"
            + "  {call bar}\n"
            + "    {param myClass: $bar /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template bar}{@param myClass: string}<div class=\"{$myClass}\"></div>{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template foo}\n"
            + "  {let $baz: xid('baz') /}\n"
            + "  {let $bar kind=\"text\"}{$baz}{/let}\n"
            + "  {call bar}\n"
            + "    {param myClasses kind=\"text\"}{$bar}{/param}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template bar}\n"
            + "  {@param myClasses: string}\n"
            + "  <div class=\"{$myClasses}\"></div>\n"
            + "{/template}");
  }

  private SoyError assertViolation(String textProto, String input) {
    ImmutableList<SoyError> violations = getViolations(textProto, input);
    assertThat(violations).hasSize(1);
    return Iterables.getOnlyElement(violations);
  }

  private SoyError assertViolation(String textProto, SoyFileSupplier... soyFileSuppliers) {
    ImmutableList<SoyError> violations = getViolations(textProto, soyFileSuppliers);
    assertThat(violations).hasSize(1);
    return Iterables.getOnlyElement(violations);
  }

  private void assertViolations(String textProto, String input, int size) {
    ImmutableList<SoyError> violations = getViolations(textProto, input);
    assertThat(violations).hasSize(size);
  }

  private void assertNoViolation(String textProto, String input) {
    ImmutableList<SoyError> violations = getViolations(textProto, input);
    assertThat(violations).isEmpty();
  }

  private void assertNoViolation(String textProto, SoyFileSupplier... soyFileSuppliers) {
    ImmutableList<SoyError> violations = getViolations(textProto, soyFileSuppliers);
    assertThat(violations).isEmpty();
  }

  private ImmutableList<SoyError> getViolations(String textProto, String input) {
    return getViolations(textProto, SoyFileSetParserBuilder.forFileContents(input));
  }

  private ImmutableList<SoyError> getViolations(String textProto, SoyFileSupplier... suppliers) {
    return getViolations(textProto, SoyFileSetParserBuilder.forSuppliers(suppliers));
  }

  private ImmutableList<SoyError> getViolations(String textProto, SoyFileSetParserBuilder builder) {
    ValidatedConformanceConfig config = parseConfigProto(textProto);
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    builder.setConformanceConfig(config).errorReporter(errorReporter).parse();
    ImmutableList<SoyError> errors = errorReporter.getErrors();
    Set<SoyErrorKind> expectedErrorKinds = new HashSet<>();
    for (RuleWithExemptions rule : config.getRules()) {
      expectedErrorKinds.add(rule.getRule().error);
    }
    for (SoyError actualError : errors) {
      if (!expectedErrorKinds.contains(actualError.errorKind())) {
        throw new AssertionError(
            "Found non-conformance error!: "
                + actualError
                + "\nexpected kind to be one of: "
                + expectedErrorKinds
                + " actual is: "
                + actualError.errorKind());
      }
    }
    return errors;
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
}
