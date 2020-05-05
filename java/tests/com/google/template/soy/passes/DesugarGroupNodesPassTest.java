/*
 * Copyright 2020 Google Inc.
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
package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GroupNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DesugarGroupNodesPassTest {

  @Test
  public void testDesugarGroupNodesFromTemplate_nestedVarRef() {
    SoyFileNode file = parseAsTemplate("{@param foo: bool}\n\n{((($foo)))}");

    TemplateNode n = (TemplateNode) file.getChild(0);
    PrintNode p = (PrintNode) n.getChild(0);
    ExprRootNode exprRoot = p.getExpr();
    assertThat(exprRoot.getChild(0)).isInstanceOf(GroupNode.class);

    // Make sure that running the pass removes all of the group node layers, but the inner expr
    // ($foo) is still there, now as a direct child of the print node.
    runPass(file);
    assertThat(SoyTreeUtils.getAllNodesOfType(n, GroupNode.class)).isEmpty();
    assertThat(exprRoot.getChild(0)).isInstanceOf(VarRefNode.class);
  }

  @Test
  public void testDesugarGroupNodesFromTemplate_multipleSubExprs() {
    SoyFileNode file = parseAsTemplate("{(((1 + 5) * 6) / 2)}");

    TemplateNode n = (TemplateNode) file.getChild(0);
    PrintNode p = (PrintNode) n.getChild(0);
    ExprRootNode exprRoot = p.getExpr();
    assertThat(exprRoot.getChild(0)).isInstanceOf(GroupNode.class);

    // Make sure that running the pass removes all of the group node layers, but the inner exprs are
    // stil there (and in the correct structure).
    runPass(file);
    assertThat(SoyTreeUtils.getAllNodesOfType(n, GroupNode.class)).isEmpty();
    DivideByOpNode divideOpNode = (DivideByOpNode) exprRoot.getChild(0);
    TimesOpNode timesOpNode = (TimesOpNode) divideOpNode.getChild(0);
    PlusOpNode plusOpNode = (PlusOpNode) timesOpNode.getChild(0);
    assertThat(((IntegerNode) plusOpNode.getChild(0)).getValue()).isEqualTo(1L);
    assertThat(((IntegerNode) plusOpNode.getChild(1)).getValue()).isEqualTo(5L);
    assertThat(((IntegerNode) timesOpNode.getChild(1)).getValue()).isEqualTo(6L);
    assertThat(((IntegerNode) divideOpNode.getChild(1)).getValue()).isEqualTo(2L);
  }

  private static void runPass(SoyFileNode file) {
    new DesugarGroupNodesPass().run(file, null);
  }

  private static SoyFileNode parseAsTemplate(String input) {
    String soyFile =
        Joiner.on('\n')
            .join("{namespace ns}", "", "{template .t stricthtml=\"false\"}", input, "{/template}");
    return new SoyFileParser(
            new IncrementingIdGenerator(),
            new StringReader(soyFile),
            "test.soy",
            ErrorReporter.exploding())
        .parseSoyFile();
  }
}
