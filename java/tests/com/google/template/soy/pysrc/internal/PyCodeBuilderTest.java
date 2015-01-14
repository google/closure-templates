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

import com.google.common.collect.Lists;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.pysrc.restricted.PyExpr;

import junit.framework.TestCase;


/**
 * Unit tests for PyCodeBuilder.
 *
 */
public final class PyCodeBuilderTest extends TestCase {

  public void testOutputVar() {

    // Output initialization.
    PyCodeBuilder pcb = new PyCodeBuilder();
    pcb.pushOutputVar("output");
    pcb.appendOutputVarName().appendLineEnd();
    assertEquals("output\n", pcb.getCode());
    pcb.initOutputVarIfNecessary();
    assertEquals("output\noutput = ''\n", pcb.getCode());
    pcb.pushOutputVar("param5");
    pcb.appendOutputVarName().appendLineEnd();
    pcb.setOutputVarInited();
    pcb.initOutputVarIfNecessary();  // nothing added
    assertEquals("output\noutput = ''\nparam5\n", pcb.getCode());

    // Output assignment should cast to a string.
    pcb = new PyCodeBuilder();
    pcb.pushOutputVar("output");
    pcb.addToOutputVar(Lists.newArrayList(new PyExpr("boo", Integer.MAX_VALUE)));
    assertEquals("output = str(boo)\n", pcb.getCode());

    // Multiple expressions should use list joining to improve performance.
    pcb.pushOutputVar("param5");
    pcb.setOutputVarInited();
    pcb.addToOutputVar(Lists.newArrayList(
        new PyExpr("a - b", Operator.MINUS.getPrecedence()),
        new PyExpr("c - d", Operator.MINUS.getPrecedence()),
        new PyExpr("e * f", Operator.TIMES.getPrecedence())));
    assertEquals("output = str(boo)\nparam5 += ''.join([str(a - b),str(c - d),str(e * f)])\n",
        pcb.getCode());
    pcb.popOutputVar();
    pcb.appendOutputVarName().appendLineEnd();
    assertEquals(
        "output = str(boo)\nparam5 += ''.join([str(a - b),str(c - d),str(e * f)])\noutput\n",
        pcb.getCode());
  }
}
