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

import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;

import junit.framework.TestCase;


/**
 * Unit tests for AbstractOperatorNode.
 *
 */
public class AbstractOperatorNodeTest extends TestCase {


  // Note: We're going to reuse this leaf node in the test trees. This isn't really correct, but
  // should work for this test.
  private static final DataRefNode x;
  static {
    x = new DataRefNode(false);
    x.addChild(new DataRefKeyNode("x"));
  }


  public void testToSourceString1() {

    // Test expression: $x - -$x - (-($x - $x) - $x)
    //
    // The expression tree looks like this:
    // [MinusOpNode] n0
    //    [MinusOpNode] n1
    //       [DataRefNode] $x
    //       [NegativeOpNode] n3
    //          [DataRefNode] $x
    //    [MinusOpNode] n2
    //       [NegativeOpNode] n4
    //          [MinusOpNode] n5
    //             [DataRefNode] $x
    //             [DataRefNode] $x
    //       [DataRefNode] $x

    // Root n0.
    MinusOpNode n0 = new MinusOpNode();
    // Children of n0.
    MinusOpNode n1 = new MinusOpNode();
    MinusOpNode n2 = new MinusOpNode();
    n0.addChild(n1);
    n0.addChild(n2);
    // Children of n1.
    NegativeOpNode n3 = new NegativeOpNode();
    n1.addChild(x);
    n1.addChild(n3);
    // Child of n3.
    n3.addChild(x);
    // Children of n2.
    NegativeOpNode n4 = new NegativeOpNode();
    n2.addChild(n4);
    n2.addChild(x);
    // Child of n4.
    MinusOpNode n5 = new MinusOpNode();
    n4.addChild(n5);
    // Children of n5.
    n5.addChild(x);
    n5.addChild(x);

    assertEquals("$x - -$x - (-($x - $x) - $x)", n0.toSourceString());
  }


  public void testToSourceString2() {

    // Test expression: not $x ? $x != $x : $x * $x
    //
    // The expression tree looks like this:
    // [ConditionalOpNode] n0
    //    [NotOpNode] n1
    //       [DataRefNode] $x
    //    [NotEqualOpNode] n2
    //       [DataRefNode] $x
    //       [DataRefNode] $x
    //    [TimesOpNode] n3
    //       [DataRefNode] $x
    //       [DataRefNode] $x

    // Root n0.
    ConditionalOpNode n0 = new ConditionalOpNode();
    // Children of n0.
    NotOpNode n1 = new NotOpNode();
    NotEqualOpNode n2 = new NotEqualOpNode();
    TimesOpNode n3 = new TimesOpNode();
    n0.addChild(n1);
    n0.addChild(n2);
    n0.addChild(n3);
    // Child of n1.
    n1.addChild(x);
    // Children of n2.
    n2.addChild(x);
    n2.addChild(x);
    // Children of n3.
    n3.addChild(x);
    n3.addChild(x);

    assertEquals("not $x ? $x != $x : $x * $x", n0.toSourceString());
  }

}
