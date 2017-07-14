/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.StringReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AddHtmlCommentsForDebugPassTest {

  @Test
  public void testNoOpForNonHtmlContent() throws Exception {
    assertNoOp("{template .t kind=\"text\"}{/template}");
    assertNoOp("{template .t kind=\"css\"}{/template}");
    assertNoOp("{template .t kind=\"js\"}{/template}");
    assertNoOp("{template .t kind=\"attributes\"}{/template}");
  }

  @Test
  public void testNoOpForNonStrictAutoEscapeMode() throws Exception {
    assertNoOp("{template .t autoescape=\"deprecated-contextual\"}{/template}");
    assertNoOp("{template .t autoescape=\"deprecated-noncontextual\"}{/template}");
  }

  @Test
  public void testRewrites() {
    // Templates that explicitly set ContentKind and/or AutoescapeMode.
    assertThat(runPass("{template .t kind=\"html\"}{/template}"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertThat(runPass("{template .t kind=\"html\" autoescape=\"strict\"}{/template}"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");

    assertThat(runPass("{template .t}{/template}"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertThat(runPass("{template .t}foo{/template}"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "foo"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
    assertThat(runPass("{template .t}{if $foo}bar{/if}{/template}"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, test.soy, 1)-->{/if}"
                + "{if $foo}bar{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
  }

  @Test
  public void testFilePathIsEscaped() {
    assertThat(runPass("{template .t kind=\"html\"}{/template}", "-->.soy"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, --&gt;.soy, 1)-->{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");

    assertThat(runPass("{template .t kind=\"html\"}{/template}", "<!--.soy"))
        .isEqualTo(
            "{if $$debugSoyTemplateInfo()}<!--dta_of(ns.t, &lt;!--.soy, 1)-->{/if}"
                + "{if $$debugSoyTemplateInfo()}<!--dta_cf(ns.t)-->{/if}");
  }

  private static void assertNoOp(String input) {
    assertThat(runPass(input)).isEmpty();
  }

  private static String runPass(String input) {
    return runPass(input, "test.soy");
  }

  /**
   * Parses the given input as a template content, runs the AddHtmlCommentsForDebug pass and returns
   * the resulting source string of the template body.
   */
  private static String runPass(String input, String fileName) {
    String soyFile = "{namespace ns}" + input;
    IncrementingIdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileNode node =
        new SoyFileParser(
                new SoyTypeRegistry(),
                nodeIdGen,
                new StringReader(soyFile),
                SoyFileKind.SRC,
                fileName,
                ExplodingErrorReporter.get())
            .parseSoyFile();
    new AddHtmlCommentsForDebugPass().run(node, nodeIdGen);
    StringBuilder sb = new StringBuilder();
    node.getChild(0).appendSourceStringForChildren(sb);
    return sb.toString();
  }
}
