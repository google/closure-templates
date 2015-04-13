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

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
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
public final class ReplaceMsgsWithGoogMsgsVisitorTest extends TestCase {

  public void testReplaceMsgsWithGoogMsgsVisitor() {

    String soyCode = "" +
        "{msg desc=\"Tells the user to click a link.\"}\n" +
        "  Hello {$userName}, please click <a href=\"{$url}\">here</a>.\n" +
        "{/msg}\n" +
        "{msg meaning=\"blah\" desc=\"A span with generated id.\" hidden=\"true\"}\n" +
        "  <span id=\"{for $i in range(3)}{$i}{/for}\">\n" +
        "{/msg}\n";

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(boom)
        .parse();
    new ReplaceMsgsWithGoogMsgsVisitor(boom).exec(soyTree);
    TemplateNode template = soyTree.getChild(0).getChild(0);

    GoogMsgDefNode gmd0 = (GoogMsgDefNode) template.getChild(0);
    assertThat(gmd0.getRenderedGoogMsgVarName()).matches("msg_s[0-9]+");
    assertThat(gmd0.getVarName()).isEqualTo(gmd0.getRenderedGoogMsgVarName());
    MsgNode m0 = gmd0.getChild(0);
    assertThat(m0.getMeaning()).isNull();
    assertThat(m0.getDesc()).isEqualTo("Tells the user to click a link.");
    assertThat(m0.isHidden()).isFalse();

    assertThat(m0.numChildren()).isEqualTo(7);
    assertThat(((RawTextNode) m0.getChild(0)).getRawText()).isEqualTo("Hello ");
    MsgPlaceholderNode gm0p1 = (MsgPlaceholderNode) m0.getChild(1);
    assertThat(m0.getRepPlaceholderNode("USER_NAME")).isEqualTo(gm0p1);
    assertThat(m0.getPlaceholderName(gm0p1)).isEqualTo("USER_NAME");
    PrintNode gm0pc1 = (PrintNode) gm0p1.getChild(0);
    assertThat(gm0pc1.getExprText()).isEqualTo("$userName");
    assertThat(((RawTextNode) m0.getChild(2)).getRawText()).isEqualTo(", please click ");
    MsgPlaceholderNode gm0p3 = (MsgPlaceholderNode) m0.getChild(3);
    assertThat(m0.getRepPlaceholderNode("START_LINK")).isEqualTo(gm0p3);
    assertThat(m0.getPlaceholderName(gm0p3)).isEqualTo("START_LINK");
    MsgHtmlTagNode gm0pc3 = (MsgHtmlTagNode) gm0p3.getChild(0);
    assertThat(gm0pc3.numChildren()).isEqualTo(3);
    assertThat(((PrintNode) gm0pc3.getChild(1)).getExprText()).isEqualTo("$url");
    assertThat(((RawTextNode) m0.getChild(4)).getRawText()).isEqualTo("here");
    MsgPlaceholderNode gm0p5 = (MsgPlaceholderNode) m0.getChild(5);
    assertThat(m0.getRepPlaceholderNode("END_LINK")).isEqualTo(gm0p5);
    assertThat(m0.getPlaceholderName(gm0p5)).isEqualTo("END_LINK");
    MsgHtmlTagNode gm0pc5 = (MsgHtmlTagNode) gm0p5.getChild(0);
    assertThat(gm0pc5.numChildren()).isEqualTo(1);
    assertThat(((RawTextNode) gm0pc5.getChild(0)).getRawText()).isEqualTo("</a>");
    assertThat(((RawTextNode) m0.getChild(6)).getRawText()).isEqualTo(".");

    GoogMsgRefNode gmr1 = (GoogMsgRefNode) template.getChild(1);
    assertThat(gmr1.getRenderedGoogMsgVarName()).isEqualTo(gmd0.getRenderedGoogMsgVarName());

    GoogMsgDefNode gmd2 = (GoogMsgDefNode) template.getChild(2);
    assertThat(gmd2.getRenderedGoogMsgVarName()).matches("msg_s[0-9]+");
    assertThat(gmd0.getRenderedGoogMsgVarName().equals(gmd2.getRenderedGoogMsgVarName())).isFalse();
    assertThat(gmd2.getVarName()).isEqualTo(gmd2.getRenderedGoogMsgVarName());
    MsgNode m2 = gmd2.getChild(0);
    assertThat(m2.getMeaning()).isEqualTo("blah");
    assertThat(m2.getDesc()).isEqualTo("A span with generated id.");
    assertThat(m2.isHidden()).isTrue();

    assertThat(m2.numChildren()).isEqualTo(1);
    MsgPlaceholderNode gm2p0 = (MsgPlaceholderNode) m2.getChild(0);
    assertThat(m2.getRepPlaceholderNode("START_SPAN")).isEqualTo(gm2p0);
    assertThat(m2.getPlaceholderName(gm2p0)).isEqualTo("START_SPAN");
    MsgHtmlTagNode gm2pc0 = (MsgHtmlTagNode) gm2p0.getChild(0);
    assertThat(gm2pc0.numChildren()).isEqualTo(3);
    assertThat(gm2pc0.getChild(1)).isInstanceOf(ForNode.class);

    GoogMsgRefNode gmr3 = (GoogMsgRefNode) template.getChild(3);
    assertThat(gmr3.getRenderedGoogMsgVarName()).isEqualTo(gmd2.getRenderedGoogMsgVarName());
  }


  public void testDisallowsIcuEscapingInRawText() throws Exception {

    String soyCode = "" +
        "{msg genders=\"$gender\" desc=\"\"}\n" +
        "  Gender is '{$gender}'.\n" +
        "{/msg}\n";

    try {
      SoyFileSetParserBuilder.forTemplateContents(soyCode).parse();
    } catch (SoySyntaxException sse) {
      assertThat(sse.getMessage())
          .contains(
              "Apologies, Soy currently does not support a single quote character at the end of a"
              + " text part in plural/gender source msgs (including immediately preceding an HTML"
              + " tag or Soy tag).");
    }
  }

}
