/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.truth.StringSubject;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DesugarStateNodesPassTest {

  @Test
  public void testRewrites() {
    assertRewrite("{@state foo := 1}").isEqualTo("{let $foo : 1 /}<div>{$foo}</div>");
  }

  private static StringSubject assertRewrite(String input) {
    return assertThat(runPass(input));
  }

  /**
   * Parses the given input as a template content, runs the HtmlRewrite pass and the Desugar Passes
   * and returns the resulting source string of the template body
   */
  private static String runPass(String input) {
    String soyFile =
        Joiner.on('\n')
            .join("{namespace ns}", "{element .t}", input, "<div>{$foo}</div>", "{/element}");
    SoyFileNode node =
        SoyFileSetParserBuilder.forFileContents(soyFile)
            .desugarHtmlAndStateNodes(true)
            .parse()
            .fileSet()
            .getChild(0);
    assertThat(SoyTreeUtils.hasHtmlNodes(node)).isFalse();
    StringBuilder sb = new StringBuilder();
    node.getChild(0).appendSourceStringForChildren(sb);
    return sb.toString();
  }
}
