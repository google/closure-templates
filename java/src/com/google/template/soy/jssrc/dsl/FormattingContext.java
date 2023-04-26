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

package com.google.template.soy.jssrc.dsl;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Helper class to keep track of state during a single call to {@link CodeChunk#getCode()},
 * including the initial statements that have already been formatted and the current indentation
 * level.
 */
class FormattingContext implements AutoCloseable {
  private static final int MAX_LINE_LENGTH = 80;
  private final StringBuilder buf;
  private final int initialSize;

  private final FormatOptions formatOptions;
  private Scope curScope = new Scope(/* parent= */ null, /* emitClosingBrace= */ false);
  private String curIndent;
  private boolean nextAppendShouldStartNewLine = false;
  private final ArrayDeque<LexicalState> lexicalStateStack;

  public enum LexicalState {
    JS,
    TSX,
    TSX_ATTR,
    TTL,
    RANGE_COMMENT
  }

  FormattingContext(FormatOptions formatOptions) {
    this.formatOptions = formatOptions;
    curIndent = "";
    buf = new StringBuilder();
    initialSize = 0;
    lexicalStateStack = new ArrayDeque<>();
    lexicalStateStack.push(LexicalState.JS);
  }

  public FormatOptions getFormatOptions() {
    return formatOptions;
  }

  public FormattingContext copyWithSameOptions() {
    return new FormattingContext(formatOptions);
  }

  /**
   * Returns a buffering context that will not insert any line breaks or indents automatically. The
   * contents of the buffer will be appended to the main context as a single string on close.
   */
  FormattingContext buffer() {
    FormattingContext parent = this;
    FormatOptions bufferOptions =
        formatOptions.useTsxLineBreaks()
            ? formatOptions.toBuilder().setUseTsxLineBreaks(false).build()
            : formatOptions;
    FormattingContext context =
        new FormattingContext(bufferOptions) {
          @Override
          public void close() {
            String buffer = this.toString();
            parent.append(buffer);
          }
        };
    context.lexicalStateStack.push(this.lexicalStateStack.peek());
    return context;
  }

  void pushLexicalState(LexicalState lexicalState) {
    lexicalStateStack.push(lexicalState);
  }

  void popLexicalState() {
    lexicalStateStack.pop();
  }

  private LexicalState getCurrentLexicalState() {
    return lexicalStateStack.peek();
  }

  @CanIgnoreReturnValue
  public FormattingContext appendQuotedString(String s, QuoteStyle style) {
    switch (getCurrentLexicalState()) {
      case TSX_ATTR:
        style = style.escaped(); // fall-through
      case JS:
        return append(
            escapeCloseScript(
                BaseUtils.escapeToWrappedSoyString(s, formatOptions.htmlEscapeStrings(), style)));
      case TTL:
        String content =
            BaseUtils.escapeToSoyString(s, formatOptions.htmlEscapeStrings(), QuoteStyle.BACKTICK);
        if (content.contains("\n")) {
          return appendWithoutBreaks(content);
        } else {
          return append(content);
        }
      default:
        return append(s);
    }
  }

  private static String escapeCloseScript(String s) {
    // </script in a JavaScript string will end the current script tag in most browsers. Escape the
    // forward slash in the string to get around this issue.
    return s.replace("</script", "<\\/script");
  }

  String getInterpolationOpenString() {
    switch (getCurrentLexicalState()) {
      case JS:
        return "";
      case TSX:
      case TSX_ATTR:
        return "{";
      case TTL:
        return "${";
      case RANGE_COMMENT:
        throw new IllegalStateException();
    }
    throw new AssertionError();
  }

  String getInterpolationCloseString() {
    switch (getCurrentLexicalState()) {
      case JS:
        return "";
      case TSX:
      case TSX_ATTR:
      case TTL:
        return "}";
      case RANGE_COMMENT:
        throw new IllegalStateException();
    }
    throw new AssertionError();
  }

  String getConcatenationOperator() {
    switch (getCurrentLexicalState()) {
      case JS:
        return " + ";
      case TSX:
      case TSX_ATTR:
      case TTL:
        return "";
      case RANGE_COMMENT:
        throw new IllegalStateException();
    }
    throw new AssertionError();
  }

  @CanIgnoreReturnValue
  FormattingContext append(String stuff) {
    if (!stuff.equals(";")) {
      maybeBreakLineInsideTsxElement(stuff);
      maybeIndent(stuff.isEmpty() ? '\0' : stuff.charAt(0));
    }
    buf.append(stuff);
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext append(char c) {
    maybeBreakLineInsideTsxElement(Character.toString(c));
    maybeIndent(c);
    buf.append(c);
    return this;
  }

  /** Appends exactly {@code s} onto the buffer without attempting to add line breaks or indents. */
  @CanIgnoreReturnValue
  FormattingContext appendWithoutBreaks(String s) {
    buf.append(s);
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext appendUnlessEmpty(String stuff) {
    if (stuff != null && !stuff.isEmpty()) {
      this.append(stuff);
    }
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext appendForeignCode(String stuff) {
    stuff = stuff.replace("\n", "\n" + curIndent);
    return append(stuff);
  }

  void appendBlankLine() {
    endLine();
    append("\n");
    endLine();
  }

  /**
   * Writes the initial statements for the {@code chunk} to the buffer. This is the only allowed
   * direct caller of {@link CodeChunk#doFormatInitialStatements}.
   */
  @CanIgnoreReturnValue
  FormattingContext appendInitialStatements(CodeChunk chunk) {
    // Never write the same initial statement more than once.
    if (shouldAppend(chunk)) {
      chunk.doFormatInitialStatements(this);
    }
    return this;
  }

  /** Writes the output expression for the {@code value} to the buffer. */
  @CanIgnoreReturnValue
  FormattingContext appendOutputExpression(Expression value) {
    value.doFormatOutputExpr(this);
    return this;
  }

  /** Writes all code for the {@code chunk} to the buffer. */
  @CanIgnoreReturnValue
  FormattingContext appendAll(CodeChunk chunk) {
    appendInitialStatements(chunk);
    if (chunk instanceof Expression) {
      appendOutputExpression((Expression) chunk);
      if (getCurrentLexicalState() == LexicalState.JS) {
        append(";");
        endLine();
      }
    } else if (chunk instanceof SpecialToken) {
      ((SpecialToken) chunk).doFormatToken(this);
    }
    return this;
  }

  private boolean shouldAppend(CodeChunk chunk) {
    return curScope.append(chunk);
  }

  @CanIgnoreReturnValue
  FormattingContext enterBlock() {
    maybeIndent('{');
    buf.append('{');
    increaseIndent();
    endLine();
    curScope = new Scope(curScope, /* emitClosingBrace= */ true);
    return this;
  }

  /**
   * For use only by {@link Switch#doFormatStatement}. It's not an error for bodies of case clauses
   * to be brace-delimited, but it is slightly less readable, so omit them.
   */
  @CanIgnoreReturnValue
  FormattingContext enterCaseBody() {
    maybeIndent('\0');
    increaseIndent();
    endLine();
    curScope = new Scope(curScope, /* emitClosingBrace= */ false);
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext endLine() {
    // To prevent spurious trailing whitespace, don't actually write the newline
    // until the next call to append().
    nextAppendShouldStartNewLine = true;
    return this;
  }

  char getLastChar() {
    return buf.length() == 0 ? '\0' : buf.charAt(buf.length() - 1);
  }

  private void maybeBreakLineInsideTsxElement(String nextAppendContent) {
    if (!formatOptions.useTsxLineBreaks() || buf.length() == 0 || nextAppendContent.isEmpty()) {
      return;
    }

    if (lexicalStateStack.peek() == LexicalState.RANGE_COMMENT) {
      return;
    }

    char lastChar = getLastChar();
    char nextChar = nextAppendContent.charAt(0);
    if (getCurrentLexicalState() == LexicalState.TSX && lastChar == '>' && nextChar == '<') {
      endLine();
    } else if (lastChar == '}' && nextChar == '{') {
      endLine();
    } else if (!fitsOnCurrentLine(nextAppendContent)) {
      endLine();
    }
  }

  /**
   * Return whether a code string can be appended to the current line without going over
   * MAX_LINE_LENGTH.
   */
  private boolean fitsOnCurrentLine(String stuff) {
    int lastNewLine = buf.lastIndexOf("\n");
    int currentLineLength =
        lastNewLine < 0
            ? buf.length()
            : lastNewLine == buf.length() - 1 ? 0 : buf.length() - lastNewLine;
    return currentLineLength + stuff.length() < MAX_LINE_LENGTH;
  }

  /**
   * If this is the first call to {@link #append} since the last {@link #endLine}, writes the
   * newline and leading indentation.
   */
  private void maybeIndent(char nextChar) {
    char lastChar = getLastChar();
    LexicalState current = lexicalStateStack.peek();
    if ((current != LexicalState.RANGE_COMMENT)) {
      // TSX safeguard: it's never safe to break a line when there's a space character at the join
      // location.
      // The reasoning above is faulty (blaming this on TSX). Some line breaks in JS are also
      // problematic (due to optional semicolon?).
      if (formatOptions.useTsxLineBreaks() && (nextChar == ' ' || lastChar == ' ')) {
        nextAppendShouldStartNewLine = false;
      }
    }

    if (nextAppendShouldStartNewLine) {
      if (lastChar != '\n') {
        buf.append('\n');
      }
      if (nextChar != '\n') {
        buf.append(curIndent);
      }
      nextAppendShouldStartNewLine = false;
    }
  }

  /** Increases the indent once (two spaces). */
  @CanIgnoreReturnValue
  FormattingContext increaseIndent() {
    curIndent += "  ";
    return this;
  }

  /** Decreases the indent once (two spaces). */
  @CanIgnoreReturnValue
  FormattingContext decreaseIndent() {
    Preconditions.checkState(!curIndent.isEmpty());
    return decreaseIndentLenient();
  }

  @CanIgnoreReturnValue
  FormattingContext decreaseIndentLenient() {
    if (curIndent.length() > 1) {
      curIndent = curIndent.substring(2);
    }
    return this;
  }

  @Override
  public String toString() {
    return isEmpty() ? "" : buf.toString();
  }

  boolean isEmpty() {
    return buf.length() == initialSize;
  }

  @Override
  public void close() {
    boolean emitClosingBrace = curScope.emitClosingBrace;
    curScope = Preconditions.checkNotNull(curScope.parent);
    decreaseIndentLenient();
    endLine();
    if (emitClosingBrace) {
      append('}');
    }
  }

  /**
   * Returns a FormattingContext representing the concatenation of this FormattingContext with
   * {@code other}. For use only by {@link CodeChunk#getCode(int, OutputContext)}.
   */
  FormattingContext concat(FormattingContext other) {
    if (isEmpty()) {
      return other;
    } else if (other.isEmpty()) {
      return this;
    } else {
      curIndent = ""; // don't serialize trailing whitespace in front of the next FormattingContext.
      return append(other.toString());
    }
  }

  /**
   * {@link FormattingContext} needs to keep track of the conditional nesting structure in order to
   * avoid, for example, formatting the initial statements of a code chunk in one branch and
   * referencing the chunk in another. The scopes form a simple tree, built and torn down by {@link
   * #enterBlock()} and {@link #close()} respectively. {@link FormattingContext#curScope} points to
   * the current tip of the tree.
   */
  private static final class Scope {
    private final Set<CodeChunk> appendedChunks =
        Collections.newSetFromMap(new IdentityHashMap<>());
    @Nullable final Scope parent;
    final boolean emitClosingBrace;

    Scope(@Nullable Scope parent, boolean emitClosingBrace) {
      this.parent = parent;
      this.emitClosingBrace = emitClosingBrace;
    }

    boolean append(CodeChunk chunk) {
      if (!alreadyAppended(chunk)) {
        appendedChunks.add(chunk);
        return true;
      }
      return false;
    }

    private boolean alreadyAppended(CodeChunk chunk) {
      return appendedChunks.contains(chunk) || (parent != null && parent.alreadyAppended(chunk));
    }
  }
}
