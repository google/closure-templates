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
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.soytree.AutoescapeMode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;

import junit.framework.TestCase;


/**
 * Unit tests for TemplateNode.
 *
 * @author Kai Huang
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


  public void testCommandTextErrors() throws SoySyntaxException {

    try {
      new TemplateBasicNode(0, null, "autoescape=\"true\"", "/***/");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Invalid 'template' command missing template name: {template autoescape=\"true\"}."));
    }

    try {
      new TemplateBasicNode(0, null, ".foo name=\"x.foo\" autoescape=\"true\"", "/***/");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Invalid 'template' command with template name declared multiple times (.foo, x.foo)."));
    }

    try {
      new TemplateBasicNode(0, null, "autoescape=\"true", "/***/");
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Malformed attributes in 'template' command text (autoescape=\"true)."));
    }
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


  public void testValidStrictTemplates() throws SoySyntaxException {
    TemplateNode node;

    node = new TemplateBasicNode(
        0, new SoyFileHeaderInfo("testNs"),
        ".boo kind=\"text\" autoescape=\"strict\"",
        "/** Strict template. */");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.TEXT, node.getContentKind());

    node = new TemplateBasicNode(
        0, new SoyFileHeaderInfo("testNs"),
        ".boo autoescape=\"strict\" kind=\"html\"",
        "/** Strict template. */");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.HTML, node.getContentKind());

    // "kind" is optional, defaults to HTML
    node = new TemplateBasicNode(
        0, new SoyFileHeaderInfo("testNs"),
        ".boo autoescape=\"strict\"",
        "/** Strict template. */");
    assertEquals(AutoescapeMode.STRICT, node.getAutoescapeMode());
    assertEquals(ContentKind.HTML, node.getContentKind());
  }


  public void testInvalidStrictTemplates() throws SoySyntaxException {
    try {
      new TemplateBasicNode(
          0, new SoyFileHeaderInfo("testNs"),
          ".boo kind=\"text\"",
          "/** Strict template. */");
      fail("Should be a syntax error");
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "kind=\"...\" attribute is only valid with autoescape=\"strict\"."));
    }
  }

}
