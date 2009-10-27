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

import com.google.common.base.CharEscapers;

import java.util.Locale;


/**
 * Utility class for formatting text for display in a potentially opposite-directionality context
 * without garbling. The directionality of the context is set at formatter creation and the
 * directionality of the text can be either estimated or passed in when known. Provides the
 * following functionality:
 * <p>
 * 1. BiDi Wrapping
 * When text in one language is mixed into a document in another, opposite-directionality language,
 * e.g. when an English business name is embedded in a Hebrew web page, both the inserted string
 * and the text following it may be displayed incorrectly unless the inserted string is explicitly
 * separated from the surrounding text in a "wrapper" that declares its directionality at the start
 * and then resets it back at the end. This wrapping can be done in HTML mark-up (e.g. a
 * 'span dir=rtl' tag) or - only in contexts where mark-up cannot be used - in Unicode BiDi
 * formatting codes (LRE|RLE and PDF). Optionally, the mark-upp can be inserted even when the
 * directionality the same, in order to keep the DOM structure more stable. Providing such wrapping
 * services is the basic purpose of the BiDi formatter.
 * <p>
 * 2. Directionality estimation
 * How does one know whether a string about to be inserted into surrounding text has the same
 * directionality? Well, in many cases, one knows that this must be the case when writing the code
 * doing the insertion, e.g. when a localized message is inserted into a localized page. In such
 * cases there is no need to involve the BiDi formatter at all. In some other cases, it need not be
 * the same as the context, but is either constant (e.g. urls are always LTR) or otherwise known.
 * In the remaining cases, e.g. when the string is user-entered or comes from a database, the
 * language of the string (and thus its directionality) is not known a priori, and must be
 * estimated at run-time. The BiDi formatter can do this automatically.
 * <p>
 * 3. Escaping
 * When wrapping plain text - i.e. text that is not already HTML or HTML-escaped - in HTML mark-up,
 * the text must first be HTML-escaped to prevent XSS attacks and other nasty business. This of
 * course is always true, but the escaping can not be done after the string has already been wrapped
 * in mark-up, so the BiDi formatter also serves as a last chance and includes escaping services.
 * <p>
 * Thus, in a single call, the formatter will escape the input string as specified, determine its
 * directionality, and wrap it as necessary. It is then up to the caller to insert the return value
 * in the output.
 */
public class BidiFormatter {

  private BidiUtils.Dir contextDir;
  private boolean alwaysSpan;

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality and the
   * desired span wrapping behavior (see below).
   *
   * @param contextDir The context directionality. Try not to use Dir.UNKNOWN, since it is
   * impossible to reset the directionality back to the context when it is unknown.
   * @param alwaysSpan Whether {@link #spanWrap} (and its variations) should always use a 'span'
   *     tag, even when the input directionality is neutral or matches the context, so that the DOM
   *     structure of the output does not depend on the combination of directionalities
   */
  public static BidiFormatter getInstance(BidiUtils.Dir contextDir, boolean alwaysSpan) {
    return new BidiFormatter(contextDir, alwaysSpan);
  }

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality. The
   * default behavior of {@link #spanWrap} and its variations is set to avoid span wrapping unless
   * there's a reason ('dir' attribute should be appended).
   *
   * @param contextDir The context directionality. Try not to use Dir.UNKNOWN, since it is
   * impossible to reset the directionality back to the context when it is unknown.
   */
  public static BidiFormatter getInstance(BidiUtils.Dir contextDir) {
    return getInstance(contextDir, false);
  }

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality and the
   * desired span wrapping behavior (see below).
   *
   * @param rtlContext Whether the context directionality is RTL
   * @param alwaysSpan Whether {@link #spanWrap} (and its variations) should always use a 'span'
   *     tag, even when the input directionality is neutral or matches the context, so that the DOM
   *     structure of the output does not depend on the combination of directionalities
   */
  public static BidiFormatter getInstance(boolean rtlContext, boolean alwaysSpan) {
    return new BidiFormatter(rtlContext ? BidiUtils.Dir.RTL : BidiUtils.Dir.LTR, alwaysSpan);
  }

  /**
   * Factory for creating an instance of BidiFormatter given the context directionality. The
   * default behavior of {@link #spanWrap} and its variations is set to avoid span wrapping unless
   * there's a reason ('dir' attribute should be appended).
   *
   * @param rtlContext Whether the context directionality is RTL
   */
  public static BidiFormatter getInstance(boolean rtlContext) {
    return getInstance(rtlContext, false);
  }

  /**
   * Factory for creating an instance of BidiFormatter given the context locale and the desired
   * span wrapping behavior (see below).
   *
   * @param locale The context locale
   * @param alwaysSpan Whether {@link #spanWrap} (and its variations) should always use a 'span'
   *     tag, even when the input directionality is neutral or matches the context, so that the DOM
   *     structure of the output does not depend on the combination of directionalities
   */
  public static BidiFormatter getInstance(Locale locale, boolean alwaysSpan) {
    return getInstance(BidiUtils.languageDir(locale.toString()), alwaysSpan);
  }

  /**
   * Factory for creating an instance of BidiFormatter given the context locale. The default
   * behavior of {@link #spanWrap} and its variations is set to avoid span wrapping unless there's a
   * reason ('dir' attribute should be appended).
   *
   * @param locale The context locale
   */
  public static BidiFormatter getInstance(Locale locale) {
    return getInstance(locale, false);
  }

  /**
   * @param contextDir The context directionality
   * @param alwaysSpan Whether {@link #spanWrap} (and its variations) should always use a 'span'
   *     tag, even when the input directionality is neutral or matches the context, so that the DOM
   *     structure of the output does not depend on the combination of directionalities
   */
  private BidiFormatter(BidiUtils.Dir contextDir, boolean alwaysSpan) {
    this.contextDir = contextDir;
    this.alwaysSpan = alwaysSpan;
  }

  /**
   * @return the context directionality
   */
  public BidiUtils.Dir getContextDir() {
    return contextDir;
  }

  /**
   * @return whether the context directionality is RTL
   */
  public boolean isRtlContext() {
    return contextDir == BidiUtils.Dir.RTL;
  }

  /**
   * @return whether the span structure added by the formatter should be stable, i.e. spans added
   * even when the directionality does not need to be declared
   */
  public boolean getAlwaysSpan() {
    return alwaysSpan;
  }

  /**
   * Returns "rtl" if {@code str}'s estimated directionality is RTL, and "ltr" if it is LTR. In case
   * it's UNKNOWN, returns "rtl" if the context directionality is RTL, and "ltr" otherwise.
   * Needed for GXP, which can't handle dirAttr.<p>
   * Example use case: <td expr:dir='bidiFormatter.dirAttrValue(foo)'><gxp:eval expr='foo'></td> 
   *
   * @param str String whose directionality is to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return "rtl" if {@code str}'s estimated directionality is RTL, and "ltr" otherwise.
   */
  public String dirAttrValue(String str, boolean isHtml) {
    return knownDirAttrValue(detectDirectionality(str, isHtml));
  }

  /**
   * Operates like {@link #dirAttrValue(String, boolean)}, but assumes {@code isHtml} is false.
   */
  public String dirAttrValue(String str) {
    return dirAttrValue(str, false);
  }

  /**
   * Returns "rtl" if the given directionality is RTL, and "ltr" if it is LTR. In case the given
   * directionality is UNKNOWN, returns "rtl" if the context directionality is RTL, and "ltr"
   * otherwise.
   *
   * @param dir Given directionality
   * @return "rtl" if the given directionality is RTL, and "ltr" otherwise.
   */
  public String knownDirAttrValue(BidiUtils.Dir dir) {
    if (dir == BidiUtils.Dir.UNKNOWN) {
      dir = contextDir;
    }

    return dir == BidiUtils.Dir.RTL ? "rtl" : "ltr";
  }

  /**
   * Returns "dir=ltr" or "dir=rtl", depending on {@code str}'s estimated directionality, if it is
   * not the same as the context directionality.
   * Otherwise, returns the empty string.
   *
   * @param str String whose directionality is to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return "dir=rtl" for RTL text in non-RTL context; "dir=ltr" for LTR text in non-LTR context;
   *     else, the empty string.
   */
  public String dirAttr(String str, boolean isHtml) {
    return knownDirAttr(detectDirectionality(str, isHtml));
  }

  /**
   * Operates like {@link #dirAttr(String, boolean)}, but assumes {@code isHtml} is false.
   */
  public String dirAttr(String str) {
    return dirAttr(str, false);
  }

  /**
   * Returns "dir=ltr" or "dir=rtl", depending on the given directionality, if it is not
   * the same as the context directionality.
   * Otherwise, returns the empty string.
   *
   * @param dir Given directionality
   * @return "dir=rtl" for RTL text in non-RTL context; "dir=ltr" for LTR text in non-LTR context;
   *     else, the empty string.
   */
  public String knownDirAttr(BidiUtils.Dir dir) {
    if (dir != contextDir) {
      return dir == BidiUtils.Dir.LTR ? "dir=ltr" : dir == BidiUtils.Dir.RTL ? "dir=rtl" : "";
    }
    return "";
  }

  /**
   * Formats a string of unknown directionality for use in HTML output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles what
   * follows it.<p>
   * The algorithm: estimates the directionality of input argument {@code str}. In case its
   * directionality doesn't match the context directionality, wraps it with a 'span' tag and adds a
   * "dir" attribute (either 'dir=rtl' or 'dir=ltr').<p>
   * If {@code setAlwaysSpan(true)} was used, the input is always wrapped with 'span', skipping just
   * the dir attribute when it's not needed.
   * <p>
   * If {@code dirReset}, and if the overall directionality or the exit directionality of
   * {@code str} are opposite to the context directionality, a trailing unicode BiDi mark matching
   * the context directionality is appended (LRM or RLM).
   * <p>
   * If !{@code isHtml}, HTML-escapes {@code str} regardless of wrapping.
   *
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param dirReset Whether to append a trailing unicode bidi mark matching the context
   *     directionality, when needed, to prevent the possible garbling of whatever may follow
   *     {@code str}
   * @return Input string after applying the above processing.
   */
  public String spanWrap(String str, boolean isHtml, boolean dirReset) {
    BidiUtils.Dir dir = detectDirectionality(str, isHtml);
    return spanWrapWithKnownDir(dir, str, isHtml, dirReset);
  }

  /**
   * Operates like {@link #spanWrap(String, boolean, boolean)}, but assumes {@code dirReset} is
   * true.
   */
  public String spanWrap(String str, boolean isHtml) {
    return spanWrap(str, isHtml, true);
  }

  /**
   * Operates like {@link #spanWrap(String, boolean, boolean)}, but assumes {@code isHtml} is false
   * and {@code dirReset} is true.
   */
  public String spanWrap(String str) {
    return spanWrap(str, false, true);
  }

  /**
   * Formats a string of given directionality for use in HTML output of the context directionality,
   * so an opposite-directionality string is neither garbled nor garbles what follows it.<p>
   * The algorithm: estimates the directionality of input argument {@code str}. In case its
   * directionality doesn't match the context directionality, wraps it with a 'span' tag and adds a
   * "dir" attribute (either 'dir=rtl' or 'dir=ltr').<p>
   * If {@code setAlwaysSpan(true)} was used, the input is always wrapped with 'span', skipping just
   * the dir attribute when it's not needed.
   * <p>
   * If {@code dirReset}, and if the overall directionality or the exit directionality of
   * {@code str} are opposite to the context directionality, a trailing unicode BiDi mark matching
   * the context directionality is appended (LRM or RLM).
   * <p>
   * If !{@code isHtml}, HTML-escapes {@code str} regardless of wrapping.
   *
   * @param dir {@code str}'s directionality
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param dirReset Whether to append a trailing unicode bidi mark matching the context
   *     directionality, when needed, to prevent the possible garbling of whatever may follow
   *     {@code str}
   * @return Input string after applying the above processing.
   */
  public String spanWrapWithKnownDir(BidiUtils.Dir dir, String str, boolean isHtml,
      boolean dirReset) {
    boolean dirCondition = dir != BidiUtils.Dir.UNKNOWN && dir != contextDir;
    String origStr = str;
    if (!isHtml) {
      str = CharEscapers.asciiHtmlEscaper().escape(str);
    }

    StringBuilder result = new StringBuilder();
    if (alwaysSpan || dirCondition) {
      result.append("<span");
      if (dirCondition) {
        result.append(" ");
        result.append(dir == BidiUtils.Dir.RTL ? "dir=rtl" : "dir=ltr");
      }
      result.append(">" + str + "</span>");
    } else {
      result.append(str);
    }
    // origStr is passed (more efficient when isHtml is false).
    result.append(dirResetIfNeeded(origStr, dir, isHtml, dirReset));
    return result.toString();
  }

  /**
   * Operates like {@link #spanWrapWithKnownDir(BidiUtils.Dir, String, boolean, boolean)}, but
   * assumes {@code dirReset} is true.
   */
  public String spanWrapWithKnownDir(BidiUtils.Dir dir, String str, boolean isHtml) {
    return spanWrapWithKnownDir(dir, str, isHtml, true);
  }

  /**
   * Operates like {@link #spanWrapWithKnownDir(BidiUtils.Dir, String, boolean, boolean)}, but
   * assumes {@code isHtml} is false and {@code dirReset} is true.
   */
  public String spanWrapWithKnownDir(BidiUtils.Dir dir, String str) {
    return spanWrapWithKnownDir(dir, str, false, true);
  }

  /**
   * Formats a string of unknown directionality for use in plain-text output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles what
   * follows it. As opposed to {@link #spanWrap}, this makes use of unicode BiDi formatting
   * characters. In HTML, its *only* valid use is inside of elements that do not allow mark-up, e.g.
   * an 'option' tag.<p>
   * The algorithm: estimates the directionality of input argument {@code str}. In case it doesn't
   * match  the context directionality, wraps it with Unicode BiDi formatting characters:
   * RLE+{@code str}+PDF for RTL text, or LRE+{@code str}+PDF for LTR text.
   * <p>
   * If {@code opt_dirReset}, and if the overall directionality or the exit directionality of
   * {@code str} are opposite to the context directionality, a trailing unicode BiDi mark matching
   * the context directionality is appended (LRM or RLM).
   * <p>
   * Does *not* do HTML-escaping regardless of the value of {@code isHtml}.
   *
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param dirReset Whether to append a trailing unicode bidi mark matching the context
   *     directionality, when needed, to prevent the possible garbling of whatever may follow
   *     {@code str}
   * @return Input string after applying the above processing.
   */
  public String unicodeWrap(String str, boolean isHtml, boolean dirReset) {
    BidiUtils.Dir dir = detectDirectionality(str, isHtml);
    return unicodeWrapWithKnownDir(dir, str, isHtml, dirReset);
  }

  /**
   * Operates like {@link #unicodeWrap(String, boolean, boolean)}, but assumes {@code dirReset} is
   * true.
   */
  public String unicodeWrap(String str, boolean isHtml) {
    return unicodeWrap(str, isHtml, true);
  }

  /**
   * Operates like {@link #unicodeWrap(String, boolean, boolean)}, but assumes {@code isHtml} is
   * false and {@code dirReset} is true.
   */
  public String unicodeWrap(String str) {
    return unicodeWrap(str, false, true);
  }

  /**
   * Formats a string of given directionality for use in plain-text output of the context
   * directionality, so an opposite-directionality string is neither garbled nor garbles what
   * follows it. As opposed to {@link #spanWrapWithKnownDir}, this makes use of unicode BiDi
   * formatting characters. In HTML, its *only* valid use is inside of elements that do not allow
   * mark-up, e.g. an 'option' tag.<p>
   * The algorithm: estimates the directionality of input argument {@code str}. In case it doesn't
   * match  the context directionality, wraps it with Unicode BiDi formatting characters:
   * RLE+{@code str}+PDF for RTL text, or LRE+{@code str}+PDF for LTR text.
   * <p>
   * If {@code opt_dirReset}, and if the overall directionality or the exit directionality of
   * {@code str} are opposite to the context directionality, a trailing unicode BiDi mark matching
   * the context directionality is appended (LRM or RLM).
   * <p>
   * Does *not* do HTML-escaping regardless of the value of {@code isHtml}.
   *
   * @param dir {@code str}'s directionality
   * @param str The input string
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param dirReset Whether to append a trailing unicode bidi mark matching the context
   *     directionality, when needed, to prevent the possible garbling of whatever may follow
   *     {@code str}
   * @return Input string after applying the above processing.
   */
  public String unicodeWrapWithKnownDir(BidiUtils.Dir dir, String str, boolean isHtml,
      boolean dirReset) {
    StringBuilder result = new StringBuilder();
    if (dir != BidiUtils.Dir.UNKNOWN && dir != contextDir) {
      result.append(dir == BidiUtils.Dir.RTL ? BidiUtils.Format.RLE : BidiUtils.Format.LRE);
      result.append(str);
      result.append(BidiUtils.Format.PDF);
    } else {
      result.append(str);
    }

    result.append(dirResetIfNeeded(str, dir, isHtml, dirReset));
    return result.toString();
  }

  /**
   * Operates like {@link #unicodeWrapWithKnownDir(BidiUtils.Dir, String, boolean, boolean)}, but
   * assumes {@code dirReset} is true.
   */
  public String unicodeWrapWithKnownDir(String str, boolean isHtml) {
    return unicodeWrap(str, isHtml, true);
  }

  /**
   * Operates like {@link #unicodeWrapWithKnownDir(BidiUtils.Dir, String, boolean, boolean)}, but
   * assumes {@code isHtml} is false and {@code dirReset} is true.
   */
  public String unicodeWrapWithKnownDir(String str) {
    return unicodeWrap(str, false, true);
  }

  /**
   * Returns a Unicode BiDi mark matching the context directionality 
   * (LRM or RLM) if either the directionality or the exit directionality of 
   * {@code str} is opposite to the context directionality. Otherwise returns 
   * the empty string.
   *
   * @param str string to be estimated
   * @param isHtml whether {@code str} is HTML / HTML-escaped
   * @return LRM for RTL text in LTR context; RLM for LTR text in RTL context; 
   *     else, the empty string.
   */
  public String markAfter(String str, boolean isHtml) {
    str = BidiUtils.stripHtmlIfNeeded(str, isHtml);
    return dirResetIfNeeded(str, detectDirectionality(str, false), false, true);
  }

  /**
   * Operates like {@link #markAfter(String, boolean)}, but assumes 
   * {@code isHtml} is false.
   */
  public String markAfter(String str) {
    return markAfter(str, false);
  }

  /**
   * Returns the Unicode BiDi mark matching the context directionality (LRM for LTR context
   * directionality, RLM for RTL context directionality), or the empty string for neutral / unknown
   * context directionality.
   */
  public String mark() {
    return contextDir == BidiUtils.Dir.LTR ? BidiUtils.Format.LRM_STRING :
           contextDir == BidiUtils.Dir.RTL ? BidiUtils.Format.RLM_STRING : "";
  }

  /**
   * Returns "right" for RTL context directionality. Otherwise (LTR or neutral / unknown context
   * directionality) returns "left".
   */
  public String startEdge() {
    return contextDir == BidiUtils.Dir.RTL ? BidiUtils.RIGHT : BidiUtils.LEFT;
  }

  /**
   * Returns "left" for RTL context directionality. Otherwise (LTR or neutral / unknown context
   * directionality) returns "right".
   */
  public String endEdge() {
    return contextDir == BidiUtils.Dir.RTL ? BidiUtils.LEFT : BidiUtils.RIGHT;
  }

  /**
   * Returns the estimated overall directionality of input argument {@code str}. Dir.UNKNOWN
   * indicates a completely neutral input.
   *
   * @param str String to be estimated
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @return Overall directionality estimation of input string.
   */
  static BidiUtils.Dir detectDirectionality(String str, boolean isHtml) {
    return BidiUtils.estimateDirection(str, isHtml);
  }

  /**
   * Returns a unicode BiDi mark matching the context directionality (LRM or RLM) if
   * {@code dirReset}, and if the overall directionality or the exit directionality of {@code str}
   * are opposite to the context directionality.
   * Otherwise returns the empty string.
   *
   * @param str The input string
   * @param dir {@code str}'s overall directionality
   * @param isHtml Whether {@code str} is HTML / HTML-escaped
   * @param dirReset Whether to perform the reset
   * @return A unicode BiDi mark or the empty string.
   */
  private String dirResetIfNeeded(String str, BidiUtils.Dir dir, boolean isHtml, boolean dirReset) {
    // endsWithRtl and endsWithLtr are called only if needed (short-circuit).
    if (dirReset &&
        ((contextDir == BidiUtils.Dir.LTR &&
          (dir == BidiUtils.Dir.RTL || BidiUtils.endsWithRtl(str, isHtml))) ||
         (contextDir == BidiUtils.Dir.RTL &&
          (dir == BidiUtils.Dir.LTR || BidiUtils.endsWithLtr(str, isHtml))))) {
     return contextDir == BidiUtils.Dir.LTR ? BidiUtils.Format.LRM_STRING :
                                              BidiUtils.Format.RLM_STRING;
    } else {
      return "";
    }
  }
}
