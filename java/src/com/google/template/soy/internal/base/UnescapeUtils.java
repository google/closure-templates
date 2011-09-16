/*
 * Copyright 2010 Google Inc.
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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * Utilities for unescaping strings from context-specific formats.
 *
 * @author Greg Slovacek
 */
public class UnescapeUtils {

  private UnescapeUtils() {}

  /**
   * Unescapes a Javascript string.
   * Throws an IllegalArgumentException if the string contains bad escaping.
   */
  public static String unescapeJs(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); ) {
      char c = s.charAt(i);
      if (c == '\\') {
        i = unescapeJsHelper(s, i + 1, sb);
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }

  /**
   * Looks for an escape code starting at index i of s, and appends it to sb.
   * @return the index of the first character in s after the escape code.
   * @throws IllegalArgumentException if the escape code is invalid
   */
  private static int unescapeJsHelper(String s, int i, StringBuilder sb) {
    if (i >= s.length()) {
      throw new IllegalArgumentException(
          "End-of-string after escape character in [" + s + "]");
    }

    char c = s.charAt(i++);
    switch (c) {
      case 'n': sb.append('\n'); break;
      case 'r': sb.append('\r'); break;
      case 't': sb.append('\t'); break;
      case 'b': sb.append('\b'); break;
      case 'f': sb.append('\f'); break;
      case '\\':
      case '\"':
      case '\'':
      case '>':
        sb.append(c);
        break;
      case '0': case '1': case '2': case '3':
      case '4': case '5': case '6': case '7':
        --i;  // backup to first octal digit
        int nOctalDigits = 1;
        int digitLimit = c < '4' ? 3 : 2;
        while (nOctalDigits < digitLimit && i + nOctalDigits < s.length()
               && isOctal(s.charAt(i + nOctalDigits))) {
          ++nOctalDigits;
        }
        sb.append(
            (char) Integer.parseInt(s.substring(i, i + nOctalDigits), 8));
        i += nOctalDigits;
        break;
      case 'x':
      case 'u':
        String hexCode;
        int nHexDigits = (c == 'u' ? 4 : 2);
        try {
          hexCode = s.substring(i, i + nHexDigits);
        } catch (IndexOutOfBoundsException ioobe) {
          throw new IllegalArgumentException(
              "Invalid unicode sequence [" + s.substring(i) + "] at index " + i
              + " in [" + s + "]");
        }
        int unicodeValue;
        try {
          unicodeValue = Integer.parseInt(hexCode, 16);
        } catch (NumberFormatException nfe) {
          throw new IllegalArgumentException(
              "Invalid unicode sequence [" + hexCode + "] at index " + i +
              " in [" + s + "]");
        }
        sb.append((char) unicodeValue);
        i += nHexDigits;
        break;
      default:
        throw new IllegalArgumentException(
            "Unknown escape code [" + c + "] at index " + i + " in [" + s + "]"
            );
    }

    return i;
  }

  private static boolean isOctal(char c) {
    return (c >= '0') && (c <= '7');
  }

  /**
   * Replace all the occurrences of HTML entities with the appropriate code-points.
   * @param s HTML.
   * @return Plain text.
   */
  public static String unescapeHtml(String s) {
    int amp = s.indexOf('&');
    if (amp < 0) {  // Fast path.
      return s;
    }
    int n = s.length();
    StringBuilder sb = new StringBuilder(n);
    int pos = 0;
    do {
      // All numeric entities and all named entities can be represented in less than 12 chars, so
      // avoid any O(n**2) problem on "&&&&&&&&&" by not looking for ; more than 12 chars out.
      int end = -1;
      int entityLimit = Math.min(n, amp + 12);
      for (int i = amp + 1; i < entityLimit; ++i) {
        if (s.charAt(i) == ';') {
          end = i + 1;
          break;
        }
      }
      int cp = -1;
      if (end == -1) {
        cp = -1;
      } else {
        if (s.charAt(amp + 1) == '#') {  // Decode a numeric entity
          char ch = s.charAt(amp + 2);
          try {
            if (ch == 'x' || ch == 'X') {  // hex
              //         & # x A B C D ;
              //         ^ ^ ^ ^       ^
              // amp +   0 1 2 3       end - 1
              cp = Integer.parseInt(s.substring(amp + 3, end - 1), 16);
            } else {  // decimal
              //         & # 1 6 0 ;
              //         ^ ^ ^     ^
              // amp +   0 1 2     end - 1
              cp = Integer.parseInt(s.substring(amp + 2, end - 1), 10);
            }
          } catch (NumberFormatException ex) {
            cp = -1;  // Malformed numeric entity
          }
        } else {
          //     & q u o t ;
          //     ^           ^
          //   amp           end
          Integer cpI = HTML_ENTITY_TO_CODEPOINT.get(s.substring(amp, end));
          cp = cpI != null ? cpI.intValue() : -1;
        }
      }
      if (cp == -1) {  // Don't decode
        end = amp + 1;
      } else {
        sb.append(s, pos, amp);
        sb.appendCodePoint(cp);
        pos = end;
      }
      amp = s.indexOf('&', end);
    } while (amp >= 0);
    return sb.append(s, pos, n).toString();
  }

  // Reverse of map used in com.google.common.html.HtmlEscapers.htmlCharEscaper()
  private static final
  Map<String, Integer> HTML_ENTITY_TO_CODEPOINT = ImmutableMap.<String, Integer>builder()
      .put("&quot;", (int) '"')
      .put("&apos;", (int) '\'')
      .put("&amp;", (int) '&')
      .put("&lt;", (int) '<')
      .put("&gt;", (int) '>')
      .put("&nbsp;", (int) '\u00A0')
      .put("&iexcl;", (int) '\u00A1')
      .put("&cent;", (int) '\u00A2')
      .put("&pound;", (int) '\u00A3')
      .put("&curren;", (int) '\u00A4')
      .put("&yen;", (int) '\u00A5')
      .put("&brvbar;", (int) '\u00A6')
      .put("&sect;", (int) '\u00A7')
      .put("&uml;", (int) '\u00A8')
      .put("&copy;", (int) '\u00A9')
      .put("&ordf;", (int) '\u00AA')
      .put("&laquo;", (int) '\u00AB')
      .put("&not;", (int) '\u00AC')
      .put("&shy;", (int) '\u00AD')
      .put("&reg;", (int) '\u00AE')
      .put("&macr;", (int) '\u00AF')
      .put("&deg;", (int) '\u00B0')
      .put("&plusmn;", (int) '\u00B1')
      .put("&sup2;", (int) '\u00B2')
      .put("&sup3;", (int) '\u00B3')
      .put("&acute;", (int) '\u00B4')
      .put("&micro;", (int) '\u00B5')
      .put("&para;", (int) '\u00B6')
      .put("&middot;", (int) '\u00B7')
      .put("&cedil;", (int) '\u00B8')
      .put("&sup1;", (int) '\u00B9')
      .put("&ordm;", (int) '\u00BA')
      .put("&raquo;", (int) '\u00BB')
      .put("&frac14;", (int) '\u00BC')
      .put("&frac12;", (int) '\u00BD')
      .put("&frac34;", (int) '\u00BE')
      .put("&iquest;", (int) '\u00BF')
      .put("&Agrave;", (int) '\u00C0')
      .put("&Aacute;", (int) '\u00C1')
      .put("&Acirc;", (int) '\u00C2')
      .put("&Atilde;", (int) '\u00C3')
      .put("&Auml;", (int) '\u00C4')
      .put("&Aring;", (int) '\u00C5')
      .put("&AElig;", (int) '\u00C6')
      .put("&Ccedil;", (int) '\u00C7')
      .put("&Egrave;", (int) '\u00C8')
      .put("&Eacute;", (int) '\u00C9')
      .put("&Ecirc;", (int) '\u00CA')
      .put("&Euml;", (int) '\u00CB')
      .put("&Igrave;", (int) '\u00CC')
      .put("&Iacute;", (int) '\u00CD')
      .put("&Icirc;", (int) '\u00CE')
      .put("&Iuml;", (int) '\u00CF')
      .put("&ETH;", (int) '\u00D0')
      .put("&Ntilde;", (int) '\u00D1')
      .put("&Ograve;", (int) '\u00D2')
      .put("&Oacute;", (int) '\u00D3')
      .put("&Ocirc;", (int) '\u00D4')
      .put("&Otilde;", (int) '\u00D5')
      .put("&Ouml;", (int) '\u00D6')
      .put("&times;", (int) '\u00D7')
      .put("&Oslash;", (int) '\u00D8')
      .put("&Ugrave;", (int) '\u00D9')
      .put("&Uacute;", (int) '\u00DA')
      .put("&Ucirc;", (int) '\u00DB')
      .put("&Uuml;", (int) '\u00DC')
      .put("&Yacute;", (int) '\u00DD')
      .put("&THORN;", (int) '\u00DE')
      .put("&szlig;", (int) '\u00DF')
      .put("&agrave;", (int) '\u00E0')
      .put("&aacute;", (int) '\u00E1')
      .put("&acirc;", (int) '\u00E2')
      .put("&atilde;", (int) '\u00E3')
      .put("&auml;", (int) '\u00E4')
      .put("&aring;", (int) '\u00E5')
      .put("&aelig;", (int) '\u00E6')
      .put("&ccedil;", (int) '\u00E7')
      .put("&egrave;", (int) '\u00E8')
      .put("&eacute;", (int) '\u00E9')
      .put("&ecirc;", (int) '\u00EA')
      .put("&euml;", (int) '\u00EB')
      .put("&igrave;", (int) '\u00EC')
      .put("&iacute;", (int) '\u00ED')
      .put("&icirc;", (int) '\u00EE')
      .put("&iuml;", (int) '\u00EF')
      .put("&eth;", (int) '\u00F0')
      .put("&ntilde;", (int) '\u00F1')
      .put("&ograve;", (int) '\u00F2')
      .put("&oacute;", (int) '\u00F3')
      .put("&ocirc;", (int) '\u00F4')
      .put("&otilde;", (int) '\u00F5')
      .put("&ouml;", (int) '\u00F6')
      .put("&divide;", (int) '\u00F7')
      .put("&oslash;", (int) '\u00F8')
      .put("&ugrave;", (int) '\u00F9')
      .put("&uacute;", (int) '\u00FA')
      .put("&ucirc;", (int) '\u00FB')
      .put("&uuml;", (int) '\u00FC')
      .put("&yacute;", (int) '\u00FD')
      .put("&thorn;", (int) '\u00FE')
      .put("&yuml;", (int) '\u00FF')
      .put("&OElig;", (int) '\u0152')
      .put("&oelig;", (int) '\u0153')
      .put("&Scaron;", (int) '\u0160')
      .put("&scaron;", (int) '\u0161')
      .put("&Yuml;", (int) '\u0178')
      .put("&fnof;", (int) '\u0192')
      .put("&circ;", (int) '\u02C6')
      .put("&tilde;", (int) '\u02DC')
      .put("&Alpha;", (int) '\u0391')
      .put("&Beta;", (int) '\u0392')
      .put("&Gamma;", (int) '\u0393')
      .put("&Delta;", (int) '\u0394')
      .put("&Epsilon;", (int) '\u0395')
      .put("&Zeta;", (int) '\u0396')
      .put("&Eta;", (int) '\u0397')
      .put("&Theta;", (int) '\u0398')
      .put("&Iota;", (int) '\u0399')
      .put("&Kappa;", (int) '\u039A')
      .put("&Lambda;", (int) '\u039B')
      .put("&Mu;", (int) '\u039C')
      .put("&Nu;", (int) '\u039D')
      .put("&Xi;", (int) '\u039E')
      .put("&Omicron;", (int) '\u039F')
      .put("&Pi;", (int) '\u03A0')
      .put("&Rho;", (int) '\u03A1')
      .put("&Sigma;", (int) '\u03A3')
      .put("&Tau;", (int) '\u03A4')
      .put("&Upsilon;", (int) '\u03A5')
      .put("&Phi;", (int) '\u03A6')
      .put("&Chi;", (int) '\u03A7')
      .put("&Psi;", (int) '\u03A8')
      .put("&Omega;", (int) '\u03A9')
      .put("&alpha;", (int) '\u03B1')
      .put("&beta;", (int) '\u03B2')
      .put("&gamma;", (int) '\u03B3')
      .put("&delta;", (int) '\u03B4')
      .put("&epsilon;", (int) '\u03B5')
      .put("&zeta;", (int) '\u03B6')
      .put("&eta;", (int) '\u03B7')
      .put("&theta;", (int) '\u03B8')
      .put("&iota;", (int) '\u03B9')
      .put("&kappa;", (int) '\u03BA')
      .put("&lambda;", (int) '\u03BB')
      .put("&mu;", (int) '\u03BC')
      .put("&nu;", (int) '\u03BD')
      .put("&xi;", (int) '\u03BE')
      .put("&omicron;", (int) '\u03BF')
      .put("&pi;", (int) '\u03C0')
      .put("&rho;", (int) '\u03C1')
      .put("&sigmaf;", (int) '\u03C2')
      .put("&sigma;", (int) '\u03C3')
      .put("&tau;", (int) '\u03C4')
      .put("&upsilon;", (int) '\u03C5')
      .put("&phi;", (int) '\u03C6')
      .put("&chi;", (int) '\u03C7')
      .put("&psi;", (int) '\u03C8')
      .put("&omega;", (int) '\u03C9')
      .put("&thetasym;", (int) '\u03D1')
      .put("&upsih;", (int) '\u03D2')
      .put("&piv;", (int) '\u03D6')
      .put("&ensp;", (int) '\u2002')
      .put("&emsp;", (int) '\u2003')
      .put("&thinsp;", (int) '\u2009')
      .put("&zwnj;", (int) '\u200C')
      .put("&zwj;", (int) '\u200D')
      .put("&lrm;", (int) '\u200E')
      .put("&rlm;", (int) '\u200F')
      .put("&ndash;", (int) '\u2013')
      .put("&mdash;", (int) '\u2014')
      .put("&lsquo;", (int) '\u2018')
      .put("&rsquo;", (int) '\u2019')
      .put("&sbquo;", (int) '\u201A')
      .put("&ldquo;", (int) '\u201C')
      .put("&rdquo;", (int) '\u201D')
      .put("&bdquo;", (int) '\u201E')
      .put("&dagger;", (int) '\u2020')
      .put("&Dagger;", (int) '\u2021')
      .put("&bull;", (int) '\u2022')
      .put("&hellip;", (int) '\u2026')
      .put("&permil;", (int) '\u2030')
      .put("&prime;", (int) '\u2032')
      .put("&Prime;", (int) '\u2033')
      .put("&lsaquo;", (int) '\u2039')
      .put("&rsaquo;", (int) '\u203A')
      .put("&oline;", (int) '\u203E')
      .put("&frasl;", (int) '\u2044')
      .put("&euro;", (int) '\u20AC')
      .put("&image;", (int) '\u2111')
      .put("&weierp;", (int) '\u2118')
      .put("&real;", (int) '\u211C')
      .put("&trade;", (int) '\u2122')
      .put("&alefsym;", (int) '\u2135')
      .put("&larr;", (int) '\u2190')
      .put("&uarr;", (int) '\u2191')
      .put("&rarr;", (int) '\u2192')
      .put("&darr;", (int) '\u2193')
      .put("&harr;", (int) '\u2194')
      .put("&crarr;", (int) '\u21B5')
      .put("&lArr;", (int) '\u21D0')
      .put("&uArr;", (int) '\u21D1')
      .put("&rArr;", (int) '\u21D2')
      .put("&dArr;", (int) '\u21D3')
      .put("&hArr;", (int) '\u21D4')
      .put("&forall;", (int) '\u2200')
      .put("&part;", (int) '\u2202')
      .put("&exist;", (int) '\u2203')
      .put("&empty;", (int) '\u2205')
      .put("&nabla;", (int) '\u2207')
      .put("&isin;", (int) '\u2208')
      .put("&notin;", (int) '\u2209')
      .put("&ni;", (int) '\u220B')
      .put("&prod;", (int) '\u220F')
      .put("&sum;", (int) '\u2211')
      .put("&minus;", (int) '\u2212')
      .put("&lowast;", (int) '\u2217')
      .put("&radic;", (int) '\u221A')
      .put("&prop;", (int) '\u221D')
      .put("&infin;", (int) '\u221E')
      .put("&ang;", (int) '\u2220')
      .put("&and;", (int) '\u2227')
      .put("&or;", (int) '\u2228')
      .put("&cap;", (int) '\u2229')
      .put("&cup;", (int) '\u222A')
      .put("&int;", (int) '\u222B')
      .put("&there4;", (int) '\u2234')
      .put("&sim;", (int) '\u223C')
      .put("&cong;", (int) '\u2245')
      .put("&asymp;", (int) '\u2248')
      .put("&ne;", (int) '\u2260')
      .put("&equiv;", (int) '\u2261')
      .put("&le;", (int) '\u2264')
      .put("&ge;", (int) '\u2265')
      .put("&sub;", (int) '\u2282')
      .put("&sup;", (int) '\u2283')
      .put("&nsub;", (int) '\u2284')
      .put("&sube;", (int) '\u2286')
      .put("&supe;", (int) '\u2287')
      .put("&oplus;", (int) '\u2295')
      .put("&otimes;", (int) '\u2297')
      .put("&perp;", (int) '\u22A5')
      .put("&sdot;", (int) '\u22C5')
      .put("&lceil;", (int) '\u2308')
      .put("&rceil;", (int) '\u2309')
      .put("&lfloor;", (int) '\u230A')
      .put("&rfloor;", (int) '\u230B')
      .put("&lang;", (int) '\u2329')
      .put("&rang;", (int) '\u232A')
      .put("&loz;", (int) '\u25CA')
      .put("&spades;", (int) '\u2660')
      .put("&clubs;", (int) '\u2663')
      .put("&hearts;", (int) '\u2665')
      .put("&diams;", (int) '\u2666')
      .build();
}
