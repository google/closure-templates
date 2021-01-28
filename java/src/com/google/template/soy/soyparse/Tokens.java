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

import com.google.common.base.MoreObjects;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;

/** Helpers for dealing with {@link Token tokens} */
final class Tokens {

  /**
   * Returns a source location for the given tokens.
   *
   * <p>All the provided tokens should be in strictly increasing order.
   */
  static SourceLocation createSrcLoc(SourceFilePath filePath, Token first, Token... rest) {
    int beginLine = first.beginLine;
    int beginColumn = first.beginColumn;
    int endLine = first.endLine;
    int endColumn = first.endColumn;

    for (Token next : rest) {
      if (!startsLaterThan(next, beginLine, beginColumn)) {
        throw new IllegalArgumentException(
            String.format(
                "In file: %s, expected %s to start after (%d, %d)",
                filePath, toString(next), beginLine, beginColumn));
      }
      if (!endsLaterThan(next, endLine, endColumn)) {
        throw new IllegalArgumentException(
            String.format(
                "In file: %s, expected %s to end after (%d, %d)",
                filePath, toString(next), endLine, endColumn));
      }
      ;
      endLine = next.endLine;
      endColumn = next.endColumn;
    }
    // this special case happens for completely empty files.
    if (beginLine == 0 && endLine == 0 && beginColumn == 0 && endColumn == 0) {
      return new SourceLocation(filePath);
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
   * Returns {@code true} if the two tokens are adjacent in the input stream with no intervening
   * characters.
   */
  static boolean areAdjacent(Token first, Token second) {
    return first.endLine == second.beginLine && first.endColumn == second.beginColumn - 1;
  }

  static String toString(Token token) {
    return MoreObjects.toStringHelper(Token.class)
        .add("type", getTokenDisplayName(token.kind))
        .add("image", token.image)
        .add("beginLine", token.beginLine)
        .add("beginColumn", token.beginColumn)
        .add("endLine", token.endLine)
        .add("endColumn", token.endColumn)
        .toString();
  }

  /**
   * Returns a human friendly display name for tokens. By default we use the generated token image
   * which is appropriate for literal tokens. or returns {@code null} if the name shouldn't be
   * displayed.
   */
  static String getTokenDisplayName(int tokenId) {
    switch (tokenId) {

        // File-level tokens:
      case SoyFileParserConstants.ALIAS_OPEN:
        return "{alias";
      case SoyFileParserConstants.DELPACKAGE_OPEN:
        return "{delpackage";
      case SoyFileParserConstants.DELTEMPLATE_OPEN:
        return "{deltemplate";
      case SoyFileParserConstants.NAMESPACE_OPEN:
        return "{namespace";
      case SoyFileParserConstants.CONST_OPEN:
        return "{[export ]const";
      case SoyFileParserConstants.TEMPLATE_OPEN:
        return "{template";
      case SoyFileParserConstants.ELEMENT_OPEN:
        return "{element";
      case SoyFileParserConstants.EOF:
        return "eof";

        // Template tokens:
      case SoyFileParserConstants.CMD_BEGIN_CALL:
        return "{call";
      case SoyFileParserConstants.CMD_BEGIN_DELCALL:
        return "{delcall";
      case SoyFileParserConstants.CMD_BEGIN_PARAM:
        return "{param";
      case SoyFileParserConstants.CMD_BEGIN_MSG:
        return "{msg";
      case SoyFileParserConstants.CMD_BEGIN_FALLBACK_MSG:
        return "{fallbackmsg";
      case SoyFileParserConstants.CMD_BEGIN_IF:
        return "{if";
      case SoyFileParserConstants.CMD_BEGIN_ELSEIF:
        return "{elseif";
      case SoyFileParserConstants.CMD_BEGIN_LET:
        return "{let";
      case SoyFileParserConstants.CMD_BEGIN_FOR:
        return "{for";
      case SoyFileParserConstants.CMD_BEGIN_PLURAL:
        return "{plural";
      case SoyFileParserConstants.CMD_BEGIN_SELECT:
        return "{select";
      case SoyFileParserConstants.CMD_BEGIN_SWITCH:
        return "{switch";
      case SoyFileParserConstants.CMD_BEGIN_CASE:
        return "{case";
      case SoyFileParserConstants.CMD_BEGIN_PRINT:
        return "{print";
      case SoyFileParserConstants.CMD_BEGIN_KEY:
        return "{key";
      case SoyFileParserConstants.CMD_BEGIN_VELOG:
        return "{velog";
      case SoyFileParserConstants.NAME:
      case SoyFileParserConstants.IDENT:
        return "identifier";
      case SoyFileParserConstants.ATTR_IDENT:
        return "attribute identifier";
      case SoyFileParserConstants.SQ_ATTRIBUTE_VALUE:
      case SoyFileParserConstants.DQ_ATTRIBUTE_VALUE:
        return "attribute value";

      case SoyFileParserConstants.CMD_FULL_SP:
      case SoyFileParserConstants.CMD_FULL_NIL:
      case SoyFileParserConstants.CMD_FULL_CR:
      case SoyFileParserConstants.CMD_FULL_LF:
      case SoyFileParserConstants.CMD_FULL_TAB:
      case SoyFileParserConstants.CMD_FULL_LB:
      case SoyFileParserConstants.CMD_FULL_RB:
      case SoyFileParserConstants.CMD_FULL_NBSP:
      case SoyFileParserConstants.TOKEN_NOT_WS:
        return "text";
      case SoyFileParserConstants.TOKEN_WS:
        return "whitespace";

        // Expression tokens:
      case SoyFileParserConstants.HEX_INTEGER:
      case SoyFileParserConstants.DEC_INTEGER:
      case SoyFileParserConstants.FLOAT:
        return "number";
      case SoyFileParserConstants.SINGLE_QUOTE:
      case SoyFileParserConstants.DOUBLE_QUOTE:
        return "string";
      case SoyFileParserConstants.FOR:
        return "\'for\'";
      case SoyFileParserConstants.IN:
        return "\'in\'";

      case SoyFileParserConstants.UNEXPECTED_TOKEN:
        throw new AssertionError("we should never expect the unexpected token");

      default:
        return SoyFileParserConstants.tokenImage[tokenId];
    }
  }

  private Tokens() {}
}
