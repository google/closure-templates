/*
 * Copyright 2026 Google Inc.
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
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AddHtmlSourceLocationsPassTest {

  @Test
  public void testRewrite() throws Exception {
    assertRewritten(
        join(
            "{template foo}\n",
            "<div data-sourcecode-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + "path/to/test.soy;l=4-4;c=1-25\">",
            "<span data-sourcecode-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + "path/to/test.soy;l=4-4;c=6-19\"></span>",
            "</div>\n",
            "{/template}"),
        join("{template foo}\n", "<div><span></span></div>\n", "{/template}"));
  }

  @Test
  public void testRewrite_preservesExisting() throws Exception {
    assertRewritten(
        join(
            "{template foo}\n",
            "<div data-sourcecode-loc=\"existing_loc\">",
            "<span data-sourcecode-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + "path/to/test.soy;l=4-4;c=41-54\"></span>",
            "</div>\n",
            "{/template}"),
        join(
            "{template foo}\n",
            "<div data-sourcecode-loc=\"existing_loc\"><span></span></div>\n",
            "{/template}"));
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private void assertRewritten(String expectedOutput, String input) {
    String namespace = "{namespace ns}\n\n";
    SoyGeneralOptions options = new SoyGeneralOptions();
    options.setExperimentalFeatures(
        com.google.common.collect.ImmutableList.of("annotate_html_source_locations"));

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forSuppliers(
                SoyFileSupplier.Factory.create(
                    namespace + input, SourceFilePath.forTest("path/to/test.soy")))
            .options(options)
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
