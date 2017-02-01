/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.soyparse;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.template.soy.base.SourceLocation;

/** Helpers for dealing with {@link Token tokens} */
final class Tokens {

  /**
   * Returns a source location for the given tokens.
   *
   * <p>All the provided tokens should be in strictly increasing order.
   */
  static SourceLocation createSrcLoc(String filePath, Token first, Token... rest) {
    int beginLine = first.beginLine;
    int beginColumn = first.beginColumn;
    int endLine = first.endLine;
    int endColumn = first.endColumn;

    for (Token next : rest) {
      checkArgument(startsLaterThan(next, beginLine, beginColumn));
      checkArgument(endsLaterThan(next, endLine, endColumn));
      endLine = next.endLine;
      endColumn = next.endColumn;
    }
    return new SourceLocation(filePath, beginLine, beginColumn, endLine, endColumn);
  }

  private static boolean startsLaterThan(Token tok, int beginLine, int beginCol) {
    return tok.beginLine > beginLine || (tok.beginLine == beginLine && tok.beginColumn > beginCol);
  }

  private static boolean endsLaterThan(Token tok, int endLine, int endCol) {
    return tok.endLine > endLine || (tok.endLine == endLine && tok.endColumn > endCol);
  }

  /**
   * Checks that the parser is exactly one token ahead of the parser.
   *
   * <p>Our grammar is (mostly) LL(1) so this should be the standard case, but if LOOKAHEAD is
   * introduced http://www.engr.mun.ca/~theo/JavaCC-FAQ/javacc-faq-moz.htm#tth_sEc3.12
   *
   * @param parser The parser.
   */
  static void checkLexerIsExactlyOneTokenAhead(SoyFileParser parser) {
    Token current = parser.getToken(0);
    if (current.next != null) {
      if (current.next.next != null) {
        throw new IllegalStateException("lexer is more than one token ahead");
      }
    } else {
      throw new IllegalStateException("lexer is 0 tokens ahead, this should be impossible.");
    }
  }
}
