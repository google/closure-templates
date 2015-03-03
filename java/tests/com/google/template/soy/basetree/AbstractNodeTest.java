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

package com.google.template.soy.basetree;

import junit.framework.*;


/**
 * Unit tests for AbstractNode.
 *
 */
public class AbstractNodeTest extends TestCase {


  public void testAncestorMethods() {

    DummyNodeAlpha dummyA = new DummyNodeAlpha();
    SillyNode silly = new SillyNode();
    DopeyNode dopey = new DopeyNode();
    DummyNodeBeta dummyB = new DummyNodeBeta();
    SillyNode leaf = new SillyNode();

    dummyA.addChild(silly);
    silly.addChild(dummyB);
    silly.addChild(dopey);
    dummyB.addChild(leaf);

    assertTrue(leaf.hasAncestor(SillyNode.class));
    assertTrue(leaf.hasAncestor(DummyNode.class));
    assertTrue(leaf.hasAncestor(DummyNodeAlpha.class));
    assertTrue(leaf.hasAncestor(DummyNodeBeta.class));
    assertFalse(leaf.hasAncestor(DopeyNode.class));

    assertEquals(leaf, leaf.getNearestAncestor(SillyNode.class));
    assertEquals(silly, leaf.getParent().getNearestAncestor(SillyNode.class));
    assertEquals(dummyB, leaf.getNearestAncestor(DummyNode.class));
    assertEquals(dummyA, leaf.getNearestAncestor(DummyNodeAlpha.class));
    assertEquals(dummyB, leaf.getNearestAncestor(DummyNodeBeta.class));
    assertEquals(null, leaf.getNearestAncestor(DopeyNode.class));
  }


  private static interface DummyNode extends Node {}

  private static class DummyNodeAlpha extends AbstractParentNode<Node> implements DummyNode {
    @Override public String toSourceString() { return null; }
    @Override public Node clone() { throw new UnsupportedOperationException(); }
  }

  private static class DummyNodeBeta extends AbstractParentNode<Node> implements DummyNode {
    @Override public String toSourceString() { return null; }
    @Override public Node clone() { throw new UnsupportedOperationException(); }
  }

  private static class SillyNode extends AbstractParentNode<Node> {
    @Override public String toSourceString() { return null; }
    @Override public Node clone() { throw new UnsupportedOperationException(); }
  }

  private static class DopeyNode extends AbstractParentNode<Node> {
    @Override public String toSourceString() { return null; }
    @Override public Node clone() { throw new UnsupportedOperationException(); }
  }

}
