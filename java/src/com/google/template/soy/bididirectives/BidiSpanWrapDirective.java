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
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.SoyValue;
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
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * A directive that maybe wraps the output within a 'span' with dir=ltr or dir=rtl. This wrapping is
 * only applied when the output text's bidi directionality is different from the bidi global
 * directionality.
 *
 */
@Singleton
final class BidiSpanWrapDirective
    implements SanitizedContentOperator,
        SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective {

  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Provider for the current bidi global directionality. */
  @Inject
  BidiSpanWrapDirective(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public String getName() {
    return "|bidiSpanWrap";
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
  @Nonnull
  public ContentKind getContentKind() {
    // This directive expects HTML as input and produces HTML as output.
    return ContentKind.HTML;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      valueDir = ((SanitizedContent) value).getContentDirection();
    }
    BidiFormatter bidiFormatter =
        SoyBidiUtils.getBidiFormatter(bidiGlobalDirProvider.get().getStaticValue());

    // We always treat the value as HTML, because span-wrapping is only useful when its output will
    // be treated as HTML (without escaping), and because |bidiSpanWrap is not itself specified to
    // do HTML escaping in Soy. (Both explicit and automatic HTML escaping, if any, is done before
    // calling |bidiSpanWrap because BidiSpanWrapDirective implements SanitizedContentOperator,
    // but this does not mean that the input has to be HTML SanitizedContent. In legacy usage, a
    // string that is not SanitizedContent is often printed in an autoescape="false" template or by
    // a print with a |noAutoescape, in which case our input is just SoyData.) If the output will be
    // treated as HTML, the input had better be safe HTML/HTML-escaped (even if it isn't HTML
    // SanitizedData), or we have an XSS opportunity and a much bigger problem than bidi garbling.
    String wrappedValue =
        bidiFormatter.spanWrapWithKnownDir(valueDir, value.coerceToString(), true /* isHtml */);

    // Like other directives implementing SanitizedContentOperator, BidiSpanWrapDirective is called
    // after the escaping (if any) has already been done, and thus there is no need for it to
    // produce actual SanitizedContent.
    return StringData.forValue(wrappedValue);
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new JsExpr(
        "soy.$$bidiSpanWrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new PyExpr(
        "bidi.span_wrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }
}
