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

package com.google.template.soy.parsepasses.contextautoesc;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Some utilities for dealing with JavaScript syntax.
 *
 * @author Mike Samuel
 */
final class JsUtil {

  /**
   * True iff a slash after the given run of non-whitespace tokens starts a regular expression
   * instead of a div operator : (/ or /=).
   * <p>
   * This fails on some valid but nonsensical JavaScript programs like {@code x = ++/foo/i} which is
   * quite different than {@code x++/foo/i}, but is not known to fail on any known useful programs.
   * It is based on the draft
   * <a href="http://www.mozilla.org/js/language/js20-2000-07/rationale/syntax.html">JavaScript 2.0
   * lexical grammar</a> and requires one token of lookbehind.
   *
   * @param jsTokens A run of non-whitespace, non-comment, non string tokens not including the '/'
   *     character.  Non-empty.
   */
  public static boolean isRegexPreceder(String jsTokens) {
    // Tokens that precede a regular expression in JavaScript.
    // "!", "!=", "!==", "#", "%", "%=", "&", "&&", "&&=", "&=", "(", "*", "*=", "+", "+=", ",",
    // "-", "-=", "->", ".", "..", "...", "/", "/=", ":", "::", ";", "<", "<<", "<<=", "<=", "=",
    // "==", "===", ">", ">=", ">>", ">>=", ">>>", ">>>=", "?", "@", "[", "^", "^=", "^^", "^^=",
    // "{", "|", "|=", "||", "||=", "~",
    // "break", "case", "continue", "delete", "do", "else", "finally", "instanceof", "return",
    // "throw", "try", "typeof"

    int jsTokensLen = jsTokens.length();
    char lastChar = jsTokens.charAt(jsTokensLen - 1);
    switch (lastChar) {
      case '=': case '#': case '%': case '&': case '(': case '*':
      case ',': case '<': case '>': case '?': case ':': case ';':
      case '^': case '{': case '|': case '}': case '~': case '[':
        return true;
      case '+': case '-':
        // ++ and -- are not
        int signStart = jsTokensLen - 1;
        // Count the number of adjacent dashes or pluses.
        while (signStart > 0 && jsTokens.charAt(signStart - 1) == lastChar) {
          --signStart;
        }
        int numAdjacent = jsTokensLen - signStart;
        // True for odd numbers since "---" is the same as "-- -".
        // False for even numbers since "----" is the same as "-- --" which ends with a decrement,
        // not a minus sign.
        return (numAdjacent & 1) == 1;
      case '.':
        if (jsTokensLen == 1) {
          return true;
        }
        // There is likely to be a .. or ... operator in newer versions of EcmaScript.
        char ch = jsTokens.charAt(jsTokensLen - 2);
        return !('0' <= ch && ch <= '9');
      default:
        // Look for one of the keywords above.
        int wordStart = jsTokensLen;
        while (wordStart > 0 && Character.isJavaIdentifierPart(jsTokens.charAt(wordStart - 1))) {
          --wordStart;
        }
        return REGEX_PRECEDER_KEYWORDS.contains(jsTokens.substring(wordStart));
    }
  }

  private static final Set<String> REGEX_PRECEDER_KEYWORDS = ImmutableSet.of(
      "break", "case", "continue", "delete", "do", "else", "finally", "instanceof", "return",
      "throw", "try", "typeof");

  private JsUtil() {
    // Not instantiable.
  }
}
