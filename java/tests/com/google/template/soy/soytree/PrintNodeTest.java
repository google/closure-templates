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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SoySyntaxException;

import junit.framework.TestCase;


/**
 * Unit tests for PrintNode.
 *
 */
public class PrintNodeTest extends TestCase {


  public void testPlaceholderMethods() throws SoySyntaxException {

    PrintNode pn = new PrintNode(0, true, "$boo", null);
    assertEquals("BOO", pn.genBasePlaceholderName());
    assertTrue(pn.genSamenessKey().equals((new PrintNode(4, true, "$boo", null)).genSamenessKey()));
    assertTrue(
        pn.genSamenessKey().equals((new PrintNode(4, true, "  $boo  ", null)).genSamenessKey()));

    pn = new PrintNode(0, true, "$boo.foo", null);
    assertEquals("FOO", pn.genBasePlaceholderName());
    assertFalse(
        pn.genSamenessKey().equals((new PrintNode(4, true, "$boo", null)).genSamenessKey()));

    pn = new PrintNode(0, true, "$boo.foo", null);
    pn.addChild(new PrintDirectiveNode(0, "|insertWordBreaks", "8"));
    assertEquals("FOO", pn.genBasePlaceholderName());
    assertFalse(
        pn.genSamenessKey().equals((new PrintNode(4, true, "$boo.foo", null)).genSamenessKey()));

    pn = new PrintNode(0, true, "$boo['foo']", null);
    assertEquals("Fallback value expected.", "XXX", pn.genBasePlaceholderName());

    pn = new PrintNode(0, true, "$boo + $foo", null);
    assertEquals("Fallback value expected.", "XXX", pn.genBasePlaceholderName());

    // V1 syntax.
    pn = new PrintNode(0, true, "\"blah\"", null);
    assertEquals("Fallback value expected.", "XXX", pn.genBasePlaceholderName());
  }


  public void testToSourceString() {

    PrintNode pn = new PrintNode(0, true, "$boo", null);
    assertEquals("{$boo}", pn.toSourceString());

    pn = new PrintNode(0, false, "$boo", null);
    assertEquals("{print $boo}", pn.toSourceString());
  }

}
