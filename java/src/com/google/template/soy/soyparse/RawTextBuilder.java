/*
 * Copyright 2016 Google Inc.
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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.RawTextNode.SourceOffsets;
import com.google.template.soy.soytree.RawTextNode.SourceOffsets.Reason;
import javax.annotation.Nullable;

/**
 * A helper for building raw text nodes.
 *
 * <p>RawText handling is complex. We need to
 *
 * <ul>
 *   <li>Perform line joining on sequences of 'basic raw text'
 *       <ul>
 *         <li>Remove a leading newline (and surrounding whitespace)
 *         <li>Remove a trailing newline (and surrounding whitespace)
 *         <li>Replace interior newlines and surrounding whitespace with a single {@code ' '} unless
 *             it immediately precedes a '<' or succeeds a '>' in which case we just remove it.
 *       </ul>
 *
 *   <li>Calculate the 'effective' start and end locations given the stripping of leading and
 *       trailing whitespace
 *   <li>Accumulate our other 'textual' tokens which do not trigger line joining
 * </ul>
 *
 * <p>These rules appear to be an approximation of the <a
 * href="https://www.w3.org/TR/html4/struct/text.html#h-9.1">html whitespace rules</a> but it
 * completely ignores interior non-newline whitespace and preserves it entirely. See the unit tests
 * for examples.
 */
final class RawTextBuilder {

  private final RawTextNode.SourceOffsets.Builder offsets = new RawTextNode.SourceOffsets.Builder();
  private final StringBuilder buffer = new StringBuilder();
  private final String fileName;
  private final IdGenerator nodeIdGen;

  // The index in buffer where the current sequence of basic textual content starts.
  private int basicStart = -1;
  // within a sequence of basic text, tracks the start of the current sequence of whitespace
  private int basicStartOfWhitespace = -1;
  // The ending line and column at the start of the current sequence of whitespace.  May be -1 if
  // the current whitespace is leading whitespace.
  private int endLineAtStartOfWhitespace;
  private int endColumnAtStartOfWhitespace;
  // tracks whether the current sequence of whitespace contains a newline
  private boolean basicHasNewline = false;
  // this will be set to non {@code NONE} if the previous sequence of text added isn't a basic
  // text literal.  this will force us to record a new offset for the next token
  private SourceOffsets.Reason discontinuityReason = Reason.NONE;

  RawTextBuilder(String fileName, IdGenerator nodeIdGen) {
    this.fileName = checkNotNull(fileName);
    this.nodeIdGen = checkNotNull(nodeIdGen);
  }

  /** Append a basic token. 'Basic' tokens are text literals. */
  void addBasic(Token token) {
    if (basicStart == -1) {
      basicStart = buffer.length();
      basicStartOfWhitespace = -1;
      basicHasNewline = false;
    }
    switch (token.kind) {
      case SoyFileParserConstants.TOKEN_WS:
        if (token.image.equals("\r\n") || token.image.equals("\r") || token.image.equals("\n")) {
          basicHasNewline = true;
        }
        if (basicStartOfWhitespace == -1) {
          basicStartOfWhitespace = buffer.length();
          endLineAtStartOfWhitespace = offsets.endLine();
          endColumnAtStartOfWhitespace = offsets.endColumn();
        }
        break;
      case SoyFileParserConstants.TOKEN_NOT_WS:
        maybeCollapseWhitespace(token.image);
        break;
      default:
        throw new AssertionError(
            SoyFileParserConstants.tokenImage[token.kind] + " is not a basic text token");
    }
    append(token, token.image);
  }

  /** Add the content for a '{literal}...{/literal}' section. */
  void addLiteral(Token literalContent) {
    checkArgument(literalContent.kind == SoyFileParserConstants.LITERAL_RAW_TEXT_CONTENT);

    maybeFinishBasic();
    // Note: the LITERAL_RAW_TEXT_CONTENT already has the correct image content (it matches the
    // closing {/literal} but excludes the actual closing tag).
    if (!literalContent.image.isEmpty()) {
      append(literalContent, literalContent.image);
    }
    discontinuityReason = Reason.LITERAL;
  }

  /** Add the content for a 'textual' command token, like '{sp}'. */
  void addTextualCommand(Token token) {
    maybeFinishBasic();
    // appending the empty string violates some invariants about the buffer only ever being extended
    if (token.kind != SoyFileParserConstants.CMD_FULL_NIL) {
      append(token, rawTextCmdToString(token));
    }
    discontinuityReason = Reason.COMMAND;
  }

  private static String rawTextCmdToString(Token token) {
    switch (token.kind) {
      case SoyFileParserConstants.CMD_FULL_SP:
        return " ";
      case SoyFileParserConstants.CMD_FULL_CR:
        return "\r";
      case SoyFileParserConstants.CMD_FULL_LF:
        return "\n";
      case SoyFileParserConstants.CMD_FULL_TAB:
        return "\t";
      case SoyFileParserConstants.CMD_FULL_LB:
        return "{";
      case SoyFileParserConstants.CMD_FULL_RB:
        return "}";
      default:
        throw new IllegalArgumentException(
            "unexpected token: " + SoyFileParserConstants.tokenImage[token.kind]);
    }
  }

  RawTextNode build() {
    maybeFinishBasic();
    String text = buffer.toString();
    RawTextNode.SourceOffsets sourceOffsets = offsets.build(text.length(), discontinuityReason);
    return new RawTextNode(
        nodeIdGen.genId(), text, sourceOffsets.getSourceLocation(fileName), sourceOffsets);
  }

  /** updates the location with the given tokens location. */
  private void append(Token token, String content) {
    if (content.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "shouldn't append empty content: %s @ %s",
              SoyFileParserConstants.tokenImage[token.kind], Tokens.createSrcLoc(fileName, token)));
    }
    // add a new offset if:
    // - this is the first token
    // - the previous token introduced a discontinuity (due to a special token, or whitespace
    //   joining)
    // - this token doesn't directly abut the previous token (this happens when there is a comment)
    boolean addOffset = false;
    if (offsets.isEmpty()) {
      addOffset = true;
    } else if (discontinuityReason != Reason.NONE) {
      addOffset = true;
    } else {
      // are the two tokens not adjacent? We don't actually record comments in the AST or token
      // stream so this is kind of a guess, but all known cases are due to comments.
      if (offsets.endLine() == token.beginLine) {
        if (offsets.endColumn() + 1 != token.beginColumn) {
          addOffset = true;
          discontinuityReason = Reason.COMMENT;
        }
      } else if (offsets.endLine() + 1 == token.beginLine && token.beginColumn != 1) {
        addOffset = true;
        discontinuityReason = Reason.COMMENT;
      }
    }
    if (addOffset) {
      offsets.add(buffer.length(), token.beginLine, token.beginColumn, discontinuityReason);
      discontinuityReason = Reason.NONE;
    }
    offsets.setEndLocation(token.endLine, token.endColumn);
    buffer.append(content);
  }

  // Completes the current open basic text sequence.
  private void maybeFinishBasic() {
    if (basicStart != -1) {
      maybeCollapseWhitespace(null);
      basicStart = -1;
    }
  }

  /**
   * This method should be called at the end of a sequence of basic whitespace tokens. This is how
   * we implement the line joining algorithm.
   *
   * @param next The next basic text token image, or null if the next token isn't a basic token.
   */
  private void maybeCollapseWhitespace(@Nullable String next) {
    if (basicStartOfWhitespace != -1) {
      if (basicHasNewline) {
        // Note: if we are replacing the whitespace we don't need to update our source location
        // information.  This is because
        // 1. if we are stripping leading whitespace, the next token will be the start token
        //    - note: if there is no next token, the whole raw text node will get dropped, so we
        //      won't need a source location
        // 2. if we are stripping trailing whitespace, the previously assigned location should be
        //    preserved
        // 3. if we are in the middle, then our location is irrelevant
        if (basicStart == basicStartOfWhitespace || next == null) {
          // leading or trailing whitespace, remove it all
          buffer.delete(basicStartOfWhitespace, buffer.length());
          offsets.delete(basicStartOfWhitespace);
          if (next == null && endColumnAtStartOfWhitespace != -1) {
            // if this is trailing whitespace, then our end location will be wrong, so restore it to
            // what it was when we started accumulating whitespace (assuming we had one).
            offsets.setEndLocation(endLineAtStartOfWhitespace, endColumnAtStartOfWhitespace);
          }
        } else {
          // We are in the middle, we either remove the whole segment or replace it with a single
          // space character based on whether or not we appear to be butted up next to an html tag.
          // This logic is definitely suspicious but it is important to maintain for compatibility
          // reasons.
          if (next.charAt(0) == '<' || buffer.charAt(basicStartOfWhitespace - 1) == '>') {
            // we are immediately before or after an html tag.
            buffer.delete(basicStartOfWhitespace, buffer.length());
            offsets.delete(basicStartOfWhitespace);
          } else {
            // Otherwise, collapse to a single whitespace character.
            buffer.replace(basicStartOfWhitespace, buffer.length(), " ");
            offsets.delete(basicStartOfWhitespace);
          }
        }
        discontinuityReason = Reason.WHITESPACE;
        basicHasNewline = false;
      }
      basicStartOfWhitespace = -1;
    }
  }
}
