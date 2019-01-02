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

package com.google.template.soy.internal.i18n;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContents;
import javax.annotation.Nullable;

public class BidiFormatter {

  /** The text used to bidi wrap a string. */
  @AutoValue
  public abstract static class BidiWrappingText {
    static BidiWrappingText create(String beforeText, String afterText) {
      return new AutoValue_BidiFormatter_BidiWrappingText(beforeText, afterText);
    }

    /** The text to go before the string to wrap. */
    public abstract String beforeText();

    /** The text to go after the string to wrap. */
    public abstract String afterText();
  }

  private static final BidiFormatter DEFAULT_LTR_INSTANCE = new BidiFormatter(Dir.LTR);
  private static final BidiFormatter DEFAULT_RTL_INSTANCE = new BidiFormatter(Dir.RTL);
  private static final SanitizedContent LTR_DIR =
      SanitizedContents.constantAttributes("dir=\"ltr\"");
  private static final SanitizedContent RTL_DIR =
      SanitizedContents.constantAttributes("dir=\"rtl\"");
  private static final SanitizedContent NEUTRAL_DIR =
      SanitizedContents.emptyString(ContentKind.ATTRIBUTES);

  private final Dir contextDir;

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality. {@link
   * #spanWrap} avoids span wrapping unless there's a reason ('dir' attribute should be appended).
   *
   * @param contextDir The context directionality. Must be RTL or LTR.
   */
  public static BidiFormatter getInstance(Dir contextDir) {
    switch (contextDir) {
      case LTR:
        return DEFAULT_LTR_INSTANCE;
      case RTL:
        return DEFAULT_RTL_INSTANCE;
      case NEUTRAL:
        throw new IllegalArgumentException("invalid context directionality: " + contextDir);
    }
    throw new AssertionError(contextDir);
  }

  /** @param contextDir The context directionality */
  private BidiFormatter(@Nullable Dir contextDir) {
    this.contextDir = contextDir;
  }

  /**
   * Returns "dir=\"ltr\"" or "dir=\"rtl\"", depending on the given directionality, if it is not
   * NEUTRAL or the same as the context directionality. Otherwise, returns "".
   *
   * @param dir Given directionality. Must not be null.
   * @return "dir=\"rtl\"" for RTL text in non-RTL context; "dir=\"ltr\"" for LTR text in non-LTR
   *     context; else, the empty string.
   */
  public SanitizedContent knownDirAttrSanitized(Dir dir) {
    Preconditions.checkNotNull(dir);
    if (dir != contextDir) {
      switch (dir) {
        case LTR:
          return LTR_DIR;
        case RTL:
          return RTL_DIR;
        case NEUTRAL:
          // fall out.
      }
    }
    return NEUTRAL_DIR;
  }

  /**
   * Formats a string of given directionality for use in HTML output of the context directionality,
   * so an opposite-directionality string is neither garbled nor garbles its surroundings.
   *
   * <p>The algorithm: In case the given directionality doesn't match the context directionality,
   * wraps the string with a 'span' element and adds a 'dir' attribute (either 'dir=\"rtl\"' or
   * 'dir=\"ltr\"').
   *
   * <p>Directionally isolates the string so that it does not garble its surroundings. Currently,
   * this is done by "resetting" the directionality after the string by appending a trailing Unicode
   * bidi mark matching the context directionality (LRM or RLM) when either the overall
   * directionality or the exit directionality of the string is opposite to that of the context.
   * Note that as opposed to the overall directionality, the entry and exit directionalities are
   * determined from the string itself.
   *
   * <p>If !{@code isHtml}, HTML-escapes the string regardless of wrapping.
   *
   * @param dir {@code str}'s directionality. If null, i.e. unknown, it is estimated.
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Input string after applying the above processing.
   */
  public String spanWrap(@Nullable Dir dir, String str, boolean isHtml) {
    BidiWrappingText wrappingText = spanWrappingText(dir, str, isHtml);
    if (!isHtml) {
      str = HtmlEscapers.htmlEscaper().escape(str);
    }
    return wrappingText.beforeText() + str + wrappingText.afterText();
  }

  /**
   * Operates like {@link #spanWrap(Dir, String, boolean)} but only returns the text that would be
   * prepended and appended to {@code str}.
   *
   * @param dir {@code str}'s directionality. If null, i.e. unknown, it is estimated.
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   */
  public BidiWrappingText spanWrappingText(@Nullable Dir dir, String str, boolean isHtml) {
    if (dir == null) {
      dir = estimateDirection(str, isHtml);
    }

    StringBuilder beforeText = new StringBuilder();
    StringBuilder afterText = new StringBuilder();
    boolean dirCondition = (dir != Dir.NEUTRAL && dir != contextDir);
    if (dirCondition) {
      beforeText.append("<span dir=\"").append(dir == Dir.RTL ? "rtl" : "ltr").append("\">");
      afterText.append("</span>");
    }
    afterText.append(markAfter(dir, str, isHtml));
    return BidiWrappingText.create(beforeText.toString(), afterText.toString());
  }

  /**
   * Formats a string of given directionality for use in plain-text output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles its
   * surroundings. As opposed to {@link #spanWrap}, this makes use of Unicode bidi formatting
   * characters. In HTML, its *only* valid use is inside of elements that do not allow markup, e.g.
   * the 'option' and 'title' elements.
   *
   * <p>The algorithm: In case the given directionality doesn't match the context directionality,
   * wraps the string with Unicode bidi formatting characters: RLE+{@code str}+PDF for RTL text, or
   * LRE+{@code str}+PDF for LTR text.
   *
   * <p>Directionally isolates the string so that it does not garble its surroundings. Currently,
   * this is done by "resetting" the directionality after the string by appending a trailing Unicode
   * bidi mark matching the context directionality (LRM or RLM) when either the overall
   * directionality or the exit directionality of the string is opposite to that of the context.
   * Note that as opposed to the overall directionality, the entry and exit directionalities are
   * determined from the string itself.
   *
   * <p>Does *not* do HTML-escaping regardless of the value of {@code isHtml}.
   *
   * @param dir {@code str}'s directionality. If null, i.e. unknown, it is estimated.
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Input string after applying the above processing.
   */
  public String unicodeWrap(@Nullable Dir dir, String str, boolean isHtml) {
    BidiWrappingText wrappingText = unicodeWrappingText(dir, str, isHtml);
    return wrappingText.beforeText() + str + wrappingText.afterText();
  }

  /**
   * Operates like {@link #unicodeWrap(Dir, String, boolean)} but only returns the text that would
   * be prepended and appended to {@code str}.
   *
   * @param dir {@code str}'s directionality. If null, i.e. unknown, it is estimated.
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   */
  public BidiWrappingText unicodeWrappingText(@Nullable Dir dir, String str, boolean isHtml) {
    if (dir == null) {
      dir = estimateDirection(str, isHtml);
    }
    StringBuilder beforeText = new StringBuilder();
    StringBuilder afterText = new StringBuilder();
    if (dir != Dir.NEUTRAL && dir != contextDir) {
      beforeText.append(dir == Dir.RTL ? BidiUtils.Format.RLE : BidiUtils.Format.LRE);
      afterText.append(BidiUtils.Format.PDF);
    }
    afterText.append(markAfter(dir, str, isHtml));
    return BidiWrappingText.create(beforeText.toString(), afterText.toString());
  }

  /**
   * Returns a Unicode bidi mark matching the context directionality (LRM or RLM) if either the
   * overall or the exit directionality of a given string is opposite to the context directionality.
   * Putting this after the string (including its directionality declaration wrapping) prevents it
   * from "sticking" to other opposite-directionality text or a number appearing after it inline
   * with only neutral content in between. Otherwise returns the empty string. While the exit
   * directionality is determined by scanning the end of the string, the overall directionality is
   * given explicitly in {@code dir}.
   *
   * @param str String after which the mark may need to appear
   * @param dir {@code str}'s overall directionality. If null, i.e. unknown, it is estimated.
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markAfter(@Nullable Dir dir, String str, boolean isHtml) {
    if (dir == null) {
      dir = estimateDirection(str, isHtml);
    }
    // BidiUtils.getExitDir() is called only if needed (short-circuit).
    if (contextDir == Dir.LTR && (dir == Dir.RTL || BidiUtils.getExitDir(str, isHtml) == Dir.RTL)) {
      return BidiUtils.Format.LRM_STRING;
    }
    if (contextDir == Dir.RTL && (dir == Dir.LTR || BidiUtils.getExitDir(str, isHtml) == Dir.LTR)) {
      return BidiUtils.Format.RLM_STRING;
    }
    return "";
  }

  /**
   * Estimates the directionality of a string using the best known general-purpose method, i.e.
   * using relative word counts. Dir.NEUTRAL return value indicates completely neutral input.
   *
   * @param str String whose directionality is to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return {@code str}'s estimated overall directionality
   */
  @VisibleForTesting
  static Dir estimateDirection(String str, boolean isHtml) {
    return BidiUtils.estimateDirection(str, isHtml);
  }
}
