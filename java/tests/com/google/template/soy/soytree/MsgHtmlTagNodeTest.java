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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for MsgHtmlTagNode.
 *
 */
@RunWith(JUnit4.class)
public final class MsgHtmlTagNodeTest {

  private static final SourceLocation X = SourceLocation.UNKNOWN;
  private static final ErrorReporter FAIL = ExplodingErrorReporter.get();

  @Test
  public void testPlaceholderBold() {
    MsgHtmlTagNode mhtn =
        new MsgHtmlTagNode.Builder(
                0, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<b>", X)), X)
            .build(FAIL);
    assertThat(mhtn.genBasePhName()).isEqualTo("START_BOLD");
    assertThat(mhtn.genSamenessKey())
        .isEqualTo(
            new MsgHtmlTagNode.Builder(
                    4, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<b>", X)), X)
                .build(FAIL)
                .genSamenessKey());
    assertThat(mhtn.genSamenessKey())
        .isNotEqualTo(
            new MsgHtmlTagNode.Builder(
                    4, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "</b>", X)), X)
                .build(FAIL)
                .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<b>");
  }

  @Test
  public void testPlaceholderBreak() {
    MsgHtmlTagNode mhtn =
        new MsgHtmlTagNode.Builder(
                0, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<br />", X)), X)
            .build(FAIL);
    assertThat(mhtn.genBasePhName()).isEqualTo("BREAK");
    assertThat(mhtn.genSamenessKey())
        .isNotEqualTo(
            new MsgHtmlTagNode.Builder(
                    4, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<br/>", X)), X)
                .build(FAIL)
                .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<br />");
  }

  @Test
  public void testPlaceholderDiv() {
    MsgHtmlTagNode mhtn =
        new MsgHtmlTagNode.Builder(
                1,
                ImmutableList.<StandaloneNode>of(
                    new RawTextNode(0, "<div class=\"", X),
                    new PrintNode.Builder(0, true /* isImplicit */, X)
                        .exprText("$cssClass")
                        .build(SoyParsingContext.exploding()),
                    new RawTextNode(0, "\">", X)),
                X)
            .build(FAIL);
    assertThat(mhtn.genBasePhName()).isEqualTo("START_DIV");
    assertThat(mhtn.genSamenessKey())
        .isNotEqualTo(
            new MsgHtmlTagNode.Builder(
                    2,
                    ImmutableList.<StandaloneNode>of(
                        new RawTextNode(0, "<div class=\"", X),
                        new PrintNode.Builder(0, true /* isImplicit */, X)
                            .exprText("$cssClass")
                            .build(SoyParsingContext.exploding()),
                        new RawTextNode(0, "\">", X)),
                    X)
                .build(FAIL)
                .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<div class=\"{$cssClass}\">");
  }

  @Test
  public void testUserSuppliedPlaceholderName() {
    MsgHtmlTagNode mhtn =
        new MsgHtmlTagNode.Builder(
                1,
                ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<div phname=\"foo\" />", X)),
                X)
            .build(FAIL);
    assertThat(mhtn.getUserSuppliedPhName()).isEqualTo("foo");
  }

  @Test
  public void testErrorNodeReturnedWhenPhNameAttrIsMalformed() {
    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    MsgHtmlTagNode mhtn =
        new MsgHtmlTagNode.Builder(
                1,
                ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<div phname=\".+\" />", X)),
                X)
            .build(errorReporter);
    assertThat(mhtn.getUserSuppliedPhName()).isNull();
    assertThat(errorReporter.getErrorMessages()).isNotEmpty();
  }
}
