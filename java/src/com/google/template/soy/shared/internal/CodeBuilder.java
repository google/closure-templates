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

package com.google.template.soy.shared.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.internal.targetexpr.TargetExpr;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A class for building code.
 *
 * <p> All implementations share indenting behavior and output variable behavior, while
 * implementing their own idiosyncrasies for creating and modifying output variables.
 *
 */
public abstract class CodeBuilder<E extends TargetExpr> {

  /** The size of a single indent level. */
  private static final int INDENT_SIZE = 2;

  /** A buffer to accumulate the generated code. */
  private final StringBuilder code;

  /** The current indent (some even number of spaces). */
  private String indent;

  /** The current stack of output variables. */
  private Deque<Pair<String, Boolean>> outputVars;

  /** The current output variable name. */
  private String currOutputVarName;

  /** Whether the current output variable is initialized. */
  private boolean currOutputVarIsInited;



  /**
   * Constructs a new instance. At the start, the code is empty and the indent is 0 spaces.
   */
  public CodeBuilder() {
    code = new StringBuilder();
    indent = "";
    outputVars = new ArrayDeque<>();
    currOutputVarName = null;
    currOutputVarIsInited = false;
  }

  /**
   * Increases the current indent.
   */
  public void increaseIndent() {
    changeIndentHelper(1);
  }

  /**
   * Increases the current indent twice.
   */
  public void increaseIndentTwice() {
    changeIndentHelper(2);
  }

  /**
   * Decreases the current indent.
   */
  public void decreaseIndent() {
    changeIndentHelper(-1);
  }

  /**
   * Decreases the current indent twice.
   */
  public void decreaseIndentTwice() {
    changeIndentHelper(-2);
  }

  /**
   * Private helper for increaseIndent(), increaseIndentTwice(), decreaseIndent(), and
   * decreaseIndentTwice().
   * @param chg The number of indent levels to change.
   */
  private void changeIndentHelper(int chg) {
    int newIndentDepth = indent.length() + chg * INDENT_SIZE;
    Preconditions.checkState(newIndentDepth >= 0);
    indent = Strings.repeat(" ", newIndentDepth);
  }

  /**
   * Pushes on a new current output variable.
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    outputVars.push(Pair.of(outputVarName, false));
    currOutputVarName = outputVarName;
    currOutputVarIsInited = false;
  }

  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  public void popOutputVar() {
    outputVars.pop();
    Pair<String, Boolean> topPair = outputVars.peek();  // null if outputVars is now empty
    if (topPair != null) {
      currOutputVarName = topPair.first;
      currOutputVarIsInited = topPair.second;
    } else {
      currOutputVarName = null;
      currOutputVarIsInited = false;
    }
  }

  /**
   * Tells this CodeBuilder that the current output variable has already been initialized. This
   * causes {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization
   * code even on the first use of the variable.
   */
  public void setOutputVarInited() {
    outputVars.pop();
    outputVars.push(Pair.of(currOutputVarName, true));
    currOutputVarIsInited = true;
  }

  /**
   * Gets the current output variable name.
   * @return The current output variable name.
   */
  public String getOutputVarName() {
    return currOutputVarName;
  }

  /**
   * Appends one or more strings to the generated code.
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public CodeBuilder<E> append(String... codeFragments) {
    for (String codeFragment : codeFragments) {
      code.append(codeFragment);
    }
    return this;
  }

  /**
   * Appends the current indent, then the given strings, then a newline.
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public CodeBuilder<E> appendLine(String... codeFragments) {
    code.append(indent);
    append(codeFragments);
    code.append("\n");
    return this;
  }

  /**
   * Appends the current indent, then the given strings.
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public CodeBuilder<E> appendLineStart(String... codeFragments) {
    code.append(indent);
    append(codeFragments);
    return this;
  }

  /**
   * Appends the given strings, then a newline.
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public CodeBuilder<E> appendLineEnd(String... codeFragments) {
    append(codeFragments);
    code.append("\n");
    return this;
  }

  /**
   * Appends the name of the current output variable.
   * @return This CodeBuilder (for stringing together operations).
   */
  public CodeBuilder<E> appendOutputVarName() {
    code.append(currOutputVarName);
    return this;
  }

  /**
   * @return The generated code.
   */
  public String getCode() {
    return code.toString();
  }

  /**
   * Appends a full line/statement for initializing the current output variable.
   */
  public abstract void initOutputVarIfNecessary();

  /**
   * Appends a line/statement with the concatenation of the given target expressions saved to the
   * current output variable.
   * @param targetExprs One or more target expressions to compute output.
   */
  public abstract void addToOutputVar(List<? extends E> targetExprs);

  /**
   * Gets the current output variable initialization status.
   * @return The current output variable initialization status.
   */
  protected boolean getOutputVarIsInited() {
    return currOutputVarIsInited;
  }
}
