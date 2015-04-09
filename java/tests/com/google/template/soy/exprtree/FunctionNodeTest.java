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

import com.google.template.soy.base.SourceLocation;

import junit.framework.TestCase;


/**
 * Unit tests for FunctionNode.
 *
 */
public final class FunctionNodeTest extends TestCase {

  public void testToSourceString() {
    FunctionNode fn = new FunctionNode("round", SourceLocation.UNKNOWN);
    fn.addChild(new FloatNode(3.14159, SourceLocation.UNKNOWN));
    fn.addChild(new IntegerNode(2, SourceLocation.UNKNOWN));
    assertEquals("round(3.14159, 2)", fn.toSourceString());
  }
}
