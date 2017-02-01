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

import com.google.common.base.Preconditions;
import com.google.common.html.HtmlEscapers;
import com.google.template.soy.data.Dir;
import com.ibm.icu.util.ULocale;
import javax.annotation.Nullable;

/**
 * Utility class for formatting text for display in a potentially opposite-directionality context
 * without garbling. The directionality of the context is set at formatter creation and the
 * directionality of the text can be either estimated or passed in when known. Provides the
 * following functionality:
 *
 * <p>1. Bidi Wrapping When text in one language is mixed into a document in another,
 * opposite-directionality language, e.g. when an English business name is embedded in a Hebrew web
 * page, both the inserted string and the text surrounding it may be displayed incorrectly unless
 * the inserted string is explicitly separated from the surrounding text in a "wrapper" that:
 *
 * <p>- Declares its directionality so that the string is displayed correctly. This can be done in
 * HTML markup (e.g. a 'span dir="rtl"' element) by {@link #spanWrap} and similar methods, or - only
 * in contexts where markup can't be used - in Unicode bidi formatting codes by {@link #unicodeWrap}
 * and similar methods. Optionally, the markup can be inserted even when the directionality is the
 * same, in order to keep the DOM structure more stable.
 *
 * <p>- Isolates the string's directionality, so it does not unduly affect the surrounding content.
 * Currently, this can only be done using invisible Unicode characters of the same direction as the
 * context (LRM or RLM) in addition to the directionality declaration above, thus "resetting" the
 * directionality to that of the context. The "reset" may need to be done at both ends of the
 * string. Without "reset" after the string, the string will "stick" to a number or logically
 * separate opposite-direction text that happens to follow it in-line (even if separated by neutral
 * content like spaces and punctuation). Without "reset" before the string, the same can happen
 * there, but only with more opposite-direction text, not a number. One approach is to "reset" the
 * direction only after each string, on the theory that if the preceding opposite- direction text is
 * itself bidi-wrapped, the "reset" after it will prevent the sticking. (Doing the "reset" only
 * before each string definitely does not work because we do not want to require bidi-wrapping
 * numbers, and a bidi-wrapped opposite-direction string could be followed by a number.) Still, the
 * safest policy is to do the "reset" on both ends of each string, since RTL message translations
 * often contain untranslated Latin-script brand names and technical terms, and one of these can be
 * followed by a bidi-wrapped inserted value. On the other hand, when one has such a message, it is
 * best to do the "reset" manually in the message translation itself, since the message's
 * opposite-direction text could be followed by an inserted number, which we would not bidi-wrap
 * anyway. Thus, "reset" only after the string is the current default. In an alternative to "reset",
 * recent additions to the HTML, CSS, and Unicode standards allow the isolation to be part of the
 * directionality declaration. This form of isolation is better than "reset" because it takes less
 * space, does not require knowing the context directionality, has a gentler effect than "reset",
 * and protects both ends of the string. However, we do not yet allow using it because required
 * platforms do not yet support it.
 *
 * <p>Providing these wrapping services is the basic purpose of the bidi formatter.
 *
 * <p>2. Directionality estimation How does one know whether a string about to be inserted into
 * surrounding text has the same directionality? Well, in many cases, one knows that this must be
 * the case when writing the code doing the insertion, e.g. when a localized message is inserted
 * into a localized page. In such cases there is no need to involve the bidi formatter at all. In
 * some other cases, it need not be the same as the context, but is either constant (e.g. urls are
 * always LTR) or otherwise known. In the remaining cases, e.g. when the string is user-entered or
 * comes from a database, the language of the string (and thus its directionality) is not known a
 * priori, and must be estimated at run-time. The bidi formatter can do this automatically.
 *
 * <p>3. Escaping When wrapping plain text - i.e. text that is not already HTML or HTML-escaped - in
 * HTML markup, the text must first be HTML-escaped to prevent XSS attacks and other nasty business.
 * This of course is always true, but the escaping can not be done after the string has already been
 * wrapped in markup, so the bidi formatter also serves as a last chance and includes escaping
 * services.
 *
 * <p>Thus, in a single call, the formatter can escape the input string as specified, determine its
 * directionality, and wrap it as necessary. It is then up to the caller to insert the return value
 * in the output.
 */
public class BidiFormatter {

  /** A class for building a BidiFormatter with non-default options. */
  public static final class Builder {
    private Dir contextDir;
    private int flags;

    /**
     * Constructor
     *
     * @param contextDir The context directionality. Must not be NEUTRAL. It can be (Dir) null to
     *     indicate that the context is unknown, but this is not recommended: the wrapping methods
     *     then wrap text of either directionality, and cannot "reset" the directionality back to
     *     the context.
     */
    public Builder(@Nullable Dir contextDir) {
      Preconditions.checkArgument(contextDir != Dir.NEUTRAL);
      initialize(contextDir);
    }

    /**
     * Constructor
     *
     * @param rtlContext Whether the context directionality is RTL
     */
    public Builder(boolean rtlContext) {
      initialize(rtlContext ? Dir.RTL : Dir.LTR);
    }

    /**
     * Constructor
     *
     * @param locale The context locale
     */
    public Builder(ULocale locale) {
      initialize(BidiUtils.languageDir(locale));
    }

    /**
     * Initializes the builder with the given context directionality and default options.
     *
     * @param contextDir The context directionality.
     */
    private void initialize(@Nullable Dir contextDir) {
      this.contextDir = contextDir;
      this.flags = DEFAULT_FLAGS;
    }

    /**
     * Specifies whether the {@link #spanWrap} and {@link #spanWrapWithKnownDir} methods of the
     * BidiFormatter to be built should produce a stable span structure, i.e. wrap the string in a
     * span even when its directionality does not need to be declared. The default is false.
     */
    public Builder alwaysSpan(boolean alwaysSpan) {
      if (alwaysSpan) {
        flags |= FLAG_ALWAYS_SPAN;
      } else {
        flags &= ~FLAG_ALWAYS_SPAN;
      }
      return this;
    }

    /**
     * Specifies whether the BidiFormatter to be built should also "reset" directionality before a
     * string being bidi-wrapped, not just after it. The default is false.
     */
    public Builder stereoReset(boolean stereoReset) {
      if (stereoReset) {
        flags |= FLAG_STEREO_RESET;
      } else {
        flags &= ~FLAG_STEREO_RESET;
      }
      return this;
    }

    /** @return A BidiFormatter with the specified options. */
    public BidiFormatter build() {
      if (flags == DEFAULT_FLAGS) {
        if (contextDir == Dir.LTR) {
          return DEFAULT_LTR_INSTANCE;
        }
        if (contextDir == Dir.RTL) {
          return DEFAULT_RTL_INSTANCE;
        }
      }
      return new BidiFormatter(contextDir, flags);
    }
  }

  private static final int FLAG_ALWAYS_SPAN = 1;
  private static final int FLAG_STEREO_RESET = 2;
  // We will soon also need the following:
  // private static final int FLAG_UNICODE_ISOLATES_SUPPORTED
  // private static final int FLAG_HTML_ISOLATES_SUPPORTED

  private static final int DEFAULT_FLAGS = 0;

  private static final BidiFormatter DEFAULT_LTR_INSTANCE =
      new BidiFormatter(Dir.LTR, DEFAULT_FLAGS);
  private static final BidiFormatter DEFAULT_RTL_INSTANCE =
      new BidiFormatter(Dir.RTL, DEFAULT_FLAGS);

  private final Dir contextDir;
  private final int flags;

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality. The default
   * behavior of {@link #spanWrap} and its variations is set to avoid span wrapping unless there's a
   * reason ('dir' attribute should be appended).
   *
   * @param contextDir The context directionality. Must not be NEUTRAL. It can be (Dir) null to
   *     indicate that the context is unknown, but this is not recommended: the wrapping methods
   *     then wrap text of either directionality, and cannot "reset" the directionality back to the
   *     context.
   */
  public static BidiFormatter getInstance(@Nullable Dir contextDir) {
    return new Builder(contextDir).build();
  }

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality. The default
   * behavior of {@link #spanWrap} and its variations is set to avoid span wrapping unless there's a
   * reason ('dir' attribute should be appended).
   *
   * @param rtlContext Whether the context directionality is RTL
   */
  public static BidiFormatter getInstance(boolean rtlContext) {
    return new Builder(rtlContext).build();
  }

  /**
   * Factory for creating an instance of BidiFormatter for an unknown directionality context. This
   * is NOT RECOMMENDED: the wrapping methods then wrap text of either directionality, and cannot
   * "reset" the directionality back to the context. The default behavior of {@link #spanWrap} and
   * its variations is set to avoid span wrapping when it can (which is only for neutral content).
   */
  public static BidiFormatter getInstanceWithNoContext() {
    return new Builder((Dir) null).build();
  }

  /**
   * @param contextDir The context directionality
   * @param flags The option flags
   */
  private BidiFormatter(@Nullable Dir contextDir, int flags) {
    this.contextDir = contextDir;
    this.flags = flags;
  }

  /** @return The context directionality */
  @Nullable
  public Dir getContextDir() {
    return contextDir;
  }

  /** @return Whether the context directionality is RTL */
  public boolean isRtlContext() {
    return contextDir == Dir.RTL;
  }

  /**
   * @return Whether the {@link #spanWrap} and {@link #spanWrapWithKnownDir} methods should produce
   *     a stable span structure, i.e. wrap the string in a span even when its directionality does
   *     not need to be declared.
   */
  public boolean getAlwaysSpan() {
    return (flags & FLAG_ALWAYS_SPAN) != 0;
  }

  /**
   * @return Whether directionality "reset" should also be done before a string being bidi-wrapped,
   *     not just after it.
   */
  public boolean getStereoReset() {
    return (flags & FLAG_STEREO_RESET) != 0;
  }

  /**
   * Returns "rtl" if {@code str}'s estimated directionality is RTL, and "ltr" if it is LTR. In case
   * it's NEUTRAL, returns "rtl" if the context directionality is RTL, and "ltr" otherwise. Needed
   * for GXP, which can't handle dirAttr.
   *
   * <p>Example use case: <td expr:dir='bidiFormatter.dirAttrValue(foo)'><gxp:eval expr='foo'></td>
   *
   * @param str String whose directionality is to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return "rtl" if {@code str}'s estimated directionality is RTL, and "ltr" otherwise.
   */
  public String dirAttrValue(String str, boolean isHtml) {
    return knownDirAttrValue(estimateDirection(str, isHtml));
  }

  /**
   * Returns "rtl" if the given directionality is RTL, and "ltr" if it is LTR. In case the given
   * directionality is NEUTRAL, returns "rtl" if the context directionality is RTL, and "ltr"
   * otherwise.
   *
   * @param dir Given directionality. Must not be null.
   * @return "rtl" if the given directionality is RTL, and "ltr" otherwise.
   */
  public String knownDirAttrValue(Dir dir) {
    Preconditions.checkNotNull(dir);
    if (dir == Dir.NEUTRAL) {
      dir = contextDir;
    }

    return dir == Dir.RTL ? "rtl" : "ltr";
  }

  /**
   * Returns "dir=\"ltr\"" or "dir=\"rtl\"", depending on {@code str}'s estimated directionality, if
   * it is not the same as the context directionality. Otherwise, returns the empty string.
   *
   * @param str String whose directionality is to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return "dir=\"rtl\"" for RTL text in non-RTL context; "dir=\"ltr\"" for LTR text in non-LTR
   *     context; else, the empty string.
   */
  public String dirAttr(String str, boolean isHtml) {
    return knownDirAttr(estimateDirection(str, isHtml));
  }

  /**
   * Operates like {@link #dirAttr(String, boolean)}, but assumes {@code isHtml} is false.
   *
   * @param str String whose directionality is to be estimated
   * @return "dir=\"rtl\"" for RTL text in non-RTL context; "dir=\"ltr\"" for LTR text in non-LTR
   *     context; else, the empty string.
   */
  public String dirAttr(String str) {
    return dirAttr(str, false);
  }

  /**
   * Returns "dir=\"ltr\"" or "dir=\"rtl\"", depending on the given directionality, if it is not
   * NEUTRAL or the same as the context directionality. Otherwise, returns "".
   *
   * @param dir Given directionality. Must not be null.
   * @return "dir=\"rtl\"" for RTL text in non-RTL context; "dir=\"ltr\"" for LTR text in non-LTR
   *     context; else, the empty string.
   */
  public String knownDirAttr(Dir dir) {
    Preconditions.checkNotNull(dir);
    if (dir != contextDir) {
      return dir == Dir.LTR ? "dir=\"ltr\"" : dir == Dir.RTL ? "dir=\"rtl\"" : "";
    }
    return "";
  }

  /**
   * Formats a given string of unknown directionality for use in HTML output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles its
   * surroundings.
   *
   * <p>The algorithm: estimates the directionality of the given string. In case its directionality
   * doesn't match the context directionality, wraps it with a 'span' element and adds a "dir"
   * attribute (either 'dir=\"rtl\"' or 'dir=\"ltr\"').
   *
   * <p>If the formatter was built using {@link #alwaysSpan(true)}, the input is always wrapped in a
   * span, skipping just the dir attribute when it's not needed.
   *
   * <p>If {@code isolate}, directionally isolates the string so that it does not garble its
   * surroundings. Currently, this is done by "resetting" the directionality after the string by
   * appending a trailing Unicode bidi mark matching the context directionality (LRM or RLM) when
   * either the overall directionality or the exit directionality of the string is opposite to that
   * of the context. If the formatter was built using {@link #stereoReset(true)}, also prepends a
   * Unicode bidi mark matching the context directionality when either the overall directionality or
   * the entry directionality of the string is opposite to that of the context.
   *
   * <p>If !{@code isHtml}, HTML-escapes the string regardless of wrapping.
   *
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param isolate Whether to directionally isolate the string to prevent it from garbling the
   *     content around it
   * @return Input string after applying the above processing.
   */
  public String spanWrap(String str, boolean isHtml, boolean isolate) {
    return spanWrapWithKnownDir(null, str, isHtml, isolate);
  }

  /**
   * Operates like {@link #spanWrap(String, boolean, boolean)}, but assumes {@code isolate} is true.
   *
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Input string after applying the above processing.
   */
  public String spanWrap(String str, boolean isHtml) {
    return spanWrap(str, isHtml, true);
  }

  /**
   * Operates like {@link #spanWrap(String, boolean, boolean)}, but assumes {@code isHtml} is false
   * and {@code isolate} is true.
   *
   * @param str The input string
   * @return Input string after applying the above processing.
   */
  public String spanWrap(String str) {
    return spanWrap(str, false, true);
  }

  /**
   * Formats a string of given directionality for use in HTML output of the context directionality,
   * so an opposite-directionality string is neither garbled nor garbles its surroundings.
   *
   * <p>The algorithm: In case the given directionality doesn't match the context directionality,
   * wraps the string with a 'span' element and adds a 'dir' attribute (either 'dir=\"rtl\"' or
   * 'dir=\"ltr\"').
   *
   * <p>If the formatter was built using {@link #alwaysSpan(true)}, the input is always wrapped in a
   * span, skipping just the dir attribute when it's not needed.
   *
   * <p>If {@code isolate}, directionally isolates the string so that it does not garble its
   * surroundings. Currently, this is done by "resetting" the directionality after the string by
   * appending a trailing Unicode bidi mark matching the context directionality (LRM or RLM) when
   * either the overall directionality or the exit directionality of the string is opposite to that
   * of the context. If the formatter was built using {@link #stereoReset(true)}, also prepends a
   * Unicode bidi mark matching the context directionality when either the overall directionality or
   * the entry directionality of the string is opposite to that of the context. Note that as opposed
   * to the overall directionality, the entry and exit directionalities are determined from the
   * string itself.
   *
   * <p>If !{@code isHtml}, HTML-escapes the string regardless of wrapping.
   *
   * @param dir {@code str}'s directionality. If null, i.e. unknown, it is estimated.
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param isolate Whether to directionally isolate the string to prevent it from garbling the
   *     content around it
   * @return Input string after applying the above processing.
   */
  public String spanWrapWithKnownDir(
      @Nullable Dir dir, String str, boolean isHtml, boolean isolate) {
    if (dir == null) {
      dir = estimateDirection(str, isHtml);
    }
    String origStr = str;
    if (!isHtml) {
      str = HtmlEscapers.htmlEscaper().escape(str);
    }

    StringBuilder result = new StringBuilder();
    if (getStereoReset() && isolate) {
      result.append(markBeforeKnownDir(dir, origStr, isHtml));
    }
    boolean dirCondition = (dir != Dir.NEUTRAL && dir != contextDir);
    if (getAlwaysSpan() || dirCondition) {
      result.append("<span");
      if (dirCondition) {
        result.append(' ').append(dir == Dir.RTL ? "dir=\"rtl\"" : "dir=\"ltr\"");
      }
      result.append('>').append(str).append("</span>");
    } else {
      result.append(str);
    }
    if (isolate) {
      result.append(markAfterKnownDir(dir, origStr, isHtml));
    }
    return result.toString();
  }

  /**
   * Operates like {@link #spanWrapWithKnownDir(Dir, String, boolean, boolean)}, but assumes {@code
   * isolate} is true.
   *
   * @param dir {@code str}'s directionality
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Input string after applying the above processing.
   */
  public String spanWrapWithKnownDir(@Nullable Dir dir, String str, boolean isHtml) {
    return spanWrapWithKnownDir(dir, str, isHtml, true);
  }

  /**
   * Operates like {@link #spanWrapWithKnownDir(Dir, String, boolean, boolean)}, but assumes {@code
   * isHtml} is false and {@code isolate} is true.
   *
   * @param dir {@code str}'s directionality
   * @param str The input string
   * @return Input string after applying the above processing.
   */
  public String spanWrapWithKnownDir(@Nullable Dir dir, String str) {
    return spanWrapWithKnownDir(dir, str, false, true);
  }

  /**
   * Formats a given string of unknown directionality for use in plain-text output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles its
   * surroundings. As opposed to {@link #spanWrap}, this makes use of Unicode bidi formatting
   * characters. In HTML, its *only* valid use is inside elements within which markup is not
   * allowed, e.g. the 'option' and 'title' elements.
   *
   * <p>The algorithm: estimates the directionality of the given string. In case it doesn't match
   * the context directionality, wraps it with Unicode bidi formatting characters: RLE+{@code
   * str}+PDF for RTL text, or LRE+{@code str}+PDF for LTR text.
   *
   * <p>If {@code isolate}, directionally isolates the string so that it does not garble its
   * surroundings. Currently, this is done by "resetting" the directionality after the string by
   * appending a trailing Unicode bidi mark matching the context directionality (LRM or RLM) when
   * either the overall directionality or the exit directionality of the string is opposite to that
   * of the context. If the formatter was built using {@link #stereoReset(true)}, also prepends a
   * Unicode bidi mark matching the context directionality when either the overall directionality or
   * the entry directionality of the string is opposite to that of the context.
   *
   * <p>Does *not* do HTML-escaping regardless of the value of {@code isHtml}.
   *
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param isolate Whether to directionally isolate the string to prevent it from garbling the
   *     content around it
   * @return Input string after applying the above processing.
   */
  public String unicodeWrap(String str, boolean isHtml, boolean isolate) {
    return unicodeWrapWithKnownDir(null, str, isHtml, isolate);
  }

  /**
   * Operates like {@link #unicodeWrap(String, boolean, boolean)}, but assumes {@code isolate} is
   * true.
   *
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Input string after applying the above processing.
   */
  public String unicodeWrap(String str, boolean isHtml) {
    return unicodeWrap(str, isHtml, true);
  }

  /**
   * Operates like {@link #unicodeWrap(String, boolean, boolean)}, but assumes {@code isHtml} is
   * false and {@code isolate} is true.
   *
   * @param str The input string
   * @return Input string after applying the above processing.
   */
  public String unicodeWrap(String str) {
    return unicodeWrap(str, false, true);
  }

  /**
   * Formats a string of given directionality for use in plain-text output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles its
   * surroundings. As opposed to {@link #spanWrapWithKnownDir}, this makes use of Unicode bidi
   * formatting characters. In HTML, its *only* valid use is inside of elements that do not allow
   * markup, e.g. the 'option' and 'title' elements.
   *
   * <p>The algorithm: In case the given directionality doesn't match the context directionality,
   * wraps the string with Unicode bidi formatting characters: RLE+{@code str}+PDF for RTL text, or
   * LRE+{@code str}+PDF for LTR text.
   *
   * <p>If {@code isolate}, directionally isolates the string so that it does not garble its
   * surroundings. Currently, this is done by "resetting" the directionality after the string by
   * appending a trailing Unicode bidi mark matching the context directionality (LRM or RLM) when
   * either the overall directionality or the exit directionality of the string is opposite to that
   * of the context. If the formatter was built using {@link #stereoReset(true)}, also prepends a
   * Unicode bidi mark matching the context directionality when either the overall directionality or
   * the entry directionality of the string is opposite to that of the context. Note that as opposed
   * to the overall directionality, the entry and exit directionalities are determined from the
   * string itself.
   *
   * <p>Does *not* do HTML-escaping regardless of the value of {@code isHtml}.
   *
   * @param dir {@code str}'s directionality. If null, i.e. unknown, it is estimated.
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param isolate Whether to directionally isolate the string to prevent it from garbling the
   *     content around it
   * @return Input string after applying the above processing.
   */
  public String unicodeWrapWithKnownDir(
      @Nullable Dir dir, String str, boolean isHtml, boolean isolate) {
    if (dir == null) {
      dir = estimateDirection(str, isHtml);
    }
    StringBuilder result = new StringBuilder();
    if (getStereoReset() && isolate) {
      result.append(markBeforeKnownDir(dir, str, isHtml));
    }
    if (dir != Dir.NEUTRAL && dir != contextDir) {
      result.append(dir == Dir.RTL ? BidiUtils.Format.RLE : BidiUtils.Format.LRE);
      result.append(str);
      result.append(BidiUtils.Format.PDF);
    } else {
      result.append(str);
    }
    if (isolate) {
      result.append(markAfterKnownDir(dir, str, isHtml));
    }
    return result.toString();
  }

  /**
   * Operates like {@link #unicodeWrapWithKnownDir(Dir, String, boolean, boolean)}, but assumes
   * {@code isolate} is true.
   *
   * @param dir {@code str}'s directionality
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Input string after applying the above processing.
   */
  public String unicodeWrapWithKnownDir(@Nullable Dir dir, String str, boolean isHtml) {
    return unicodeWrapWithKnownDir(dir, str, isHtml, true);
  }

  /**
   * Operates like {@link #unicodeWrapWithKnownDir(Dir, String, boolean, boolean)}, but assumes
   * {@code isHtml} is false and {@code isolate} is true.
   *
   * @param dir {@code str}'s directionality
   * @param str The input string
   * @return Input string after applying the above processing.
   */
  public String unicodeWrapWithKnownDir(@Nullable Dir dir, String str) {
    return unicodeWrapWithKnownDir(dir, str, false, true);
  }

  /**
   * Returns a Unicode bidi mark matching the context directionality (LRM or RLM) if either the
   * overall or the exit directionality of a given string is opposite to the context directionality.
   * Putting this after the string (including its directionality declaration wrapping) prevents it
   * from "sticking" to other opposite-directionality text or a number appearing after it inline
   * with only neutral content in between. Otherwise returns the empty string.
   *
   * @param str String after which the mark may need to appear
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markAfter(String str, boolean isHtml) {
    return markAfterKnownDir(null, str, isHtml);
  }

  /**
   * Operates like {@link #markAfter(String, boolean)}, but assumes {@code isHtml} is false.
   *
   * @param str String after which the mark may need to appear
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markAfter(String str) {
    return markAfter(str, false);
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
  public String markAfterKnownDir(@Nullable Dir dir, String str, boolean isHtml) {
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
   * Operates like {@link #markAfterKnownDir(Dir, String, boolean)}, but assumes that {@code isHtml}
   * is false.
   *
   * @param str The input string
   * @param dir {@code str}'s overall directionality
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markAfterKnownDir(@Nullable Dir dir, String str) {
    return markAfterKnownDir(dir, str, false);
  }

  /**
   * Returns a Unicode bidi mark matching the context directionality (LRM or RLM) if either the
   * overall or the entry directionality of a given string is opposite to the context
   * directionality. Putting this before the string (including its directionality declaration
   * wrapping) prevents it from "sticking" to other opposite-directionality text appearing before it
   * inline with only neutral content in between. Otherwise returns the empty string.
   *
   * @param str String before which the mark may need to appear
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markBefore(String str, boolean isHtml) {
    return markBeforeKnownDir(null, str, isHtml);
  }

  /**
   * Operates like {@link #markBefore(String, boolean)}, but assumes {@code isHtml} is false.
   *
   * @param str String before which the mark may need to appear
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markBefore(String str) {
    return markBefore(str, false);
  }

  /**
   * Returns a Unicode bidi mark matching the context directionality (LRM or RLM) if either the
   * overall or the entry directionality of a given string is opposite to the context
   * directionality. Putting this before the string (including its directionality declaration
   * wrapping) prevents it from "sticking" to other opposite-directionality text appearing before it
   * inline with only neutral content in between. Otherwise returns the empty string. While the
   * entry directionality is determined by scanning the beginning of the string, the overall
   * directionality is given explicitly in {@code dir}.
   *
   * @param str String before which the mark may need to appear
   * @param dir {@code str}'s overall directionality. If null, i.e. unknown, it is estimated.
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markBeforeKnownDir(@Nullable Dir dir, String str, boolean isHtml) {
    if (dir == null) {
      dir = estimateDirection(str, isHtml);
    }
    // BidiUtils.getExitDir() is called only if needed (short-circuit).
    if (contextDir == Dir.LTR
        && (dir == Dir.RTL || BidiUtils.getEntryDir(str, isHtml) == Dir.RTL)) {
      return BidiUtils.Format.LRM_STRING;
    }
    if (contextDir == Dir.RTL
        && (dir == Dir.LTR || BidiUtils.getEntryDir(str, isHtml) == Dir.LTR)) {
      return BidiUtils.Format.RLM_STRING;
    }
    return "";
  }

  /**
   * Operates like {@link #markBeforeKnownDir(Dir, String, boolean)}, but assumes that {@code
   * isHtml} is false.
   *
   * @param str String before which the mark may need to appear
   * @param dir {@code str}'s overall directionality
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; else, the empty
   *     string.
   */
  public String markBeforeKnownDir(@Nullable Dir dir, String str) {
    return markBeforeKnownDir(dir, str, false);
  }

  /**
   * Returns the Unicode bidi mark matching the context directionality (LRM for LTR context
   * directionality, RLM for RTL context directionality), or the empty string for unknown context
   * directionality.
   */
  public String mark() {
    return contextDir == Dir.LTR
        ? BidiUtils.Format.LRM_STRING
        : contextDir == Dir.RTL ? BidiUtils.Format.RLM_STRING : "";
  }

  /**
   * Returns "right" for RTL context directionality. Otherwise (LTR or unknown context
   * directionality) returns "left".
   */
  public String startEdge() {
    return contextDir == Dir.RTL ? BidiUtils.RIGHT : BidiUtils.LEFT;
  }

  /**
   * Returns "left" for RTL context directionality. Otherwise (LTR or unknown context
   * directionality) returns "right".
   */
  public String endEdge() {
    return contextDir == Dir.RTL ? BidiUtils.LEFT : BidiUtils.RIGHT;
  }

  /**
   * Estimates the directionality of a string using the best known general-purpose method, i.e.
   * using relative word counts. Dir.NEUTRAL return value indicates completely neutral input.
   *
   * @param str String whose directionality is to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return {@code str}'s estimated overall directionality
   */
  public static Dir estimateDirection(String str, boolean isHtml) {
    return BidiUtils.estimateDirection(str, isHtml);
  }
}
