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
import java.io.IOException;

/** Java implementations of the bididirectives. */
public final class BidiDirectivesRuntime {

  private BidiDirectivesRuntime() {}

  public static SoyValue bidiUnicodeWrap(BidiGlobalDir dir, SoyValue value) {
    // normalize null between tofu and jbcsrc
    value = value == null ? NullData.INSTANCE : value;
    // We treat the value as HTML if and only if it says it's HTML.
    boolean isHtml = false;
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      isHtml = sanitizedContent.getContentKind() == ContentKind.HTML;
      valueDir = sanitizedContent.getContentDirection();
    }
    BidiFormatter bidiFormatter = BidiFormatter.getInstance(dir.toDir());

    String wrappedValue = bidiFormatter.unicodeWrap(valueDir, value.coerceToString(), isHtml);

    // Unicode-wrapping UnsanitizedText gives UnsanitizedText.
    // Unicode-wrapping safe HTML.
    if (isHtml) {
      // Bidi-wrapping a value converts it to the context directionality. Since it does not cost us
      // anything, we will indicate this known direction in the output SanitizedContent, even though
      // the intended consumer of that information - a bidi wrapping directive - has already been
      // run.
      return UnsafeSanitizedContentOrdainer.ordainAsSafe(
          wrappedValue, ContentKind.HTML, dir.toDir());
    }

    // Unicode-wrapping does not conform to the syntax of the other types of content. For lack of
    // anything better to do, we output non-SanitizedContent.
    // TODO(user): Consider throwing a runtime error on receipt of values other than string, or
    // HTML.
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
    // but this does not mean that the input has to be HTML SanitizedContent.
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

  private static final class BidiWrapAppendable extends ForwardingLoggingAdvisingAppendable {
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
    public void flushBuffers(int depth) throws IOException {
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
                  getSanitizedContentKind() == ContentKind.HTML);
          break;
        default:
          throw new IllegalArgumentException("invalid wrap type: " + wrapType);
      }
      delegate.append(wrappingText.beforeText());
      commandBuffer.replayOn(delegate);
      delegate.append(wrappingText.afterText());
      super.flushBuffers(depth);
    }
  }
}
