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
 * Unit tests for ListLiteralNode.
 *
 */
public class ListLiteralNodeTest extends TestCase {


  public void testToSourceString() {

    DataRefNode dataRef = new DataRefNode(false);
    dataRef.addChild(new DataRefKeyNode("foo"));

    ListLiteralNode listLit = new ListLiteralNode(
        Lists.<ExprNode>newArrayList(new StringNode("blah"), new IntegerNode(123), dataRef));
    assertEquals("['blah', 123, $foo]", listLit.toSourceString());

    ListLiteralNode emptyListLit = new ListLiteralNode(Lists.<ExprNode>newArrayList());
    assertEquals("[]", emptyListLit.toSourceString());
  }

}
