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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;
import static com.google.template.soy.soytree.TemplateSubject.assertThatTemplateContent;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.VarRefNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for PrintNode.
 *
 */
@RunWith(JUnit4.class)
public final class PrintNodeTest {

  private static final ErrorReporter FAIL = ErrorReporter.exploding();
  private static final SourceLocation X = SourceLocation.UNKNOWN;

  @Test
  public void testPlaceholderMethods() throws SoySyntaxException {
    String template =
        "{@param boo: ?}\n"
            + "{$boo}\n" // 0
            + "{$boo}\n" // 1
            + "{$boo.foo}\n" // 2
            + "{$boo.foo}\n" // 3
            + "{$boo.foo |insertWordBreaks}\n" // 4
            + "{$boo['moo']}\n" // 5
            + "{$boo + $boo.foo}\n"; // 6

    TemplateNode templateNode = assertThatTemplateContent(template).getTemplateNode();
    List<PrintNode> printNodes = getAllNodesOfType(templateNode, PrintNode.class);

    assertThat(printNodes).hasSize(7);

    PrintNode pn = printNodes.get(0);
    assertThat(pn.genBasePhName()).isEqualTo("BOO");
    assertThat(pn.genSamenessKey()).isEqualTo(printNodes.get(1).genSamenessKey());

    pn = printNodes.get(2);
    assertThat(pn.genBasePhName()).isEqualTo("FOO");
    assertThat(pn.genSamenessKey()).isEqualTo(printNodes.get(3).genSamenessKey());
    assertThat(pn.genSamenessKey()).isNotEqualTo(printNodes.get(4).genSamenessKey());

    pn = printNodes.get(5);
    assertWithMessage("Fallback value expected.").that(pn.genBasePhName()).isEqualTo("XXX");

    pn = printNodes.get(6);
    assertWithMessage("Fallback value expected.").that(pn.genBasePhName()).isEqualTo("XXX");
  }

  @Test
  public void testToSourceString() {
    VarRefNode boo = new VarRefNode("boo", X, false, null);

    PrintNode pn = new PrintNode(0, X, true /* isImplicit */, boo, null, FAIL);
    assertThat(pn.toSourceString()).isEqualTo("{$boo}");

    pn = new PrintNode(0, X, false /* isImplicit */, boo, null, FAIL);
    assertThat(pn.toSourceString()).isEqualTo("{print $boo}");
  }
}
