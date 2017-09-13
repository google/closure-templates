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
package com.google.template.soy.bididirectives;

import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.SoyString;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiFormatter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;

/** Java implementations of the bididirectives. */
public final class BidiDirectivesRuntime {

  private BidiDirectivesRuntime() {}

  public static SoyString bidiUnicodeWrap(BidiGlobalDir dir, SoyValue value) {
    // normalize null between tofu and jbcsrc
    value = value == null ? NullData.INSTANCE : value;
    ContentKind valueKind = null;
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueKind = sanitizedContent.getContentKind();
      valueDir = sanitizedContent.getContentDirection();
    }
    BidiFormatter bidiFormatter = SoyBidiUtils.getBidiFormatter(dir.getStaticValue());

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

  public static String bidiSpanWrap(BidiGlobalDir dir, SoyValue value) {
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      valueDir = ((SanitizedContent) value).getContentDirection();
    }
    BidiFormatter bidiFormatter = SoyBidiUtils.getBidiFormatter(dir.getStaticValue());

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
    return wrappedValue;
  }
}
