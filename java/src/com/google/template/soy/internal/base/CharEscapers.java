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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

/**
 * Utility functions for dealing with {@code CharEscaper}s, and some commonly
 * used {@code CharEscaper} instances.
 *
 * @author Sven Mawson
 * @author Laurence Gonsalves
 */
public final class CharEscapers {

  private CharEscapers() {}

  /**
   * Performs no escaping.
   */
  private static final CharEscaper NULL_ESCAPER = new CharEscaper() {
      @Override
      public String escape(String string) {
        checkNotNull(string);
        return string;
      }

      @Override
      public Appendable escape(final Appendable out) {
        checkNotNull(out);

        // we can't simply return out because the CharEscaper contract says that
        // the returned Appendable will throw a NullPointerException if asked to
        // append null.
        return new Appendable() {
            @Override public Appendable append(CharSequence csq) throws IOException {
              checkNotNull(csq);
              out.append(csq);
              return this;
            }

            @Override public Appendable append(CharSequence csq, int start, int end)
                throws IOException {
              checkNotNull(csq);
              out.append(csq, start, end);
              return this;
            }

            @Override public Appendable append(char c) throws IOException {
              out.append(c);
              return this;
            }
          };
      }

      @Override
      protected char[] escape(char c) {
        return null;
      }
    };

  /**
   * Returns a {@link CharEscaper} that does no escaping.
   */
  public static CharEscaper nullEscaper() {
    return NULL_ESCAPER;
  }

  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in an XML document in either element
   * content or attribute values.
   *
   * <p><b>Note</b></p>: silently removes null-characters and control
   * characters, as there is no way to represent them in XML.
   */
  public static CharEscaper xmlEscaper() {
    return XML_ESCAPER;
  }

  /**
   * Escapes special characters from a string so it can safely be included in an
   * XML document in either element content or attribute values.  Also removes
   * null-characters and control characters, as there is no way to represent
   * them in XML.
   */
  private static final CharEscaper XML_ESCAPER = newBasicXmlEscapeBuilder()
      .addEscape('"', "&quot;")
      .addEscape('\'', "&apos;")
      .toEscaper();

  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in an XML document in element content.
   *
   * <p><b>Note</b></p>: double and single quotes are not escaped, so it is not
   * safe to use this escaper to escape attribute values. Use the
   * {@link #xmlEscaper()} escaper to escape attribute values or if you are
   * unsure. Also silently removes non-whitespace control characters, as there
   * is no way to represent them in XML.
   */
  public static CharEscaper xmlContentEscaper() {
    return XML_CONTENT_ESCAPER;
  }

  /**
   * Escapes special characters from a string so it can safely be included in an
   * XML document in element content.  Note that quotes are <em>not</em>
   * escaped, so <em>this is not safe for use in attribute values</em>. Use
   * {@link #XML_ESCAPER} for attribute values, or if you are unsure.  Also
   * removes non-whitespace control characters, as there is no way to represent
   * them in XML.
   */
  private static final CharEscaper XML_CONTENT_ESCAPER =
      newBasicXmlEscapeBuilder().toEscaper();

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
  public static CharEscaper asciiHtmlEscaper() {
    return ASCII_HTML_ESCAPER;
  }

  /**
   * Escapes special characters from a string so it can safely be included in an
   * HTML document in either element content or attribute values. Does
   * <em>not</em> alter non-ASCII characters or control characters.
   */
  private static final CharEscaper ASCII_HTML_ESCAPER = new CharEscaperBuilder()
      .addEscape('"', "&quot;")
      .addEscape('\'', "&#39;")
      .addEscape('&', "&amp;")
      .addEscape('<', "&lt;")
      .addEscape('>', "&gt;")
      .toEscaper();

  /**
   * Returns an {@link Escaper} instance that escapes Java chars so they can be
   * safely included in URIs. For details on escaping URIs, see section 2.4 of
   * <a href="http://www.ietf.org/rfc/rfc2396.txt">RFC 2396</a>.
   *
   * <p>When encoding a String, the following rules apply:
   * <ul>
   * <li>The alphanumeric characters "a" through "z", "A" through "Z" and "0"
   *     through "9" remain the same.
   * <li>The special characters ".", "-", "*", and "_" remain the same.
   * <li>The space character " " is converted into a plus sign "+".
   * <li>All other characters are converted into one or more bytes using UTF-8
   *     encoding and each byte is then represented by the 3-character string
   *     "%XY", where "XY" is the two-digit, uppercase, hexadecimal
   *     representation of the byte value.
   * <ul>
   *
   * <p><b>Note</b>: Unlike other escapers, URI escapers produce uppercase
   * hexadecimal sequences. From <a href="http://www.ietf.org/rfc/rfc3986.txt">
   * RFC 3986</a>:<br>
   * <i>"URI producers and normalizers should use uppercase hexadecimal digits
   * for all percent-encodings."</i>
   *
   * <p>This escaper has identical behavior to (but is potentially much faster
   * than):
   * <ul>
   * <li>{@link com.google.httputil.FastURLEncoder#encode(String)}
   * <li>{@link com.google.httputil.FastURLEncoder#encode(String,String)}
   *     with the encoding name "UTF-8"
   * <li>{@link com.google.common.net.UriEncoder#encode(String)}
   * <li>{@link com.google.common.net.UriEncoder#encode(String,java.nio.charset.Charset)}
   *     with the UTF_8 Charset
   * <li>{@link java.net.URLEncoder#encode(String, String)}
   *     with the encoding name "UTF-8"
   * </ul>
   *
   * <p>This method is equivalent to {@code uriEscaper(true)}.
   */
  public static Escaper uriEscaper() {
    return uriEscaper(true);
  }

  /**
   * Returns an {@link Escaper} instance that escapes Java chars so they can be
   * safely included in URI path segments. For details on escaping URIs, see
   * section 2.4 of <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>.
   *
   * <p>When encoding a String, the following rules apply:
   * <ul>
   * <li>The alphanumeric characters "a" through "z", "A" through "Z" and "0"
   *     through "9" remain the same.
   * <li>The unreserved characters ".", "-", "~", and "_" remain the same.
   * <li>The general delimiters "@" and ":" remain the same.
   * <li>The subdelimiters "!", "$", "&amp;", "'", "(", ")", "*", ",", ";",
   *     and "=" remain the same.
   * <li>The space character " " is converted into %20.
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
   */
  public static Escaper uriPathEscaper() {
    return URI_PATH_ESCAPER;
  }

  /**
   * Returns an {@link Escaper} instance that escapes Java chars so they can be
   * safely included in URI query string segments. When the query string
   * consists of a sequence of name=value pairs separated by &amp;, the names
   * and values should be individually encoded. If you escape an entire query
   * string in one pass with this escaper, then the "=" and "&amp;" characters
   * used as separators will also be escaped.
   *
   * <p>This escaper is also suitable for escaping fragment identifiers.
   *
   * <p>For details on escaping URIs, see
   * section 2.4 of <a href="http://www.ietf.org/rfc/rfc3986.txt">RFC 3986</a>.
   *
   * <p>When encoding a String, the following rules apply:
   * <ul>
   * <li>The alphanumeric characters "a" through "z", "A" through "Z" and "0"
   *     through "9" remain the same.
   * <li>The unreserved characters ".", "-", "~", and "_" remain the same.
   * <li>The general delimiters "@" and ":" remain the same.
   *  <li>The path delimiters "/" and "?" remain the same.
   * <li>The subdelimiters "!", "$", "'", "(", ")", "*", ",", and ";",
   *     remain the same.
   * <li>The space character " " is converted into %20.
   * <li>The equals sign "=" is converted into %3D.
   * <li>The ampersand "&amp;" is converted into %26.
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
   */
  public static Escaper uriQueryStringEscaper() {
    return URI_QUERY_STRING_ESCAPER;
  }

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

  private static final Escaper URI_ESCAPER =
      new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, true);

  private static final Escaper URI_ESCAPER_NO_PLUS =
      new PercentEscaper(PercentEscaper.SAFECHARS_URLENCODER, false);

  private static final Escaper URI_PATH_ESCAPER =
      new PercentEscaper(PercentEscaper.SAFEPATHCHARS_URLENCODER, false);

  private static final Escaper URI_QUERY_STRING_ESCAPER =
      new PercentEscaper(PercentEscaper.SAFEQUERYSTRINGCHARS_URLENCODER, false);

  /**
   * Returns a {@link Escaper} instance that escapes Java characters in a manner
   * compatible with the C++ webutil/url URL class (the {@code kGoogle1Escape}
   * set).
   *
   * <p>When encoding a String, the following rules apply:
   * <ul>
   * <li>The alphanumeric characters "a" through "z", "A" through "Z" and "0"
   * through "9" remain the same.
   * <li>The special characters "!", "(", ")", "*", "-", ".", "_", "~", ",", "/"
   * and ":" remain the same.
   * <li>The space character " " is converted into a plus sign "+".
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
   * <p><b>Note</b>: This escaper is a special case and is <em>not
   * compliant</em> with <a href="http://www.ietf.org/rfc/rfc2396.txt">
   * RFC 2396</a>. Specifically it will not escape "/", ":" and ",". This is
   * only provided for certain limited use cases and you should favor using
   * {@link #uriEscaper()} whenever possible.
   */
  public static Escaper cppUriEscaper() {
    return CPP_URI_ESCAPER;
  }

  // Based on comments from FastURLEncoder:
  // These octets mimic the ones escaped by the C++ webutil/url URL class --
  // the kGoogle1Escape set.
  // To produce the same escaping as C++, use this set with the plusForSpace
  // option.
  // WARNING: Contrary to RFC 2396 ",", "/" and ":" are listed as safe here.
  private static final Escaper CPP_URI_ESCAPER =
      new PercentEscaper("!()*-._~,/:", true);

  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in a Java string literal.
   *
   * <p><b>Note</b></p>: does not escape single quotes, so use the escaper
   * returned by {@link #javaCharEscaper()} if you are generating char
   * literals or if you are unsure.
   */
  public static CharEscaper javaStringEscaper() {
    return JAVA_STRING_ESCAPER;
  }

  /**
   * Escapes special characters from a string so it can safely be included in a
   * Java string literal. Does <em>not</em> escape single-quotes, so use
   * JAVA_CHAR_ESCAPE if you are generating char literals, or if you are unsure.
   *
   * <p>Note that non-ASCII characters will be octal or Unicode escaped.
   */
  private static final CharEscaper JAVA_STRING_ESCAPER
      = new JavaCharEscaper(new CharEscaperBuilder()
          .addEscape('\b', "\\b")
          .addEscape('\f', "\\f")
          .addEscape('\n', "\\n")
          .addEscape('\r', "\\r")
          .addEscape('\t', "\\t")
          .addEscape('\"', "\\\"")
          .addEscape('\\', "\\\\")
          .toArray());

  /**
   * Returns a {@link CharEscaper} instance that escapes special characters in a
   * string so it can safely be included in a Java char or string literal. The
   * behavior of this escaper is the same as that of the
   * {@link #javaStringEscaper()}, except it also escapes single quotes.
   */
  public static CharEscaper javaCharEscaper() {
    return JAVA_CHAR_ESCAPER;
  }

  /**
   * Escapes special characters from a string so it can safely be included in a
   * Java char literal or string literal.
   *
   * <p>Note that non-ASCII characters will be octal or Unicode escaped.
   *
   * <p>This is the same as {@link #JAVA_STRING_ESCAPER}, except that it escapes
   * single quotes.
   */
  private static final CharEscaper JAVA_CHAR_ESCAPER
      = new JavaCharEscaper(new CharEscaperBuilder()
          .addEscape('\b', "\\b")
          .addEscape('\f', "\\f")
          .addEscape('\n', "\\n")
          .addEscape('\r', "\\r")
          .addEscape('\t', "\\t")
          .addEscape('\'', "\\'")
          .addEscape('\"', "\\\"")
          .addEscape('\\', "\\\\")
          .toArray());

  /**
   * Returns a {@link CharEscaper} instance that replaces non-ASCII characters
   * in a string with their Unicode escape sequences ({@code \\uxxxx} where
   * {@code xxxx} is a hex number). Existing escape sequences won't be affected.
   */
  public static CharEscaper javaStringUnicodeEscaper() {
    return JAVA_STRING_UNICODE_ESCAPER;
  }

  /**
   * Escapes each non-ASCII character in with its Unicode escape sequence
   * {@code \\uxxxx} where {@code xxxx} is a hex number. Existing escape
   * sequences won't be affected.
   */
  private static final CharEscaper JAVA_STRING_UNICODE_ESCAPER
      = new CharEscaper() {
          @Override protected char[] escape(char c) {
            if (c <= 127) {
              return null;
            }

            char[] r = new char[6];
            r[5] = HEX_DIGITS[c & 15];
            c >>>= 4;
            r[4] = HEX_DIGITS[c & 15];
            c >>>= 4;
            r[3] = HEX_DIGITS[c & 15];
            c >>>= 4;
            r[2] = HEX_DIGITS[c & 15];
            r[1] = 'u';
            r[0] = '\\';
            return r;
          }
        };

  /**
   * Returns a {@link CharEscaper} instance that escapes special characters from
   * a string so it can safely be included in a Python string literal. Does not
   * have any special handling for non-ASCII characters.
   */
  public static CharEscaper pythonEscaper() {
    return PYTHON_ESCAPER;
  }

  /**
   * Escapes special characters in a string so it can safely be included in a
   * Python string literal. Does not have any special handling for non-ASCII
   * characters.
   */
  private static final CharEscaper PYTHON_ESCAPER = new CharEscaperBuilder()
      // TODO: perhaps this should escape non-ASCII characters?
      .addEscape('\n', "\\n")
      .addEscape('\r', "\\r")
      .addEscape('\t', "\\t")
      .addEscape('\\', "\\\\")
      .addEscape('\"', "\\\"")
      .addEscape('\'', "\\\'")
      .toEscaper();

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
  private static final CharEscaper JAVASCRIPT_ESCAPER
      = new JavascriptCharEscaper(new CharEscaperBuilder()
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

  private static CharEscaperBuilder newBasicXmlEscapeBuilder() {
    return new CharEscaperBuilder()
        .addEscape('&', "&amp;")
        .addEscape('<', "&lt;")
        .addEscape('>', "&gt;")
        .addEscapes(new char[] {
            '\000', '\001', '\002', '\003', '\004',
            '\005', '\006', '\007', '\010', '\013',
            '\014', '\016', '\017', '\020', '\021',
            '\022', '\023', '\024', '\025', '\026',
            '\027', '\030', '\031', '\032', '\033',
            '\034', '\035', '\036', '\037'}, "");
  }

  /**
   * Returns a composite {@link CharEscaper} instance that tries to escape
   * characters using a primary {@code CharEscaper} first and falls back to a
   * secondary one if there is no escaping.
   *
   * <p>The returned escaper will attempt to escape each character using the
   * primary escaper, and if the primary escaper has no escaping for that
   * character, it will use the secondary escaper. If the secondary escaper has
   * no escaping for a character either, the original character will be used.
   * If the primary escaper has an escape for a character, the secondary escaper
   * will not be used at all for that character; the escaped output of the
   * primary is not run through the secondary. For a case where you would like
   * to first escape with one escaper, and then with another, it is recommended
   * that you call each escaper in order.
   *
   * @param primary The primary {@code CharEscaper} to use
   * @param secondary The secondary {@code CharEscaper} to use if the first one
   *     has no escaping rule for a character
   * @throws NullPointerException if any of the arguments is null
   */
  public static CharEscaper fallThrough(CharEscaper primary,
      CharEscaper secondary) {
    checkNotNull(primary);
    checkNotNull(secondary);
    return new FallThroughCharEscaper(primary, secondary);
  }

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
   * Escaper for Java character escaping, contains both an array and a
   * backup function.  We're not overriding the array decorator because we
   * want to keep this as fast as possible, so no calls to super.escape first.
   */
  private static class JavaCharEscaper extends FastCharEscaper {

    public JavaCharEscaper(char[][] replacements) {
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

      // This range is un-escaped.
      if (safeMin <= c && c <= safeMax) {
        return null;
      }

      if (c <= 0xFF) {
        // Convert c to an octal-escaped string.
        // Equivalent to String.format("\\%03o", (int)c);
        char[] r = new char[4];
        r[0] = '\\';
        r[3] = HEX_DIGITS[c & 7];
        c >>>= 3;
        r[2] = HEX_DIGITS[c & 7];
        c >>>= 3;
        r[1] = HEX_DIGITS[c & 7];
        return r;
      }

      // Convert c to a hex-escaped string.
      // Equivalent to String.format("\\u%04x", (int)c);
      char[] r = new char[6];
      r[0] = '\\';
      r[1] = 'u';
      r[5] = HEX_DIGITS[c & 15];
      c >>>= 4;
      r[4] = HEX_DIGITS[c & 15];
      c >>>= 4;
      r[3] = HEX_DIGITS[c & 15];
      c >>>= 4;
      r[2] = HEX_DIGITS[c & 15];
      return r;
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

  /**
   * A composite {@code CharEscaper} object that tries to escape characters
   * using a primary {@code CharEscaper} first and falls back to a secondary
   * one if there is no escaping.
   */
  private static class FallThroughCharEscaper extends CharEscaper {

    private final CharEscaper primary;
    private final CharEscaper secondary;

    public FallThroughCharEscaper(CharEscaper primary, CharEscaper secondary) {
      this.primary = primary;
      this.secondary = secondary;
    }

    @Override
    protected char[] escape(char c) {
      char result[] = primary.escape(c);
      if (result == null) {
        result = secondary.escape(c);
      }
      return result;
    }
  }

  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
}
