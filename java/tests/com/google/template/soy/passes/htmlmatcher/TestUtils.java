/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.passes.htmlmatcher;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherTagNode.TagKind;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode.TagExistence;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.SwitchCaseNode;

/** Utility functions for HTML Matcher Graph tests. */
public final class TestUtils {
  private static final IncrementingIdGenerator idGenerator = new IncrementingIdGenerator();

  public static HtmlOpenTagNode soyHtmlOpenTagNode() {
    return new HtmlOpenTagNode(
        idGenerator.genId(),
        new RawTextNode(idGenerator.genId(), "div", SourceLocation.UNKNOWN),
        SourceLocation.UNKNOWN,
        /** selfClosing */
        false,
        TagExistence.IN_TEMPLATE);
  }

  public static HtmlCloseTagNode soyHtmlCloseTagNode() {
    return new HtmlCloseTagNode(
        idGenerator.genId(),
        new RawTextNode(idGenerator.genId(), "div", SourceLocation.UNKNOWN),
        SourceLocation.UNKNOWN,
        TagExistence.IN_TEMPLATE);
  }

  public static HtmlMatcherTagNode htmlMatcherOpenTagNode(HtmlOpenTagNode soyNode) {
    return new HtmlMatcherTagNode(soyNode);
  }

  public static HtmlMatcherTagNode htmlMatcherCloseTagNode(HtmlCloseTagNode soyNode) {
    return new HtmlMatcherTagNode(soyNode);
  }

  public static ExprNode soyExprNode(String exprText) {
    return SoyFileParser.parseExpression(exprText, ErrorReporter.exploding());
  }

  public static IfCondNode soyIfCondNode(String exprText) {
    IfCondNode soyNode =
        new IfCondNode(
            idGenerator.genId(),
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            "if",
            soyExprNode(exprText));
    IfNode parentIfNode = new IfNode(idGenerator.genId(), SourceLocation.UNKNOWN);
    parentIfNode.addChild(soyNode);
    return soyNode;
  }

  public static IfCondNode soyIfElseCondNode(String exprText) {
    IfCondNode soyNode =
        new IfCondNode(
            idGenerator.genId(),
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            "elseif",
            soyExprNode(exprText));
    IfNode parentIfNode = new IfNode(idGenerator.genId(), SourceLocation.UNKNOWN);
    parentIfNode.addChild(soyNode);
    return soyNode;
  }

  public static SwitchCaseNode soySwitchCaseNode(String exprText) {
    SwitchCaseNode soyNode =
        new SwitchCaseNode(
            idGenerator.genId(),
            SourceLocation.UNKNOWN,
            SourceLocation.UNKNOWN,
            ImmutableList.of(soyExprNode(exprText)));
    IfNode parentIfNode = new IfNode(idGenerator.genId(), SourceLocation.UNKNOWN);
    parentIfNode.addChild(soyNode);
    return soyNode;
  }

  public static void assertNodeIsOpenTagWithName(HtmlMatcherGraphNode node, String tagName) {
    assertThat(node).isInstanceOf(HtmlMatcherTagNode.class);
    assertThat(((HtmlMatcherTagNode) node).getTagKind()).isEqualTo(TagKind.OPEN_TAG);

    SoyNode soyNode = node.getSoyNode().get();

    assertThat(soyNode.getKind()).isEqualTo(Kind.HTML_OPEN_TAG_NODE);
    assertThat(((HtmlOpenTagNode) soyNode).getTagName().toString()).isEqualTo(tagName);
  }

  public static void assertNodeIsCloseTagWithName(HtmlMatcherGraphNode node, String tagName) {
    assertThat(node).isInstanceOf(HtmlMatcherTagNode.class);
    assertThat(((HtmlMatcherTagNode) node).getTagKind()).isEqualTo(TagKind.CLOSE_TAG);

    SoyNode soyNode = node.getSoyNode().get();

    assertThat(soyNode.getKind()).isEqualTo(Kind.HTML_CLOSE_TAG_NODE);
    assertThat(((HtmlCloseTagNode) soyNode).getTagName().toString()).isEqualTo(tagName);
  }
}
