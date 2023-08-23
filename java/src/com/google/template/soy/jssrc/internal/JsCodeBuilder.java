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

package com.google.template.soy.jssrc.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.jssrc.dsl.GoogRequire;
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

  /** A buffer to accumulate the generated code. */
  private final StringBuilder code;
  /** The current stack of output variables. */
  private final OutputVarHandler outputVars;

  // the set of symbols to require, indexed by symbol name to detect conflicting imports
  private final Map<String, GoogRequire> googRequires = new TreeMap<>();

  public JsCodeBuilder(OutputVarHandler outputVars) {
    code = new StringBuilder();
    this.outputVars = outputVars;
  }

  /**
   * Pushes on a new current output variable.
   *
   * @param outputVarName The new output variable name.
   */
  @CanIgnoreReturnValue
  public JsCodeBuilder pushOutputVar(String outputVarName) {
    outputVars.pushOutputVar(outputVarName);
    return this;
  }

  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  @CanIgnoreReturnValue
  public JsCodeBuilder popOutputVar() {
    outputVars.popOutputVar();
    return this;
  }

  /**
   * Tells this CodeBuilder that the current output variable has already been initialized. This
   * causes {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization
   * code even on the first use of the variable.
   */
  @CanIgnoreReturnValue
  public JsCodeBuilder setOutputVarInited() {
    outputVars.setOutputVarInited();
    return this;
  }

  /**
   * Serializes the given {@link CodeChunk} into the code builder, respecting the code builder's
   * current indentation level.
   */
  public JsCodeBuilder append(CodeChunk codeChunk) {
    codeChunk.collectRequires(this::addGoogRequire);
    return appendLine(codeChunk.getCode(FormatOptions.JSSRC));
  }

  public JsCodeBuilder appendNullable(@Nullable CodeChunk codeChunk) {
    if (codeChunk != null) {
      return append(codeChunk);
    }
    return this;
  }

  /**
   * Appends the current indent, then the given strings, then a newline.
   *
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  @CanIgnoreReturnValue
  public JsCodeBuilder appendLine(String... codeFragments) {
    for (String codeFragment : codeFragments) {
      code.append(codeFragment);
    }
    code.append("\n");
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

  /** Should only be used by {@link GenJsCodeVisitor#visitSoyFileNode}. */
  public void appendGoogRequiresTo(StringBuilder sb) {
    for (GoogRequire require : googRequires.values()) {
      // TODO(lukes): we need some namespace management here... though really we need namespace
      // management with all declarations... The problem is that a require could introduce a name
      // alias that conflicts with a symbol defined elsewhere in the file.
      require.writeTo(sb);
    }
  }

  /**
   * @return The generated code.
   */
  public String getCode() {
    return code.toString();
  }

  /** Appends the code accumulated in this builder to the given {@link StringBuilder}. */
  void appendCodeTo(StringBuilder sb) {
    sb.append(code);
  }
}
