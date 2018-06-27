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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.ForwardingLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiFormatter;
import com.google.template.soy.internal.i18n.BidiFormatter.BidiWrappingText;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import java.io.Closeable;
import java.io.IOException;

/** Java implementations of the bididirectives. */
public final class BidiDirectivesRuntime {

  private BidiDirectivesRuntime() {}

  public static SoyValue bidiUnicodeWrap(BidiGlobalDir dir, SoyValue value) {
    // normalize null between tofu and jbcsrc
    value = value == null ? NullData.INSTANCE : value;
    ContentKind valueKind = null;
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueKind = sanitizedContent.getContentKind();
      valueDir = sanitizedContent.getContentDirection();
    }
    BidiFormatter bidiFormatter = BidiFormatter.getInstance(dir.toDir());

    // We treat the value as HTML if and only if it says it's HTML, even though in legacy usage, we
    // sometimes have an HTML string (not SanitizedContent) that is passed to an autoescape="false"
    // template or a {print $foo |noAutoescape}, with the output going into an HTML context without
    // escaping. We simply have no way of knowing if this is what is happening when we get
    // non-SanitizedContent input, and most of the time it isn't.
    boolean isHtml = valueKind == ContentKind.HTML;
    String wrappedValue = bidiFormatter.unicodeWrap(valueDir, value.coerceToString(), isHtml);

    // Unicode-wrapping UnsanitizedText gives UnsanitizedText.
    // Unicode-wrapping safe HTML.
    if (valueKind == ContentKind.TEXT || valueKind == ContentKind.HTML) {
      // Bidi-wrapping a value converts it to the context directionality. Since it does not cost us
      // anything, we will indicate this known direction in the output SanitizedContent, even though
      // the intended consumer of that information - a bidi wrapping directive - has already been
      // run.
      return UnsafeSanitizedContentOrdainer.ordainAsSafe(wrappedValue, valueKind, dir.toDir());
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

  public static LoggingAdvisingAppendable bidiUnicodeWrapStreaming(
      LoggingAdvisingAppendable delegateAppendable, BidiGlobalDir dir) {
    return new BidiWrapAppendable(delegateAppendable, dir, WrapType.UNICODE);
  }

  public static String bidiSpanWrap(BidiGlobalDir dir, SoyValue value) {
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      valueDir = ((SanitizedContent) value).getContentDirection();
    }
    BidiFormatter bidiFormatter = BidiFormatter.getInstance(dir.toDir());

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
        bidiFormatter.spanWrap(valueDir, value.coerceToString(), /* isHtml= */ true);

    // Like other directives implementing SanitizedContentOperator, BidiSpanWrapDirective is called
    // after the escaping (if any) has already been done, and thus there is no need for it to
    // produce actual SanitizedContent.
    return wrappedValue;
  }

  public static LoggingAdvisingAppendable bidiSpanWrapStreaming(
      LoggingAdvisingAppendable delegateAppendable, BidiGlobalDir dir) {
    return new BidiWrapAppendable(delegateAppendable, dir, WrapType.SPAN);
  }

  private enum WrapType {
    SPAN,
    UNICODE
  }

  private static final class BidiWrapAppendable extends ForwardingLoggingAdvisingAppendable
      implements Closeable {
    private final BidiGlobalDir globalDir;
    private final WrapType wrapType;
    private final StringBuilder buffer;
    private final BufferingAppendable commandBuffer;

    BidiWrapAppendable(
        LoggingAdvisingAppendable delegate, BidiGlobalDir globalDir, WrapType wrapType) {
      super(delegate);
      this.globalDir = globalDir;
      this.wrapType = Preconditions.checkNotNull(wrapType);
      buffer = new StringBuilder();
      commandBuffer = LoggingAdvisingAppendable.buffering();
    }

    @Override
    protected void notifyContentKind(ContentKind kind) throws IOException {
      commandBuffer.setSanitizedContentKind(kind);
    }

    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      commandBuffer.enterLoggableElement(statement);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      commandBuffer.exitLoggableElement();
      return this;
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers)
        throws IOException {
      commandBuffer.appendLoggingFunctionInvocation(funCall, escapers);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(char c) throws IOException {
      buffer.append(c);
      commandBuffer.append(c);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
      buffer.append(csq);
      commandBuffer.append(csq);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
        throws IOException {
      buffer.append(csq, start, end);
      commandBuffer.append(csq, start, end);
      return this;
    }

    @Override
    public void close() throws IOException {
      BidiFormatter formatter = BidiFormatter.getInstance(globalDir.toDir());
      BidiWrappingText wrappingText;
      switch (wrapType) {
        case SPAN:
          wrappingText =
              formatter.spanWrappingText(
                  getSanitizedContentDirectionality(), buffer.toString(), /* isHtml= */ true);
          break;
        case UNICODE:
          wrappingText =
              formatter.unicodeWrappingText(
                  getSanitizedContentDirectionality(),
                  buffer.toString(),
                  getSantizedContentKind() == ContentKind.HTML);
          break;
        default:
          throw new IllegalArgumentException("invalid wrap type: " + wrapType);
      }
      delegate.append(wrappingText.beforeText());
      commandBuffer.replayOn(delegate);
      delegate.append(wrappingText.afterText());
    }
  }
}
