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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.IncrementingIdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.defn.LocalVar;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.StringReader;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit tests for SoyTreeUtils.
 *
 */
public final class SoyTreeUtilsTest extends TestCase {


  // -----------------------------------------------------------------------------------------------
  // Tests for executing an ExprNode visitor on all expressions in a Soy tree.


  public void testVisitAllExprs() {

    String testFileContent =
        "{namespace boo autoescape=\"deprecated-noncontextual\"}\n"
            + "\n"
            + "/** @param items */\n"
            + "{template .foo}\n"
            + "  {length($items) + 5}\n" // 5 nodes
            + "  {foreach $item in $items}\n" // 2 nodes
            + "    {$item.goo}\n" // 3 nodes
            + "  {/foreach}\n"
            + "{/template}\n";

    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(testFileContent)
            .errorReporter(boom)
            .parse()
            .fileSet();

    CountingVisitor countingVisitor = new CountingVisitor();
    SoyTreeUtils.execOnAllV2Exprs(soyTree, countingVisitor);
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

    private final Counts counts = new Counts();

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
      "/** example for cloning. */",
      "{template .ex1 private=\"true\"}",
      "  {@param a : ?}",
      "  {@param b : ?}",
      "  {@param c : ?}",
      "  {@param v : ?}",
      "  {@param x : ?}",
      "  {@param start : ?}",
      "  {@param end : ?}",
      "  {@param cond0 : ?}",
      "  {@param cond1 : ?}",
      "  {@param items : ?}",
      "  {@param world : ?}",
      "  {@param foo : ?}",
      "  Hello, World!",
      "  {lb}{call foo data=\"all\"}{param x: $x /}{/call}{rb}",
      "  {$x |escapeHtml}",
      "  {if $cond0}",
      "    {$a}",
      "  {elseif $cond1}",
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
      "  {let $local : 'foo' /}",
      "  {$local}",
      "{/template}");


  public final void testClone() throws Exception {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(SOY_SOURCE_FOR_TESTING_CLONING)
            .declaredSyntaxVersion(SyntaxVersion.V2_4)
            .parse()
            .fileSet();

    SoyFileSetNode clone = SoyTreeUtils.cloneNode(soyTree);
    assertEquals(1, clone.numChildren());

    assertEquals(clone.getChild(0).toSourceString(), soyTree.getChild(0).toSourceString());
    // All the localvarnodes, there is one of each type
    ForNode forNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(clone, ForNode.class));
    ForeachNonemptyNode foreachNonemptyNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(clone, ForeachNonemptyNode.class));
    LetValueNode letValueNode =
        Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(clone, LetValueNode.class));
    for (VarRefNode varRef : SoyTreeUtils.getAllNodesOfType(clone, VarRefNode.class)) {
      VarDefn defn = varRef.getDefnDecl();
      LocalVar local;
      switch (varRef.getName()) {
        case "local":
          local = (LocalVar) defn;
          assertSame(letValueNode, local.declaringNode());
          assertSame(letValueNode.getVar(), local);
          break;
        case "item":
          local = (LocalVar) defn;
          assertSame(foreachNonemptyNode, local.declaringNode());
          assertSame(foreachNonemptyNode.getVar(), defn);
          break;
        case "i":
          local = (LocalVar) defn;
          assertSame(forNode, local.declaringNode());
          assertSame(forNode.getVar(), defn);
          break;
      }
    }
  }


  public final void testCloneWithNewIds() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    SoyFileNode soyFile = new SoyFileParser(
        new SoyTypeRegistry(),
        nodeIdGen,
        new StringReader(SOY_SOURCE_FOR_TESTING_CLONING),
        SoyFileKind.SRC,
        "test.soy",
        ExplodingErrorReporter.get())
        .parseSoyFile();
    soyTree.addChild(soyFile);

    SoyFileSetNode clone = SoyTreeUtils.cloneWithNewIds(soyTree, nodeIdGen);
    assertEquals(1, clone.numChildren());

    assertFalse(clone.getId() == soyTree.getId());
    assertEquals(clone.getChild(0).toSourceString(), soyFile.toSourceString());
  }


  public final void testCloneListWithNewIds() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    SoyFileNode soyFile = new SoyFileParser(
        new SoyTypeRegistry(),
        nodeIdGen,
         new StringReader(SOY_SOURCE_FOR_TESTING_CLONING),
        SoyFileKind.SRC,
        "test.soy",
        ExplodingErrorReporter.get())
        .parseSoyFile();
    soyTree.addChild(soyFile);

    TemplateNode template = soyFile.getChild(0);
    int numChildren = template.numChildren();

    List<StandaloneNode> clones =
        SoyTreeUtils.cloneListWithNewIds(template.getChildren(), nodeIdGen);
    assertThat(clones).hasSize(numChildren);

    for (int i = 0; i < numChildren; i++) {
      StandaloneNode clone = clones.get(i);
      StandaloneNode child = template.getChild(i);
      assertFalse(clone.getId() == child.getId());
      assertEquals(child.toSourceString(), clone.toSourceString());
    }
  }


  public final void testMsgHtmlTagNode() throws Exception {

    IdGenerator nodeIdGen = new IncrementingIdGenerator();
    SoyFileSetNode soyTree = new SoyFileSetNode(nodeIdGen.genId(), nodeIdGen);
    ErrorReporter boom = ExplodingErrorReporter.get();
    SoyFileNode soyFile = new SoyFileParser(
        new SoyTypeRegistry(),
        nodeIdGen,
        new StringReader(SOY_SOURCE_FOR_TESTING_CLONING),
        SoyFileKind.SRC,
        "test.soy",
        boom)
        .parseSoyFile();
    soyTree.addChild(soyFile);

    List<MsgHtmlTagNode> msgHtmlTagNodes =
        SoyTreeUtils.getAllNodesOfType(soyFile, MsgHtmlTagNode.class);

    for (MsgHtmlTagNode origMsgHtmlTagNode : msgHtmlTagNodes) {
      MsgHtmlTagNode clonedMsgHtmlTagNode = SoyTreeUtils.cloneNode(origMsgHtmlTagNode);

      assertEquals(clonedMsgHtmlTagNode.numChildren(), origMsgHtmlTagNode.numChildren());
      assertEquals(clonedMsgHtmlTagNode.getId(), origMsgHtmlTagNode.getId());
      assertEquals(clonedMsgHtmlTagNode.getFullTagText(), origMsgHtmlTagNode.getFullTagText());
      assertEquals(clonedMsgHtmlTagNode.getLcTagName(), origMsgHtmlTagNode.getLcTagName());
      assertEquals(
          clonedMsgHtmlTagNode.getSyntaxVersionUpperBound(),
          origMsgHtmlTagNode.getSyntaxVersionUpperBound());
    }
  }

}
