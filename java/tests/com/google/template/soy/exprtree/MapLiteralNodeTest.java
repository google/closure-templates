/*
 * Copyright 2011 Google Inc.
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

import com.google.common.collect.Lists;

import junit.framework.TestCase;


/**
 * Unit tests for MapLiteralNode.
 *
 */
public class MapLiteralNodeTest extends TestCase {


  public void testToSourceString() {

    DataRefNode booDataRef = new DataRefNode(false);
    booDataRef.addChild(new DataRefKeyNode("boo"));
    DataRefNode fooDataRef = new DataRefNode(false);
    fooDataRef.addChild(new DataRefKeyNode("foo"));

    MapLiteralNode mapLit = new MapLiteralNode(Lists.<ExprNode>newArrayList(
        new StringNode("aaa"), new StringNode("blah"), new StringNode("bbb"), new IntegerNode(123),
        booDataRef, fooDataRef));
    assertEquals("['aaa': 'blah', 'bbb': 123, $boo: $foo]", mapLit.toSourceString());

    MapLiteralNode emptyMapLit = new MapLiteralNode(Lists.<ExprNode>newArrayList());
    assertEquals("[:]", emptyMapLit.toSourceString());
  }

}
