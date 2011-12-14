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
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;

import junit.framework.TestCase;


/**
 * Unit tests for TemplateNode.
 *
 */
public class TemplateNodeTest extends TestCase {


  public void testToSourceString() throws SoySyntaxException {

    TemplateNode tn = new TemplateBasicNode(
        0, new SoyFileHeaderInfo("testNs"), "name=\".boo\"",
        "/**" +
        " * Test template.\n" +
        " *\n" +
        " * @param foo Foo to print.\n" +
        " * @param goo\n" +
        " *     Goo to print.\n" +
        " */");
    tn.addChild(new RawTextNode(0, "  "));  // 2 spaces
    tn.addChild(new PrintNode(0, true, "$foo", null));
    tn.addChild(new PrintNode(0, true, "$goo", null));
    tn.addChild(new RawTextNode(0, "  "));  // 2 spaces

    assertEquals(
        "/**" +
        " * Test template.\n" +
        " *\n" +
        " * @param foo Foo to print.\n" +
        " * @param goo\n" +
        " *     Goo to print.\n" +
        " */\n" +
        "{template name=\".boo\"}\n" +
        "{sp} {$foo}{$goo} {sp}\n" +
        "{/template}\n",
        tn.toSourceString());
  }


  public void testInvalidDeclarations() throws SoySyntaxException {

    SoyFileHeaderInfo testSoyFileHeaderInfo = new SoyFileHeaderInfo("testNs");

    try {
      new TemplateBasicNode(0, testSoyFileHeaderInfo, ".boo", "/** @param $foo */");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains("Invalid SoyDoc declaration \"@param $foo\"."));
    }

    // For now, allow this. But we should eventually fix these and turn on checking for bad syntax.
    new TemplateBasicNode(0, testSoyFileHeaderInfo, ".boo", "/** @param {string} foo */");
    new TemplateBasicNode(
        0, testSoyFileHeaderInfo, ".boo", "/** @param {Object.<string, string>} foo */");
  }


  public void testDuplicateParam() throws SoySyntaxException {

    try {
      new TemplateBasicNode(
          0, new SoyFileHeaderInfo("testNs"), ".boo", "/** @param foo @param goo @param? foo */");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains("Duplicate declaration of param in SoyDoc: 'foo'."));
    }
  }

}
