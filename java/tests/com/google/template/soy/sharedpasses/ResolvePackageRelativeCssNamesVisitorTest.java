/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.common.collect.Lists;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit test for resolivng package-relative class names.
 */
public class ResolvePackageRelativeCssNamesVisitorTest extends TestCase {

  public List<CssNode> compileTemplate(String templateText) {
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(
        SharedTestUtils.parseSoyFiles(templateText));
    new ResolvePackageRelativeCssNamesVisitor().exec(template);
    return getCssNodes(template);
  }

  public void testBaseCssOnNamespace() {
    List<CssNode> cssNodes = compileTemplate(
        "{namespace boo cssbase=\"some.test.package\"}\n\n" +
        "/** Test template. @param goo */\n" +
        "{template name=\".foo\"}\n" +
        "  <div class=\"{css %AAA}\">\n" +
        "{/template}\n");

    assertEquals("someTestPackageAAA", cssNodes.get(0).getSelectorText());
  }

  public void testBaseCssOnTemplate() {
    List<CssNode> cssNodes = compileTemplate(
        "{namespace boo}\n\n" +
        "/** Test template. @param goo */\n" +
        "{template name=\".foo\" cssbase=\"some.test.package\"}\n" +
        "  <div class=\"{css %AAA}\">\n" +
        "{/template}\n");

    assertEquals("someTestPackageAAA", cssNodes.get(0).getSelectorText());
  }

  public void testRequireCssOnNamespace() {
    List<CssNode> cssNodes = compileTemplate(
        "{namespace boo requirecss=\"some.test.package,some.other.package\"}\n\n" +
        "/** Test template. @param goo */\n" +
        "{template name=\".foo\"}\n" +
        "  <div class=\"{css %AAA}\">\n" +
        "{/template}\n");

    assertEquals("someTestPackageAAA", cssNodes.get(0).getSelectorText());
  }

  public void testUnprefixedNode() {
    List<CssNode> cssNodes = compileTemplate(
        "{namespace boo cssbase=\"some.test.package\"}\n\n" +
        "/** Test template. @param goo */\n" +
        "{template name=\".foo\"}\n" +
        "  <div class=\"{css AAA}\">\n" +
        "{/template}\n");

    assertEquals("AAA", cssNodes.get(0).getSelectorText());
  }

  public void testMissingCssBase() {
    try {
      compileTemplate(
          "{namespace boo}\n\n" +
          "/** Test template. @param goo */\n" +
          "{template name=\".foo\"}\n" +
          "  <div class=\"{css %AAA}\">\n" +
          "{/template}\n");
      fail("Exception expected");
    } catch (SoySyntaxException e) {
      assertTrue(e.getMessage().contains("No CSS package"));
    }
  }

  public void testWithComponentName() {
    try {
      compileTemplate(
          "{namespace boo}\n\n" +
          "/** Test template. @param goo */\n" +
          "{template name=\".foo\"}\n" +
          "  <div class=\"{css $goo, %AAA}\">\n" +
          "{/template}\n");
      fail("Exception expected");
    } catch (SoySyntaxException e) {
      assertTrue(e.getMessage().contains("component expression"));
    }
  }

  /**
   * Helper function that gathers all of the CSS nodes within a tree.
   * @return A list of CSS nodes.
   */
  private <T extends SoyNode> List<T> getCssNodes(SoyNode root) {
    CollectNodesVisitor visitor = new CollectNodesVisitor(CssNode.class);
    visitor.exec(root);
    return (List<T>) visitor.getNodes();
  }

  /**
   * Test helper class that scarfs up all nodes of a given type.
   * TODO(user): Move this to someplace more general.
   */
  public static class CollectNodesVisitor extends AbstractSoyNodeVisitor<Void> {
    private final List<SoyNode> nodes = Lists.newArrayList();
    private final Class<?> nodeType;

    CollectNodesVisitor(Class<?> nodeType) {
      this.nodeType = nodeType;
    }

    public List<SoyNode> getNodes() {
      return nodes;
    }

    @Override protected void visitSoyNode(SoyNode node) {
      if (nodeType.isInstance(node)) {
        nodes.add(node);
      }
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
