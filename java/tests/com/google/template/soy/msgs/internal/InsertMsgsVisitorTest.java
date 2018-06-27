/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.msgs.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link InsertMsgsVisitor}.
 *
 */
@RunWith(JUnit4.class)
public final class InsertMsgsVisitorTest {

  private static final ErrorReporter FAIL = ErrorReporter.exploding();

  // -----------------------------------------------------------------------------------------------
  // Test basic messages.

  private static final String BASIC_TEST_FILE_CONTENT =
      ""
          + "{namespace boo}\n"
          + "\n"
          + "/** Test template. */\n"
          + "{template .foo}\n"
          + "  {@param boo: ?}\n"
          + "  {@param foo: ?}\n"
          + "  {@param moo: ?}\n"
          + "  {@param goo: ?}\n"
          + "  {$boo}scary{sp}\n"
          + "  {msg desc=\"Test.\"}\n"
          + "    random{$foo}\n"
          + "    <a href=\"{$goo}\">slimy</a>\n"
          + "  {/msg}{sp}\n"
          + "  {msg desc=\"\"}dairy{$moo}{/msg}\n"
          + "{/template}\n";

  @Test
  public void testBasicMsgsUsingSoySource() {

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(BASIC_TEST_FILE_CONTENT).parse().fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertThat(template.numChildren()).isEqualTo(5);
    MsgNode msg = ((MsgFallbackGroupNode) template.getChild(2)).getChild(0);
    assertThat(msg.numChildren()).isEqualTo(5);
    MsgHtmlTagNode msgHtmlTag2 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(2)).getChild(0);
    assertThat(msgHtmlTag2.numChildren()).isEqualTo(3);
    MsgHtmlTagNode msgHtmlTag4 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(4)).getChild(0);
    assertThat(msgHtmlTag4.numChildren()).isEqualTo(1);
    assertThat(((RawTextNode) msgHtmlTag4.getChild(0)).getRawText()).isEqualTo("</a>");

    // Execute the visitor.
    new InsertMsgsVisitor(/* msgBundle= */ null, FAIL).insertMsgs(template);

    // After.
    assertThat(template.numChildren()).isEqualTo(12);
    assertThat(((PrintNode) template.getChild(0)).getExpr().toSourceString()).isEqualTo("$boo");
    assertThat(((RawTextNode) template.getChild(1)).getRawText()).isEqualTo("scary ");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("random");
    assertThat(((PrintNode) template.getChild(3)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(((RawTextNode) template.getChild(4)).getRawText()).isEqualTo("<a href=\"");
    assertThat(((PrintNode) template.getChild(5)).getExpr().toSourceString()).isEqualTo("$goo");
    assertThat(((RawTextNode) template.getChild(6)).getRawText()).isEqualTo("\">");
    assertThat(((RawTextNode) template.getChild(7)).getRawText()).isEqualTo("slimy");
    assertThat(((RawTextNode) template.getChild(8)).getRawText()).isEqualTo("</a>");
    assertThat(((RawTextNode) template.getChild(9)).getRawText()).isEqualTo(" ");
    assertThat(((RawTextNode) template.getChild(10)).getRawText()).isEqualTo("dairy");
    assertThat(((PrintNode) template.getChild(11)).getExpr().toSourceString()).isEqualTo("$moo");
  }

  @Test
  public void testBasicMsgsUsingMsgBundle() {

    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(BASIC_TEST_FILE_CONTENT).parse().fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertThat(template.numChildren()).isEqualTo(5);
    MsgNode msg = ((MsgFallbackGroupNode) template.getChild(2)).getChild(0);
    assertThat(msg.numChildren()).isEqualTo(5);
    MsgHtmlTagNode msgHtmlTag2 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(2)).getChild(0);
    assertThat(msgHtmlTag2.numChildren()).isEqualTo(3);
    MsgHtmlTagNode msgHtmlTag4 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(4)).getChild(0);
    assertThat(msgHtmlTag4.numChildren()).isEqualTo(1);
    assertThat(((RawTextNode) msgHtmlTag4.getChild(0)).getRawText()).isEqualTo("</a>");

    // Build the translated message bundle.
    List<SoyMsg> translatedMsgs = Lists.newArrayList();
    // Original (en): random{{FOO}}{{START_LINK}}slimy{{END_LINK}}
    // Translation (x-zz): {{START_LINK}}zslimy{{END_LINK}}{{FOO}}zrandom
    translatedMsgs.add(
        SoyMsg.builder()
            .setId(MsgUtils.computeMsgIdForDualFormat(msg))
            .setLocaleString("x-zz")
            .setParts(
                ImmutableList.of(
                    new SoyMsgPlaceholderPart("START_LINK", /* placeholderExample= */ null),
                    SoyMsgRawTextPart.of("zslimy"),
                    new SoyMsgPlaceholderPart("END_LINK", /* placeholderExample= */ null),
                    new SoyMsgPlaceholderPart("FOO", /* placeholderExample= */ null),
                    SoyMsgRawTextPart.of("zrandom")))
            .build());
    // Note: This bundle has no translation for the message "dairy{$moo}".
    SoyMsgBundle msgBundle = new SoyMsgBundleImpl("x-zz", translatedMsgs);

    // Execute the visitor.
    new InsertMsgsVisitor(msgBundle, FAIL).insertMsgs(template);

    // After.
    assertThat(template.numChildren()).isEqualTo(12);
    assertThat(((PrintNode) template.getChild(0)).getExpr().toSourceString()).isEqualTo("$boo");
    assertThat(((RawTextNode) template.getChild(1)).getRawText()).isEqualTo("scary ");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("<a href=\"");
    assertThat(((PrintNode) template.getChild(3)).getExpr().toSourceString()).isEqualTo("$goo");
    assertThat(((RawTextNode) template.getChild(4)).getRawText()).isEqualTo("\">");
    assertThat(((RawTextNode) template.getChild(5)).getRawText()).isEqualTo("zslimy");
    assertThat(((RawTextNode) template.getChild(6)).getRawText()).isEqualTo("</a>");
    assertThat(((PrintNode) template.getChild(7)).getExpr().toSourceString()).isEqualTo("$foo");
    assertThat(((RawTextNode) template.getChild(8)).getRawText()).isEqualTo("zrandom");
    assertThat(((RawTextNode) template.getChild(9)).getRawText()).isEqualTo(" ");
    assertThat(((RawTextNode) template.getChild(10)).getRawText()).isEqualTo("dairy");
    assertThat(((PrintNode) template.getChild(11)).getExpr().toSourceString()).isEqualTo("$moo");
  }

  // -----------------------------------------------------------------------------------------------
  // Test plural/select messages.

  private static final String PLRSEL_TEST_FILE_CONTENT =
      ""
          + "{namespace boo}\n"
          + "\n"
          + "/** Test template with plural/select msgs. */\n"
          + "{template .foo}\n"
          + "  {@param gender: ?}\n"
          + "  {@param numFriends: ?}\n"
          + "  {msg desc=\"Plural message.\"}\n"
          + "    {plural $numFriends}\n"
          + "      {case 1}Added a friend to your circle.\n"
          + "      {default}Added {$numFriends} friends to your circle.\n"
          + "    {/plural}\n"
          + "  {/msg}\n"
          + "  {msg desc=\"Select message.\"}\n"
          + "    {select $gender}\n"
          + "      {case 'female'}She is in your circles.\n"
          + "      {default}He is in your circles.\n"
          + "    {/select}\n"
          + "  {/msg}\n"
          + "{/template}\n";

  @Test
  public void testPlrselMsgsUsingSoySource() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(PLRSEL_TEST_FILE_CONTENT).parse().fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    assertThat(template.numChildren()).isEqualTo(2);

    // Execute the visitor.
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    new InsertMsgsVisitor(/* msgBundle= */ null, errorReporter).insertMsgs(template);

    assertThat(errorReporter.getErrors()).hasSize(2);
    assertThat(errorReporter.getErrors().get(0).toString())
        .contains(
            "JS code generation currently only supports plural/select messages when "
                + "shouldGenerateGoogMsgDefs is true.");
    assertThat(errorReporter.getErrors().get(1).toString())
        .contains(
            "JS code generation currently only supports plural/select messages when "
                + "shouldGenerateGoogMsgDefs is true.");
  }

  // -----------------------------------------------------------------------------------------------
  // Test messages with fallback.

  private static final String FALLBACK_TEST_FILE_CONTENT =
      ""
          + "{namespace boo}\n"
          + "\n"
          + "/** Test template. */\n"
          + "{template .foo}\n"
          + "  {msg desc=\"\"}\n"
          + // no trans + no trans
          "    noTrans1\n"
          + "  {fallbackmsg desc=\"\"}\n"
          + "    noTrans2\n"
          + "  {/msg}\n"
          + "  {msg desc=\"\"}\n"
          + // trans + no trans
          "    trans1\n"
          + "  {fallbackmsg desc=\"\"}\n"
          + "    noTrans2\n"
          + "  {/msg}\n"
          + "  {msg desc=\"\"}\n"
          + // no trans + trans
          "    noTrans1\n"
          + "  {fallbackmsg desc=\"\"}\n"
          + "    trans2\n"
          + "  {/msg}\n"
          + "  {msg desc=\"\"}\n"
          + // trans + trans
          "    trans1\n"
          + "  {fallbackmsg desc=\"\"}\n"
          + "    trans2\n"
          + "  {/msg}\n"
          + "{/template}\n";

  @Test
  public void testFallbackMsgsUsingSoySource() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(FALLBACK_TEST_FILE_CONTENT).parse().fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertThat(template.numChildren()).isEqualTo(4);
    assertThat(((MsgFallbackGroupNode) template.getChild(0)).numChildren()).isEqualTo(2);
    assertThat(((MsgFallbackGroupNode) template.getChild(1)).numChildren()).isEqualTo(2);
    assertThat(((MsgFallbackGroupNode) template.getChild(2)).numChildren()).isEqualTo(2);
    assertThat(((MsgFallbackGroupNode) template.getChild(3)).numChildren()).isEqualTo(2);

    // Execute the visitor.
    new InsertMsgsVisitor(/* msgBundle= */ null, FAIL).insertMsgs(template);

    // After.
    assertThat(template.numChildren()).isEqualTo(4);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("noTrans1");
    assertThat(((RawTextNode) template.getChild(1)).getRawText()).isEqualTo("trans1");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("noTrans1");
    assertThat(((RawTextNode) template.getChild(3)).getRawText()).isEqualTo("trans1");
  }

  @Test
  public void testFallbackMsgsUsingMsgBundle() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(FALLBACK_TEST_FILE_CONTENT).parse().fileSet();
    TemplateNode template = (TemplateNode) SharedTestUtils.getNode(soyTree);

    // Before.
    assertThat(template.numChildren()).isEqualTo(4);
    assertThat(((MsgFallbackGroupNode) template.getChild(0)).numChildren()).isEqualTo(2);
    assertThat(((MsgFallbackGroupNode) template.getChild(1)).numChildren()).isEqualTo(2);
    assertThat(((MsgFallbackGroupNode) template.getChild(2)).numChildren()).isEqualTo(2);
    assertThat(((MsgFallbackGroupNode) template.getChild(3)).numChildren()).isEqualTo(2);

    // Build the translated message bundle.
    List<SoyMsg> translatedMsgs = Lists.newArrayList();
    MsgNode trans1FirstInstance = ((MsgFallbackGroupNode) template.getChild(1)).getChild(0);
    translatedMsgs.add(
        SoyMsg.builder()
            .setId(MsgUtils.computeMsgIdForDualFormat(trans1FirstInstance))
            .setLocaleString("x-zz")
            .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("ztrans1")))
            .build());
    MsgNode trans2FirstInstance = ((MsgFallbackGroupNode) template.getChild(2)).getChild(1);
    translatedMsgs.add(
        SoyMsg.builder()
            .setId(MsgUtils.computeMsgIdForDualFormat(trans2FirstInstance))
            .setLocaleString("x-zz")
            .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("ztrans2")))
            .build());
    SoyMsgBundle msgBundle = new SoyMsgBundleImpl("x-zz", translatedMsgs);

    // Execute the visitor.
    new InsertMsgsVisitor(msgBundle, FAIL).insertMsgs(template);

    // After.
    assertThat(template.numChildren()).isEqualTo(4);
    assertThat(((RawTextNode) template.getChild(0)).getRawText()).isEqualTo("noTrans1");
    assertThat(((RawTextNode) template.getChild(1)).getRawText()).isEqualTo("ztrans1");
    assertThat(((RawTextNode) template.getChild(2)).getRawText()).isEqualTo("ztrans2");
    assertThat(((RawTextNode) template.getChild(3)).getRawText()).isEqualTo("ztrans1");
  }
}
