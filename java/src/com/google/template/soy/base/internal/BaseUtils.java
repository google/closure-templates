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

package com.google.template.soy.base.internal;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base utilities for Soy code.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class BaseUtils {

  private BaseUtils() {}

  /** Used by {@code ensureDirsExistInPath()}. Keeps track of known existing directory paths. */
  private static final Set<String> knownExistingDirs =
      Collections.synchronizedSet(Sets.newHashSet());

  /** Names of Soy constructs that can't be used as plugin names. */
  public static final ImmutableSet<String> ILLEGAL_PLUGIN_NAMES = ImmutableSet.of("map", "record");

  /** Regular expression for an identifier. */
  public static final String IDENT_RE = "\\$?[a-zA-Z_][a-zA-Z_0-9]*";

  /** Pattern for an identifier. */
  private static final Pattern IDENT_PATTERN = Pattern.compile(IDENT_RE);

  /** Pattern for an identifier with leading dot. */
  private static final Pattern IDENT_WITH_LEADING_DOT_PATTERN = Pattern.compile("[.]" + IDENT_RE);

  /** Regular expression for a dotted identifier. */
  public static final String DOTTED_IDENT_RE = IDENT_RE + "(?:[.]" + IDENT_RE + ")*";

  /** Pattern for a dotted identifier. */
  private static final Pattern DOTTED_IDENT_PATTERN = Pattern.compile(DOTTED_IDENT_RE);

  /** Pattern for a leading or trailing underscore. */
  private static final Pattern LEADING_OR_TRAILING_UNDERSCORE_PATTERN =
      Pattern.compile("^_+|_+\\Z");

  /** Pattern for places to insert underscores to make an identifier name underscore-separated. */
  private static final Pattern WORD_BOUNDARY_IN_IDENT_PATTERN =
      Pattern.compile(
          // <letter>_<upper><lower>
          "(?<= [a-zA-Z])(?= [A-Z][a-z])"
              // <letter>_<digit>
              + "| (?<= [a-zA-Z])(?= [0-9])"
              // <digit>_<letter>
              + "| (?<= [0-9])(?= [a-zA-Z])",
          Pattern.COMMENTS);

  /** Pattern for consecutive underscores. */
  private static final Pattern CONSECUTIVE_UNDERSCORES_PATTERN =
      Pattern.compile("_ _ _*", Pattern.COMMENTS);

  /** Hex digits for Soy strings (requires upper-case hex digits). */
  private static final char[] HEX_DIGITS = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
  };

  /**
   * Ensures that the directories in the given path exist, creating them if necessary.
   *
   * <p>Note: If the path does not end with the separator char (slash in Linux), then the name at
   * the end is assumed to be the file name, so directories are only created down to its parent.
   *
   * @param path The path for which to ensure directories exist.
   */
  public static void ensureDirsExistInPath(String path) {

    if (path == null || path.length() == 0) {
      throw new AssertionError("ensureDirsExistInPath called with null or empty path.");
    }

    String dirPath =
        (path.charAt(path.length() - 1) == File.separatorChar)
            ? path.substring(0, path.length() - 1)
            : new File(path).getParent();
    if (dirPath == null || knownExistingDirs.contains(dirPath)) {
      return; // known to exist
    } else {
      new File(dirPath).mkdirs();
      knownExistingDirs.add(dirPath);
    }
  }

  /**
   * Determines whether the given string is an identifier.
   *
   * <p>An identifier must start with a letter or underscore and must only contain letters, digits,
   * and underscores (i.e. it must match the regular expression {@code [A-Za-z_][A-Za-z_0-9]*}).
   *
   * @param s The string to check.
   * @return True if the given string is an identifier.
   */
  public static boolean isIdentifier(String s) {
    return IDENT_PATTERN.matcher(s).matches();
  }

  /**
   * Determines whether the given string is a dot followed by an identifier.
   *
   * @param s The string to check.
   * @return True if the given string is a dot followed by an identifier.
   */
  public static boolean isIdentifierWithLeadingDot(String s) {
    return IDENT_WITH_LEADING_DOT_PATTERN.matcher(s).matches();
  }

  /**
   * Determines whether the given string is a dotted identifier (e.g. {@code boo.foo0._goo}). A
   * dotted identifier is not required to have dots (i.e. a simple identifier qualifies as a dotted
   * identifier).
   *
   * @param s The string to check.
   * @return True if the given string is a dotted identifier (e.g. {@code boo.foo0._goo}).
   */
  public static boolean isDottedIdentifier(String s) {
    return DOTTED_IDENT_PATTERN.matcher(s).matches();
  }

  /**
   * Gets the part after the last dot in a dotted identifier. If there are no dots, returns the
   * whole input string.
   *
   * <p>Important: The input must be a dotted identifier. This is not checked.
   */
  public static String extractPartAfterLastDot(String dottedIdent) {
    int lastDotIndex = dottedIdent.lastIndexOf('.');
    return (lastDotIndex == -1) ? dottedIdent : dottedIdent.substring(lastDotIndex + 1);
  }

  /**
   * Converts an identifier to upper-underscore format. The identifier must start with a letter or
   * underscore and must only contain letters, digits, and underscores (i.e. it must match the
   * regular expression {@code [A-Za-z_][A-Za-z_0-9]*}).
   *
   * @param ident The identifier to convert.
   * @return The identifier in upper-underscore format.
   */
  public static String convertToUpperUnderscore(String ident) {

    ident = LEADING_OR_TRAILING_UNDERSCORE_PATTERN.matcher(ident).replaceAll("");
    ident = WORD_BOUNDARY_IN_IDENT_PATTERN.matcher(ident).replaceAll("_");
    ident = CONSECUTIVE_UNDERSCORES_PATTERN.matcher(ident).replaceAll("_");
    return Ascii.toUpperCase(ident);
  }

  /**
   * Builds a Soy string literal for this string value, including the surrounding quotes. Note that
   * Soy string syntax is a subset of JS string syntax, so the result should also be a valid JS
   * string.
   *
   * @param value The string value to escape.
   * @param shouldEscapeToAscii Whether to escape non-ASCII characters as Unicode hex escapes
   *     (backslash + 'u' + 4 hex digits).
   * @param quoteStyle What kind of quotes to wrap the string with.
   * @return A Soy string literal for this string value (including the surrounding quotes).
   */
  public static String escapeToWrappedSoyStringDoubleQuote(
      String value, boolean shouldEscapeToAscii, QuoteStyle quoteStyle) {
    return '\"' + escapeToSoyString(value, shouldEscapeToAscii, quoteStyle) + '\"';
  }

  /**
   * Builds a Soy string literal for this string value, including the surrounding quotes. Note that
   * Soy string syntax is a subset of JS string syntax, so the result should also be a valid JS
   * string.
   *
   * @param value The string value to escape.
   * @param shouldEscapeToAscii Whether to escape non-ASCII characters as Unicode hex escapes
   *     (backslash + 'u' + 4 hex digits).
   * @param quoteStyle What kind of quotes to wrap the string with.
   * @return A Soy string literal for this string value (including the surrounding quotes).
   */
  public static String escapeToWrappedSoyString(
      String value, boolean shouldEscapeToAscii, QuoteStyle quoteStyle) {
    return quoteStyle.getQuoteChar()
        + escapeToSoyString(value, shouldEscapeToAscii, quoteStyle)
        + quoteStyle.getQuoteChar();
  }

  /**
   * Builds a Soy string literal for this string value. Note that Soy string syntax is a subset of
   * JS string syntax, so the result should also be a valid JS string.
   *
   * <p>Adapted from StringUtil.javaScriptEscape().
   *
   * @param value The string value to escape.
   * @param shouldEscapeToAscii Whether to escape non-ASCII characters as Unicode hex escapes
   *     (backslash + 'u' + 4 hex digits).
   * @param quoteStyle What kind of quotes this string is expected to be wrapped with. This affects
   *     escaping (e.g. single quotes will be escaped in {@code QuoteStyle.SINGLE} strings).
   * @return A Soy string literal for this string value.
   */
  public static String escapeToSoyString(
      String value, boolean shouldEscapeToAscii, QuoteStyle quoteStyle) {

    if (quoteStyle == QuoteStyle.BACKTICK) {
      return value
          .replace("" + quoteStyle.getQuoteChar(), quoteStyle.getEscapeSeq())
          .replace("${", "\\${");
    }

    // StringUtil.javaScriptEscape() is meant to be compatible with JS string syntax, which is a
    // superset of the Soy expression string syntax, so we can't depend on it to properly escape a
    // Soy expression string literal. For example, they switched the default character escaping
    // to octal to save a few bytes, but octal escapes are not allowed in Soy syntax. I'm rewriting
    // the code here in a correct way for Soy.

    int len = value.length();
    StringBuilder out = new StringBuilder(len * 9 / 8);

    int codePoint;
    for (int i = 0; i < len; i += Character.charCount(codePoint)) {
      codePoint = value.codePointAt(i);

      switch (codePoint) {
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        case '\b':
          out.append("\\b");
          break;
        case '\f':
          out.append("\\f");
          break;
        case '\\':
          out.append("\\\\");
          break;
        default:
          if (codePoint == quoteStyle.getQuoteChar()) {
            out.append(quoteStyle.getEscapeSeq());
            break;
          }
          // If shouldEscapeToAscii, then hex escape characters outside the range 0x20 to 0x7F.
          if (shouldEscapeToAscii && (codePoint < 0x20 || codePoint >= 0x7F)) {
            appendHexEscape(out, codePoint);
          } else {
            out.appendCodePoint(codePoint);
          }
          break;
      }
    }

    return out.toString();
  }

  /**
   * Appends the Unicode hex escape sequence for the given code point (backslash + 'u' + 4 hex
   * digits) to the given StringBuilder.
   *
   * <p>Note: May append 2 escape sequences (surrogate pair) in the case of a supplementary
   * character (outside the Unicode BMP).
   *
   * <p>Adapted from StringUtil.appendHexJavaScriptRepresentation().
   *
   * @param out The StringBuilder to append to.
   * @param codePoint The Unicode code point whose hex escape sequence to append.
   */
  public static void appendHexEscape(StringBuilder out, int codePoint) {

    if (Character.isSupplementaryCodePoint(codePoint)) {
      // Handle supplementary unicode values which are not representable in
      // javascript.  We deal with these by escaping them as two 4B sequences
      // so that they will round-trip properly when sent from java to javascript
      // and back.
      char[] surrogates = Character.toChars(codePoint);
      appendHexEscape(out, surrogates[0]);
      appendHexEscape(out, surrogates[1]);

    } else {
      out.append("\\u")
          .append(HEX_DIGITS[(codePoint >>> 12) & 0xF])
          .append(HEX_DIGITS[(codePoint >>> 8) & 0xF])
          .append(HEX_DIGITS[(codePoint >>> 4) & 0xF])
          .append(HEX_DIGITS[codePoint & 0xF]);
    }
  }

  /** Trims the stack trace of the throwable such that the top item points at {@code cls}. */
  public static void trimStackTraceTo(Throwable t, Class<?> cls) {
    StackTraceElement[] ste = t.getStackTrace();
    String name = cls.getName();
    for (int i = 0; i < ste.length; i++) {
      if (ste[i].getClassName().equals(name)) {
        t.setStackTrace(Arrays.copyOf(ste, i + 1));
        return;
      }
    }
  }
}
