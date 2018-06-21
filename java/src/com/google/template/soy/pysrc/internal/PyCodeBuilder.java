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
package com.google.template.soy.pysrc.internal;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A Python implementation of the CodeBuilder class.
 *
 * <p>Usage example that demonstrates most of the methods:
 *
 * <pre>
 *   PyCodeBuilder pcb = new PyCodeBuilder();
 *   pcb.appendLine("def title(data):");
 *   pcb.increaseIndent();
 *   pcb.pushOutputVar("output");
 *   pcb.initOutputVarIfNecessary();
 *   pcb.pushOutputVar("temp");
 *   pcb.addToOutputVar(Lists.newArrayList(
 *       new PyExpr("'Snow White and the '", Integer.MAX_VALUE),
 *       new PyExpr("data['numDwarfs']", Integer.MAX_VALUE));
 *   pcb.popOutputVar();
 *   pcb.addToOutputVar(Lists.newArrayList(
 *       new PyExpr("temp", Integer.MAX_VALUE),
 *       new PyExpr("' Dwarfs'", Integer.MAX_VALUE));
 *   pcb.appendLineStart("return ").appendOutputVarName().appendLineEnd();
 *   pcb.popOutputVar();
 *   pcb.decreaseIndent();
 *   String THE_END = "the end";
 *   pcb.appendLine("# ", THE_END);
 * </pre>
 *
 * The above example builds the following Python code:
 *
 * <pre>
 * def title(data):
 *   output = ''
 *   temp = ''.join(['Snow White and the ', str(data['numDwarfs'])])
 *   output += ''.join([temp, ' Dwarfs'])
 *   return output
 * # the end
 * </pre>
 *
 */
final class PyCodeBuilder {

  @AutoValue
  abstract static class OutputVar {
    static OutputVar create(String name, boolean isInited) {
      return new AutoValue_PyCodeBuilder_OutputVar(name, isInited);
    }

    abstract String name();

    abstract boolean isInited();
  }

  /** The size of a single indent level. */
  private static final int INDENT_SIZE = 2;
  /** A buffer to accumulate the generated code. */
  private final StringBuilder code;
  /** The current indent (some even number of spaces). */
  private String indent;
  /** The current stack of output variables. */
  private final Deque<OutputVar> outputVars;
  /** The current output variable name. */
  private String currOutputVarName;
  /** Whether the current output variable is initialized. */
  private boolean currOutputVarIsInited;

  PyCodeBuilder() {
    code = new StringBuilder();
    indent = "";
    outputVars = new ArrayDeque<>();
    currOutputVarName = null;
    currOutputVarIsInited = false;
  }

  void initOutputVarIfNecessary() {
    if (getOutputVarIsInited()) {
      // Nothing to do since it's already initialized.
      return;
    }

    // output = ''
    appendLine(getOutputVarName(), " = []");

    setOutputVarInited();
  }

  void addToOutputVar(List<? extends PyExpr> pyExprs) {
    addToOutputVar(PyExprUtils.concatPyExprs(pyExprs));
  }

  /** Add a single PyExpr object to the output variable. */
  void addToOutputVar(PyExpr pyExpr) {
    boolean isList = pyExpr instanceof PyListExpr;
    if (isList && !getOutputVarIsInited()) {
      appendLine(getOutputVarName(), " = ", pyExpr.getText());
    } else {
      initOutputVarIfNecessary();
      String function = isList ? ".extend(" : ".append(";
      appendLine(getOutputVarName(), function, pyExpr.getText(), ")");
    }
    setOutputVarInited();
  }

  /**
   * Provide the output object as a string. Since we store all data in the output variables as a
   * list for concatenation performance, this step does the joining to convert the output into a
   * String.
   *
   * @return A PyExpr object of the output joined into a String.
   */
  PyStringExpr getOutputAsString() {
    Preconditions.checkState(getOutputVarName() != null);

    initOutputVarIfNecessary();
    return new PyListExpr(getOutputVarName(), Integer.MAX_VALUE).toPyString();
  }

  /** Increases the current indent. */
  void increaseIndent() {
    changeIndentHelper(1);
  }

  /** Increases the current indent twice. */
  void increaseIndentTwice() {
    changeIndentHelper(2);
  }

  /** Decreases the current indent. */
  public void decreaseIndent() {
    changeIndentHelper(-1);
  }

  /** Decreases the current indent twice. */
  void decreaseIndentTwice() {
    changeIndentHelper(-2);
  }

  /**
   * Private helper for increaseIndent(), increaseIndentTwice(), decreaseIndent(), and
   * decreaseIndentTwice().
   *
   * @param chg The number of indent levels to change.
   */
  private void changeIndentHelper(int chg) {
    int newIndentDepth = indent.length() + chg * INDENT_SIZE;
    Preconditions.checkState(newIndentDepth >= 0);
    indent = Strings.repeat(" ", newIndentDepth);
  }

  /**
   * Pushes on a new current output variable.
   *
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    outputVars.push(OutputVar.create(outputVarName, false));
    currOutputVarName = outputVarName;
    currOutputVarIsInited = false;
  }

  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  void popOutputVar() {
    outputVars.pop();
    OutputVar outputVar = outputVars.peek(); // null if outputVars is now empty
    if (outputVar != null) {
      currOutputVarName = outputVar.name();
      currOutputVarIsInited = outputVar.isInited();
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
  void setOutputVarInited() {
    outputVars.pop();
    outputVars.push(OutputVar.create(currOutputVarName, true));
    currOutputVarIsInited = true;
  }

  /**
   * Gets the current output variable name.
   *
   * @return The current output variable name.
   */
  String getOutputVarName() {
    return currOutputVarName;
  }

  /**
   * Appends one or more strings to the generated code.
   *
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder append(String... codeFragments) {
    for (String codeFragment : codeFragments) {
      code.append(codeFragment);
    }
    return this;
  }

  /**
   * Appends the current indent, then the given strings, then a newline.
   *
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder appendLine(String... codeFragments) {
    code.append(indent);
    append(codeFragments);
    code.append("\n");
    return this;
  }

  /**
   * Appends the current indent, then the given strings.
   *
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder appendLineStart(String... codeFragments) {
    code.append(indent);
    append(codeFragments);
    return this;
  }

  /**
   * Appends the given strings, then a newline.
   *
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public PyCodeBuilder appendLineEnd(String... codeFragments) {
    append(codeFragments);
    code.append("\n");
    return this;
  }

  /**
   * Appends the name of the current output variable.
   *
   * @return This CodeBuilder (for stringing together operations).
   */
  PyCodeBuilder appendOutputVarName() {
    code.append(currOutputVarName);
    return this;
  }

  /** @return The generated code. */
  public String getCode() {
    return code.toString();
  }

  /**
   * Gets the current output variable initialization status.
   *
   * @return The current output variable initialization status.
   */
  boolean getOutputVarIsInited() {
    return currOutputVarIsInited;
  }
}
