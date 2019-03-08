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

import static org.junit.Assert.assertEquals;

import com.google.common.base.Joiner;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for AbstractSoyNodeVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class AbstractSoyNodeVisitorTest {

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  @Test
  public void testUsingIncompleteOutputVisitor() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                Joiner.on("\n")
                    .join(
                        "{namespace boo}",
                        "{template .foo}",
                        "  {@param goo: ?}",
                        "  {$goo}",
                        "  {2 + 2}",
                        "{/template}",
                        "{template .moo}",
                        "  {'moo'}",
                        "{/template}",
                        ""))
            .errorReporter(FAIL)
            .parse()
            .fileSet();

    IncompleteOutputVisitor iov = new IncompleteOutputVisitor();
    assertEquals("[Parent][SoyFile][Parent][Print][Print][Parent][Print]", iov.exec(soyTree));
  }

  private static class IncompleteOutputVisitor extends AbstractSoyNodeVisitor<String> {

    private StringBuilder outputSb;

    @Override
    public String exec(SoyNode node) {
      outputSb = new StringBuilder();
      visit(node);
      return outputSb.toString();
    }

    @Override
    protected void visitSoyFileNode(SoyFileNode node) {
      outputSb.append("[SoyFile]");
      visitChildren(node);
    }

    @Override
    protected void visitPrintNode(PrintNode node) {
      outputSb.append("[Print]");
    }

    @Override
    protected void visitSoyNode(SoyNode node) {
      if (node instanceof ParentSoyNode<?>) {
        outputSb.append("[Parent]");
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }
}
