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

import com.google.common.collect.Lists;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.util.List;

public class SoyMsgExtractorTest extends TestCase {
  private List<File> filesToDelete;

  @Override protected void setUp() throws Exception {
    super.setUp();
    filesToDelete = Lists.newArrayList();
  }


  @Override protected void tearDown() throws Exception {
    super.tearDown();
    for (File fileToDelete : filesToDelete) {
      fileToDelete.delete();
    }
    filesToDelete = null;
  }


  private File getTempFile(String ext) throws Exception {
    File tmpFile = File.createTempFile(getName(), ext);
    filesToDelete.add(tmpFile);
    return tmpFile;
  }

  public final void testOutputFileFlag() throws Exception {
    File soyFile1 = getTempFile(".soy");
    Files.write(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
        + "/***/\n{template .a}\n{msg desc=\"a\"}H\uff49{/msg}\n{/template}",
        soyFile1, UTF_8);
    File soyFile2 = getTempFile(".soy");
    Files.write(
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n"
        + "/***/\n{template .b}\n{msg desc=\"a\"}World{/msg}\n{/template}",
        soyFile2, UTF_8);

    File xmlFile = getTempFile(".xml");

    int exitCode = new SoyMsgExtractor().run(
        "--outputFile", xmlFile.toString(), soyFile1.toString(), soyFile2.toString());
    assertThat(exitCode).isEqualTo(0);
    String xmlContent = Files.toString(xmlFile, UTF_8);
    assertThat(xmlContent).contains("<source>H\uff49</source>");
    assertThat(xmlContent).contains("<source>World</source>");
  }
}
