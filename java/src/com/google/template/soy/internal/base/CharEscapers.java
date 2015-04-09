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

package com.google.template.soy.internal.base;

import com.google.common.escape.CharEscaper;
import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import com.google.common.net.PercentEscaper;

/**
 * Utility functions for dealing with {@code CharEscaper}s, and some commonly
 * used {@code CharEscaper} instances.
 *
 */
public final class CharEscapers {

  private CharEscapers() {}
  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in an HTML document in either element
   * content or attribute values.
   *
   * <p><b>Note</b></p>: alters non-ASCII and control characters.
   *
   * The entity list was taken from:
   * <a href="http://www.w3.org/TR/html4/sgml/entities.html">here</a>
   */
  public static CharEscaper htmlEscaper() {
    return HtmlEscaperHolder.HTML_ESCAPER;
  }

  /**
   * A lazy initialization holder for HTML_ESCAPER.
   */
  private static class HtmlEscaperHolder {
    private static final CharEscaper HTML_ESCAPER
        = new HtmlCharEscaper(new CharEscaperBuilder()
            .addEscape('"',      "&quot;")
            .addEscape('\'',     "&#39;")
            .addEscape('&',      "&amp;")
            .addEscape('<',      "&lt;")
            .addEscape('>',      "&gt;")
            .addEscape('\u00A0', "&nbsp;")
            .addEscape('\u00A1', "&iexcl;")
            .addEscape('\u00A2', "&cent;")
            .addEscape('\u00A3', "&pound;")
            .addEscape('\u00A4', "&curren;")
            .addEscape('\u00A5', "&yen;")
            .addEscape('\u00A6', "&brvbar;")
            .addEscape('\u00A7', "&sect;")
            .addEscape('\u00A8', "&uml;")
            .addEscape('\u00A9', "&copy;")
            .addEscape('\u00AA', "&ordf;")
            .addEscape('\u00AB', "&laquo;")
            .addEscape('\u00AC', "&not;")
            .addEscape('\u00AD', "&shy;")
            .addEscape('\u00AE', "&reg;")
            .addEscape('\u00AF', "&macr;")
            .addEscape('\u00B0', "&deg;")
            .addEscape('\u00B1', "&plusmn;")
            .addEscape('\u00B2', "&sup2;")
            .addEscape('\u00B3', "&sup3;")
            .addEscape('\u00B4', "&acute;")
            .addEscape('\u00B5', "&micro;")
            .addEscape('\u00B6', "&para;")
            .addEscape('\u00B7', "&middot;")
            .addEscape('\u00B8', "&cedil;")
            .addEscape('\u00B9', "&sup1;")
            .addEscape('\u00BA', "&ordm;")
            .addEscape('\u00BB', "&raquo;")
            .addEscape('\u00BC', "&frac14;")
            .addEscape('\u00BD', "&frac12;")
            .addEscape('\u00BE', "&frac34;")
            .addEscape('\u00BF', "&iquest;")
            .addEscape('\u00C0', "&Agrave;")
            .addEscape('\u00C1', "&Aacute;")
            .addEscape('\u00C2', "&Acirc;")
            .addEscape('\u00C3', "&Atilde;")
            .addEscape('\u00C4', "&Auml;")
            .addEscape('\u00C5', "&Aring;")
            .addEscape('\u00C6', "&AElig;")
            .addEscape('\u00C7', "&Ccedil;")
            .addEscape('\u00C8', "&Egrave;")
            .addEscape('\u00C9', "&Eacute;")
            .addEscape('\u00CA', "&Ecirc;")
            .addEscape('\u00CB', "&Euml;")
            .addEscape('\u00CC', "&Igrave;")
            .addEscape('\u00CD', "&Iacute;")
            .addEscape('\u00CE', "&Icirc;")
            .addEscape('\u00CF', "&Iuml;")
            .addEscape('\u00D0', "&ETH;")
            .addEscape('\u00D1', "&Ntilde;")
            .addEscape('\u00D2', "&Ograve;")
            .addEscape('\u00D3', "&Oacute;")
            .addEscape('\u00D4', "&Ocirc;")
            .addEscape('\u00D5', "&Otilde;")
            .addEscape('\u00D6', "&Ouml;")
            .addEscape('\u00D7', "&times;")
            .addEscape('\u00D8', "&Oslash;")
            .addEscape('\u00D9', "&Ugrave;")
            .addEscape('\u00DA', "&Uacute;")
            .addEscape('\u00DB', "&Ucirc;")
            .addEscape('\u00DC', "&Uuml;")
            .addEscape('\u00DD', "&Yacute;")
            .addEscape('\u00DE', "&THORN;")
            .addEscape('\u00DF', "&szlig;")
            .addEscape('\u00E0', "&agrave;")
            .addEscape('\u00E1', "&aacute;")
            .addEscape('\u00E2', "&acirc;")
            .addEscape('\u00E3', "&atilde;")
            .addEscape('\u00E4', "&auml;")
            .addEscape('\u00E5', "&aring;")
            .addEscape('\u00E6', "&aelig;")
            .addEscape('\u00E7', "&ccedil;")
            .addEscape('\u00E8', "&egrave;")
            .addEscape('\u00E9', "&eacute;")
            .addEscape('\u00EA', "&ecirc;")
            .addEscape('\u00EB', "&euml;")
            .addEscape('\u00EC', "&igrave;")
            .addEscape('\u00ED', "&iacute;")
            .addEscape('\u00EE', "&icirc;")
            .addEscape('\u00EF', "&iuml;")
            .addEscape('\u00F0', "&eth;")
            .addEscape('\u00F1', "&ntilde;")
            .addEscape('\u00F2', "&ograve;")
            .addEscape('\u00F3', "&oacute;")
            .addEscape('\u00F4', "&ocirc;")
            .addEscape('\u00F5', "&otilde;")
            .addEscape('\u00F6', "&ouml;")
            .addEscape('\u00F7', "&divide;")
            .addEscape('\u00F8', "&oslash;")
            .addEscape('\u00F9', "&ugrave;")
            .addEscape('\u00FA', "&uacute;")
            .addEscape('\u00FB', "&ucirc;")
            .addEscape('\u00FC', "&uuml;")
            .addEscape('\u00FD', "&yacute;")
            .addEscape('\u00FE', "&thorn;")
            .addEscape('\u00FF', "&yuml;")
            .addEscape('\u0152', "&OElig;")
            .addEscape('\u0153', "&oelig;")
            .addEscape('\u0160', "&Scaron;")
            .addEscape('\u0161', "&scaron;")
            .addEscape('\u0178', "&Yuml;")
            .addEscape('\u0192', "&fnof;")
            .addEscape('\u02C6', "&circ;")
            .addEscape('\u02DC', "&tilde;")
            .addEscape('\u0391', "&Alpha;")
            .addEscape('\u0392', "&Beta;")
            .addEscape('\u0393', "&Gamma;")
            .addEscape('\u0394', "&Delta;")
            .addEscape('\u0395', "&Epsilon;")
            .addEscape('\u0396', "&Zeta;")
            .addEscape('\u0397', "&Eta;")
            .addEscape('\u0398', "&Theta;")
            .addEscape('\u0399', "&Iota;")
            .addEscape('\u039A', "&Kappa;")
            .addEscape('\u039B', "&Lambda;")
            .addEscape('\u039C', "&Mu;")
            .addEscape('\u039D', "&Nu;")
            .addEscape('\u039E', "&Xi;")
            .addEscape('\u039F', "&Omicron;")
            .addEscape('\u03A0', "&Pi;")
            .addEscape('\u03A1', "&Rho;")
            .addEscape('\u03A3', "&Sigma;")
            .addEscape('\u03A4', "&Tau;")
            .addEscape('\u03A5', "&Upsilon;")
            .addEscape('\u03A6', "&Phi;")
            .addEscape('\u03A7', "&Chi;")
            .addEscape('\u03A8', "&Psi;")
            .addEscape('\u03A9', "&Omega;")
            .addEscape('\u03B1', "&alpha;")
            .addEscape('\u03B2', "&beta;")
            .addEscape('\u03B3', "&gamma;")
            .addEscape('\u03B4', "&delta;")
            .addEscape('\u03B5', "&epsilon;")
            .addEscape('\u03B6', "&zeta;")
            .addEscape('\u03B7', "&eta;")
            .addEscape('\u03B8', "&theta;")
            .addEscape('\u03B9', "&iota;")
            .addEscape('\u03BA', "&kappa;")
            .addEscape('\u03BB', "&lambda;")
            .addEscape('\u03BC', "&mu;")
            .addEscape('\u03BD', "&nu;")
            .addEscape('\u03BE', "&xi;")
            .addEscape('\u03BF', "&omicron;")
            .addEscape('\u03C0', "&pi;")
            .addEscape('\u03C1', "&rho;")
            .addEscape('\u03C2', "&sigmaf;")
            .addEscape('\u03C3', "&sigma;")
            .addEscape('\u03C4', "&tau;")
            .addEscape('\u03C5', "&upsilon;")
            .addEscape('\u03C6', "&phi;")
            .addEscape('\u03C7', "&chi;")
            .addEscape('\u03C8', "&psi;")
            .addEscape('\u03C9', "&omega;")
            .addEscape('\u03D1', "&thetasym;")
            .addEscape('\u03D2', "&upsih;")
            .addEscape('\u03D6', "&piv;")
            .addEscape('\u2002', "&ensp;")
            .addEscape('\u2003', "&emsp;")
            .addEscape('\u2009', "&thinsp;")
            .addEscape('\u200C', "&zwnj;")
            .addEscape('\u200D', "&zwj;")
            .addEscape('\u200E', "&lrm;")
            .addEscape('\u200F', "&rlm;")
            .addEscape('\u2013', "&ndash;")
            .addEscape('\u2014', "&mdash;")
            .addEscape('\u2018', "&lsquo;")
            .addEscape('\u2019', "&rsquo;")
            .addEscape('\u201A', "&sbquo;")
            .addEscape('\u201C', "&ldquo;")
            .addEscape('\u201D', "&rdquo;")
            .addEscape('\u201E', "&bdquo;")
            .addEscape('\u2020', "&dagger;")
            .addEscape('\u2021', "&Dagger;")
            .addEscape('\u2022', "&bull;")
            .addEscape('\u2026', "&hellip;")
            .addEscape('\u2030', "&permil;")
            .addEscape('\u2032', "&prime;")
            .addEscape('\u2033', "&Prime;")
            .addEscape('\u2039', "&lsaquo;")
            .addEscape('\u203A', "&rsaquo;")
            .addEscape('\u203E', "&oline;")
            .addEscape('\u2044', "&frasl;")
            .addEscape('\u20AC', "&euro;")
            .addEscape('\u2111', "&image;")
            .addEscape('\u2118', "&weierp;")
            .addEscape('\u211C', "&real;")
            .addEscape('\u2122', "&trade;")
            .addEscape('\u2135', "&alefsym;")
            .addEscape('\u2190', "&larr;")
            .addEscape('\u2191', "&uarr;")
            .addEscape('\u2192', "&rarr;")
            .addEscape('\u2193', "&darr;")
            .addEscape('\u2194', "&harr;")
            .addEscape('\u21B5', "&crarr;")
            .addEscape('\u21D0', "&lArr;")
            .addEscape('\u21D1', "&uArr;")
            .addEscape('\u21D2', "&rArr;")
            .addEscape('\u21D3', "&dArr;")
            .addEscape('\u21D4', "&hArr;")
            .addEscape('\u2200', "&forall;")
            .addEscape('\u2202', "&part;")
            .addEscape('\u2203', "&exist;")
            .addEscape('\u2205', "&empty;")
            .addEscape('\u2207', "&nabla;")
            .addEscape('\u2208', "&isin;")
            .addEscape('\u2209', "&notin;")
            .addEscape('\u220B', "&ni;")
            .addEscape('\u220F', "&prod;")
            .addEscape('\u2211', "&sum;")
            .addEscape('\u2212', "&minus;")
            .addEscape('\u2217', "&lowast;")
            .addEscape('\u221A', "&radic;")
            .addEscape('\u221D', "&prop;")
            .addEscape('\u221E', "&infin;")
            .addEscape('\u2220', "&ang;")
            .addEscape('\u2227', "&and;")
            .addEscape('\u2228', "&or;")
            .addEscape('\u2229', "&cap;")
            .addEscape('\u222A', "&cup;")
            .addEscape('\u222B', "&int;")
            .addEscape('\u2234', "&there4;")
            .addEscape('\u223C', "&sim;")
            .addEscape('\u2245', "&cong;")
            .addEscape('\u2248', "&asymp;")
            .addEscape('\u2260', "&ne;")
            .addEscape('\u2261', "&equiv;")
            .addEscape('\u2264', "&le;")
            .addEscape('\u2265', "&ge;")
            .addEscape('\u2282', "&sub;")
            .addEscape('\u2283', "&sup;")
            .addEscape('\u2284', "&nsub;")
            .addEscape('\u2286', "&sube;")
            .addEscape('\u2287', "&supe;")
            .addEscape('\u2295', "&oplus;")
            .addEscape('\u2297', "&otimes;")
            .addEscape('\u22A5', "&perp;")
            .addEscape('\u22C5', "&sdot;")
            .addEscape('\u2308', "&lceil;")
            .addEscape('\u2309', "&rceil;")
            .addEscape('\u230A', "&lfloor;")
            .addEscape('\u230B', "&rfloor;")
            .addEscape('\u2329', "&lang;")
            .addEscape('\u232A', "&rang;")
            .addEscape('\u25CA', "&loz;")
            .addEscape('\u2660', "&spades;")
            .addEscape('\u2663', "&clubs;")
            .addEscape('\u2665', "&hearts;")
            .addEscape('\u2666', "&diams;")
            .toArray());
  }

  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in an HTML document in either element
   * content or attribute values.
   *
   * <p><b>Note</b></p>: does not alter non-ASCII and control characters.
   */
  public static Escaper asciiHtmlEscaper() {
    return ASCII_HTML_ESCAPER;
  }

  /**
   * Escapes special characters from a string so it can safely be included in an
   * HTML document in either element content or attribute values. Does
   * <em>not</em> alter non-ASCII characters or control characters.
   */
  private static final Escaper ASCII_HTML_ESCAPER = new CharEscaperBuilder()
      .addEscape('"', "&quot;")
      .addEscape('\'', "&#39;")
      .addEscape('&', "&amp;")
      .addEscape('<', "&lt;")
      .addEscape('>', "&gt;")
      .toEscaper();

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
   * hexadecimal sequences. From <a href="http://www.ietf.org/rfc/rfc3986.txt">
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
   *
   * @see #uriEscaper()
   */
  public static Escaper uriEscaper(boolean plusForSpace) {
    return plusForSpace ? URI_ESCAPER : URI_ESCAPER_NO_PLUS;
  }

  /**
   * A string of safe characters that mimics the behavior of
   * {@link java.net.URLEncoder}.
   *
   * <p>TODO: Fix escapers to be compliant with RFC 3986
   */
  public static final String SAFECHARS_URLENCODER = "-_.*";
  private static final Escaper URI_ESCAPER =
      new PercentEscaper(SAFECHARS_URLENCODER, true);

  private static final Escaper URI_ESCAPER_NO_PLUS =
      new PercentEscaper(SAFECHARS_URLENCODER, false);


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
   * Escaper for HTML character escaping, contains both an array and a
   * backup function.  We're not overriding the array decorator because we
   * want to keep this as fast as possible, so no calls to super.escape first.
   */
  private static class HtmlCharEscaper extends FastCharEscaper {

    public HtmlCharEscaper(char[][] replacements) {
      super(replacements, Character.MIN_VALUE, '~');
    }

    @Override protected char[] escape(char c) {
      // First check if our array has a valid escaping.
      if (c < replacementLength) {
        char[] r = replacements[c];
        if (r != null) {
          return r;
        }
      }

      // ~ is ASCII 126, the highest value char that does not need
      // to be escaped
      if (c <= safeMax) {
        return null;
      }

      int index;
      if (c < 1000) {
        index = 4;
      } else if (c < 10000) {
        index = 5;
      } else {
        index = 6;
      }
      char[] result = new char[index + 2];
      result[0] = '&';
      result[1] = '#';
      result[index + 1] = ';';

      // TODO: Convert this to a sequence of shifts/additions
      // to avoid the division and modulo operators.
      int intValue = c;
      for (; index > 1; index--) {
        result[index] = HEX_DIGITS[intValue % 10];
        intValue /= 10;
      }
      return result;
    }
  }

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
}
