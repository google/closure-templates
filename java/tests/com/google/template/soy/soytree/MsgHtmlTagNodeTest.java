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
import com.google.template.soy.soyparse.ErrorReporter.Checkpoint;
import com.google.template.soy.soyparse.TransitionalThrowingErrorReporter;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;

import junit.framework.TestCase;


/**
 * Unit tests for MsgHtmlTagNode.
 *
 */
public final class MsgHtmlTagNodeTest extends TestCase {


  public void testPlaceholderBold() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();

    MsgHtmlTagNode mhtn = new MsgHtmlTagNode.Builder(
        0, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<b>")), SourceLocation.UNKNOWN)
        .build(errorReporter);
    assertThat(mhtn.genBasePhName()).isEqualTo("START_BOLD");
    assertThat(mhtn.genSamenessKey()).isEqualTo(new MsgHtmlTagNode.Builder(
        4,
        ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<b>")),
        SourceLocation.UNKNOWN)
        .build(errorReporter)
        .genSamenessKey());
    assertThat(mhtn.genSamenessKey()).isNotEqualTo(new MsgHtmlTagNode.Builder(
        4,
        ImmutableList.<StandaloneNode>of(new RawTextNode(0, "</b>")),
        SourceLocation.UNKNOWN)
        .build(errorReporter)
        .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<b>");
    errorReporter.throwIfErrorsPresent();
  }

  public void testPlaceholderBreak() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();

    MsgHtmlTagNode mhtn = new MsgHtmlTagNode.Builder(
        0, ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<br />")), SourceLocation.UNKNOWN)
        .build(errorReporter);
    assertThat(mhtn.genBasePhName()).isEqualTo("BREAK");
    assertThat(mhtn.genSamenessKey()).isNotEqualTo(new MsgHtmlTagNode.Builder(
        4,
        ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<br/>")),
        SourceLocation.UNKNOWN)
        .build(errorReporter)
        .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<br />");
    errorReporter.throwIfErrorsPresent();
  }

  public void testPlaceholderDiv() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    MsgHtmlTagNode mhtn = new MsgHtmlTagNode.Builder(
        1,
        ImmutableList.<StandaloneNode>of(
            new RawTextNode(0, "<div class=\""),
            new PrintNode(0, true, "$cssClass", null),
            new RawTextNode(0, "\">")),
        SourceLocation.UNKNOWN)
        .build(errorReporter);
    assertThat(mhtn.genBasePhName()).isEqualTo("START_DIV");
    assertThat(mhtn.genSamenessKey()).isNotEqualTo(new MsgHtmlTagNode.Builder(
        2,
        ImmutableList.<StandaloneNode>of(
            new RawTextNode(0, "<div class=\""),
            new PrintNode(0, true, "$cssClass", null),
            new RawTextNode(0, "\">")),
        SourceLocation.UNKNOWN)
        .build(errorReporter)
        .genSamenessKey());
    assertThat(mhtn.toSourceString()).isEqualTo("<div class=\"{$cssClass}\">");
    errorReporter.throwIfErrorsPresent();
  }

  public void testUserSuppliedPlaceholderName() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    MsgHtmlTagNode mhtn = new MsgHtmlTagNode.Builder(
        1,
        ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<div phname=\"foo\" />")),
        SourceLocation.UNKNOWN)
        .build(errorReporter);
    assertThat(mhtn.getUserSuppliedPhName()).isEqualTo("foo");
    errorReporter.throwIfErrorsPresent();
  }

  public void testErrorNodeReturnedWhenPhNameAttrIsMalformed() {
    TransitionalThrowingErrorReporter errorReporter = new TransitionalThrowingErrorReporter();
    Checkpoint checkpoint = errorReporter.checkpoint();
    MsgHtmlTagNode mhtn = new MsgHtmlTagNode.Builder(
        1,
        ImmutableList.<StandaloneNode>of(new RawTextNode(0, "<div phname=\".+\" />")),
        SourceLocation.UNKNOWN)
        .build(errorReporter);
    assertThat(mhtn.getUserSuppliedPhName()).isNull();
    assertThat(errorReporter.errorsSince(checkpoint)).isTrue();
  }
}
