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
package com.google.template.soy.basicdirectives;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.ForwardingLoggingAdvisingAppendable;
import com.google.template.soy.data.LogStatement;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.LoggingFunctionInvocation;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Static methods implementing the basic directives in this package. */
public final class BasicDirectivesRuntime {

  private static final Logger logger = Logger.getLogger(BasicDirectivesRuntime.class.getName());

  private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\r\\n|\\r|\\n");

  public static String truncate(String str, int maxLen, boolean doAddEllipsis) {
    if (str.length() <= maxLen) {
      return str; // no need to truncate
    }
    // If doAddEllipsis, either reduce maxLen to compensate, or else if maxLen is too small, just
    // turn off doAddEllipsis.
    if (doAddEllipsis) {
      if (maxLen > 3) {
        maxLen -= 3;
      } else {
        doAddEllipsis = false;
      }
    }

    // Make sure truncating at maxLen doesn't cut up a unicode surrogate pair.
    if (Character.isHighSurrogate(str.charAt(maxLen - 1))
        && Character.isLowSurrogate(str.charAt(maxLen))) {
      maxLen -= 1;
    }

    // Truncate.
    str = str.substring(0, maxLen);

    // Add ellipsis.
    if (doAddEllipsis) {
      str += "...";
    }

    return str;
  }

  public static LoggingAdvisingAppendable truncateStreaming(
      LoggingAdvisingAppendable appendable, int maxLength, boolean addEllipsis) {
    return new TruncateAppendable(appendable, maxLength, addEllipsis);
  }

  private static final class TruncateAppendable extends LoggingAdvisingAppendable {
    private final StringBuilder buffer;
    private final LoggingAdvisingAppendable delegate;
    private final int maxLength;
    private final boolean addEllipsis;

    TruncateAppendable(LoggingAdvisingAppendable delegate, int maxLength, boolean addEllipsis) {
      buffer = new StringBuilder();
      this.delegate = delegate;
      this.maxLength = maxLength;
      this.addEllipsis = addEllipsis;
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq) {
      buffer.append(csq);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(CharSequence csq, int start, int end) {
      buffer.append(csq, start, end);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable append(char c) {
      buffer.append(c);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable enterLoggableElement(LogStatement statement) {
      logger.log(
          Level.WARNING,
          "Visual element logging behavior is undefined when used with the |truncate directive. "
              + "This logging call has been dropped: {0}",
          statement);
      return this;
    }

    @Override
    public LoggingAdvisingAppendable exitLoggableElement() {
      return this;
    }

    @Override
    protected void notifyContentKind(ContentKind kind) throws IOException {
      // |truncate converts all input to TEXT, so label the output appendable as such. This isn't
      // strictly necessary, as the autoescaper will have already made sure the output is properly
      // escaped, but it helps make the intent clear.
      delegate.setSanitizedContentKind(ContentKind.TEXT);
    }

    @Override
    public LoggingAdvisingAppendable appendLoggingFunctionInvocation(
        LoggingFunctionInvocation funCall, ImmutableList<Function<String, String>> escapers) {
      buffer.append(escapePlaceholder(funCall.placeholderValue(), escapers));
      return this;
    }

    @Override
    public boolean softLimitReached() {
      return false;
    }

    @Override
    public void flushBuffers(int depth) throws IOException {
      delegate.append(truncate(buffer.toString(), maxLength, addEllipsis));
      if (depth > 0) {
        delegate.flushBuffers(depth - 1);
      }
    }
  }

  public static SoyValue changeNewlineToBr(SoyValue value) {
    String result = NEWLINE_PATTERN.matcher(coerceToString(value)).replaceAll("<br>");

    // Make sure to transmit the known direction, if any, to any downstream directive that may need
    // it, e.g. BidiSpanWrapDirective. Since a known direction is carried only by SanitizedContent,
    // and the transformation we make is only valid in HTML, we only transmit the direction when we
    // get HTML SanitizedContent.
    // TODO(user): Consider always returning HTML SanitizedContent.
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == ContentKind.HTML) {
        return UnsafeSanitizedContentOrdainer.ordainAsSafe(
            result, ContentKind.HTML, sanitizedContent.getContentDirection());
      }
    }
    return StringData.forValue(result);
  }

  public static LoggingAdvisingAppendable changeNewlineToBrStreaming(
      LoggingAdvisingAppendable appendable) {
    return new ForwardingLoggingAdvisingAppendable(appendable) {
      private boolean lastCharWasCarriageReturn;

      @Override
      public LoggingAdvisingAppendable append(char c) throws IOException {
        switch (c) {
          case '\n':
            if (!lastCharWasCarriageReturn) {
              super.append("<br>");
            }
            lastCharWasCarriageReturn = false;
            break;
          case '\r':
            super.append("<br>");
            lastCharWasCarriageReturn = true;
            break;
          default:
            super.append(c);
            lastCharWasCarriageReturn = false;
            break;
        }
        return this;
      }

      @Override
      public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
        return append(csq, 0, csq.length());
      }

      @Override
      public LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
          throws IOException {
        int appendedUpTo = start;
        boolean carriageReturn = lastCharWasCarriageReturn;
        for (int i = start; i < end; i++) {
          switch (csq.charAt(i)) {
            case '\n':
              appendUpTo(csq, appendedUpTo, i);
              if (!carriageReturn) {
                super.append("<br>");
              }
              appendedUpTo = i + 1;
              carriageReturn = false;
              break;
            case '\r':
              appendUpTo(csq, appendedUpTo, i);
              super.append("<br>");
              appendedUpTo = i + 1;
              carriageReturn = true;
              break;
            default:
              carriageReturn = false;
              break;
          }
        }
        appendUpTo(csq, appendedUpTo, end);
        lastCharWasCarriageReturn = carriageReturn;
        return this;
      }

      private void appendUpTo(CharSequence csq, int start, int end) throws IOException {
        if (start != end) {
          super.append(csq, start, end);
        }
      }
    };
  }

  public static SoyValue insertWordBreaks(SoyValue value, int maxCharsBetweenWordBreaks) {
    String result =
        new InsertWordBreaks(maxCharsBetweenWordBreaks).processString(coerceToString(value));

    // Make sure to transmit the known direction, if any, to any downstream directive that may need
    // it, e.g. BidiSpanWrapDirective. Since a known direction is carried only by SanitizedContent,
    // and the transformation we make is only valid in HTML, we only transmit the direction when we
    // get HTML SanitizedContent.
    // TODO(user): Consider always returning HTML SanitizedContent.
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      if (sanitizedContent.getContentKind() == ContentKind.HTML) {
        return UnsafeSanitizedContentOrdainer.ordainAsSafe(
            result, ContentKind.HTML, sanitizedContent.getContentDirection());
      }
    }

    return StringData.forValue(result);
  }

  public static LoggingAdvisingAppendable insertWordBreaksStreaming(
      LoggingAdvisingAppendable appendable, final int maxCharsBetweenWordBreaks) {
    return new ForwardingLoggingAdvisingAppendable(appendable) {
      private final InsertWordBreaks insertWordBreaks =
          new InsertWordBreaks(maxCharsBetweenWordBreaks);

      @Override
      public LoggingAdvisingAppendable append(char c) throws IOException {
        delegate.append(insertWordBreaks.processChar(c));
        return this;
      }

      @Override
      public LoggingAdvisingAppendable append(CharSequence csq) throws IOException {
        delegate.append(insertWordBreaks.processString(csq.toString()));
        return this;
      }

      @Override
      public LoggingAdvisingAppendable append(CharSequence csq, int start, int end)
          throws IOException {
        delegate.append(insertWordBreaks.processString(csq.subSequence(start, end).toString()));
        return this;
      }
    };
  }

  private static final class InsertWordBreaks {
    private final int maxCharsBetweenWordBreaks;
    private final StringBuilder result;

    /** Whether we're inside an HTML tag. */
    private boolean isInTag;
    /** Whether we might be inside an HTML entity. */
    private boolean isMaybeInEntity;
    /** The number of characters since the last word break. */
    private int numCharsWithoutBreak;

    InsertWordBreaks(int maxCharsBetweenWordBreaks) {
      this.maxCharsBetweenWordBreaks = maxCharsBetweenWordBreaks;
      result = new StringBuilder();
    }

    String processString(String str) {
      for (int codePoint, i = 0, n = str.length(); i < n; i += Character.charCount(codePoint)) {
        codePoint = str.codePointAt(i);
        processOneCodePoint(codePoint);
      }
      return getAndReset();
    }

    String processChar(char c) {
      processOneCodePoint(c);
      return getAndReset();
    }

    private String getAndReset() {
      String resultStr = result.toString();
      result.setLength(0);
      return resultStr;
    }

    private void processOneCodePoint(int codePoint) {
      // If hit maxCharsBetweenWordBreaks, and next char is not a space, then add <wbr>.
      if (numCharsWithoutBreak >= maxCharsBetweenWordBreaks && codePoint != ' ') {
        result.append("<wbr>");
        numCharsWithoutBreak = 0;
      }

      if (isInTag) {
        // If inside an HTML tag and we see '>', it's the end of the tag.
        if (codePoint == '>') {
          isInTag = false;
        }

      } else if (isMaybeInEntity) {
        switch (codePoint) {
            // If maybe inside an entity and we see ';', it's the end of the entity. The entity
            // that just ended counts as one char, so increment numCharsWithoutBreak.
          case ';':
            isMaybeInEntity = false;
            ++numCharsWithoutBreak;
            break;
            // If maybe inside an entity and we see '<', we weren't actually in an entity. But
            // now we're inside an HTML tag.
          case '<':
            isMaybeInEntity = false;
            isInTag = true;
            break;
            // If maybe inside an entity and we see ' ', we weren't actually in an entity. Just
            // correct the state and reset the numCharsWithoutBreak since we just saw a space.
          case ' ':
            isMaybeInEntity = false;
            numCharsWithoutBreak = 0;
            break;
          default: // fall out
        }

      } else { // !isInTag && !isInEntity
        switch (codePoint) {
            // When not within a tag or an entity and we see '<', we're now inside an HTML tag.
          case '<':
            isInTag = true;
            break;
            // When not within a tag or an entity and we see '&', we might be inside an entity.
          case '&':
            isMaybeInEntity = true;
            break;
            // When we see a space, reset the numCharsWithoutBreak count.
          case ' ':
            numCharsWithoutBreak = 0;
            break;
            // When we see a non-space, increment the numCharsWithoutBreak.
          default:
            ++numCharsWithoutBreak;
            break;
        }
      }

      // In addition to adding <wbr>s, we still have to add the original characters.
      result.appendCodePoint(codePoint);
    }
  }

  private static String coerceToString(@Nullable SoyValue v) {
    return v == null ? "null" : v.coerceToString();
  }
}
