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
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.base.internal.StableSoyFileSupplier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import java.util.HashSet;
import java.util.Set;
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
            + "{template .foo}\n"
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
            + "{template .checkNotNull}\n"
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
        "{namespace ns}\n"
            + "{template .foo autoescape=\"deprecated-contextual\"}\n"
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
        "{namespace ns}\n"
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
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "{css('goog-inline-block')}\n"
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
        "{namespace ns}\n"
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
        "{namespace ns}\n"
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
        "{namespace ns}\n"
            + "{template .bar}\n"
            + "  {@param foo: string}\n"
            + "  {$foo}\n"
            + "{/template}\n");
  }

  @Test
  public void testWhitelistedFileDoesNotCauseErrors() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'foo/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template .foo}\n"
                    + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
                    + "{/template}"),
            "foo/bar/baz.soy"));
  }

  @Test
  public void testWhitelistEntriesAreSubstrings() {
    assertNoViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "  whitelist: 'c/foo/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template .foo}\n"
                    + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
                    + "{/template}"),
            "a/b/c/foo/bar/baz.soy"));
  }

  @Test
  public void testWhitelistedSubstringsAreContiguous() {
    assertViolation(
        "requirement: {\n"
            + "  banned_function {\n"
            + "    function: 'checkNotNull'\n"
            + "  }\n"
            + "  error_message: 'foooo'"
            + "  whitelist: 'foo/c/bar/baz.soy'\n"
            + "}",
        new StableSoyFileSupplier(
            CharSource.wrap(
                "{namespace ns}\n"
                    + "{template .foo}\n"
                    + "{checkNotNull(['xxx', 'bar', 'yyy', 'baz'])}\n"
                    + "{/template}"),
            "a/b/c/foo/bar/baz.soy"));
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
        "{namespace ns}\n" + "{template .bar}\n" + "foo\n" + "{/template}");
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
        "{namespace ns}\n" + "{template .foo}\n" + "bar\n" + "{/template}");
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
        "{namespace ns}\n" + "{template .bar}\n" + "  <div>test</div>\n" + "{/template}");
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
        "{namespace ns}\n"
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
        "{namespace ns}\n"
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
        "{namespace ns}\n" + "{template .bar}\n" + "<div><p></p></div>\n" + "{/template}");
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
        "{namespace ns}\n" + "{template .foo}\n" + "<div onclick='bar'></div>\n" + "{/template}");
    assertViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template .foo}\n" + "<div ONCLICK='bar'></div>\n" + "{/template}");
    assertNoViolation(
        "requirement: {\n"
            + "  custom: {\n"
            + "    java_class: 'com.google.template.soy.conformance.BanInlineEventHandlers'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}\n" + "{template .foo}\n" + "<div on='bar'></div>\n" + "{/template}");
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
    assertNoViolation(config, "{namespace ns}\n" + "{template .foo}\n" + "{/template}");
    assertViolation(
        config, "{namespace ns}\n" + "{template .foo stricthtml=\"false\"}\n" + "{/template}");
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
            "foo/bar/baz.soy"),
        new StableSoyFileSupplier(
            CharSource.wrap("{namespace ns}\n" + "{template .noViolation}{/template}"),
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
        "{namespace ns}\n" + "{template .foo}\n" + "<div foo='bar'></div>\n" + "{/template}");
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
        "{namespace ns}{template .bar}{css('foo')}{/template}");
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector {\n"
            + "    selector: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}{template .bar}{css('foo')}{/template}");
    assertViolation(
        "requirement: {\n"
            + "  banned_css_selector {\n"
            + "    selector: 'foo'\n"
            + "  }\n"
            + "  error_message: 'foo'"
            + "}",
        "{namespace ns}{template .bar}{@param foo : ?}{css($foo, 'foo')}{/template}");
  }

  @Test
  public void testRequireStrictAutoescaping() {
    assertNoViolation(
        "requirement: {\n"
            + "  require_strict_autoescaping: {}\n"
            + "  error_message: 'foo'"
            + " "
            + "}",
        "{namespace ns}\n" + "{template .foo}{/template}\n");
    assertViolation(
        "requirement: {\n"
            + "  require_strict_autoescaping: {}\n"
            + "  error_message: 'foo'"
            + " "
            + "}",
        "{namespace ns }\n" + "{template .foo autoescape=\"deprecated-contextual\"}{/template}\n");
  }

  @Test
  public void testRequireStrictlyTypedIjs() {
    assertNoViolation(
        "requirement: {\n"
            + "  require_strongly_typed_ij_params: {}\n"
            + "  error_message: 'foo'"
            + " "
            + "}",
        "{namespace ns}\n" + "{template .foo}{/template}\n");
    assertViolation(
        "requirement: {\n"
            + "  require_strongly_typed_ij_params: {}\n"
            + "  error_message: 'foo'"
            + " "
            + "}",
        "{namespace ns}{template .foo}{$ij.foo}{/template}\n");
    assertNoViolation(
        "requirement: {\n"
            + "  require_strongly_typed_ij_params: {}\n"
            + "  error_message: 'foo'"
            + " "
            + "}",
        "{namespace ns}{template .foo}{@inject foo : ?}{$foo}{/template}\n");
  }

  @Test
  public void testBanXidForCssObfuscation_valid() {
    assertNoViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "\n"
            + "{template .foo}\n"
            + "  {@param foo: string}\n"
            + "  {let $baz: css('baz') /}\n"
            + "  <div class=\"{css('foo')} {$baz}\n"
            + "              {if $foo == xid('bar')}{css('bar')}{/if}\n"
            + "              {$foo == xid('baz') ? css('a') : css('b')}\n"
            + "              {call .shouldIgnoreThis}{param foo: xid('foo') /}{/call}\"\n"
            + "       data-foo=\"{xid('foo')}\">\n"
            + "    {call .bar}\n"
            + "      {param fooClasses: css('foo') /}\n"
            + "      {param barClasses kind=\"text\"}\n"
            + "        {css('bar')}\n"
            + "      {/param}\n"
            + "      {param complexClass kind=\"text\"}\n"
            + "        {call .shouldIgnoreThis}{param foo: xid('foo') /}{/call}\n"
            + "      {/param}\n"
            + "      {param foo: xid('foo') /}\n"
            + "      {param bar kind=\"text\"}{xid('bar')}{/param}\n"
            + "    {/call}\n"
            + "  </div>\n"
            + "{/template}\n"
            + "\n"
            + "{template .bar}\n"
            + "  {@param fooClasses: string}\n"
            + "  {@param barClasses: string}\n"
            + "  {@param complexClass: string}\n"
            + "  {@param foo: string}\n"
            + "  {@param bar: string}\n"
            + "  {$fooClasses + $barClasses + $complexClass + $foo + $bar}\n"
            + "{/template}\n"
            + "\n"
            + "{template .shouldIgnoreThis}\n"
            + "  {@param foo: string}\n"
            + "  {if $foo == xid('foo')}{css('bar')}{/if}\n"
            + "{/template}\n");
  }

  @Test
  public void testBanXidForCssObfuscation_inClassAttribute() {
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  <div class=\"{css('foo')} {xid('bar')}\"></div>\n"
            + "{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {let $bar kind=\"text\"}{xid('bar')}{/let}\n"
            + "  <div class=\"{css('foo')} {$bar}\"></div>\n"
            + "{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {let $baz: xid('baz') /}\n"
            + "  {let $bar kind=\"text\"}{$baz}{/let}\n"
            + "  <div class=\"{css('foo')} {$bar}\"></div>\n"
            + "{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {@param foo: bool}\n"
            + "  <div class=\"{css('foo')} {$foo ? css('bar') : xid('baz')}\"></div>\n"
            + "{/template}");
  }

  @Test
  public void testBanXidForCssObfuscation_inPotentialClassParam() {
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {call .bar}\n"
            + "    {param classes kind=\"text\"}{css('foo')} {xid('bar')}{/param}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template .bar}{@param classes: string}<div class=\"{$classes}\"></div>{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {call .bar}\n"
            + "    {param class: xid('foo') /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template .bar}{@param class: string}<div class=\"{$class}\"></div>{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {let $bar: xid('bar') /}\n"
            + "  {call .bar}\n"
            + "    {param myClass: $bar /}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template .bar}{@param myClass: string}<div class=\"{$myClass}\"></div>{/template}");
    assertViolation(
        "requirement { ban_xid_for_css_obfuscation {} error_message: 'foo' }",
        "{namespace ns}\n"
            + "{template .foo}\n"
            + "  {let $baz: xid('baz') /}\n"
            + "  {let $bar kind=\"text\"}{$baz}{/let}\n"
            + "  {call .bar}\n"
            + "    {param myClasses kind=\"text\"}{$bar}{/param}\n"
            + "  {/call}\n"
            + "{/template}\n"
            + "{template .bar}\n"
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
    for (RuleWithWhitelists rule : config.getRules()) {
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
