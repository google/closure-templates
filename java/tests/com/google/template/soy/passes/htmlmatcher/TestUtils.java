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

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.passes.StrictHtmlValidationPassNewMatcher;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherTagNode.TagKind;
import com.google.template.soy.soyparse.PluginResolver;
import com.google.template.soy.soyparse.PluginResolver.Mode;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.HtmlCloseTagNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.Kind;
import com.google.template.soy.soytree.TagName;

/** Utility functions for HTML Matcher Graph tests. */
public final class TestUtils {
  private static final IncrementingIdGenerator idGenerator = new IncrementingIdGenerator();

  public static HtmlOpenTagNode soyHtmlOpenTagNode() {
    return new HtmlOpenTagNode(
        idGenerator.genId(),
        new TagName(new RawTextNode(idGenerator.genId(), "div", SourceLocation.UNKNOWN)),
        SourceLocation.UNKNOWN,
        /** selfClosing */
        false);
  }

  public static HtmlCloseTagNode soyHtmlCloseTagNode() {
    return new HtmlCloseTagNode(
        idGenerator.genId(),
        new TagName(new RawTextNode(idGenerator.genId(), "div", SourceLocation.UNKNOWN)),
        SourceLocation.UNKNOWN);
  }

  public static HtmlMatcherTagNode htmlMatcherOpenTagNode(HtmlOpenTagNode soyNode) {
    return new HtmlMatcherTagNode(soyNode) {
      @Override
      public TagKind getTagKind() {
        return TagKind.OPEN_TAG;
      }
    };
  }

  public static HtmlMatcherTagNode htmlMatcherCloseTagNode(HtmlCloseTagNode soyNode) {
    return new HtmlMatcherTagNode(soyNode) {
      @Override
      public TagKind getTagKind() {
        return TagKind.CLOSE_TAG;
      }
    };
  }

  public static ExprNode soyExprNode(String exprText) {
    return SoyFileParser.parseExpression(
        exprText,
        PluginResolver.nullResolver(Mode.ALLOW_UNDEFINED, ErrorReporter.exploding()),
        ErrorReporter.exploding());
  }

  public static IfCondNode soyIfCondNode(String exprText) {
    IfCondNode soyNode =
        new IfCondNode(idGenerator.genId(), SourceLocation.UNKNOWN, "if", soyExprNode(exprText));
    IfNode parentIfNode = new IfNode(idGenerator.genId(), SourceLocation.UNKNOWN);
    parentIfNode.addChild(soyNode);
    return soyNode;
  }

  public static IfCondNode soyIfElseCondNode(String exprText) {
    IfCondNode soyNode =
        new IfCondNode(
            idGenerator.genId(), SourceLocation.UNKNOWN, "elseif", soyExprNode(exprText));
    IfNode parentIfNode = new IfNode(idGenerator.genId(), SourceLocation.UNKNOWN);
    parentIfNode.addChild(soyNode);
    return soyNode;
  }

  public static Optional<HtmlMatcherGraph> matcherGraphFromTemplateBody(String... templateBody) {
    SoyFileNode template = parseTemplateBody(Joiner.on("\n").join(templateBody));
    StrictHtmlValidationPassNewMatcher matcherPass =
        new StrictHtmlValidationPassNewMatcher(ErrorReporter.exploding());

    matcherPass.run(template, new IncrementingIdGenerator());
    return matcherPass.getHtmlMatcherGraph();
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

  /**
   * Wraps the given template body with a {@code {template}} node and parses the result.
   *
   * <p><b>Note:</b> All parser passes up to {@code StrictHtmlValidationPass} are run. Tests in this
   * suite run the Strict HTML Validation pass manually.
   *
   * @return a Parse tree representing the given template body
   */
  private static SoyFileNode parseTemplateBody(String input) {
    String soyFile =
        Joiner.on('\n').join("{namespace ns}", "", "{template .test}", input, "{/template}");
    return SoyFileSetParserBuilder.forFileContents(soyFile)
        // Tests in this suite run the Strict HTML Validation passes manually.
        .addPassContinuationRule("StrictHtmlValidation", PassContinuationRule.STOP_BEFORE_PASS)
        .addPassContinuationRule(
            "StrictHtmlValidationPassNewMatcher", PassContinuationRule.STOP_BEFORE_PASS)
        // TODO(b/113531978): Remove the "new_html_matcher" flag when the new HTML matcher
        // goes live.
        .enableExperimentalFeatures(ImmutableList.of("new_html_matcher"))
        .desugarHtmlNodes(false)
        .errorReporter(ErrorReporter.createForTest())
        .parse()
        .fileSet()
        .getChild(0);
  }
}
