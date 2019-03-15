/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for AbstractExprNodeVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class AbstractExprNodeVisitorTest {

  private static final SourceLocation LOC = SourceLocation.UNKNOWN;

  @Test
  public void testConcreteImplementation() {

    IntegerNode expr = new IntegerNode(17, LOC);

    IncompleteEvalVisitor iev = new IncompleteEvalVisitor(null);
    assertThat(iev.exec(expr)).isEqualTo(17.0);
  }

  @Test
  public void testInterfaceImplementation() {

    MinusOpNode expr = new MinusOpNode(LOC);
    expr.addChild(new IntegerNode(17, LOC));

    VarRefNode dataRef = new VarRefNode("boo", LOC, null);
    expr.addChild(dataRef);

    IncompleteEvalVisitor iev = new IncompleteEvalVisitor(ImmutableMap.of("boo", 13.0));
    assertThat(iev.exec(expr)).isEqualTo(4.0);

    expr.replaceChild(0, new IntegerNode(34, LOC));

    assertThat(iev.exec(expr)).isEqualTo(21.0);
  }

  @Test
  public void testNotImplemented() {

    MinusOpNode expr = new MinusOpNode(LOC);
    expr.addChild(new FloatNode(17.0, LOC));

    VarRefNode dataRef = new VarRefNode("boo", LOC, null);
    expr.addChild(dataRef);

    IncompleteEvalVisitor iev = new IncompleteEvalVisitor(ImmutableMap.of("boo", 13.0));

    try {
      iev.exec(expr);
      fail();
    } catch (UnsupportedOperationException uoe) {
      // Test passes.
    }
  }

  private static final class IncompleteEvalVisitor extends AbstractExprNodeVisitor<Double> {

    private final Map<String, Double> env;

    private Deque<Double> resultStack;

    IncompleteEvalVisitor(Map<String, Double> env) {
      this.env = env;
    }

    @Override
    public Double exec(ExprNode node) {
      resultStack = new ArrayDeque<>();
      visit(node);
      return resultStack.peek();
    }

    @Override
    protected void visitIntegerNode(IntegerNode node) {
      resultStack.push((double) node.getValue());
    }

    @Override
    protected void visitVarRefNode(VarRefNode node) {
      resultStack.push(env.get(node.getName()));
    }

    @Override
    protected void visitOperatorNode(OperatorNode node) {
      // Note: This isn't the "right" way to implement this, but we want to override the interface
      // implementation for the purpose of testing.
      if (node.getOperator() != Operator.MINUS) {
        throw new UnsupportedOperationException();
      }
      visitChildren(node); // results will be on stack in reverse operand order
      double operand1 = resultStack.pop();
      double operand0 = resultStack.pop();
      resultStack.push(operand0 - operand1);
    }
  }
}
