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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;

/**
 * Unit tests for {@link JsCodeBuilder}.
 *
 */
public final class JsCodeBuilderTest extends TestCase {

  public void testOutputVarWithConcat() {
    JsCodeBuilder jcb = new JsCodeBuilder();
    jcb.pushOutputVar("output");
    jcb.appendOutputVarName().appendLineEnd();
    assertThat(jcb.getCode()).isEqualTo("output\n");
    jcb.initOutputVarIfNecessary();
    assertThat(jcb.getCode()).isEqualTo("output\nvar output = '';\n");
    jcb.pushOutputVar("param5");
    jcb.appendOutputVarName().appendLineEnd();
    jcb.setOutputVarInited();
    jcb.initOutputVarIfNecessary();  // nothing added
    assertThat(jcb.getCode()).isEqualTo("output\nvar output = '';\nparam5\n");

    jcb = new JsCodeBuilder();
    jcb.pushOutputVar("output");
    jcb.addToOutputVar(Lists.newArrayList(new JsExpr("boo", Integer.MAX_VALUE)));
    assertThat(jcb.getCode()).isEqualTo("var output = '' + boo;\n");
    jcb.pushOutputVar("param5");
    jcb.setOutputVarInited();
    jcb.addToOutputVar(Lists.newArrayList(
        new JsExpr("a - b", Operator.MINUS.getPrecedence()),
        new JsExpr("c - d", Operator.MINUS.getPrecedence()),
        new JsExpr("e * f", Operator.TIMES.getPrecedence())));
    assertThat(jcb.getCode())
        .isEqualTo("var output = '' + boo;\nparam5 += a - b + (c - d) + e * f;\n");
    jcb.popOutputVar();
    jcb.appendOutputVarName().appendLineEnd();
    assertThat(jcb.getCode())
        .isEqualTo("var output = '' + boo;\nparam5 += a - b + (c - d) + e * f;\noutput\n");
  }
}
