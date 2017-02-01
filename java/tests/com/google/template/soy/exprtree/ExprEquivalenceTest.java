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

package com.google.template.soy.exprtree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Equivalence.Wrapper;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExprEquivalence}. */
@RunWith(JUnit4.class)
public final class ExprEquivalenceTest {

  @Test
  public void testReflexive() {
    assertReflexive("1");
    assertReflexive("'a'");
    assertReflexive("$map['a']");
    assertReflexive("$list[1][2]");
  }

  @Test
  public void testSubExpressions() {
    PlusOpNode node = (PlusOpNode) parse("$foo.a.b + $foo.a.b");
    assertEquivalent(node.getChild(0), node.getChild(1));
    assertNotEquivalent(node, node.getChild(0));
    assertNotEquivalent(node, node.getChild(1));
  }

  private void assertReflexive(String expr) {
    // We parse twice so we don't get trivial == equality
    assertEquivalent(parse(expr), parse(expr));
  }

  private static ExprNode parse(String input) {
    return new ExpressionParser(input, SourceLocation.UNKNOWN, SoyParsingContext.exploding())
        .parseExpression();
  }

  private void assertEquivalent(ExprNode left, ExprNode right) {
    Wrapper<ExprNode> wrappedLeft = ExprEquivalence.get().wrap(left);
    Wrapper<ExprNode> wrappedRight = ExprEquivalence.get().wrap(right);
    assertThat(wrappedLeft).isEqualTo(wrappedRight);
    // Test symmetry
    assertThat(wrappedRight).isEqualTo(wrappedLeft);

    assertThat(wrappedLeft.hashCode()).isEqualTo(wrappedRight.hashCode());

    // If two expressions are equal, then all subexpressions must also be equal
    if (left instanceof ParentExprNode) {
      List<ExprNode> leftChildren = ((ParentExprNode) left).getChildren();
      List<ExprNode> rightChildren = ((ParentExprNode) right).getChildren();
      for (int i = 0; i < leftChildren.size(); i++) {
        assertEquivalent(leftChildren.get(i), rightChildren.get(i));
      }
    }
  }

  private static void assertNotEquivalent(ExprNode left, ExprNode right) {
    // test symmetry
    assertThat(ExprEquivalence.get().wrap(left)).isNotEqualTo(ExprEquivalence.get().wrap(right));
    assertThat(ExprEquivalence.get().wrap(right)).isNotEqualTo(ExprEquivalence.get().wrap(left));
  }
}
