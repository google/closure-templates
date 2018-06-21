/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jbcsrc;

import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TemplateAnalysis}. */
@RunWith(JUnit4.class)
public final class TemplateAnalysisTest {

  // All the tests are written as soy templates with references to two soy functions 'refed' and
  // 'notrefed'.
  //  if a variable is passed to 'refed' then it means that at that point in the program we expect
  //  that it has definitely already been referenced.
  //  'notrefed' just means the opposite.  at that point in the program there is no guarantee that
  //  it has already been referenced.

  @Test
  public void testSimpleSequentalAccess() {
    runTest("{@param p : string}", "{notrefed($p)}{refed($p)}");

    runTest(
        "{@param b : bool}",
        "{@param p1 : string}",
        "{@param p2 : string}",
        "{@param p3 : string}",
        "{$b ? $p1 + $p3 : $p2 + $p3}",
        "{refed($b)}",
        "{notrefed($p1)}",
        "{notrefed($p2)}",
        "{refed($p3)}");
  }

  @Test
  public void testDataAccess() {
    runTest("{@param p : list<string>}", "{notrefed($p[0])}", "{refed($p[0])}");

    runTest("{@param p : [field:string]}", "{notrefed($p.field)}", "{refed($p.field)}");
  }

  @Test
  public void testIf() {
    // conditions are refed prior to the blocks they control
    // if there is an {else} then anything refed in all branches is refed after the if
    runTest(
        "{@param p1 : string}",
        "{@param p2 : string}",
        "{@param p3 : string}",
        "{if $p1}",
        "  {refed($p1)}",
        "  {notrefed($p2)}",
        "  {$p3}",
        "{elseif $p2}",
        "  {refed($p1)}",
        "  {refed($p2)}",
        "  {$p3}",
        "{else}",
        "  {refed($p1)}",
        "  {refed($p2)}",
        "  {$p3}",
        "{/if}",
        "{refed($p3)}");

    runTest(
        "{@param p : string}",
        "{@param b1 : bool}",
        "{@param b2 : bool}",
        "{if $b1}",
        "  {$p}",
        "  {refed($b1)}",
        "  {notrefed($b2)}",
        "{elseif $b2}",
        "  {$p}",
        "  {refed($b1)}",
        "  {refed($b2)}",
        "{/if}",
        "{notrefed($p)}");
  }

  @Test
  public void testSwitch() {
    // cases
    runTest(
        "{@param p : int}",
        "{@param p2 : int}",
        "{switch $p}",
        "  {case $p2}",
        "    {refed($p)}",
        "    {refed($p2)}",
        "  {default}",
        "    {$p2}",
        "{/switch}",
        "{refed($p)}",
        "{refed($p2)}");

    // cases
    runTest(
        "{@param p : int}",
        "{@param p2 : int}",
        "{@param p3 : int}",
        "{switch $p}",
        "  {case $p2}",
        "    {refed($p)}",
        "    {refed($p2)}",
        "  {case $p3}",
        "    {refed($p)}",
        "    {refed($p2)}",
        "    {refed($p3)}",
        "{/switch}",
        "{refed($p)}",
        "{refed($p2)}",
        "{notrefed($p3)}"); // p3 is not refed because it only happens if $p != $p2
  }

  @Test
  public void testFor() {
    runTest(
        "{@param limit : int}",
        "{@param p : string}",
        "{for $i in range(0, $limit)}",
        "  {$p}",
        "  {$i}",
        "  {refed($limit)}",
        "{/for}",
        "{refed($limit)}",
        "{notrefed($p)}");
    // In this case we can prove that the loop will execute and thus p will have been referenced
    // after the loop.
    runTest(
        "{@param p : string}",
        "{@param p2 : string}",
        "{for $i in range(0, 1)}",
        "  {$p}",
        "  {$i}",
        "{ifempty}",
        "  {$p2}",
        "{/for}",
        "{refed($p)}",
        "{notrefed($p2)}");

    runTest(
        "{@param p : string}",
        "{@param p2 : string}",
        "{for $i in range(1, 1)}",
        "  {$p}",
        "  {$i}",
        "{ifempty}",
        "  {$p2}",
        "{/for}",
        "{notrefed($p)}",
        "{refed($p2)}");
  }

  @Test
  public void testForeach() {
    // test special functions for foreach loops. though these all look like references to the loop
    // var, they actually aren't.
    runTest(
        "{@param list : list<?>}",
        "{for $item in $list}",
        "  {if isFirst($item)}first{/if}",
        "  {if isLast($item)}last{/if}",
        "  {index($item)}",
        "  {notrefed($item)}",
        "  {refed($list)}",
        "{/for}",
        "{refed($list)}");

    // test ifempty blocks
    runTest(
        "{@param list : list<?>}",
        "{@param p: ?}",
        "{@param p2: ?}",
        "{@param p3: ?}",
        "{for $item in $list}",
        "  {$p}",
        "  {$p2}",
        "{ifempty}",
        "  {$p}",
        "  {$p3}",
        "{/for}",
        "{refed($list)}",
        "{refed($p)}",
        "{notrefed($p2)}",
        "{notrefed($p3)}");
  }

  @Test
  public void testForeach_literalList() {
    // test literal lists
    // empty list
    runTest(
        "{call .loop data=\"all\"}",
        "  {param list: [] /}",
        "{/call}",
        "{/template}",
        "",
        "{template .loop}",
        "{@param list: list<?>}",
        "{@param p: ?}",
        "{@param p2: ?}",
        "{for $item in $list}",
        "  {$p}",
        "{ifempty}",
        "  {$p2}",
        "{/for}",
        "{notrefed($p)}",
        "{refed($p2)}");

    // nonempty list
    runTest(
        "{@param p: ?}",
        "{@param p2: ?}",
        "{for $item in [1, 2, 3]}",
        "  {$p}",
        "{ifempty}",
        "  {$p2}",
        "{/for}",
        "{refed($p)}",
        "{notrefed($p2)}");
  }

  @Test
  public void testLetVariable() {
    runTest("{@param p: ?}", "{let $l : $p/}", "{notrefed($p)}");
    // referencing $l implies we have refed $p
    runTest("{@param p: ?}", "{let $l : $p/}", "{$l}", "{refed($p)}");
    runTest(
        "{@param p: ?}",
        "{let $l kind=\"text\"}{$p}{/let}",
        "{let $l2 : '' + $l /}",
        "{notrefed($l2)}",
        "{refed($l2)}",
        "{refed($l)}",
        "{refed($p)}");
  }

  @Test
  public void testRefsInLets() {
    runTest(
        "{@param p: ?}",
        "{let $l kind=\"text\"}{$p}{/let}",
        "{let $l2 : notrefed($l) ? refed($l) : '' /}",
        "{notrefed($p)}",
        "{notrefed($l2)}",
        "{refed($l2)}",
        "{refed($l)}");
  }

  @Test
  public void testMsg() {
    runTest("{@param p : ?}", "{msg desc=\"\"}", "  Hello {$p}", "{/msg}", "{refed($p)}");

    runTest(
        "{@param p : ?}",
        "{msg desc=\"\"}",
        "  Hello {$p}",
        "{fallbackmsg desc=\"\"}",
        "  Hello foo",
        "{/msg}",
        "{notrefed($p)}");

    runTest(
        "{@param p : ?}",
        "{msg desc=\"\"}",
        "  Hello {$p}",
        "{fallbackmsg desc=\"\"}",
        "  Hello old {$p}",
        "{/msg}",
        "{refed($p)}");
  }

  @Test
  public void testCall() {
    // The tricky thing about calls is how params are handled
    runTest("{@param p : ?}", "{call .foo data=\"$p\"/}", "{refed($p)}");
    runTest("{@param p : ?}", "{call .foo data=\"all\"/}", "{notrefed($p)}");
    runTest(
        "{@param p : ?}",
        "{call .foo}",
        "  {param p1 : notrefed($p) /}",
        "  {param p2 : notrefed($p) /}",
        "{/call}",
        "{notrefed($p)}");

    runTest(
        "{@param p : ?}",
        "{$p}",
        "{call .foo}",
        "  {param p1 : refed($p) /}",
        "  {param p2 : refed($p) /}",
        "{/call}",
        "{refed($p)}");
  }

  void runTest(String... lines) {
    TemplateNode template = parseTemplate(lines);
    TemplateAnalysis analysis = TemplateAnalysis.analyze(template);
    for (FunctionNode node : SoyTreeUtils.getAllNodesOfType(template, FunctionNode.class)) {
      if (node.getSoyFunction() == NOT_REFED_FUNCTION) {
        checkNotReferenced(analysis, node.getChild(0));
      } else if (node.getSoyFunction() == REFED_FUNCTION) {
        checkReferenced(analysis, node.getChild(0));
      }
    }
  }

  private void checkNotReferenced(TemplateAnalysis analysis, ExprNode child) {
    if (hasDefinitelyAlreadyBeenAccessed(analysis, child)) {
      fail("Expected reference to " + format(child) + " to have not been definitely referenced.");
    }
  }

  private void checkReferenced(TemplateAnalysis analysis, ExprNode child) {
    if (!hasDefinitelyAlreadyBeenAccessed(analysis, child)) {
      fail("Expected reference to " + format(child) + " to have been definitely referenced.");
    }
  }

  private boolean hasDefinitelyAlreadyBeenAccessed(TemplateAnalysis analysis, ExprNode child) {
    if (child instanceof VarRefNode) {
      return analysis.isResolved((VarRefNode) child);
    }
    if (child instanceof DataAccessNode) {
      return analysis.isResolved((DataAccessNode) child);
    }
    return false;
  }

  private String format(ExprNode child) {
    SourceLocation sourceLocation = child.getSourceLocation();
    // subtract 2 from the line number since the boilerplate adds 2 lines above the user content
    return child.toSourceString()
        + " at "
        + (sourceLocation.getBeginLine() - 2)
        + ":"
        + sourceLocation.getBeginColumn();
  }

  private static TemplateNode parseTemplate(String... lines) {
    return SoyFileSetParserBuilder.forFileContents(
            Joiner.on("\n")
                .join(
                    "{namespace test}",
                    "{template .caller}",
                    Joiner.on("\n").join(lines),
                    "{/template}",
                    "",
                    // add an additional template as a callee.
                    "{template .foo}",
                    "  {@param? p1 : ?}",
                    "  {@param? p2 : ?}",
                    "  {$p1 + $p2}",
                    "{/template}",
                    ""))
        .addSoyFunction(REFED_FUNCTION)
        .addSoyFunction(NOT_REFED_FUNCTION)
        .parse()
        .fileSet()
        .getChild(0)
        .getChild(0);
  }

  private static final SoyFunction NOT_REFED_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "notrefed";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(1);
        }
      };

  private static final SoyFunction REFED_FUNCTION =
      new SoyFunction() {
        @Override
        public String getName() {
          return "refed";
        }

        @Override
        public Set<Integer> getValidArgsSizes() {
          return ImmutableSet.of(1);
        }
      };
}
