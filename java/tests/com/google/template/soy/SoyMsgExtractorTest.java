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

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import java.io.File;
import java.util.List;

import junit.framework.TestCase;


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


  public final void testOutputPathFormatFlag() throws Exception {
    File soyFile = getTempFile(".soy");
    Files.write("{namespace ns}\n/***/\n{template .a}\n{msg desc=\"a\"}H\uff49{/msg}\n{/template}",
                soyFile, Charsets.UTF_8);

    String dir = soyFile.getParent().toString();
    String name = soyFile.getName();
    File xmlFile = new File(dir, name.substring(0, name.length() - 4) + ".xml");

    SoyMsgExtractor.main(
        "--outputPathFormat", "{INPUT_DIRECTORY}/{INPUT_FILE_NAME_NO_EXT}.xml",
        soyFile.toString());

    String xmlContent = Files.toString(xmlFile, Charsets.UTF_8);
    assertTrue(xmlContent, xmlContent.contains("<source>H\uff49</source>"));
  }


  public final void testOutputFileFlag() throws Exception {
    File soyFile1 = getTempFile(".soy");
    Files.write("{namespace ns}\n/***/\n{template .a}\n{msg desc=\"a\"}H\uff49{/msg}\n{/template}",
                soyFile1, Charsets.UTF_8);
    File soyFile2 = getTempFile(".soy");
    Files.write("{namespace ns}\n/***/\n{template .b}\n{msg desc=\"a\"}World{/msg}\n{/template}",
                soyFile2, Charsets.UTF_8);

    File xmlFile = getTempFile(".xml");

    SoyMsgExtractor.main(
        "--outputFile", xmlFile.toString(), soyFile1.toString(), soyFile2.toString());

    String xmlContent = Files.toString(xmlFile, Charsets.UTF_8);
    assertTrue(xmlContent, xmlContent.contains("<source>H\uff49</source>"));
    assertTrue(xmlContent, xmlContent.contains("<source>World</source>"));
  }
}
