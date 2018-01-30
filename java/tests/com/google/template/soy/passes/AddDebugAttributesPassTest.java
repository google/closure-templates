/*
 * Copyright 2018 Google Inc.
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
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.soytree.SoyFileSetNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AddDebugAttributesPassTest {

  @Test
  public void testRewrite() throws Exception {
    assertRewritten(
        join(
            "{template .foo}\n",
            "<div{if $$debugSoyTemplateInfo()} data-debug-soy=\"ns.foo no-path:4\"{/if}></div>",
            "<input{if $$debugSoyTemplateInfo()} data-debug-soy=\"ns.foo no-path:4\"{/if}/>",
            "<a{if $$debugSoyTemplateInfo()} data-debug-soy=\"ns.foo no-path:4\"{/if}></a>\n",
            "{/template}"),
        join("{template .foo}\n", "<div></div><input /><a></a>\n", "{/template}"));
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  /**
   * Returns the contextually rewritten and injected source.
   *
   * <p>The Soy tree may have multiple files, but only the source code for the first is returned.
   */
  private void assertRewritten(String expectedOutput, String input) {
    String namespace = "{namespace ns}\n\n";
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(namespace + input)
            .addHtmlAttributesForDebugging(true)
            .parse()
            .fileSet();

    StringBuilder src = new StringBuilder();
    src.append(soyTree.getChild(0).toSourceString());

    String output = src.toString().trim();
    if (output.startsWith("{namespace ns")) {
      output = output.substring(output.indexOf('}') + 1).trim();
    }

    assertThat(output).isEqualTo(expectedOutput);
  }
}
