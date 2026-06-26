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
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.testing.Foo;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AddProtoSourceLocationsPassTest {

  @Test
  public void testRewrite_protoSourceLocations() throws Exception {
    String input =
        join(
            "{template myTemplate}\n",
            "  {@param myProto: Foo}\n",
            "  <div>\n",
            "    {$myProto.getSomeString()}\n",
            "  </div>\n",
            "{/template}");

    String protoPath = Foo.getDescriptor().getFile().getName();
    String expected =
        join(
            "import {Foo} from '" + protoPath + "'\n",
            "{template myTemplate}\n",
            "  {@param myProto: Foo}\n",
            "<div data-sourcecode-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + "no-path;l=5-7;c=3-9\""
                + " data-proto-source-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + protoPath
                + "\">"
                + "{$myProto.getSomeString()}</div>\n",
            "{/template}");

    assertRewrittenWithProtos(expected, input, Foo.getDescriptor());
  }

  @Test
  public void testRewrite_protoSourceLocations_nested() throws Exception {
    String input =
        join(
            "{template myTemplate}\n",
            "  {@param myProto: Foo}\n",
            "  <div>\n",
            "    {$myProto.getSomeEmbeddedMessage()?.getField()}\n",
            "  </div>\n",
            "{/template}");

    String protoPath = Foo.getDescriptor().getFile().getName();
    String expected =
        join(
            "import {Foo} from '" + protoPath + "'\n",
            "{template myTemplate}\n",
            "  {@param myProto: Foo}\n",
            "<div data-sourcecode-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + "no-path;l=5-7;c=3-9\""
                + " data-proto-source-loc=\""
                + AddHtmlSourceLocationsPass.FILE_PREFIX
                + protoPath
                + "\">"
                + "{$myProto.getSomeEmbeddedMessage()?.getField()}</div>\n",
            "{/template}");

    assertRewrittenWithProtos(expected, input, Foo.getDescriptor());
  }

  private static String join(String... lines) {
    return Joiner.on("").join(lines);
  }

  private void assertRewrittenWithProtos(
      String expectedOutput, String templateContent, GenericDescriptor... descriptors) {
    SoyGeneralOptions options = new SoyGeneralOptions();
    options.setExperimentalFeatures(ImmutableList.of("annotate_html_source_locations"));

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileAndImports("{namespace ns}", templateContent, descriptors)
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
