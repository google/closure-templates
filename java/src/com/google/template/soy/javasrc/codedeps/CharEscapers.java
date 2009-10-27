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

package com.google.template.soy.javasrc.codedeps;


/**
 * Utility functions for dealing with {@code CharEscaper}s, and some commonly
 * used {@code CharEscaper} instances.
 *
 * @author Sven Mawson
 * @author Laurence Gonsalves
 */
public final class CharEscapers {

  private CharEscapers() {}


  // -----------------------------------------------------------------------------------------------
  // asciiHtmlEscaper used by $$escapeHtml.


  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in an HTML document in either element
   * content or attribute values.
   *
   * <p><b>Note</b></p>: does not alter non-ASCII and control characters.
   */
  public static CharEscaper asciiHtmlEscaper() {
    return ASCII_HTML_ESCAPER;
  }


  /**
   * Escapes special characters from a string so it can safely be included in an
   * HTML document in either element content or attribute values. Does
   * <em>not</em> alter non-ASCII characters or control characters.
   */
  private static final CharEscaper ASCII_HTML_ESCAPER =
      new CharEscaperBuilder()
          .addEscape('"', "&quot;")
          .addEscape('\'', "&#39;")
          .addEscape('&', "&amp;")
          .addEscape('<', "&lt;")
          .addEscape('>', "&gt;")
          .toEscaper();


  // -----------------------------------------------------------------------------------------------
  // uriEscaper used by $$escapeUri.


  /**
   * Returns a {@link Escaper} instance that escapes Java characters so they can
   * be safely included in URIs. For details on escaping URIs, see section 2.4
   * of <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC 2396</a>.
   *
   * <p>When encoding a String, the following rules apply:
   * <ul>
   * <li>The alphanumeric characters "a" through "z", "A" through "Z" and "0"
   *     through "9" remain the same.
   * <li>The special characters ".", "-", "*", and "_" remain the same.
   * <li>If {@code plusForSpace} was specified, the space character " " is
   *     converted into a plus sign "+". Otherwise it is converted into "%20".
   * <li>All other characters are converted into one or more bytes using UTF-8
   *     encoding and each byte is then represented by the 3-character string
   *     "%XY", where "XY" is the two-digit, uppercase, hexadecimal
   *     representation of the byte value.
   * </ul>
   *
   * <p><b>Note</b>: Unlike other escapers, URI escapers produce uppercase
   * hexidecimal sequences. From <a href="http://www.ietf.org/rfc/rfc3986.txt">
   * RFC 3986</a>:<br>
   * <i>"URI producers and normalizers should use uppercase hexadecimal digits
   * for all percent-encodings."</i>
   *
   * @param plusForSpace if {@code true} space is escaped to {@code +} otherwise
   *        it is escaped to {@code %20}. Although common, the escaping of
   *        spaces as plus signs has a very ambiguous status in the relevant
   *        specifications. You should prefer {@code %20} unless you are doing
   *        exact character-by-character comparisons of URLs and backwards
   *        compatibility requires you to use plus signs.
   */
  public static Escaper uriEscaper(boolean plusForSpace) {
    return plusForSpace ? URI_ESCAPER : URI_ESCAPER_NO_PLUS;
  }


  private static final Escaper URI_ESCAPER =
      new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, true);


  private static final Escaper URI_ESCAPER_NO_PLUS =
      new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, false);


  // -----------------------------------------------------------------------------------------------
  // javascriptEscaper used by $$escapeJs.


  /**
   * Returns a {@link CharEscaper} instance that escapes non-ASCII characters in
   * a string so it can safely be included in a Javascript string literal.
   * Non-ASCII characters are replaced with their ASCII javascript escape
   * sequences (e.g., \\uhhhh or \xhh).
   */
  public static CharEscaper javascriptEscaper() {
    return JAVASCRIPT_ESCAPER;
  }


  /**
   * {@code CharEscaper} to escape javascript strings. Turns all non-ASCII
   * characters into ASCII javascript escape sequences (e.g., \\uhhhh or \xhh).
   */
  private static final CharEscaper JAVASCRIPT_ESCAPER =
      new JavascriptCharEscaper(new CharEscaperBuilder()
          .addEscape('\'', "\\x27")
          .addEscape('"',  "\\x22")
          .addEscape('<',  "\\x3c")
          .addEscape('=',  "\\x3d")
          .addEscape('>',  "\\x3e")
          .addEscape('&',  "\\x26")
          .addEscape('\b', "\\b")
          .addEscape('\t', "\\t")
          .addEscape('\n', "\\n")
          .addEscape('\f', "\\f")
          .addEscape('\r', "\\r")
          .addEscape('\\', "\\\\")
          .toArray());


  // -----------------------------------------------------------------------------------------------
  // Implementations.


  /**
   * A fast {@link CharEscaper} that uses an array of replacement characters and
   * a range of safe characters. It overrides {@link #escape(String)} to improve
   * performance. Rough benchmarking shows that this almost doubles the speed
   * when processing strings that do not require escaping (providing the escape
   * test itself is efficient).
   */
  private abstract static class FastCharEscaper extends CharEscaper {

    protected final char[][] replacements;
    protected final int replacementLength;
    protected final char safeMin;
    protected final char safeMax;

    public FastCharEscaper(char[][] replacements, char safeMin, char safeMax) {
      this.replacements = replacements;
      this.replacementLength = replacements.length;
      this.safeMin = safeMin;
      this.safeMax = safeMax;
    }

    /** Overridden for performance (see {@link FastCharEscaper}). */
    @Override public String escape(String s) {
      int slen = s.length();
      for (int index = 0; index < slen; index++) {
        char c = s.charAt(index);
        if ((c < replacementLength && replacements[c] != null)
            || c < safeMin || c > safeMax) {
          return escapeSlow(s, index);
        }
      }
      return s;
    }
  }


  /**
   * Escaper for javascript character escaping, contains both an array and a
   * backup function. We're not overriding the array decorator because we
   * want to keep this as fast as possible, so no calls to super.escape first.
   */
  private static class JavascriptCharEscaper extends FastCharEscaper {

    public JavascriptCharEscaper(char[][] replacements) {
      super(replacements, ' ', '~');
    }

    @Override protected char[] escape(char c) {
      // First check if our array has a valid escaping.
      if (c < replacementLength) {
        char[] r = replacements[c];
        if (r != null) {
          return r;
        }
      }

      // This range is unescaped.
      if (safeMin <= c && c <= safeMax) {
        return null;
      }

      // we can do a 2 digit hex escape for chars less that 0x100
      if (c < 0x100) {
        char[] r = new char[4];
        r[3] = HEX_DIGITS[c & 0xf];
        c >>>= 4;
        r[2] = HEX_DIGITS[c & 0xf];
        r[1] = 'x';
        r[0] = '\\';
        return r;
      }

      // 4 digit hex escape everything else
      char[] r = new char[6];
      r[5] = HEX_DIGITS[c & 0xf];
      c >>>= 4;
      r[4] = HEX_DIGITS[c & 0xf];
      c >>>= 4;
      r[3] = HEX_DIGITS[c & 0xf];
      c >>>= 4;
      r[2] = HEX_DIGITS[c & 0xf];
      r[1] = 'u';
      r[0] = '\\';
      return r;
    }
  }


  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

}
