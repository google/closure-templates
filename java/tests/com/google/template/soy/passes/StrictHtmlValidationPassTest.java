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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.passes.PassManager.PassContinuationRule;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherAccumulatorNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherConditionNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraph;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode;
import com.google.template.soy.passes.htmlmatcher.HtmlMatcherGraphNode.EdgeKind;
import com.google.template.soy.passes.htmlmatcher.TestUtils;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that build an HTML Matcher graph and validates it. */
@RunWith(JUnit4.class)
public final class StrictHtmlValidationPassTest {

  @Test
  public void testEmptyTemplate() {
    // Arrange: set up an empty template.
    SoyFileNode template = parseTemplateBody("");
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert.
    assertThat(matcherGraph).isPresent();
    assertThat(matcherGraph.get().getRootNode()).isEmpty();
  }

  @Test
  public void testSimpleTemplate() {
    // Arrange: set up a simple template.
    SoyFileNode template = parseTemplateBody(Joiner.on("\n").join("<div>", "</div>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert.
    HtmlMatcherGraphNode node = matcherGraph.get().getRootNode().get();
    TestUtils.assertNodeIsOpenTagWithName(node, "div");
    TestUtils.assertNodeIsCloseTagWithName(
        node.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get(), "div");
  }

  @Test
  public void testErrorForSelfClosingTag() {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileNode template = parseTemplateBody("<div/>\n");
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(errorReporter);

    matcherPass.run(template, new IncrementingIdGenerator());
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message())
        .isEqualTo("'div' tag is not allowed to be self-closing.");
  }

  @Test
  public void testTextOnlyIfBranch() {
    // Arrange: set up a template with a text-only {if $cond1} branch.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join("{@param cond1: bool}", "<span>", "  {if $cond1}Content1{/if}", "</span>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Follow the HTML matcher graph and validate the structure.
    HtmlMatcherGraphNode node = matcherGraph.get().getRootNode().get();

    // The root node should be a opening <span> tag.
    TestUtils.assertNodeIsOpenTagWithName(node, "span");

    // The next node should be {if $cond1}.
    HtmlMatcherGraphNode ifCondNode = node.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(ifCondNode).isInstanceOf(HtmlMatcherConditionNode.class);

    HtmlMatcherGraphNode accNode = ifCondNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // Follow the {@code true} edge. The next node should be the </div>
    node = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(node, "span");

    // Follow the graph along the false edge from the if condition node.
    assertThat(ifCondNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(accNode);
  }

  @Test
  public void testSingleBranchIfCondition() {
    // Arrange: set up a template with a {if $cond1} branch that contains an element.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param cond1: bool}",
                    "<div>",
                    "  {if $cond1}</div><div>Content1{/if}",
                    "</div>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be the first <div>.
    HtmlMatcherGraphNode rootNode = matcherGraph.get().getRootNode().get();
    TestUtils.assertNodeIsOpenTagWithName(rootNode, "div");

    // The next node should be the {if $cond1}.
    HtmlMatcherGraphNode ifConditionNode = rootNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThatIfExpressionEqualTo(ifConditionNode, "$cond1");

    // Follow the {@code true} edge. The next node should be the </div>
    HtmlMatcherGraphNode nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // The next node should be another <div>
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    // The next node should be an accumulator node, which closes the true branch.
    HtmlMatcherGraphNode accNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // Follow the false branch of the initial {if $cond1} node. This should link directly to the
    // accumulator node.
    assertThat(ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE)).hasValue(accNode);

    // The next node should be the final </div>.
    nextNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testTwoIfConditions() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param cond1: bool}",
                    "{@param cond2: bool}",
                    "<div>",
                    "  {if $cond1}<div>Content1{/if}",
                    "  {if $cond2}<div>Content2{/if}",
                    "</div>",
                    "</div>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be the first <div>.
    HtmlMatcherGraphNode rootNode = matcherGraph.get().getRootNode().get();
    TestUtils.assertNodeIsOpenTagWithName(rootNode, "div");

    // The next node should be the {if $cond1}.
    HtmlMatcherGraphNode ifConditionNode = rootNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThatIfExpressionEqualTo(ifConditionNode, "$cond1");

    // Follow the true edge. The next node should be another <div> open tag.
    HtmlMatcherGraphNode nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    // The next node should be an accumulator node, which closes the true branch.
    HtmlMatcherGraphNode accNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // Follow the false edge from the {if $cond1} node. The next node should be the accumulator
    // node.
    nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // Follow the graph through the accumulator node, this should be the {if cond2} node.
    ifConditionNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThatIfExpressionEqualTo(ifConditionNode, "$cond2");

    // Follow a similar pattern through the true and false branches of this second if node.
    nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    accNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // The final two nodes should both be closing </div> tags.
    nextNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testIfElseifElseif() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param cond1: bool}",
                    "{@param cond2: bool}",
                    "{@param cond3: bool}",
                    "{if $cond1}<li>List 1",
                    "{elseif $cond2}<li>List 2",
                    "{elseif $cond3}<li>List 3",
                    "{/if}",
                    "</li>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be {if $cond1}.
    HtmlMatcherGraphNode ifConditionNode1 = matcherGraph.get().getRootNode().get();
    assertThatIfExpressionEqualTo(ifConditionNode1, "$cond1");

    // Follow the true branch of {if $cond1}.
    HtmlMatcherGraphNode nextNode = ifConditionNode1.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    HtmlMatcherGraphNode accNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // Follow the false branch. This should lead to the {if $cond2} node.
    HtmlMatcherGraphNode ifConditionNode2 =
        ifConditionNode1.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThatIfExpressionEqualTo(ifConditionNode2, "$cond2");

    // Follow the true branch of {if $cond2}.
    nextNode = ifConditionNode2.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // Follow the false branch. This should lead to the {if $cond3} node.
    HtmlMatcherGraphNode ifConditionNode3 =
        ifConditionNode2.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThatIfExpressionEqualTo(ifConditionNode3, "$cond3");

    // Follow the true branch of {if $cond3}.
    nextNode = ifConditionNode3.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // Follow the false branch. This should link to the accumulator node.
    nextNode = ifConditionNode3.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // There should be a final closing </li> tag, then the graph ends.
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "li");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testIfElse() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param cond1: bool}",
                    "{if $cond1}<div>Content 1</div>",
                    "{else}<div>Content 2</div>",
                    "{/if}",
                    "<span>non-conditional content</span>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be {if $cond1}.
    HtmlMatcherGraphNode ifConditionNode = matcherGraph.get().getRootNode().get();
    assertThatIfExpressionEqualTo(ifConditionNode, "$cond1");

    // Follow the true branch of {if $cond1}.
    HtmlMatcherGraphNode nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // Reaching the accumulator node means you have reached the end of the true branch.
    HtmlMatcherGraphNode accNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // Follow the false edge of {if $cond1}; this is the template code in the {else} block.
    nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // The false branch should also end with the accumulator node.
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // There should be an open and close HTML tag, then the end of the graph.
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "span");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "span");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testIfNestedIfElse() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param cond1: bool}",
                    "{@param nestedCond: bool}",
                    "{if $cond1}<div>Content 1</div>",
                    "  {if $nestedCond}<span>blah</span>{/if}",
                    "{else}<div>Content 2</div>",
                    "{/if}",
                    "<span>non-conditional content</span>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be {if $cond1}.
    HtmlMatcherGraphNode ifConditionNode = matcherGraph.get().getRootNode().get();
    assertThatIfExpressionEqualTo(ifConditionNode, "$cond1");

    // Follow the true branch of {if $cond1}; this should lead to {if $nestedCond}.
    HtmlMatcherGraphNode nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    HtmlMatcherGraphNode nestedIfConditionNode =
        nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThatIfExpressionEqualTo(nestedIfConditionNode, "$nestedCond");

    // Follow the true branch of {if $nestedCond}.
    nextNode = nestedIfConditionNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "span");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "span");

    // The true branch of the nested if node terminates in a nested accumulator node.
    HtmlMatcherGraphNode nestedAccNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nestedAccNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // The false branch of the nested if node should terminate at the nested accumulator node.
    nextNode = nestedIfConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThat(nextNode).isEqualTo(nestedAccNode);

    // Follow the false branch of the outer if node.
    nextNode = ifConditionNode.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // The true branch of the nested if node terminates in an outer accumulator node.
    HtmlMatcherGraphNode outerAccNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(outerAccNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // The nested accumulator node should link to the outer accumulator node.
    nextNode = nestedAccNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(outerAccNode);

    // There should be an open and close HTML tag, then the end of the graph.
    nextNode = outerAccNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "span");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "span");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testSwitchCase() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param listCond: int}",
                    "{switch $listCond}",
                    "  {case 1}<li>List 1",
                    "  {case 2}<li>List 2",
                    "  {case 3}<li>List 3",
                    "{/switch}",
                    "</li>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be {case 1}.
    HtmlMatcherGraphNode switchCaseNode1 = matcherGraph.get().getRootNode().get();
    assertThatSwitchCaseCommandEqualTo(switchCaseNode1, "1");

    // Follow the true branch of {case 1}.
    HtmlMatcherGraphNode nextNode = switchCaseNode1.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    // The next code should be the accumulator node. This terminates the {case 1} branch.
    HtmlMatcherGraphNode accNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(accNode).isInstanceOf(HtmlMatcherAccumulatorNode.class);

    // Follow the false branch. This should lead to the {case 2} node.
    HtmlMatcherGraphNode switchCaseNode2 =
        switchCaseNode1.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThatSwitchCaseCommandEqualTo(switchCaseNode2, "2");

    // Follow the true branch of {case 2}.
    nextNode = switchCaseNode2.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // Follow the false branch. This should lead to the {case 3} node.
    HtmlMatcherGraphNode switchCaseNode3 =
        switchCaseNode2.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThatSwitchCaseCommandEqualTo(switchCaseNode3, "3");

    // Follow the true branch of {case 3}.
    nextNode = switchCaseNode3.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // Follow the false branch. This should link to the accumulator node.
    nextNode = switchCaseNode3.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // There should be a final closing </li> tag, then the graph ends.
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "li");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  @Test
  public void testSwitchCaseWithMultipleExpressions() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param listCond: int}",
                    "{switch $listCond}",
                    "  {case 1, 2}<li>List 1</li>",
                    "{/switch}",
                    ""));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.exploding());

    // Act: execute the graph builder.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();

    // Assert: follow the graph and validate its structure.

    // The root node should be {case 1}.
    HtmlMatcherGraphNode switchCaseNode1 = matcherGraph.get().getRootNode().get();
    assertThatSwitchCaseCommandEqualTo(switchCaseNode1, "1, 2");

    // Follow the true branch of {case 1}.
    HtmlMatcherGraphNode nextNode = switchCaseNode1.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "li");

    // The next code should be the accumulator node. This terminates the {case 1} branch.
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "li");

    // Follow the false branch. This should lead to the {case 2} node.
    HtmlMatcherGraphNode switchCaseNode2 =
        switchCaseNode1.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    assertThatSwitchCaseCommandEqualTo(switchCaseNode2, "1, 2");
  }

  @Test
  public void testSwitchCaseDefault() {
    // Arrange: set up the template under test.
    SoyFileNode template =
        parseTemplateBody(
            Joiner.on("\n")
                .join(
                    "{@param switchCond: int}",
                    "{switch $switchCond}",
                    "   {case 1}<div>Content 1</div>",
                    "   {case 2}<div>Content 2</div>",
                    "   {default}<div>Default content</div>",
                    "{/switch}",
                    "<span>non-conditional content</span>"));
    StrictHtmlValidationPass matcherPass =
        new StrictHtmlValidationPass(ErrorReporter.createForTest());

    // Act: execute the graph builder and follow the graph to the accumulator node.
    matcherPass.run(template, new IncrementingIdGenerator());
    Optional<HtmlMatcherGraph> matcherGraph = matcherPass.getHtmlMatcherGraph();
    HtmlMatcherGraphNode switchCaseNode1 = matcherGraph.get().getRootNode().get();
    HtmlMatcherGraphNode accNode =
        switchCaseNode1
            .getNodeForEdgeKind(EdgeKind.TRUE_EDGE) // <div>
            .get()
            .getNodeForEdgeKind(EdgeKind.TRUE_EDGE) // </div>
            .get()
            .getNodeForEdgeKind(EdgeKind.TRUE_EDGE) // Accumulator node
            .get();
    HtmlMatcherGraphNode switchCaseNode2 =
        switchCaseNode1.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get(); // {default} block

    // Follow the false edge of {case 2}; this is the body of the {default} block.
    HtmlMatcherGraphNode nextNode = switchCaseNode2.getNodeForEdgeKind(EdgeKind.FALSE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "div");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "div");

    // The false branch should also end with the accumulator node.
    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    assertThat(nextNode).isEqualTo(accNode);

    // There should be an open and close HTML tag, then the end of the graph.
    nextNode = accNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsOpenTagWithName(nextNode, "span");

    nextNode = nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE).get();
    TestUtils.assertNodeIsCloseTagWithName(nextNode, "span");

    // Verify that the graph ends here.
    assertThat(nextNode.getNodeForEdgeKind(EdgeKind.TRUE_EDGE)).isEmpty();
  }

  private static void assertThatIfExpressionEqualTo(HtmlMatcherGraphNode node, String exprString) {
    assertThat(node).isInstanceOf(HtmlMatcherConditionNode.class);
    assertThat(((IfCondNode) node.getSoyNode().get()).getExpr().toSourceString())
        .isEqualTo(exprString);
  }

  private static void assertThatSwitchCaseCommandEqualTo(
      HtmlMatcherGraphNode node, String commandString) {
    assertThat(node).isInstanceOf(HtmlMatcherConditionNode.class);

    Stream<ExprRootNode> expressions =
        ((SwitchCaseNode) node.getSoyNode().get()).getExprList().stream();
    assertThat(expressions.map(ExprRootNode::toSourceString).collect(joining(", ")))
        .isEqualTo(commandString);
  }

  /**
   * Wraps the given template body with a {@code {template}} node and parses the result.
   *
   * <p><b>Note:</b> All parser passes up to {@code StrictHtmlValidationPass} are run. Tests in this
   * suite run the Strict HTML Validation pass manually.
   *
   * @return a Parse tree representing the given template body
   */
  private static SoyFileNode parseTemplateBody(String templateBody) {
    String soyFile =
        Joiner.on('\n').join("{namespace ns}", "", "{template .test}", templateBody, "{/template}");
    return SoyFileSetParserBuilder.forFileContents(soyFile)
        // Tests in this suite run the new Strict HTML Validation passes manually.
        .addPassContinuationRule(
            StrictHtmlValidationPass.class, PassContinuationRule.STOP_BEFORE_PASS)
        .desugarHtmlAndStateNodes(false)
        .errorReporter(ErrorReporter.createForTest())
        .parse()
        .fileSet()
        .getChild(0);
  }
}
