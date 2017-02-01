/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.bididirectives;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiFormatter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * A directive that maybe wraps the output within Unicode bidi control characters -- start character
 * is either LRE (U+202A) or RLE (U+202B), and end character is always PDF (U+202C). This wrapping
 * is only applied when the output text's bidi directionality is different from the bidi global
 * directionality.
 *
 */
@Singleton
final class BidiUnicodeWrapDirective
    implements SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective {

  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Provider for the current bidi global directionality. */
  @Inject
  BidiUnicodeWrapDirective(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public String getName() {
    return "|bidiUnicodeWrap";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public boolean shouldCancelAutoescape() {
    return false;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    ContentKind valueKind = null;
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueKind = sanitizedContent.getContentKind();
      valueDir = sanitizedContent.getContentDirection();
    }
    BidiFormatter bidiFormatter =
        SoyBidiUtils.getBidiFormatter(bidiGlobalDirProvider.get().getStaticValue());

    // We treat the value as HTML if and only if it says it's HTML, even though in legacy usage, we
    // sometimes have an HTML string (not SanitizedContent) that is passed to an autoescape="false"
    // template or a {print $foo |noAutoescape}, with the output going into an HTML context without
    // escaping. We simply have no way of knowing if this is what is happening when we get
    // non-SanitizedContent input, and most of the time it isn't.
    boolean isHtml = valueKind == ContentKind.HTML;
    String wrappedValue =
        bidiFormatter.unicodeWrapWithKnownDir(valueDir, value.coerceToString(), isHtml);

    // Bidi-wrapping a value converts it to the context directionality. Since it does not cost us
    // anything, we will indicate this known direction in the output SanitizedContent, even though
    // the intended consumer of that information - a bidi wrapping directive - has already been run.
    Dir wrappedValueDir = bidiFormatter.getContextDir();

    // Unicode-wrapping UnsanitizedText gives UnsanitizedText.
    // Unicode-wrapping safe HTML.
    if (valueKind == ContentKind.TEXT || valueKind == ContentKind.HTML) {
      return UnsafeSanitizedContentOrdainer.ordainAsSafe(wrappedValue, valueKind, wrappedValueDir);
    }

    // Unicode-wrapping does not conform to the syntax of the other types of content. For lack of
    // anything better to do, we output non-SanitizedContent.
    // TODO(user): Consider throwing a runtime error on receipt of SanitizedContent other than
    // TEXT, or HTML.
    if (valueKind != null) {
      return StringData.forValue(wrappedValue);
    }

    // The input was not SanitizedContent, so our output isn't SanitizedContent either.
    return StringData.forValue(wrappedValue);
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new JsExpr(
        "soy.$$bidiUnicodeWrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new PyExpr(
        "bidi.unicode_wrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }
}
