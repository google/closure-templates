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

package com.google.template.soy.jssrc.internal;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.MsgHtmlTagNode;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.jssrc.GoogMsgDefNode;
import com.google.template.soy.soytree.jssrc.GoogMsgRefNode;

import junit.framework.TestCase;

/**
 * Unit tests for ReplaceMsgsWithGoogMsgsVisitor.
 *
 */
public class ReplaceMsgsWithGoogMsgsVisitorTest extends TestCase {


  public void testReplaceMsgsWithGoogMsgsVisitor() {

    String soyCode = "" +
        "{msg desc=\"Tells the user to click a link.\"}\n" +
        "  Hello {$userName}, please click <a href=\"{$url}\">here</a>.\n" +
        "{/msg}\n" +
        "{msg meaning=\"blah\" desc=\"A span with generated id.\" hidden=\"true\"}\n" +
        "  <span id=\"{for $i in range(3)}{$i}{/for}\">\n" +
        "{/msg}\n";

    SoyFileSetNode soyTree = JsSrcTestUtils.parseSoyCode(soyCode);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    GoogMsgDefNode gmd0 = (GoogMsgDefNode) template.getChild(0);
    assertTrue(gmd0.getRenderedGoogMsgVarName().matches("msg_s[0-9]+"));
    assertEquals(gmd0.getRenderedGoogMsgVarName(), gmd0.getVarName());
    MsgNode m0 = gmd0.getChild(0);
    assertEquals(null, m0.getMeaning());
    assertEquals("Tells the user to click a link.", m0.getDesc());
    assertEquals(false, m0.isHidden());

    assertEquals(7, m0.numChildren());
    assertEquals("Hello ", ((RawTextNode) m0.getChild(0)).getRawText());
    MsgPlaceholderNode gm0p1 = (MsgPlaceholderNode) m0.getChild(1);
    assertEquals(gm0p1, m0.getRepPlaceholderNode("USER_NAME"));
    assertEquals("USER_NAME", m0.getPlaceholderName(gm0p1));
    PrintNode gm0pc1 = (PrintNode) gm0p1.getChild(0);
    assertEquals("$userName", gm0pc1.getExprText());
    assertEquals(", please click ", ((RawTextNode) m0.getChild(2)).getRawText());
    MsgPlaceholderNode gm0p3 = (MsgPlaceholderNode) m0.getChild(3);
    assertEquals(gm0p3, m0.getRepPlaceholderNode("START_LINK"));
    assertEquals("START_LINK", m0.getPlaceholderName(gm0p3));
    MsgHtmlTagNode gm0pc3 = (MsgHtmlTagNode) gm0p3.getChild(0);
    assertEquals(3, gm0pc3.numChildren());
    assertEquals("$url", ((PrintNode) gm0pc3.getChild(1)).getExprText());
    assertEquals("here", ((RawTextNode) m0.getChild(4)).getRawText());
    MsgPlaceholderNode gm0p5 = (MsgPlaceholderNode) m0.getChild(5);
    assertEquals(gm0p5, m0.getRepPlaceholderNode("END_LINK"));
    assertEquals("END_LINK", m0.getPlaceholderName(gm0p5));
    MsgHtmlTagNode gm0pc5 = (MsgHtmlTagNode) gm0p5.getChild(0);
    assertEquals(1, gm0pc5.numChildren());
    assertEquals("</a>", ((RawTextNode) gm0pc5.getChild(0)).getRawText());
    assertEquals(".", ((RawTextNode) m0.getChild(6)).getRawText());

    GoogMsgRefNode gmr1 = (GoogMsgRefNode) template.getChild(1);
    assertEquals(gmd0.getRenderedGoogMsgVarName(), gmr1.getRenderedGoogMsgVarName());

    GoogMsgDefNode gmd2 = (GoogMsgDefNode) template.getChild(2);
    assertTrue(gmd2.getRenderedGoogMsgVarName().matches("msg_s[0-9]+"));
    assertFalse(gmd0.getRenderedGoogMsgVarName().equals(gmd2.getRenderedGoogMsgVarName()));
    assertEquals(gmd2.getRenderedGoogMsgVarName(), gmd2.getVarName());
    MsgNode m2 = gmd2.getChild(0);
    assertEquals("blah", m2.getMeaning());
    assertEquals("A span with generated id.", m2.getDesc());
    assertEquals(true, m2.isHidden());

    assertEquals(1, m2.numChildren());
    MsgPlaceholderNode gm2p0 = (MsgPlaceholderNode) m2.getChild(0);
    assertEquals(gm2p0, m2.getRepPlaceholderNode("START_SPAN"));
    assertEquals("START_SPAN", m2.getPlaceholderName(gm2p0));
    MsgHtmlTagNode gm2pc0 = (MsgHtmlTagNode) gm2p0.getChild(0);
    assertEquals(3, gm2pc0.numChildren());
    assertTrue(gm2pc0.getChild(1) instanceof ForNode);

    GoogMsgRefNode gmr3 = (GoogMsgRefNode) template.getChild(3);
    assertEquals(gmd2.getRenderedGoogMsgVarName(), gmr3.getRenderedGoogMsgVarName());
  }


  public void testDisallowsIcuEscapingInRawText() throws Exception {

    String soyCode = "" +
        "{msg genders=\"$gender\" desc=\"\"}\n" +
        "  Gender is '{$gender}'.\n" +
        "{/msg}\n";

    try {
      JsSrcTestUtils.parseSoyCode(soyCode);
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains(
          "Apologies, Soy currently does not support a single quote character at the end of a" +
              " text part in plural/gender source msgs (including immediately preceding an HTML" +
              " tag or Soy tag)."));
    }
  }

}
