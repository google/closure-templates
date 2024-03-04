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

package com.google.template.soy.jssrc.dsl;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.javagencode.KytheHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * Our main output buffer
 *
 * <p>Maintains several mutable datastructures to control output for a single file:
 *
 * <ul>
 *   <li>A StringBuilder for generated code
 *   <li>A set of GoogRequires
 *   <li>a stack of OutputVariables so that we can track the 'current' output variable for a block
 * </ul>
 *
 * TODO(b/33382980): We should only lazily call getCode on the CodeChunk objects in order to delay
 * when 'name selection' occurs.
 */
public final class JsCodeBuilder {

  private static final CodeChunk REQUIRES_PLACEHOLDER = Expressions.id("_requires_");

  /** A buffer to accumulate the generated code. */
  private final List<CodeChunk> chunks;

  @Nullable private final KytheHelper kytheHelper;

  // the set of symbols to require, indexed by symbol name to detect conflicting imports
  private final Map<String, GoogRequire> googRequires = new TreeMap<>();

  public JsCodeBuilder(@Nullable KytheHelper kytheHelper) {
    this.chunks = new ArrayList<>();
    this.kytheHelper = kytheHelper;
  }

  @CanIgnoreReturnValue
  public JsCodeBuilder appendRequiresPlaceholder() {
    return append(REQUIRES_PLACEHOLDER);
  }

  /**
   * Serializes the given {@link CodeChunk} into the code builder, respecting the code builder's
   * current indentation level.
   */
  @CanIgnoreReturnValue
  public JsCodeBuilder append(CodeChunk codeChunk) {
    codeChunk.collectRequires(this::addGoogRequire);
    chunks.add(codeChunk);
    return this;
  }

  @CanIgnoreReturnValue
  public JsCodeBuilder append(CodeChunk codeChunk, CodeChunk... more) {
    append(codeChunk);
    for (CodeChunk c : more) {
      append(c);
    }
    return this;
  }

  /**
   * Adds a {@code goog.require}
   *
   * @param require The namespace being required
   */
  public void addGoogRequire(GoogRequire require) {
    GoogRequire oldRequire = googRequires.put(require.symbol(), require);
    if (oldRequire != null) {
      googRequires.put(require.symbol(), require.merge(oldRequire));
    }
  }

  public void addGoogRequires(Iterable<GoogRequire> googRequires) {
    googRequires.forEach(this::addGoogRequire);
  }

  /**
   * Should only be used by {@link
   * com.google.template.soy.jssrc.internal.GenJsCodeVisitor#visitSoyFileNode}.
   */
  private void appendGoogRequiresTo(FormattingContext sb) {
    for (GoogRequire require : googRequires.values()) {
      // TODO(lukes): we need some namespace management here... though really we need namespace
      // management with all declarations... The problem is that a require could introduce a name
      // alias that conflicts with a symbol defined elsewhere in the file.
      sb.appendToBuffer(require.chunk().getCode(FormatOptions.JSSRC)).appendToBuffer('\n');
    }
  }

  /**
   * @return The generated code.
   */
  public StringBuilder getCode() {
    FormattingContext context = new FormattingContext(FormatOptions.JSSRC);
    context.setKytheHelper(kytheHelper);
    for (CodeChunk chunk : chunks) {
      if (chunk == REQUIRES_PLACEHOLDER) {
        appendGoogRequiresTo(context);
      } else if (chunk == Whitespace.BLANK_LINE) {
        context.appendToBuffer('\n');
      } else {
        context.appendAll(chunk);
        if (context.isEndOfLine()) {
          context.appendToBuffer('\n');
        }
      }
    }
    return context.getBuffer();
  }
}
