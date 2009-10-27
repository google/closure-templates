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

import java.util.regex.Pattern;

/**
 * Utility functions for performing common Bidi tests on strings.
 */
public class BidiUtils {

  /**
   * Not instantiable.
   */
  private BidiUtils() {
  }

  /**
   * Enum for directionality type.
   */
  public enum Dir {
    LTR     (1),
    UNKNOWN (0),
    RTL     (-1);

    public final int ord;

    Dir(int ord) {this.ord = ord; }

    /**
     * Interprets numeric representation of directionality: positive values are
     * interpreted as RTL, negative values as LTR, and zero as UNKNOWN.
     * These specific numeric values are standard for directionality
     * representation in Soy
     * {@link com.google.template.soy.jssrc.SoyJsSrcOptions#setBidiGlobalDir}.
     */
    public static Dir valueOf(int dir) {
      return dir > 0 ? LTR : dir < 0 ? RTL : UNKNOWN;
    }

    /**
     * Interprets boolean representation of directionality: false is interpreted
     * as LTR and true as RTL.
     */
    public static Dir valueOf(boolean dir) {
      return dir ? RTL : LTR;
    }

    /**
     * Returns whether this directionality is opposite to the given
     * directionality.
     */
    public boolean isOppositeTo(Dir dir) {
      return this.ord * dir.ord < 0;
    }
  }

  /**
   * A container class for Unicode formatting characters and for directionality
   * string constants.
   */
  public static final class Format {
    private Format () {}  // Not instantiable.
    /** Unicode "Left-To-Right Embedding" (LRE) character. */
    public static final char LRE = '\u202A';
    /** Unicode "Right-To-Left Embedding" (RLE) character. */
    public static final char RLE = '\u202B';
    /** Unicode "Pop Directional Formatting" (PDF) character. */
    public static final char PDF = '\u202C';
    /** Unicode "Left-To-Right Mark" (LRM) character. */
    public static final char LRM = '\u200E';
    /** Unicode "Right-To-Left Mark" (RLM) character. */
    public static final char RLM = '\u200F';

    // Holding also the String representation of LRM and RLM is useful for
    // several applications.
    public static final String LRM_STRING = Character.toString(LRM);
    public static final String RLM_STRING = Character.toString(RLM);
  }

  /**
   * Returns the directionality of the input language / locale.
   * Only knows the most common right-to-left languages, and ignores the script
   * subtag (if any).
   */
  public static Dir languageDir(String languageString) {
    return isRtlLanguage(languageString) ? Dir.RTL : Dir.LTR;
  }

  /**
   * A regular expression matching the common right-to-left language codes.
   * Not strictly correct - ignores the script subtag (if any).
   */
  private static final Pattern RtlLocalesRe =
      Pattern.compile("(?:ar|he|iw|fa|ps|ur|yi)(?:[-_].*)?");

  /**
   * Returns whether the language is RTL. Unfamiliar languages are assumed to be
   * LTR. Only knows the most common right-to-left languages, and ignores the
   * script subtag (if any).
   */
  public static boolean isRtlLanguage(String languageString) {
    return languageString != null &&
        RtlLocalesRe.matcher(languageString).matches();
  }

  /**
   * "right" string constant.
   */
  public static final String RIGHT = "right";

  /**
   * "left" string constant.
   */
  public static final String LEFT = "left";

  /**
   * Simplified regular expression for an HTML tag (opening or closing) or an
   * HTML escape. We might want to skip over such expressions when estimating
   * the text directionality.
   */
  private static final Pattern HtmlSkipRe = Pattern.compile("<[^>]*>|&[^;]+;");

  /**
   * Returns the input text with spaces instead of HTML tags or HTML escapes, if
   * isStripNeeded is true. Else returns the input as is.
   * Useful for text directionality estimation.
   * Note: the function should not be used in other contexts; it is not 100%
   * correct, but rather a good-enough implementation for directionality
   * estimation purposes.
   * This is package-private since it is used also by {@link BidiFormatter}.
   */
  static String stripHtmlIfNeeded(String str, boolean isStripNeeded) {
     return isStripNeeded ? HtmlSkipRe.matcher(str).replaceAll(" ") : str;
  }

  /**
   * A practical pattern to identify strong LTR characters. This pattern is not
   * completely correct according to the Unicode standard. It is simplified
   * for performance and small code size.
   */
  private static final String ltrChars =
    "A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF" +
    "\u2C00-\uFB1C\uFDFE-\uFE6F\uFEFD-\uFFFF";

  /**
   * A practical pattern to identify strong RTL characters. This pattern is not
   * completely correct according to the Unicode standard. It is simplified for
   * performance and small code size.
   */
  private static final String rtlChars =
      "\u0591-\u07FF\uFB1D-\uFDFD\uFE70-\uFEFC";

  /**
   * Regular expression to check if a string contains any LTR characters.
   */
  private static final Pattern hasAnyLtrRe =
      Pattern.compile("[" + ltrChars + ']');

  /**
   * Regular expression to check if a string contains any RTL characters.
   */
  private static final Pattern hasAnyRtlRe =
      Pattern.compile("[" + rtlChars + ']');

  /**
   * Checks if the given string has any LTR characters in it.
   * @param str the string to be tested
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether the string contains any LTR characters
   */
  public static boolean hasAnyLtr(String str, boolean isHtml) {
    return hasAnyLtr(stripHtmlIfNeeded(str, isHtml));
  }

  /**
   * Like {@link #hasAnyLtr(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   * @param str the string to be tested
   * @return whether the string contains any LTR characters
   */
  public static boolean hasAnyLtr(String str) {
    return hasAnyLtrRe.matcher(str).find();
  }

  /**
   * Checks if the given string has any RTL characters in it.
   * @param str the string to be tested
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether the string contains any RTL characters
   */
  public static boolean hasAnyRtl(String str, boolean isHtml) {
    return hasAnyRtl(stripHtmlIfNeeded(str, isHtml));
  }

  /**
   * Like {@link #hasAnyRtl(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   * @param str the string to be tested
   * @return whether the string contains any RTL characters
   */
  public static boolean hasAnyRtl(String str) {
    return hasAnyRtlRe.matcher(str).find();
  }

  /**
   * Regular expression to check if a string is LTR
   * based on the first character with strong directionality.
   */
  private static final Pattern firstStrongIsLtrRe =
      Pattern.compile("^[^" + rtlChars + "]*[" + ltrChars + ']');

  /**
   * Regular expression to check if a string is RTL
   * based on the first character with strong directionality.
   */
  private static final Pattern firstStrongIsRtlRe =
      Pattern.compile("^[^" + ltrChars + "]*[" + rtlChars + ']');

  /**
   * Returns true if the first character with strong directionality
   * is an LTR character.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return true if LTR directionality is detected
   */
  public static boolean startsWithLtr(String str, boolean isHtml) {
    return startsWithLtr(stripHtmlIfNeeded(str, isHtml));
  }

  /**
   * Like {@link #startsWithLtr(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   * @param str the string to check
   * @return true if LTR directionality is detected
   */
  public static boolean startsWithLtr(String str) {
    return firstStrongIsLtrRe.matcher(str).lookingAt();
  }

  /**
   * Returns true if the first character with strong directionality
   * is an RTL character.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return true if rtl directionality is detected
   */
  public static boolean startsWithRtl(String str, boolean isHtml) {
    return startsWithRtl(stripHtmlIfNeeded(str, isHtml));
  }

  /**
   * Like {@link #startsWithRtl(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   * @param str the string to check
   * @return true if rtl directionality is detected
   */
  public static boolean startsWithRtl(String str) {
    return firstStrongIsRtlRe.matcher(str).lookingAt();
  }

  /**
   * Regular expressions to check if the last strongly-directional character in
   * a piece of text is LTR.
   */
  private static final Pattern lastStrongIsLtrRe =
      Pattern.compile("[" + ltrChars + "][^" + rtlChars + "]*$");

  /**
   * Regular expressions to check if the last strongly-directional character in
   * a piece of text is RTL.
   */
  private static final Pattern lastStrongIsRtlRe =
      Pattern.compile("[" + rtlChars + "][^" + ltrChars + "]*$");

  /**
   * Check whether the exit directionality of a piece of text is LTR, i.e. if
   * the last strongly-directional character in the string is LTR.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether LTR exit directionality was detected
   */
  public static boolean endsWithLtr(String str, boolean isHtml) {
    return endsWithLtr(stripHtmlIfNeeded(str, isHtml));
  }

  /**
   * Like {@link #endsWithLtr(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   */
  public static boolean endsWithLtr(String str) {
    return lastStrongIsLtrRe.matcher(str).find();
  }

  /**
   * Check whether the exit directionality of a piece of text is RTL, i.e. if
   * the last strongly-directional character in the string is RTL.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   * @return whether RTL exit directionality was detected
   */
  public static boolean endsWithRtl(String str, boolean isHtml) {
    return endsWithRtl(stripHtmlIfNeeded(str, isHtml));
  }

  /**
   * Like {@link #endsWithRtl(String, boolean)}, but assumes
   * {@code str} is not HTML / HTML-escaped.
   */
  public static boolean endsWithRtl(String str) {
    return lastStrongIsRtlRe.matcher(str).find();
  }

  /**
   * Regular expression to split a string into "words" for directionality estimation based on
   * relative word counts.
   */
  private static final Pattern wordSeparatorRe = Pattern.compile("\\s+");

  /**
   * Regular expression to check if a string looks like something that must always be LTR even in
   * RTL text, e.g. a URL. When estimating the directionality of text containing these, we treat
   * these as weakly LTR, like numbers.
   */
  private static final Pattern isRequiredLtrRe = Pattern.compile("^http://.*");

  /**
   * Regular expression to check if a string contains any numerals. Used to differentiate between
   * completely neutral strings and those containing numbers, which are weakly LTR.
   */
  private static final Pattern hasNumeralsRe = Pattern.compile("\\d");

  /**
   * This constant defines the threshold of RTL directionality.
   */
  private static final float rtlDetectionThreshold = 0.40f;

  /**
   * Estimates the directionality of a string based on relative word counts.
   * If the number of RTL words is above a certain percentage of the total number of strongly
   * directional words, returns RTL.
   * Otherwise, if any words are strongly or weakly LTR, returns LTR.
   * Otherwise, returns UNKNOWN, which is used to mean "neutral".
   * Numbers are counted as weakly LTR.
   * @param str the string to check
   * @return the string's directionality
   */
  public static Dir estimateDirection(String str) {
    int rtlCount = 0;
    int total = 0;
    boolean hasWeaklyLtr = false;
    String[] tokens = wordSeparatorRe.split(str);
    for (int i = 0; i < tokens.length; i++) {
      String token = tokens[i];
      if (startsWithRtl(token)) {
        rtlCount++;
        total++;
      } else if (isRequiredLtrRe.matcher(token).matches()) {
        hasWeaklyLtr = true;
      } else if (hasAnyLtr(token)) {
        total++;
      } else if (hasNumeralsRe.matcher(token).find()) {
        hasWeaklyLtr = true;
      }
    }

    return total == 0 ? (hasWeaklyLtr ? Dir.LTR : Dir.UNKNOWN)
        : ((float) rtlCount / total > rtlDetectionThreshold ? Dir.RTL : Dir.LTR);
  }

  /**
   * Like {@link #estimateDirection(String)}, but can treat {@code str} as HTML,
   * ignoring HTML tags and escapes that would otherwise be mistaken for LTR text.
   * @param str the string to check
   * @param isHtml whether str is HTML / HTML-escaped
   */
  public static Dir estimateDirection(String str, boolean isHtml) {
    return estimateDirection(stripHtmlIfNeeded(str, isHtml));
  }
}
