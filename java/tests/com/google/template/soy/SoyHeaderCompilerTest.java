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

package com.google.template.soy;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Files;
import com.google.template.soy.soytree.CompilationUnit;
import com.google.template.soy.soytree.DataAllCallSituationP;
import com.google.template.soy.soytree.ParameterP;
import com.google.template.soy.soytree.SanitizedContentKindP;
import com.google.template.soy.soytree.SoyFileP;
import com.google.template.soy.soytree.SoyTypeP;
import com.google.template.soy.soytree.TemplateKindP;
import com.google.template.soy.soytree.TemplateMetadataP;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SoyHeaderCompilerTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testOutputFileFlag() throws Exception {
    File soyFile1 = temp.newFile("temp.soy");
    Files.asCharSink(soyFile1, UTF_8)
        .write(
            "{namespace ns}\n"
                + "/***/\n{template .a}{@param p: string}{call .a data='all'/}{/template}");
    File outputFile = temp.newFile("temp.soyh");

    int exitCode =
        new SoyHeaderCompiler()
            .run(
                new String[] {
                  "--output", outputFile.toString(), "--srcs", soyFile1.toString(),
                },
                System.err);
    assertThat(exitCode).isEqualTo(0);
    CompilationUnit unit;
    try (InputStream is = new GZIPInputStream(new FileInputStream(outputFile))) {
      unit = CompilationUnit.parseFrom(is);
    }
    assertThat(unit.getFileList()).hasSize(1);
    SoyFileP file = unit.getFile(0);
    assertThat(file.getDelpackage()).isEmpty();
    assertThat(file.getNamespace()).isEqualTo("ns");
    assertThat(file.getFilePath()).isEqualTo(soyFile1.getPath());
    assertThat(file.getTemplateList()).hasSize(1);
    TemplateMetadataP template = file.getTemplate(0);
    assertThat(template.getTemplateName()).isEqualTo(".a");
    assertThat(template.getTemplateKind()).isEqualTo(TemplateKindP.BASIC);
    assertThat(template.getContentKind()).isEqualTo(SanitizedContentKindP.HTML);
    assertThat(template.getParameterList())
        .containsExactly(
            ParameterP.newBuilder()
                .setName("p")
                .setType(SoyTypeP.newBuilder().setPrimitive(SoyTypeP.PrimitiveTypeP.STRING))
                .setRequired(true)
                .build());
    assertThat(template.getDataAllCallSituationList())
        .containsExactly(DataAllCallSituationP.newBuilder().setTemplateName(".a").build());
  }
}
