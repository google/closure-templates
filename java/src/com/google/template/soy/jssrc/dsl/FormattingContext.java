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
import com.google.common.base.Utf8;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.ByteSpan;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.javagencode.KytheHelper;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Helper class to keep track of state during a single call to {@link CodeChunk#getCode}, including
 * the initial statements that have already been formatted and the current indentation level.
 */
class FormattingContext implements AutoCloseable {
  private static final int MAX_LINE_LENGTH = 80;

  private StringBuilder buf;
  private final FormatOptions formatOptions;
  @Nullable private KytheHelper kytheHelper;
  private SourceMapHelper sourceMapHelper = SourceMapHelper.NO_OP;
  private Scope curScope = new Scope(/* parent= */ null, /* emitClosingBrace= */ false);
  private String curIndent;
  private final ArrayDeque<LexicalState> lexicalStateStack;

  private boolean nextAppendShouldStartNewLine = false;
  private boolean nextAppendShouldNeverStartNewLine = false;
  @Nullable private ByteSpan nextImputee;
  @Nullable private String nextSourcemapToken;
  private Deque<SourceLocation> locationStack = new ArrayDeque<>();

  private int currentByteOffset = 0;
  private int currentLine = 0; // 0-based to match SourceMapGeneratorV3
  private int currentColumn = 0; // 0-based to match SourceMapGeneratorV3

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
    this.buf = new StringBuilder();
    lexicalStateStack = new ArrayDeque<>();
    lexicalStateStack.push(LexicalState.JS);
  }

  public void setKytheHelper(@Nullable KytheHelper kytheHelper) {
    this.kytheHelper = kytheHelper;
  }

  public void setSourceMapHelper(SourceMapHelper sourceMapHelper) {
    this.sourceMapHelper = Preconditions.checkNotNull(sourceMapHelper);
  }

  /**
   * Returns a buffering context that will not insert any line breaks or indents automatically. The
   * contents of the buffer will be appended to the main context as a single string on close.
   */
  FormattingContext buffer() {
    FormattingContext parent = this;
    FormatOptions bufferOptions = formatOptions.toBuilder().setUseTsxLineBreaks(false).build();
    FormattingContext context =
        new FormattingContext(bufferOptions) {
          @Override
          public void close() {
            parent.append(this.getBuffer());
          }
        };

    context.setKytheHelper(kytheHelper);
    context.currentByteOffset = currentByteOffset;

    context.setSourceMapHelper(sourceMapHelper);
    context.currentLine = currentLine;
    context.currentColumn = currentColumn;

    context.lexicalStateStack.push(this.lexicalStateStack.peek());
    return context;
  }

  void pushLexicalState(LexicalState lexicalState) {
    lexicalStateStack.push(lexicalState);
  }

  void popLexicalState() {
    lexicalStateStack.pop();
  }

  LexicalState getCurrentLexicalState() {
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
          return noBreak().append(content);
        } else {
          return append(content);
        }
      case TSX:
        // '>' is allowed in Soy but not in TSX.
        return append(s.replace(">", "&gt;"));
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

  boolean whitespaceIsSignificant() {
    switch (getCurrentLexicalState()) {
      case TTL:
        return true;
      default:
        return false;
    }
  }

  /** Delays any line breaking until after the next token append. */
  @CanIgnoreReturnValue
  public FormattingContext noBreak() {
    nextAppendShouldNeverStartNewLine = true;
    return this;
  }

  public boolean commaAfterFirst(boolean first) {
    if (!first) {
      noBreak().append(", ");
    }
    return false;
  }

  @CanIgnoreReturnValue
  FormattingContext withSpan(@Nullable ByteSpan soyOffsetSpan) {
    Preconditions.checkState(nextImputee == null);
    nextImputee = soyOffsetSpan;
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext withSourceToken(@Nullable String token) {
    Preconditions.checkState(nextSourcemapToken == null);
    nextSourcemapToken = token;
    return this;
  }

  private void forceOutputWhitespace(CharSequence nextToken) {
    if (!whitespaceIsSignificant()) {
      maybeBreakLineInsideTsxElement(nextToken);
      maybeIndent(nextToken.length() == 0 ? '\0' : nextToken.charAt(0));
    }
  }

  @CanIgnoreReturnValue
  FormattingContext append(CharSequence stuff) {
    forceOutputWhitespace(stuff);

    SourceLocation loc = locationStack.peek();
    boolean addSourcemap = loc != null && loc.isKnown() && sourceMapHelper != null;
    boolean addKythe = nextImputee != null && nextImputee.isKnown() && kytheHelper != null;

    if (addSourcemap || addKythe) {
      int startBytes = currentByteOffset;
      int startCol = currentColumn;
      int startLine = currentLine;

      appendToBuffer(stuff);
      if (addKythe) {
        kytheHelper.addKytheLinkTo(
            nextImputee.getStart(), nextImputee.getEnd(), startBytes, currentByteOffset);
      }
      if (addSourcemap) {
        sourceMapHelper.mark(
            loc, startLine, startCol, currentLine, currentColumn, nextSourcemapToken);
      }
    } else {
      appendToBuffer(stuff);
    }
    nextImputee = null;
    nextSourcemapToken = null;
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext append(char c) {
    return append(Character.toString(c));
  }

  @CanIgnoreReturnValue
  FormattingContext appendToBuffer(CharSequence stuff) {
    stuff
        .codePoints()
        .forEach(
            c -> {
              if (c == '\n') {
                currentLine++;
                currentColumn = 0;
              } else {
                currentColumn++;
              }
            });
    buf.append(stuff);
    currentByteOffset += Utf8.encodedLength(stuff);
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext enterGroup() {
    if (lexicalStateStack.peek() == LexicalState.JS) {
      append('(');
    }
    return this;
  }

  @CanIgnoreReturnValue
  FormattingContext exitGroup() {
    if (lexicalStateStack.peek() == LexicalState.JS) {
      append(')');
    }
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
      withLocation(chunk, () -> chunk.doFormatInitialStatements(this));
    }
    return this;
  }

  /** Writes the output expression for the {@code value} to the buffer. */
  @CanIgnoreReturnValue
  FormattingContext appendOutputExpression(Expression value) {
    withLocation(value, () -> value.doFormatOutputExpr(this));
    return this;
  }

  private void withLocation(CodeChunk chunk, Runnable task) {
    SourceLocation loc = sourceMapHelper.getPrimaryLocation(chunk);
    if (loc != null) {
      locationStack.push(loc);
    }
    task.run();
    if (loc != null) {
      locationStack.pop();
    }
  }

  /** Writes all code for the {@code chunk} to the buffer. */
  @CanIgnoreReturnValue
  FormattingContext appendAll(CodeChunk chunk) {
    appendInitialStatements(chunk);
    if (chunk instanceof Expression) {
      int l1 = buf.length();
      appendOutputExpression((Expression) chunk);
      if ((buf.length() > l1) && getCurrentLexicalState() == LexicalState.JS) {
        append(";");
        endLine();
      }
    } else if (chunk instanceof SpecialToken) {
      withLocation(chunk, () -> ((SpecialToken) chunk).doFormatToken(this));
    }
    return this;
  }

  private boolean shouldAppend(CodeChunk chunk) {
    return curScope.append(chunk);
  }

  @CanIgnoreReturnValue
  FormattingContext enterBlock() {
    maybeIndent('{');
    appendToBuffer("{");
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
    nextAppendShouldNeverStartNewLine = false;
    return this;
  }

  boolean isEndOfLine() {
    return nextAppendShouldStartNewLine;
  }

  char getLastChar() {
    return buf.length() == 0 ? '\0' : buf.charAt(buf.length() - 1);
  }

  private void maybeBreakLineInsideTsxElement(CharSequence nextAppendContent) {
    boolean defer = nextAppendShouldNeverStartNewLine;
    nextAppendShouldNeverStartNewLine = false;

    if (!formatOptions.useTsxLineBreaks() || buf.length() == 0 || nextAppendContent.length() == 0) {
      return;
    }

    if (lexicalStateStack.peek() == LexicalState.RANGE_COMMENT) {
      return;
    }

    boolean endLine = false;
    char lastChar = getLastChar();
    char nextChar = nextAppendContent.charAt(0);
    if (getCurrentLexicalState() == LexicalState.TSX && lastChar == '>' && nextChar == '<') {
      endLine = true;
    } else if (lastChar == '}' && nextChar == '{') {
      endLine = true;
    } else if (!fitsOnCurrentLine(nextAppendContent)) {
      endLine = true;
    }

    if (endLine && !defer) {
      endLine();
    }
  }

  /**
   * Return whether a code string can be appended to the current line without going over
   * MAX_LINE_LENGTH.
   */
  private boolean fitsOnCurrentLine(CharSequence stuff) {
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
    if (current != LexicalState.RANGE_COMMENT) {
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
        appendToBuffer("\n");
      }
      if (nextChar != '\n') {
        appendToBuffer(curIndent);
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

  @CanIgnoreReturnValue
  FormattingContext clearIndent() {
    curIndent = "";
    return this;
  }

  @Override
  public String toString() {
    return isEmpty() ? "" : buf.toString();
  }

  /**
   * Returns the contents of the code buffer without converting to a string and poisons this context
   * instance.
   */
  public StringBuilder getBuffer() {
    StringBuilder tmp = buf;
    buf = null;
    return tmp;
  }

  boolean isEmpty() {
    return buf.length() == 0;
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
