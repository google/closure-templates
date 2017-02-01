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
import com.google.common.base.Strings;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Helper class to keep track of state during a single call to {@link CodeChunk#getCode()},
 * including the initial statements that have already been formatted
 * and the current indentation level.
 */
final class FormattingContext implements AutoCloseable {
  private final StringBuilder buf;
  private final int initialSize;

  private Scope curScope = new Scope(null /* parent */);
  private String curIndent;
  private boolean nextAppendShouldStartNewLine = false;

  FormattingContext() {
    this(0 /* startingIndent */);
  }

  /** @param startingIndent The number of columns to consider the "baseline" indentation level. */
  FormattingContext(int startingIndent) {
    curIndent = Strings.repeat(" ", startingIndent);
    buf = new StringBuilder(curIndent);
    initialSize = curIndent.length();
  }

  FormattingContext append(String stuff) {
    maybeIndent();
    buf.append(stuff);
    return this;
  }

  FormattingContext append(char c) {
    maybeIndent();
    buf.append(c);
    return this;
  }

  boolean shouldFormat(CodeChunk chunk) {
    boolean shouldFormat = !curScope.alreadyFormatted(chunk);
    if (shouldFormat) {
      curScope.formatted.add(chunk);
    }
    return shouldFormat;
  }

  FormattingContext enterBlock() {
    maybeIndent();
    buf.append('{');
    curIndent = curIndent + "  ";
    endLine();
    curScope = new Scope(curScope);
    return this;
  }

  FormattingContext endLine() {
    // To prevent spurious trailing whitespace, don't actually write the newline
    // until the next call to append().
    nextAppendShouldStartNewLine = true;
    return this;
  }

  /**
   * If this is the first call to {@link #append} since the last {@link #endLine},
   * writes the newline and leading indentation.
   */
  private void maybeIndent() {
    if (nextAppendShouldStartNewLine) {
      buf.append('\n').append(curIndent);
      nextAppendShouldStartNewLine = false;
    }
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
    curScope = Preconditions.checkNotNull(curScope.parent);
    Preconditions.checkState(!curIndent.isEmpty());
    curIndent = curIndent.substring(2);
    endLine();
    append('}');
  }

  /**
   * Returns a FormattingContext representing the concatenation of this FormattingContext
   * with {@code other}. For use only by {@link CodeChunk#getCode(int, OutputContext, boolean)}.
   */
  FormattingContext concat(FormattingContext other) {
    if (isEmpty()) {
      return other;
    } else if (other.isEmpty()) {
      return this;
    } else {
      return append(other.toString());
    }
  }

  /**
   * {@link FormattingContext} needs to keep track of the conditional nesting structure
   * in order to avoid, for example, formatting the initial statements of a code chunk
   * in one branch and referencing the chunk in another. The scopes form a simple tree,
   * built and torn down by {@link #enterBlock()} and {@link #close()} respectively.
   * {@link FormattingContext#curScope} points to the current tip of the tree.
   */
  private static final class Scope {
    final Set<CodeChunk> formatted =
        Collections.<CodeChunk>newSetFromMap(new IdentityHashMap<CodeChunk, Boolean>());
    @Nullable
    final Scope parent;

    Scope(@Nullable Scope parent) {
      this.parent = parent;
    }

    boolean alreadyFormatted(CodeChunk chunk) {
      return formatted.contains(chunk) || (parent != null && parent.alreadyFormatted(chunk));
    }
  }
}
