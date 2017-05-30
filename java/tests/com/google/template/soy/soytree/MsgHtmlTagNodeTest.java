/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for MsgHtmlTagNode.
 *
 */
@RunWith(JUnit4.class)
public final class MsgHtmlTagNodeTest {
  @Test
  public void testPlaceholderBold() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<b>");
    assertThat(mhtn.genBasePhName()).isEqualTo("START_BOLD");
    assertThat(mhtn.genSamenessKey()).isEqualTo(parseMsgHtmlTagNode("<b>").genSamenessKey());
    assertThat(mhtn.genSamenessKey()).isNotEqualTo(parseMsgHtmlTagNode("</b>").genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<b>");
  }

  @Test
  public void testPlaceholderBreak() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<br />");
    assertThat(mhtn.genBasePhName()).isEqualTo("BREAK");
    assertThat(mhtn.genSamenessKey()).isEqualTo(parseMsgHtmlTagNode("<br/>").genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<br/>");
  }

  @Test
  public void testPlaceholderDiv() {
    MsgHtmlTagNode mhtn =
        parseMsgHtmlTagNode("<div class=\"{$cssClass}\">", "{@param cssClass: string}");
    assertThat(mhtn.genBasePhName()).isEqualTo("START_DIV");
    // not equal to an identical tag due to the print node
    assertThat(mhtn.genSamenessKey())
        .isNotEqualTo(
            parseMsgHtmlTagNode("<div class=\"{$cssClass}\">", "{@param cssClass: string}")
                .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<div class=\"{$cssClass}\">");
  }

  @Test
  public void testUserSuppliedPlaceholderName() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<div phname=\"foo\" />");
    assertThat(mhtn.getUserSuppliedPhName()).isEqualTo("foo");
  }

  @Test
  public void testErrorNodeReturnedWhenPhNameAttrIsMalformed() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    parseMsgHtmlTagNode("<div phname=\".+\" />", errorReporter);
    assertThat(errorReporter.getErrorMessages())
        .contains("'phname' attribute is not a valid identifier.");
  }

  @Test
  public void testPlaceholderCustomTagNameWithHyphen() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<foo-bar>");
    assertThat(mhtn.genBasePhName()).isEqualTo("START_FOO_BAR");
    mhtn = parseMsgHtmlTagNode("<foo-bar />");
    assertThat(mhtn.genBasePhName()).isEqualTo("FOO_BAR");
  }

  @Test
  public void testAutomaticPlaceholderName() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<h2>");
    assertThat(mhtn.genBasePhName()).isEqualTo("START_H2");
    mhtn = parseMsgHtmlTagNode("</h2>");
    assertThat(mhtn.genBasePhName()).isEqualTo("END_H2");
  }

  private static MsgHtmlTagNode parseMsgHtmlTagNode(String htmlTag, String... params) {
    return parseMsgHtmlTagNode(htmlTag, ExplodingErrorReporter.get(), params);
  }

  private static MsgHtmlTagNode parseMsgHtmlTagNode(
      String htmlTag, ErrorReporter errorReporter, String... params) {
    Checkpoint cp = errorReporter.checkpoint();
    ParseResult parse =
        SoyFileSetParserBuilder.forFileContents(
                Joiner.on('\n')
                    .join(
                        "{namespace ns}",
                        "",
                        "{template .t}",
                        Joiner.on('\n').join(params),
                        "{msg desc=\"...\"}",
                        htmlTag,
                        "{/msg}",
                        "{/template}"))
            .errorReporter(errorReporter)
            .parse();
    if (errorReporter.errorsSince(cp)) {
      return null;
    }
    MsgFallbackGroupNode child =
        (MsgFallbackGroupNode) parse.fileSet().getChild(0).getChild(0).getChild(0);
    return (MsgHtmlTagNode) ((MsgPlaceholderNode) child.getChild(0).getChild(0)).getChild(0);
  }
}
