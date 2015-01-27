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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.SoyMsg;
import com.google.template.soy.msgs.restricted.SoyMsgBundleImpl;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import com.google.template.soy.msgs.restricted.SoyMsgSelectPart;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.TemplateNode;

import junit.framework.TestCase;

import java.util.List;

/**
 * Unit tests for InsertMsgsVisitor.
 *
 */
public class InsertMsgsVisitorTest extends TestCase {


  // -----------------------------------------------------------------------------------------------
  // Test basic messages.


  private static final String BASIC_TEST_FILE_CONTENT = "" +
      "{namespace boo autoescape=\"deprecated-noncontextual\"}\n" +
      "\n" +
      "/** Test template. */\n" +
      "{template name=\".foo\"}\n" +
      "  {$boo}scary{sp}\n" +
      "  {msg desc=\"Test.\"}\n" +
      "    random{$foo}\n" +
      "    <a href=\"{$goo}\">slimy</a>\n" +
      "  {/msg}{sp}\n" +
      "  {msg desc=\"\"}dairy{$moo}{/msg}\n" +
      "{/template}\n";


  public void testBasicMsgsUsingSoySource() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(
            SharedTestUtils.parseSoyFiles(BASIC_TEST_FILE_CONTENT));

    // Before.
    assertEquals(5, template.numChildren());
    MsgNode msg = ((MsgFallbackGroupNode) template.getChild(2)).getChild(0);
    assertEquals(5, msg.numChildren());
    MsgHtmlTagNode msgHtmlTag2 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(2)).getChild(0);
    assertEquals(3, msgHtmlTag2.numChildren());
    MsgHtmlTagNode msgHtmlTag4 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(4)).getChild(0);
    assertEquals(1, msgHtmlTag4.numChildren());
    assertEquals("</a>", ((RawTextNode) msgHtmlTag4.getChild(0)).getRawText());

    // Execute the visitor.
    (new InsertMsgsVisitor(null, true)).exec(template);

    // After.
    assertEquals(12, template.numChildren());
    assertEquals("$boo", ((PrintNode) template.getChild(0)).getExprText());
    assertEquals("scary ", ((RawTextNode) template.getChild(1)).getRawText());
    assertEquals("random", ((RawTextNode) template.getChild(2)).getRawText());
    assertEquals("$foo", ((PrintNode) template.getChild(3)).getExprText());
    assertEquals("<a href=\"", ((RawTextNode) template.getChild(4)).getRawText());
    assertEquals("$goo", ((PrintNode) template.getChild(5)).getExprText());
    assertEquals("\">", ((RawTextNode) template.getChild(6)).getRawText());
    assertEquals("slimy", ((RawTextNode) template.getChild(7)).getRawText());
    assertEquals("</a>", ((RawTextNode) template.getChild(8)).getRawText());
    assertEquals(" ", ((RawTextNode) template.getChild(9)).getRawText());
    assertEquals("dairy", ((RawTextNode) template.getChild(10)).getRawText());
    assertEquals("$moo", ((PrintNode) template.getChild(11)).getExprText());
  }


  public void testBasicMsgsUsingMsgBundle() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(
            SharedTestUtils.parseSoyFiles(BASIC_TEST_FILE_CONTENT));

    // Before.
    assertEquals(5, template.numChildren());
    MsgNode msg = ((MsgFallbackGroupNode) template.getChild(2)).getChild(0);
    assertEquals(5, msg.numChildren());
    MsgHtmlTagNode msgHtmlTag2 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(2)).getChild(0);
    assertEquals(3, msgHtmlTag2.numChildren());
    MsgHtmlTagNode msgHtmlTag4 =
        (MsgHtmlTagNode) ((MsgPlaceholderNode) msg.getChild(4)).getChild(0);
    assertEquals(1, msgHtmlTag4.numChildren());
    assertEquals("</a>", ((RawTextNode) msgHtmlTag4.getChild(0)).getRawText());

    // Build the translated message bundle.
    List<SoyMsg> translatedMsgs = Lists.newArrayList();
    // Original (en): random{{FOO}}{{START_LINK}}slimy{{END_LINK}}
    // Translation (x-zz): {{START_LINK}}zslimy{{END_LINK}}{{FOO}}zrandom
    translatedMsgs.add(new SoyMsg(
        MsgUtils.computeMsgIdForDualFormat(msg), "x-zz", null, null, false, null, null,
        ImmutableList.<SoyMsgPart>of(
            new SoyMsgPlaceholderPart("START_LINK"),
            SoyMsgRawTextPart.of("zslimy"),
            new SoyMsgPlaceholderPart("END_LINK"),
            new SoyMsgPlaceholderPart("FOO"),
            SoyMsgRawTextPart.of("zrandom"))));
    // Note: This bundle has no translation for the message "dairy{$moo}".
    SoyMsgBundle msgBundle = new SoyMsgBundleImpl("x-zz", translatedMsgs);

    // Execute the visitor.
    (new InsertMsgsVisitor(msgBundle, true)).exec(template);

    // After.
    assertEquals(12, template.numChildren());
    assertEquals("$boo", ((PrintNode) template.getChild(0)).getExprText());
    assertEquals("scary ", ((RawTextNode) template.getChild(1)).getRawText());
    assertEquals("<a href=\"", ((RawTextNode) template.getChild(2)).getRawText());
    assertEquals("$goo", ((PrintNode) template.getChild(3)).getExprText());
    assertEquals("\">", ((RawTextNode) template.getChild(4)).getRawText());
    assertEquals("zslimy", ((RawTextNode) template.getChild(5)).getRawText());
    assertEquals("</a>", ((RawTextNode) template.getChild(6)).getRawText());
    assertEquals("$foo", ((PrintNode) template.getChild(7)).getExprText());
    assertEquals("zrandom", ((RawTextNode) template.getChild(8)).getRawText());
    assertEquals(" ", ((RawTextNode) template.getChild(9)).getRawText());
    assertEquals("dairy", ((RawTextNode) template.getChild(10)).getRawText());
    assertEquals("$moo", ((PrintNode) template.getChild(11)).getExprText());
  }


  // -----------------------------------------------------------------------------------------------
  // Test plural/select messages.


  private static final String PLRSEL_TEST_FILE_CONTENT = "" +
      "{namespace boo autoescape=\"deprecated-noncontextual\"}\n" +
      "\n" +
      "/** Test template with plural/select msgs. */\n" +
      "{template name=\".foo\"}\n" +
      "  {msg desc=\"Plural message.\"}\n" +
      "    {plural $numFriends}\n" +
      "      {case 1}Added a friend to your circle.\n" +
      "      {default}Added {$numFriends} friends to your circle.\n" +
      "    {/plural}\n" +
      "  {/msg}\n" +
      "  {msg desc=\"Select message.\"}\n" +
      "    {select $gender}\n" +
      "      {case 'female'}She is in your circles.\n" +
      "      {default}He is in your circles.\n" +
      "    {/select}\n" +
      "  {/msg}\n" +
      "{/template}\n";


  public void testPlrselMsgsUsingSoySource() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(
            SharedTestUtils.parseSoyFiles(PLRSEL_TEST_FILE_CONTENT));

    // Before.
    assertEquals(2, template.numChildren());
    MsgNode pluralMsg = ((MsgFallbackGroupNode) template.getChild(0)).getChild(0);
    MsgNode selectMsg = ((MsgFallbackGroupNode) template.getChild(1)).getChild(0);
    String beforePluralMsgSourceStr = pluralMsg.toSourceString();
    String beforeSelectMsgSourceStr = selectMsg.toSourceString();

    // Execute the visitor.
    (new InsertMsgsVisitor(null, true)).exec(template);

    // After. (Current implementation does not modify/replace plural/select messages.)
    assertEquals(2, template.numChildren());
    assertSame(pluralMsg, ((MsgFallbackGroupNode) template.getChild(0)).getChild(0));
    assertSame(selectMsg, ((MsgFallbackGroupNode) template.getChild(1)).getChild(0));
    assertEquals(beforePluralMsgSourceStr, pluralMsg.toSourceString());
    assertEquals(beforeSelectMsgSourceStr, selectMsg.toSourceString());
  }


  public void testPlrselMsgsUsingMsgBundle() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(
            SharedTestUtils.parseSoyFiles(PLRSEL_TEST_FILE_CONTENT));

    // Before.
    assertEquals(2, template.numChildren());
    MsgNode pluralMsg = ((MsgFallbackGroupNode) template.getChild(0)).getChild(0);
    MsgNode selectMsg = ((MsgFallbackGroupNode) template.getChild(1)).getChild(0);
    String beforePluralMsgSourceStr = pluralMsg.toSourceString();
    String beforeSelectMsgSourceStr = selectMsg.toSourceString();

    // Build the translated message bundle.
    List<SoyMsg> translatedMsgs = Lists.newArrayList();
    // Original (en):
    //   NUM_FRIENDS_1 = 1: Added a friend to your circle.
    //   NUM_FRIENDS_1 = other: Added {{NUM_FRIENDS_2}} friends to your circle.
    // Translation (x-zz):
    //   NUM_FRIENDS_1 = 1: Zcircle zyour zto zfriend za zadded.
    //   NUM_FRIENDS_1 = other: Zcircle zyour zto zfriends {{NUM_FRIENDS_2}} zadded.
    translatedMsgs.add(new SoyMsg(
        MsgUtils.computeMsgIdForDualFormat(pluralMsg), "x-zz", null, null, false, null, null,
        ImmutableList.<SoyMsgPart>of(
            new SoyMsgPluralPart(
                "NUM_FRIENDS_1",
                0,
                ImmutableList.of(
                    Pair.of(
                        new SoyMsgPluralCaseSpec(1),
                        ImmutableList.<SoyMsgPart>of(
                            SoyMsgRawTextPart.of("Zcircle zyour zto zfriend za zadded."))),
                    Pair.of(
                        new SoyMsgPluralCaseSpec("other"),
                        ImmutableList.<SoyMsgPart>of(
                            SoyMsgRawTextPart.of("Zcircle zyour zto zfriends "),
                            new SoyMsgPlaceholderPart("NUM_FRIENDS_2"),
                            SoyMsgRawTextPart.of(" zadded."))))))));
    // Original (en):
    //   GENDER = 'female': She is in your circles.
    //   GENDER = other: He is in your circles.
    // Translation (x-zz):
    //   GENDER = 'female': Zcircles zyour zin zis zshe.
    //   GENDER = other: Zcircles zyour zin zis zhe.
    translatedMsgs.add(new SoyMsg(
        MsgUtils.computeMsgIdForDualFormat(selectMsg), "x-zz", null, null, false, null, null,
        ImmutableList.<SoyMsgPart>of(
            new SoyMsgSelectPart(
                "GENDER",
                ImmutableList.of(
                    Pair.of(
                        "female",
                        ImmutableList.<SoyMsgPart>of(
                            SoyMsgRawTextPart.of("Zcircles zyour zin zis zshe."))),
                    Pair.of(
                        (String) null,
                        ImmutableList.<SoyMsgPart>of(
                            SoyMsgRawTextPart.of("Zcircles zyour zin zis zhe."))))))));
    SoyMsgBundle msgBundle = new SoyMsgBundleImpl("x-zz", translatedMsgs);

    // Execute the visitor.
    (new InsertMsgsVisitor(msgBundle, true)).exec(template);

    // After. (Current implementation does not modify/replace plural/select messages.)
    assertEquals(2, template.numChildren());
    assertSame(pluralMsg, ((MsgFallbackGroupNode) template.getChild(0)).getChild(0));
    assertSame(selectMsg, ((MsgFallbackGroupNode) template.getChild(1)).getChild(0));
    assertEquals(beforePluralMsgSourceStr, pluralMsg.toSourceString());
    assertEquals(beforeSelectMsgSourceStr, selectMsg.toSourceString());
  }


  // -----------------------------------------------------------------------------------------------
  // Test messages with fallback.


  private static final String FALLBACK_TEST_FILE_CONTENT = "" +
      "{namespace boo autoescape=\"deprecated-noncontextual\"}\n" +
      "\n" +
      "/** Test template. */\n" +
      "{template name=\".foo\"}\n" +
      "  {msg desc=\"\"}\n" +  // no trans + no trans
      "    noTrans1\n" +
      "  {fallbackmsg desc=\"\"}\n" +
      "    noTrans2\n" +
      "  {/msg}\n" +
      "  {msg desc=\"\"}\n" +  // trans + no trans
      "    trans1\n" +
      "  {fallbackmsg desc=\"\"}\n" +
      "    noTrans2\n" +
      "  {/msg}\n" +
      "  {msg desc=\"\"}\n" +  // no trans + trans
      "    noTrans1\n" +
      "  {fallbackmsg desc=\"\"}\n" +
      "    trans2\n" +
      "  {/msg}\n" +
      "  {msg desc=\"\"}\n" +  // trans + trans
      "    trans1\n" +
      "  {fallbackmsg desc=\"\"}\n" +
      "    trans2\n" +
      "  {/msg}\n" +
      "  {msg desc=\"\"}\n" +  // trans + plursel
      "    trans1\n" +
      "  {fallbackmsg desc=\"\"}\n" +
      "    {select $gender}\n" +
      "      {case 'female'}She is in your circles.\n" +
      "      {default}He is in your circles.\n" +
      "    {/select}\n" +
      "  {/msg}\n" +
      "  {msg desc=\"\"}\n" +  // plrsel + trans
      "    {select $gender}\n" +
      "      {case 'female'}She is in your circles.\n" +
      "      {default}He is in your circles.\n" +
      "    {/select}\n" +
      "  {fallbackmsg desc=\"\"}\n" +
      "    trans2\n" +
      "  {/msg}\n" +
      "{/template}\n";


  public void testFallbackMsgsUsingSoySource() {

    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(
            SharedTestUtils.parseSoyFiles(FALLBACK_TEST_FILE_CONTENT));

    // Before.
    assertEquals(6, template.numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(0)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(1)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(2)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(3)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(4)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(5)).numChildren());

    // Execute the visitor.
    (new InsertMsgsVisitor(null, true)).exec(template);

    // After.
    assertEquals(6, template.numChildren());
    assertEquals("noTrans1", ((RawTextNode) template.getChild(0)).getRawText());
    assertEquals("trans1", ((RawTextNode) template.getChild(1)).getRawText());
    assertEquals("noTrans1", ((RawTextNode) template.getChild(2)).getRawText());
    assertEquals("trans1", ((RawTextNode) template.getChild(3)).getRawText());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(4)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(5)).numChildren());
  }


  public void testFallbackMsgsUsingMsgBundle() {


    TemplateNode template =
        (TemplateNode) SharedTestUtils.getNode(
            SharedTestUtils.parseSoyFiles(FALLBACK_TEST_FILE_CONTENT));

    // Before.
    assertEquals(6, template.numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(0)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(1)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(2)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(3)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(4)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(5)).numChildren());

    // Build the translated message bundle.
    List<SoyMsg> translatedMsgs = Lists.newArrayList();
    MsgNode trans1FirstInstance = ((MsgFallbackGroupNode) template.getChild(1)).getChild(0);
    translatedMsgs.add(new SoyMsg(
        MsgUtils.computeMsgIdForDualFormat(trans1FirstInstance), "x-zz",
        null, null, false, null, null,
        ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("ztrans1"))));
    MsgNode trans2FirstInstance = ((MsgFallbackGroupNode) template.getChild(2)).getChild(1);
    translatedMsgs.add(new SoyMsg(
        MsgUtils.computeMsgIdForDualFormat(trans2FirstInstance), "x-zz",
        null, null, false, null, null,
        ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("ztrans2"))));
    SoyMsgBundle msgBundle = new SoyMsgBundleImpl("x-zz", translatedMsgs);

    // Execute the visitor.
    (new InsertMsgsVisitor(msgBundle, true)).exec(template);

    // After.
    assertEquals(6, template.numChildren());
    assertEquals("noTrans1", ((RawTextNode) template.getChild(0)).getRawText());
    assertEquals("ztrans1", ((RawTextNode) template.getChild(1)).getRawText());
    assertEquals("ztrans2", ((RawTextNode) template.getChild(2)).getRawText());
    assertEquals("ztrans1", ((RawTextNode) template.getChild(3)).getRawText());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(4)).numChildren());
    assertEquals(2, ((MsgFallbackGroupNode) template.getChild(5)).numChildren());
  }

}
