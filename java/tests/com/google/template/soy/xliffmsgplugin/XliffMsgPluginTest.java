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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.error.ErrorReporter;
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
        msgPlugin.generateExtractedMsgsFile(
            msgBundle, outputFileOptions, ErrorReporter.exploding());

    URL expectedExtractedMsgsFile =
        Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2_extracted.xlf");
    assertThat(extractedMsgsFile.toString())
        .isEqualTo(Resources.toString(expectedExtractedMsgsFile, UTF_8));

    // Test with target language.
    outputFileOptions.setTargetLocaleString("x-zz");
    extractedMsgsFile =
        msgPlugin.generateExtractedMsgsFile(
            msgBundle, outputFileOptions, ErrorReporter.exploding());

    expectedExtractedMsgsFile =
        Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2_extracted_x-zz.xlf");
    assertThat(extractedMsgsFile.toString())
        .isEqualTo(Resources.toString(expectedExtractedMsgsFile, UTF_8));
  }

  @Test
  public void testParseTranslatedMsgsFile() throws Exception {

    URL translatedMsgsFile =
        Resources.getResource(XliffMsgPluginTest.class, "test_data/test-v2_translated_x-zz.xlf");
    XliffMsgPlugin msgPlugin = new XliffMsgPlugin();
    SoyMsgBundle msgBundle =
        msgPlugin.parseTranslatedMsgsFile(Resources.toString(translatedMsgsFile, UTF_8));

    assertThat(msgBundle.getNumMsgs()).isEqualTo(5);

    List<SoyMsg> msgs = Lists.newArrayList();
    for (SoyMsg msg : msgBundle) {
      msgs.add(msg);
    }
    assertThat(msgs).hasSize(5);

    SoyMsg moscowMsg = msgs.get(0);
    assertThat(moscowMsg.getId()).isEqualTo(626010707674174792L);
    List<SoyMsgPart> moscowMsgParts = moscowMsg.getParts();
    assertThat(moscowMsgParts).hasSize(1);
    assertThat(((SoyMsgRawTextPart) moscowMsgParts.get(0)).getRawText()).isEqualTo("Zmoscow");

    assertThat(msgs.get(1).getId()).isEqualTo(948230478248061386L);

    SoyMsg mooseMsg = msgs.get(2);
    assertThat(mooseMsg.getId()).isEqualTo(2764913337766789440L);
    List<SoyMsgPart> mooseMsgParts = mooseMsg.getParts();
    assertThat(mooseMsgParts).hasSize(7);
    assertThat(((SoyMsgRawTextPart) mooseMsgParts.get(0)).getRawText()).isEqualTo("Zmoose ");
    assertThat(((SoyMsgPlaceholderPart) mooseMsgParts.get(1)).getPlaceholderName())
        .isEqualTo("START_ITALIC");
    assertThat(((SoyMsgRawTextPart) mooseMsgParts.get(2)).getRawText()).isEqualTo("zalso");
    assertThat(((SoyMsgPlaceholderPart) mooseMsgParts.get(3)).getPlaceholderName())
        .isEqualTo("END_ITALIC");
    assertThat(((SoyMsgRawTextPart) mooseMsgParts.get(4)).getRawText()).isEqualTo(" zsays ");
    assertThat(((SoyMsgPlaceholderPart) mooseMsgParts.get(5)).getPlaceholderName())
        .isEqualTo("XXX");
    assertThat(((SoyMsgRawTextPart) mooseMsgParts.get(6)).getRawText()).isEqualTo(".");

    SoyMsg cowMsg = msgs.get(3);
    assertThat(cowMsg.getId()).isEqualTo(6632711700686641662L);
    List<SoyMsgPart> cowMsgParts = cowMsg.getParts();
    assertThat(cowMsgParts).hasSize(3);
    assertThat(((SoyMsgRawTextPart) cowMsgParts.get(0)).getRawText()).isEqualTo("Zcow zsays ");
    assertThat(((SoyMsgPlaceholderPart) cowMsgParts.get(1)).getPlaceholderName()).isEqualTo("MOO");
    assertThat(((SoyMsgRawTextPart) cowMsgParts.get(2)).getRawText()).isEqualTo(".");

    assertThat(msgs.get(4).getId()).isEqualTo(8577643341484516105L);
  }
}
