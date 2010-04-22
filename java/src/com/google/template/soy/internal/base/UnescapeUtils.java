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
}
