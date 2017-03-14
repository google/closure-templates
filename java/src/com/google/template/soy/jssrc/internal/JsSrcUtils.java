/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.BaseUtils;

/**
 * Shared utilities specific to the JS Src backend.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class JsSrcUtils {


  private JsSrcUtils() {}


  /**
   * Builds a version of the given string that has literal Unicode Format characters (Unicode
   * category "Cf") changed to valid JavaScript Unicode escapes (i.e. &92;u####). If the provided
   * string doesn't have any Unicode Format characters, then the same string is returned.
   *
   * @param str The string to escape.
   * @return A version of the given string that has literal Unicode Format characters (Unicode
   * category "Cf") changed to valid JavaScript Unicode escapes (i.e. &92;u####).
   */
  static String escapeUnicodeFormatChars(String str) {

    int len = str.length();

    // Do a quick check first, because most strings do not contain Unicode format characters.
    boolean hasFormatChar = false;
    for (int i = 0; i < len; i++) {
      if (Character.getType(str.charAt(i)) == Character.FORMAT) {
        hasFormatChar = true;
        break;
      }
    }
    if (!hasFormatChar) {
      return str;
    }

    // Now we actually need to build a new string.
    StringBuilder out = new StringBuilder(len * 4 / 3);
    int codePoint;
    for (int i = 0; i < len; i += Character.charCount(codePoint)) {
      codePoint = str.codePointAt(i);
      if (Character.getType(codePoint) == Character.FORMAT) {
        BaseUtils.appendHexEscape(out, codePoint);
      } else {
        out.appendCodePoint(codePoint);
      }
    }
    return out.toString();
  }


  /**
   * Returns true if key is a JavaScript reserved word.
   *
   * <p>TODO(lukes): rename to 'needs quoting for property access' and move callers using this for
   * local variables to use the name generator instead.
   */
  static boolean isReservedWord(String key) {
    return LEGACY_JS_RESERVED_WORDS.contains(key);
  }

  static final ImmutableSet<String> JS_LITERALS =
      ImmutableSet.of("null", "true", "false", "NaN", "Infinity", "undefined");

  static final ImmutableSet<String> JS_RESERVED_WORDS =
      ImmutableSet.of(
          "break",
          "case",
          "catch",
          "continue",
          "debugger",
          "default",
          "delete",
          "do",
          "else",
          "finally",
          "for",
          "function",
          "if",
          "in",
          "instanceof",
          "new",
          "return",
          "switch",
          "this",
          "throw",
          "try",
          "typeof",
          "var",
          "void",
          "while",
          "with",
          "class",
          "const",
          "enum",
          "export",
          "extends",
          "import",
          "super",
          "implements",
          "interface",
          "let",
          "package",
          "private",
          "protected",
          "public",
          "static",
          "yield",
          /* future reserved words */
          "async",
          "await");

  /**
   * Set of words that JavaScript considers reserved words. These words cannot be used as
   * identifiers. This list is from the ECMA-262 v5, section 7.6.1:
   * http://www.ecma-international.org/publications/files/drafts/tc39-2009-050.pdf plus the keywords
   * for boolean values and {@code null}. (Also includes the identifiers "soy" and "soydata" which
   * are used internally by Soy.)
   */
  private static final ImmutableSet<String> LEGACY_JS_RESERVED_WORDS =
      ImmutableSet.<String>builder()
          .addAll(JS_LITERALS)
          .addAll(JS_RESERVED_WORDS)
          .add("soy")
          .add("soydata")
          .build();
}
