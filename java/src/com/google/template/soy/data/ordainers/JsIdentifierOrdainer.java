/*
 * Copyright 2014 Google Inc.
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
package com.google.template.soy.data.ordainers;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import java.util.regex.Pattern;

/**
 * Creation utilities for SanitizedContent objects for JS identifiers.
 *
 * <p>This is based on http://mathiasbynens.be/notes/javascript-identifiers
 *
 */
public final class JsIdentifierOrdainer {

  /**
   * Valid patterns for JS identifiers.
   *
   * <p>This does not accept zero-width characters.
   */
  private static final Pattern VALID_JS_IDENTIFIER_PATTERN =
      Pattern.compile(
          "^[$_\\p{IsLetter}][$_\\p{IsLetter}\\p{IsDigit}]*$", Pattern.UNICODE_CHARACTER_CLASS);

  /**
   * Invalid JS identifiers.
   *
   * <p>Reserved words and other identifiers that are almost never a good idea to use.
   *
   * <p>TODO(Tony Payne): See if there is a canonical list somewhere.
   */
  private static final ImmutableSet<String> INVALID_JS_IDENTIFIERS =
      ImmutableSet.of(
          /* reserved words */
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
          /* future reserved words */
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
          /* literals */
          "null",
          "true",
          "false",
          "NaN",
          "Infinity",
          "undefined",
          /* other words to avoid */
          "eval",
          "arguments",
          "int",
          "byte",
          "char",
          "goto",
          "long",
          "final",
          "float",
          "short",
          "double",
          "native",
          "throws",
          "boolean",
          "abstract",
          "volatile",
          "transient",
          "synchronized",
          "prototype",
          "__proto__");

  /** No constructor. */
  private JsIdentifierOrdainer() {}

  /**
   * Validates that {@code identifier} matches a safe pattern for JS identifiers and ordains the
   * value as JS.
   *
   * <p>TODO: this appears to be redundant with some code in JsSrcUtils.
   */
  public static SanitizedContent jsIdentifier(String identifier) {
    checkArgument(
        VALID_JS_IDENTIFIER_PATTERN.matcher(identifier).matches(),
        "JS identifier '%s' should match the pattern '%s'",
        identifier,
        VALID_JS_IDENTIFIER_PATTERN.pattern());
    checkArgument(
        !INVALID_JS_IDENTIFIERS.contains(identifier),
        "JS identifier '%s' should not be a reserved word or match a literal",
        identifier);
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(identifier, ContentKind.JS);
  }
}
