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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for AbstractOperatorNode.
 *
 */
@RunWith(JUnit4.class)
public final class AbstractOperatorNodeTest {

  private static final SourceLocation X = SourceLocation.UNKNOWN;
  // Note: We're going to reuse this leaf node in the test trees. This isn't really correct, but
  // should work for this test.
  private static final VarRefNode x = new VarRefNode("x", X, null);

  @Test
  public void testToSourceString1() {

    // Test expression: $x - -$x - (-($x - $x) - $x)
    //
    // The expression tree looks like this:
    // [MinusOpNode] n0
    //    [MinusOpNode] n1
    //       [VarRefNode] $x
    //       [NegativeOpNode] n3
    //          [VarRefNode] $x
    //    [MinusOpNode] n2
    //       [NegativeOpNode] n4
    //          [MinusOpNode] n5
    //             [VarRefNode] $x
    //             [VarRefNode] $x
    //       [VarRefNode] $x

    // Root n0.
    MinusOpNode n0 = new MinusOpNode(X);
    // Children of n0.
    MinusOpNode n1 = new MinusOpNode(X);
    MinusOpNode n2 = new MinusOpNode(X);
    n0.addChild(n1);
    n0.addChild(n2);
    // Children of n1.
    NegativeOpNode n3 = new NegativeOpNode(X);
    n1.addChild(x);
    n1.addChild(n3);
    // Child of n3.
    n3.addChild(x.copy(new CopyState()));
    // Children of n2.
    NegativeOpNode n4 = new NegativeOpNode(X);
    n2.addChild(n4);
    n2.addChild(x.copy(new CopyState()));
    // Child of n4.
    MinusOpNode n5 = new MinusOpNode(X);
    n4.addChild(n5);
    // Children of n5.
    n5.addChild(x.copy(new CopyState()));
    n5.addChild(x.copy(new CopyState()));

    assertThat(n0.toSourceString()).isEqualTo("$x - -$x - (-($x - $x) - $x)");
  }

  @Test
  public void testToSourceString2() {

    // Test expression: not $x ? $x != $x : $x * $x
    //
    // The expression tree looks like this:
    // [ConditionalOpNode] n0
    //    [NotOpNode] n1
    //       [VarRefNode] $x
    //    [NotEqualOpNode] n2
    //       [VarRefNode] $x
    //       [VarRefNode] $x
    //    [TimesOpNode] n3
    //       [VarRefNode] $x
    //       [VarRefNode] $x

    // Root n0.
    ConditionalOpNode n0 = new ConditionalOpNode(X);
    // Children of n0.
    NotOpNode n1 = new NotOpNode(X);
    NotEqualOpNode n2 = new NotEqualOpNode(X);
    TimesOpNode n3 = new TimesOpNode(X);
    n0.addChild(n1);
    n0.addChild(n2);
    n0.addChild(n3);
    // Child of n1.
    n1.addChild(x);
    // Children of n2.
    n2.addChild(x.copy(new CopyState()));
    n2.addChild(x.copy(new CopyState()));
    // Children of n3.
    n3.addChild(x.copy(new CopyState()));
    n3.addChild(x.copy(new CopyState()));

    assertThat(n0.toSourceString()).isEqualTo("not $x ? $x != $x : $x * $x");
  }
}
