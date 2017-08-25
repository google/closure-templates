/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.xliffmsgplugin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler.OutputFileOptions;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import java.net.URL;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for XliffMsgPlugin.
 *
 */
@RunWith(JUnit4.class)
public final class XliffMsgPluginTest {

  @Test
  public void testGenerateExtractedMsgsFile() throws Exception {

    URL testSoyFile = Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2.soy");
    SoyMsgBundle msgBundle = SoyFileSet.builder().add(testSoyFile).build().extractMsgs();

    XliffMsgPlugin msgPlugin = new XliffMsgPlugin();

    // Test without target language.
    OutputFileOptions outputFileOptions = new OutputFileOptions();
    CharSequence extractedMsgsFile =
        msgPlugin.generateExtractedMsgsFile(msgBundle, outputFileOptions);

    URL expectedExtractedMsgsFile =
        Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2_extracted.xlf");
    assertEquals(
        Resources.toString(expectedExtractedMsgsFile, UTF_8), extractedMsgsFile.toString());

    // Test with target language.
    outputFileOptions.setTargetLocaleString("x-zz");
    extractedMsgsFile = msgPlugin.generateExtractedMsgsFile(msgBundle, outputFileOptions);

    expectedExtractedMsgsFile =
        Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2_extracted_x-zz.xlf");
    assertEquals(
        Resources.toString(expectedExtractedMsgsFile, UTF_8), extractedMsgsFile.toString());
  }

  @Test
  public void testParseTranslatedMsgsFile() throws Exception {

    URL translatedMsgsFile =
        Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2_translated_x-zz.xlf");
    XliffMsgPlugin msgPlugin = new XliffMsgPlugin();
    SoyMsgBundle msgBundle =
        msgPlugin.parseTranslatedMsgsFile(Resources.toString(translatedMsgsFile, UTF_8));

    assertEquals(5, msgBundle.getNumMsgs());

    List<SoyMsg> msgs = Lists.newArrayList();
    for (SoyMsg msg : msgBundle) {
      msgs.add(msg);
    }
    assertEquals(5, msgs.size());

    SoyMsg moscowMsg = msgs.get(0);
    assertEquals(626010707674174792L, moscowMsg.getId());
    List<SoyMsgPart> moscowMsgParts = moscowMsg.getParts();
    assertEquals(1, moscowMsgParts.size());
    assertEquals("Zmoscow", ((SoyMsgRawTextPart) moscowMsgParts.get(0)).getRawText());

    assertEquals(948230478248061386L, msgs.get(1).getId());

    SoyMsg mooseMsg = msgs.get(2);
    assertEquals(2764913337766789440L, mooseMsg.getId());
    List<SoyMsgPart> mooseMsgParts = mooseMsg.getParts();
    assertEquals(7, mooseMsgParts.size());
    assertEquals("Zmoose ", ((SoyMsgRawTextPart) mooseMsgParts.get(0)).getRawText());
    assertEquals(
        "START_ITALIC", ((SoyMsgPlaceholderPart) mooseMsgParts.get(1)).getPlaceholderName());
    assertEquals("zalso", ((SoyMsgRawTextPart) mooseMsgParts.get(2)).getRawText());
    assertEquals("END_ITALIC", ((SoyMsgPlaceholderPart) mooseMsgParts.get(3)).getPlaceholderName());
    assertEquals(" zsays ", ((SoyMsgRawTextPart) mooseMsgParts.get(4)).getRawText());
    assertEquals("XXX", ((SoyMsgPlaceholderPart) mooseMsgParts.get(5)).getPlaceholderName());
    assertEquals(".", ((SoyMsgRawTextPart) mooseMsgParts.get(6)).getRawText());

    SoyMsg cowMsg = msgs.get(3);
    assertEquals(6632711700686641662L, cowMsg.getId());
    List<SoyMsgPart> cowMsgParts = cowMsg.getParts();
    assertEquals(3, cowMsgParts.size());
    assertEquals("Zcow zsays ", ((SoyMsgRawTextPart) cowMsgParts.get(0)).getRawText());
    assertEquals("MOO", ((SoyMsgPlaceholderPart) cowMsgParts.get(1)).getPlaceholderName());
    assertEquals(".", ((SoyMsgRawTextPart) cowMsgParts.get(2)).getRawText());

    assertEquals(8577643341484516105L, msgs.get(4).getId());
  }
}
