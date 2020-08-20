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
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Optional;
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
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("START_BOLD"));
    assertThat(mhtn.genSamenessKey()).isEqualTo(parseMsgHtmlTagNode("<b>").genSamenessKey());
    assertThat(mhtn.genSamenessKey()).isNotEqualTo(parseMsgHtmlTagNode("</b>").genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<b>");
  }

  @Test
  public void testPlaceholderBreak() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<br />");
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("BREAK"));
    assertThat(mhtn.genSamenessKey()).isEqualTo(parseMsgHtmlTagNode("<br/>").genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<br/>");
  }

  @Test
  public void testPlaceholderDiv() {
    MsgHtmlTagNode mhtn =
        parseMsgHtmlTagNode("<div class=\"{$cssClass}\">", "{@param cssClass: string}");
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("START_DIV"));
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
    assertThat(mhtn.getPlaceholder())
        .isEqualTo(
            MessagePlaceholder.createWithUserSuppliedName(
                "FOO", "foo", new SourceLocation(SoyFileSetParserBuilder.FILE_PATH, 6, 14, 6, 16)));
  }

  @Test
  public void testUserSuppliedPlaceholderExample() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<div phname=\"foo\" phex=\"example\"/>");
    assertThat(mhtn.getPlaceholder())
        .isEqualTo(
            MessagePlaceholder.createWithUserSuppliedName(
                "FOO",
                "foo",
                new SourceLocation(SoyFileSetParserBuilder.FILE_PATH, 6, 14, 6, 16),
                Optional.of("example")));
  }

  @Test
  public void testErrorNodeReturnedWhenPhNameAttrIsMalformed() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    parseMsgHtmlTagNode("<div phname=\".+\" />", errorReporter);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("'phname' is not a valid identifier.");
  }

  @Test
  public void testPlaceholderCustomTagNameWithHyphen() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<foo-bar>");
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("START_FOO_BAR"));
    mhtn = parseMsgHtmlTagNode("<foo-bar />");
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("FOO_BAR"));
  }

  @Test
  public void testAutomaticPlaceholderName() {
    MsgHtmlTagNode mhtn = parseMsgHtmlTagNode("<h2>");
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("START_H2"));
    mhtn = parseMsgHtmlTagNode("</h2>");
    assertThat(mhtn.getPlaceholder()).isEqualTo(MessagePlaceholder.create("END_H2"));
  }

  private static MsgHtmlTagNode parseMsgHtmlTagNode(String htmlTag, String... params) {
    return parseMsgHtmlTagNode(htmlTag, ErrorReporter.exploding(), params);
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
                        "{template .t stricthtml=\"false\"}",
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
        (MsgFallbackGroupNode) ((TemplateNode) parse.fileSet().getChild(0).getChild(0)).getChild(0);
    return (MsgHtmlTagNode) ((MsgPlaceholderNode) child.getChild(0).getChild(0)).getChild(0);
  }
}
