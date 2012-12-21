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

import com.google.template.soy.base.SoyFileKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;

import junit.framework.TestCase;


/**
 * Unit tests for AbstractSoyNodeVisitor.
 *
 * @author Kai Huang
 */
public class AbstractSoyNodeVisitorTest extends TestCase {


  public void testUsingIncompleteOutputVisitor() throws SoySyntaxException {

    SoyFileSetNode soyTree = new SoyFileSetNode(0, null);

    SoyFileNode soyFile = new SoyFileNode(0, SoyFileKind.SRC, null, "boo", null);
    soyTree.addChild(soyFile);

    SoyFileHeaderInfo testSoyFileHeaderInfo = new SoyFileHeaderInfo("testNs");

    TemplateNode template1 =
        new TemplateBasicNode(0, testSoyFileHeaderInfo, "name=\".foo\"", "/** @param goo */");
    soyFile.addChild(template1);
    template1.addChild(new PrintNode(0, true, "$goo", null));
    template1.addChild(new PrintNode(0, true, "2 + 2", null));

    TemplateNode template2 =
        new TemplateBasicNode(0, testSoyFileHeaderInfo, "name=\".moo\"", null);
    soyFile.addChild(template2);
    template2.addChild(new PrintNode(0, true, "'moo'", null));

    IncompleteOutputVisitor iov = new IncompleteOutputVisitor();
    assertEquals("[Parent][SoyFile][Parent][Print][Print][Parent][Print]", iov.exec(soyTree));
  }


  private static class IncompleteOutputVisitor extends AbstractSoyNodeVisitor<String> {

    private StringBuilder outputSb;

    @Override public String exec(SoyNode node) {
      outputSb = new StringBuilder();
      visit(node);
      return outputSb.toString();
    }

    @Override protected void visitSoyFileNode(SoyFileNode node) {
      outputSb.append("[SoyFile]");
      visitChildren(node);
    }

    @Override protected void visitPrintNode(PrintNode node) {
      outputSb.append("[Print]");
    }

    @Override protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        outputSb.append("[Parent]");
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

}
