/*
 * Copyright 2011 Google Inc.
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
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SoyMsgExtractorTest {
  @Rule public final TemporaryFolder temp = new TemporaryFolder();


  @Test
  public void testOutputFileFlag() throws Exception {
    File soyFile1 = temp.newFile("temp.soy");
    Files.write(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "/***/\n{template .a}\n{msg desc=\"a\"}H\uff49{/msg}\n{/template}",
        soyFile1,
        UTF_8);
    File soyFile2 = temp.newFile("temp2.soy");
    Files.write(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
            + "/***/\n{template .b}\n{msg desc=\"a\"}World{/msg}\n{/template}",
        soyFile2,
        UTF_8);
    File xmlFile = temp.newFile("temp.xml");

    int exitCode =
        new SoyMsgExtractor()
            .run("--outputFile", xmlFile.toString(), soyFile1.toString(), soyFile2.toString());
    assertThat(exitCode).isEqualTo(0);
    String xmlContent = Files.toString(xmlFile, UTF_8);
    assertThat(xmlContent).contains("<source>H\uff49</source>");
    assertThat(xmlContent).contains("<source>World</source>");
  }
}
