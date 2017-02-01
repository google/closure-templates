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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.pysrc.restricted.PyStringExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PyCodeBuilder.
 *
 */
@RunWith(JUnit4.class)
public final class PyCodeBuilderTest {

  @Test
  public void testSimpleOutputVar() {
    // Output initialization.
    PyCodeBuilder pcb = new PyCodeBuilder();
    pcb.pushOutputVar("output");
    pcb.appendOutputVarName().appendLineEnd();
    assertThat(pcb.getCode()).isEqualTo("output\n");
    pcb.initOutputVarIfNecessary();
    assertThat(pcb.getCode()).isEqualTo("output\noutput = []\n");
    pcb.pushOutputVar("param5");
    pcb.appendOutputVarName().appendLineEnd();
    pcb.setOutputVarInited();
    pcb.initOutputVarIfNecessary(); // nothing added
    assertThat(pcb.getCode()).isEqualTo("output\noutput = []\nparam5\n");
  }

  @Test
  public void testComplexOutput() {
    // Output assignment should initialize and use append.
    PyCodeBuilder pcb = new PyCodeBuilder();
    pcb.pushOutputVar("output");
    pcb.addToOutputVar(Lists.newArrayList(new PyStringExpr("boo")));
    assertThat(pcb.getCode()).isEqualTo("output = []\noutput.append(boo)\n");

    // Multiple expressions should use extend to track output as one large list.
    pcb.pushOutputVar("param5");
    pcb.setOutputVarInited();
    pcb.addToOutputVar(
        Lists.newArrayList(
            new PyExpr("a - b", PyExprUtils.pyPrecedenceForOperator(Operator.MINUS)),
            new PyExpr("c - d", PyExprUtils.pyPrecedenceForOperator(Operator.MINUS)),
            new PyExpr("e * f", PyExprUtils.pyPrecedenceForOperator(Operator.TIMES))));
    assertThat(pcb.getCode())
        .isEqualTo(
            "output = []\noutput.append(boo)\nparam5.extend([str(a - b),str(c - d),str(e * f)])\n");
    pcb.popOutputVar();
    pcb.appendOutputVarName().appendLineEnd();
    assertThat(pcb.getCode())
        .isEqualTo(
            "output = []\noutput.append(boo)\nparam5.extend([str(a - b),str(c - d),str(e * f)])\n"
                + "output\n");
  }

  @Test
  public void testOutputAsString() {
    // Output should use String joining to convert to a String.
    PyCodeBuilder pcb = new PyCodeBuilder();
    pcb.pushOutputVar("output");
    pcb.addToOutputVar(Lists.newArrayList(new PyStringExpr("boo")));
    assertThat(pcb.getCode()).isEqualTo("output = []\noutput.append(boo)\n");
    assertThat(pcb.getOutputAsString().getText()).isEqualTo("''.join(output)");
  }
}
