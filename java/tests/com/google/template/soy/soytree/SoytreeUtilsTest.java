/*
 * Copyright 2009 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.types.SoyTypeRegistry;

import junit.framework.TestCase;

import java.util.List;


/**
 * Unit tests for SoytreeUtils.
 *
 */
public class SoytreeUtilsTest extends TestCase {


  // -----------------------------------------------------------------------------------------------
  // Tests for executing an ExprNode visitor on all expressions in a Soy tree.


  public void testVisitAllExprs() {

    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n" +
        "\n" +
        "/** @param items */\n" +
        "{template name=\".foo\"}\n" +
        "  {length($items) + 5}\n" +  // 5 nodes
        "  {foreach $item in $items}\n" +  // 2 nodes
        "    {$item.goo}\n" +  // 3 nodes
        "  {/foreach}\n" +
        "{/template}\n";

    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(testFileContent);

    CountingVisitor countingVisitor = new CountingVisitor();
    SoytreeUtils.execOnAllV2Exprs(soyTree, countingVisitor);
    CountingVisitor.Counts counts = countingVisitor.getCounts();
    assertEquals(3, counts.numExecs);
    assertEquals(10, counts.numVisitedNodes);
  }


  /**
   * Visitor that counts the number of times {@code exec()} is called and the total number of nodes
   * visited over all of those calls.
   *
   * <p> Helper class for {@code testVisitAllExprs()}.
   */
  private static class CountingVisitor extends AbstractExprNodeVisitor<Void> {

    public static class Counts {
      public int numExecs;
      public int numVisitedNodes;
    }

    private final Counts counts;

    public CountingVisitor() {
      counts = new Counts();
    }

    public Counts getCounts() {
      return counts;
    }

    @Override public Void exec(ExprNode node) {
      counts.numExecs++;
      return super.exec(node);
    }

    @Override protected void visitExprNode(ExprNode node) {
      counts.numVisitedNodes++;
      if (node instanceof ParentExprNode) {
        visitChildren((ParentExprNode) node);
      }
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Tests for cloning. Adapted from Mike Samuel's CloneVisitorTest.


  private static final String SOY_SOURCE_FOR_TESTING_CLONING = Joiner.on('\n').join(
      "{namespace ns autoescape=\"deprecated-noncontextual\"}",
      "{template ex1 private=\"true\"}",
      "  Hello, World!",
      "  {lb}{call foo data=\"all\"}{param x: $x /}{/call}{rb}",
      "  {$x |escapeHtml}",
      "  {if $cond0}",
      "    {$a}",
      "  {elsif $cond1}",
      "    {print $b}",
      "  {else}",
      "    {$c}",
      "  {/if}",
      "  {switch $v}",
      "    {case 0}",
      "      Zero",
      "    {default}",
      "      Some",
      "  {/switch}",
      "  {literal}",
      "     {interpreted literally/}",
      "  {/literal}",
      "  <style>{css $foo} {lb}color: red{rb}</style>",
      "  {msg desc=\"test\"}<h1 class=\"howdy\">Hello, {$world}!</h1>{/msg}",
      "  <ol>",
      "    {foreach $item in $items}",
      "      <li>{$item}</li>",
      "    {ifempty}",
      "      <li><i>Nothing to see here!",
      "    {/foreach}",
      "    {for $i in range($start, $end)}",
      "      <li value={$i}>foo</li>",
      "    {/for}",
      "  </ol>",
      "{/template}");


  public final void testClone() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    SoyFileNode soyFile =
        (new SoyFileParser(
            new SoyTypeRegistry(), nodeIdGen, SOY_SOURCE_FOR_TESTING_CLONING, SoyFileKind.SRC,
            "test.soy"))
            .parseSoyFile();
    soyTree.addChild(soyFile);

    SoyFileSetNode clone = soyTree.clone();
    assertEquals(1, clone.numChildren());

    assertEquals(clone.toTreeString(0), soyTree.toTreeString(0));
    assertEquals(clone.getChild(0).toSourceString(), soyFile.toSourceString());
  }


  public final void testCloneWithNewIds() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    SoyFileNode soyFile =
        (new SoyFileParser(
            new SoyTypeRegistry(), nodeIdGen, SOY_SOURCE_FOR_TESTING_CLONING, SoyFileKind.SRC,
            "test.soy"))
            .parseSoyFile();
    soyTree.addChild(soyFile);

    SoyFileSetNode clone = SoytreeUtils.cloneWithNewIds(soyTree, nodeIdGen);
    assertEquals(1, clone.numChildren());

    assertFalse(clone.getId() == soyTree.getId());
    assertEquals(ignoreNodeIds(clone.toTreeString(0)), ignoreNodeIds(soyTree.toTreeString(0)));
    assertEquals(clone.getChild(0).toSourceString(), soyFile.toSourceString());
  }


  public final void testCloneListWithNewIds() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    SoyFileNode soyFile =
        (new SoyFileParser(new SoyTypeRegistry(), nodeIdGen, SOY_SOURCE_FOR_TESTING_CLONING,
            SoyFileKind.SRC, "test.soy"))
            .parseSoyFile();
    soyTree.addChild(soyFile);

    TemplateNode template = soyFile.getChild(0);
    int numChildren = template.numChildren();

    List<StandaloneNode> clones = SoytreeUtils.cloneListWithNewIds(
        template.getChildren(), nodeIdGen);
    assertEquals(numChildren, clones.size());

    for (int i = 0; i < numChildren; i++) {
      StandaloneNode clone = clones.get(i);
      StandaloneNode child = template.getChild(i);
      assertFalse(clone.getId() == child.getId());
      assertEquals(ignoreNodeIds(child.toTreeString(0)), ignoreNodeIds(clone.toTreeString(0)));
      assertEquals(child.toSourceString(), clone.toSourceString());
    }
  }


  /**
   * Private helper for testCloneWithNewIds().
   */
  private static String ignoreNodeIds(String treeString) {
    // Nodes in a treeString look like "[<NodeClass>_<Id>]".
    // The function cloneWithNewIds should generate new IDs so normalize the IDs to suppress
    // expected differences.
    return treeString.replaceAll("_(\\d+)\\]", "_#]");
  }


  public final void testMsgHtmlTagNode() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    SoyFileNode soyFile =
        (new SoyFileParser(
            new SoyTypeRegistry(), nodeIdGen, SOY_SOURCE_FOR_TESTING_CLONING, SoyFileKind.SRC,
            "test.soy"))
            .parseSoyFile();
    soyTree.addChild(soyFile);

    FindNodeByTypeVisitor<MsgHtmlTagNode> visitor =
        new FindNodeByTypeVisitor<MsgHtmlTagNode>(MsgHtmlTagNode.class);
    List<MsgHtmlTagNode> msgHtmlTagNodes = visitor.exec(soyFile);
    assertFalse(msgHtmlTagNodes.isEmpty());

    for (MsgHtmlTagNode origMsgHtmlTagNode : msgHtmlTagNodes) {
      MsgHtmlTagNode clonedMsgHtmlTagNode = origMsgHtmlTagNode.clone();

      assertEquals(clonedMsgHtmlTagNode.numChildren(), origMsgHtmlTagNode.numChildren());
      assertEquals(clonedMsgHtmlTagNode.getId(), origMsgHtmlTagNode.getId());
      assertEquals(clonedMsgHtmlTagNode.getFullTagText(), origMsgHtmlTagNode.getFullTagText());
      assertEquals(clonedMsgHtmlTagNode.getLcTagName(), origMsgHtmlTagNode.getLcTagName());
      assertEquals(
          clonedMsgHtmlTagNode.getSyntaxVersionBound(), origMsgHtmlTagNode.getSyntaxVersionBound());
    }
  }


  /**
   * Private helper visitor for testMsgHtmlTagNode().
   */
  private static class FindNodeByTypeVisitor<T extends AbstractSoyNode>
      extends AbstractSoyNodeVisitor<List<T>> {

    /** Result list. */
    final ImmutableList.Builder<T> foundNodes = ImmutableList.builder();
    /** The type of nodes to look for. */
    final Class<? extends T> type;

    FindNodeByTypeVisitor(Class<? extends T> type) {
      this.type = type;
    }

    @Override public List<T> exec(SoyNode node) {
      visit(node);
      return foundNodes.build();
    }

    @Override protected void visitSoyNode(SoyNode node) {
      if (type.isInstance(node)) {
        foundNodes.add(type.cast(node));
      }
      if (node instanceof ParentSoyNode<?>) {
        visitChildren((ParentSoyNode<?>) node);
      }
    }
  }

}
