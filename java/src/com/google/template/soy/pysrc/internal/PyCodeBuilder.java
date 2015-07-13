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

import com.google.common.base.Preconditions;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import com.google.template.soy.shared.internal.CodeBuilder;

import java.util.List;

/**
 * A Python implementation of the CodeBuilder class.
 *
 * <p>Usage example that demonstrates most of the methods:
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
final class PyCodeBuilder extends CodeBuilder<PyExpr> {

  @Override public void initOutputVarIfNecessary() {
    if (getOutputVarIsInited()) {
      // Nothing to do since it's already initialized.
      return;
    }

    // output = ''
    appendLine(getOutputVarName(), " = []");

    setOutputVarInited();
  }

  @Override public void addToOutputVar(List<? extends PyExpr> pyExprs) {
    addToOutputVar(PyExprUtils.concatPyExprs(pyExprs));
  }

  /**
   * Add a single PyExpr object to the output variable.
   * @param pyExpr
   */
  public void addToOutputVar(PyExpr pyExpr) {
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
  public PyStringExpr getOutputAsString() {
    Preconditions.checkState(getOutputVarName() != null);

    initOutputVarIfNecessary();
    return new PyListExpr(getOutputVarName(), Integer.MAX_VALUE).toPyString();
  }
}
