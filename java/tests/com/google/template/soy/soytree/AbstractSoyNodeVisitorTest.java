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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;

import junit.framework.TestCase;

/**
 * Unit tests for AbstractSoyNodeVisitor.
 *
 */
public final class AbstractSoyNodeVisitorTest extends TestCase {

  private static final ErrorReporter FAIL = ExplodingErrorReporter.get();

  public void testUsingIncompleteOutputVisitor() throws SoySyntaxException {

    SoyFileSetNode soyTree = new SoyFileSetNode(0, null);

    SoyFileNode soyFile = new SoyFileNode(0, "", SoyFileKind.SRC, null, "boo", null);
    soyTree.addChild(soyFile);

    SoyFileHeaderInfo testSoyFileHeaderInfo = new SoyFileHeaderInfo("testNs");

    TemplateNode template1 =
        new TemplateBasicNodeBuilder(testSoyFileHeaderInfo, SourceLocation.UNKNOWN, FAIL)
            .setId(0)
            .setCmdText("name=\".foo\"")
            .setSoyDoc("/** @param goo */")
            .build();
    soyFile.addChild(template1);
    template1.addChild(
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("$goo")
            .build(FAIL));
    template1.addChild(
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("2 + 2")
            .build(FAIL));

    TemplateNode template2 =
        new TemplateBasicNodeBuilder(testSoyFileHeaderInfo, SourceLocation.UNKNOWN, FAIL)
            .setId(0)
            .setCmdText("name=\".moo\"")
            .setSoyDoc(null)
            .build();
    soyFile.addChild(template2);
    template2.addChild(
        new PrintNode.Builder(0, true /* isImplicit */, SourceLocation.UNKNOWN)
            .exprText("'moo'")
            .build(FAIL));

    IncompleteOutputVisitor iov = new IncompleteOutputVisitor(FAIL);
    assertEquals("[Parent][SoyFile][Parent][Print][Print][Parent][Print]", iov.exec(soyTree));
  }

  private static class IncompleteOutputVisitor extends AbstractSoyNodeVisitor<String> {

    private StringBuilder outputSb;

    private IncompleteOutputVisitor(ErrorReporter errorReporter) {
      super(errorReporter);
    }

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
