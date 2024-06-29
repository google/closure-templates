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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.DataAccessNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.MsgPlaceholderNode;
import com.google.template.soy.soytree.MsgPluralNode;
import com.google.template.soy.soytree.MsgSelectNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode.ExprHolderNode;
import com.google.template.soy.soytree.SoyNode.MsgSubstUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.SoyTreeUtils.VisitDirective;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TemplateAnalysisImpl}. */
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
  public void testNullSafeDataAccessWithDuplicateFieldName() {
    TemplateNode template =
        parseTemplate(
            "{@param p : [field:string]}",
            "{@param x : [field:string]}",
            "{$p?.field}",
            "{$x?.field}");
    TemplateAnalysisImpl analysis = TemplateAnalysisImpl.analyze(template);
    ExprNode xField = ((PrintNode) template.getChild(1)).getExpr().getChild(0);
    DataAccessNode access = (DataAccessNode) ((NullSafeAccessNode) xField).getDataAccess();
    assertThat(analysis.isResolved(access)).isFalse();
  }

  @Test
  public void testNullSafeDataAccessWithDuplicateIndex() {
    TemplateNode template =
        parseTemplate(
            "{@param p : list<string>}", "{@param x : list<string>}", "{$p?[0]}", "{$x?[0]}");
    TemplateAnalysisImpl analysis = TemplateAnalysisImpl.analyze(template);
    ExprNode xList = ((PrintNode) template.getChild(1)).getExpr().getChild(0);
    DataAccessNode access = (DataAccessNode) ((NullSafeAccessNode) xList).getDataAccess();
    assertThat(analysis.isResolved(access)).isFalse();
  }

  @Test
  public void testDataAccess() {
    runTest("{@param p : list<string>}", "{notrefed($p[0])}", "{refed($p[0])}");
    runTest("{@param p : list<string>}", "{notrefed($p[0])}", "{refed($p)}");
    runTest("{@param p : list<string>}", "{notrefed($p)}", "{notrefed($p[0])}");

    // TODO(nicholasyu): $p?[0] should imply $p[0] is already referenced.
    // runTest("{@param p : list<string>}", "{notrefed($p?[0])}", "{refed($p[0])}");
    runTest("{@param p : list<string>}", "{notrefed($p?[0])}", "{refed($p)}");
    runTest("{@param p : list<string>}", "{notrefed($p)}", "{notrefed($p?[0])}");

    runTest("{@param p : [field:string]}", "{notrefed($p.field)}", "{refed($p.field)}");
    runTest("{@param p : [field:string]}", "{notrefed($p.field)}", "{refed($p)}");
    runTest("{@param p : [field:string]}", "{notrefed($p)}", "{notrefed($p.field)}");

    // TODO(nicholasyu): $p?.field should imply $p.field is already referenced.

    // runTest("{@param p : [field:string]}", "{notrefed($p?.field)}", "{refed($p.field)}");
    runTest("{@param p : [field:string]}", "{notrefed($p?.field)}", "{refed($p)}");
    runTest("{@param p : [field:string]}", "{notrefed($p)}", "{notrefed($p?.field)}");
  }

  @Test
  public void mapLiteral() {
    runTest("{let $a: 1 /}", "{let $b: 1 /}", "{map($a: $b)}", "{refed($a)}", "{refed($b)}");
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
        "  {refed($i)}",
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
        "  {refed($i)}",
        "{/for}",
        "{refed($p)}",
        "{notrefed($p2)}");

    runTest(
        "{@param p : string}",
        "{@param p2 : string}",
        "{for $i in range(1, 1)}",
        "  {$p}",
        "  {refed($i)}",
        "{/for}",
        "{notrefed($p)}",
        "{notrefed($p2)}");
  }

  @Test
  public void testForeach() {
    runTest(
        "{@param list : list<?>}",
        "{@param p: ?}",
        "{@param p2: ?}",
        "{@param p3: ?}",
        "{for $item, $index in $list}",
        "  {$p}",
        "  {$p2}",
        "  {refed($index)}",
        "  {notrefed($item)}",
        "{/for}",
        "{refed($list)}",
        "{notrefed($p)}",
        "{notrefed($p2)}",
        "{notrefed($p3)}");
  }

  @Test
  public void testForeach_literalList() {
    // test literal lists
    // nonempty list
    runTest(
        "{@param p: ?}",
        "{@param p2: ?}",
        "{for $item in [1, 2, 3]}",
        "  {$p}",
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
  public void testLetWithVariablesRefedBeforeAndAfter() {
    runTest(
        "{@param p: ?}",
        "{let $a: notrefed($p.foo) /}",
        "{let $b: notrefed($p.bar) /}",
        "{notrefed($a)}",
        "{let $c: refed($a) + notrefed($b) /}",
        // $b is referenced after {let $c ...}, but before the variable refrence $c.
        "{notrefed($b)}",
        "{notrefed($c)}");
  }

  @Test
  public void testVarRefedMultipleTimes() {
    runTest(
        "{@param p: ?}",
        "{let $a: notrefed($p) + refed($p) /}",
        "{notrefed($a)}",
        "{refed($a)}",
        "{refed($p)}");
    runTest(
        "{@param p: ?}",
        "{let $a: notrefed($p) /}",
        "{notrefed($p)}",
        "{notrefed($a)}",
        "{refed($a)}",
        "{refed($p)}");
  }

  @Test
  public void testMsg() {
    // Our analysis treats message placeholders as only conditionally evaluated.
    runTest("{@param p : ?}", "{msg desc=\"\"}", "  Hello {$p}", "{/msg}", "{notrefed($p)}");
    // they do respect the incoming references
    runTest("{@param p : ?}", "{$p}", "{msg desc=\"\"}", "  Hello {refed($p)}", "{/msg}");

    // Order within messages ignored.  One of these 2 placeholders will be second but it isn't
    // predictable because translators have an opportunity to reorder them.
    runTest("{@param p : ?}", "{msg desc=\"\"}", "  Hello {notrefed($p)} {notrefed($p)}", "{/msg}");

    // these cases aren't really interested because of how we model placeholders
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
        "{notrefed($p)}");
  }

  @Test
  public void testFallbackCreatesABranch() {
    // Fallbacks are an implicit control flow structure so `$gender` is only evaluated if the
    // translation exists.  We used gendered messages here because gender expressions are always
    // evaluated.
    runTest(
        "{@param gender2 : ?}",
        "{@param gender: ?}",
        "{msg desc='...' genders='$gender'}",
        "   Hello",
        "{fallbackmsg desc='...'  genders='$gender2'}",
        "  Goodbye",
        "{/msg}",
        "{notrefed($gender)}",
        "{notrefed($gender2)}");
    runTest(
        "{@param gender: ?}",
        "{msg desc='...' genders='$gender'}",
        "   Hello",
        "{fallbackmsg desc='...'  genders='$gender'}",
        "  Goodbye",
        "{/msg}",
        "{refed($gender)}");
  }

  @Test
  public void testPluralSelectIsEvaluated() {
    runTest(
        "{@param gender: ?}",
        "{msg desc='...' genders='$gender'}",
        "   Hello {notrefed($gender)}",
        "{/msg}",
        "{refed($gender)}");

    runTest(
        "{@param gender: ?}",
        "{msg desc='...' genders='$gender'}",
        "   Hello",
        "{/msg}",
        "{refed($gender)}");

    runTest(
        "{@param num: ?}",
        "{msg desc='...'}",
        "{plural $num}",
        "{default}",
        "   Hello {notrefed($num)}",
        "{/plural}",
        "{/msg}",
        "{refed($num)}");
  }

  @Test
  public void testMsgPlural_syntheticPlaceholder() {
    // There are multiple references to the same expression in this case. Test the deduplication
    // logic such that the single $p.foo does not mark itself as resolved.
    runTest(
        "{@param p : ?}",
        "{@param gender: ?}",
        "{msg desc=\"...\" genders=\"$gender\"}",
        "  {plural notrefed($p.foo)}",
        "    {case 0}",
        "      None",
        "    {case 1}",
        "      Single",
        "    {default}",
        "      Many",
        "  {/plural}",
        "{/msg}");
  }

  @Test
  public void testCall() {
    // The tricky thing about calls is how params are handled
    runTest("{@param p : ?}", "{call foo data=\"$p\"/}", "{refed($p)}");
    runTest("{@param p : ?}", "{call foo data=\"all\"/}", "{notrefed($p)}");
    runTest(
        "{@param p : ?}",
        "{call foo}",
        "  {param p1 : notrefed($p) /}",
        "  {param p2 : notrefed($p) /}",
        "{/call}",
        "{notrefed($p)}");

    runTest(
        "{@param p : ?}",
        "{$p}",
        "{call foo}",
        "  {param p1 : refed($p) /}",
        "  {param p2 : refed($p) /}",
        "{/call}",
        "{refed($p)}");
  }

  // we can construct a deep analysis graph with a large number of sequential if-statements
  @Test
  public void testDeepGraph() {
    StringBuilder longTemplate = new StringBuilder();
    longTemplate.append("{@param p0: ?}\n");
    for (int i = 1; i < 1000; i++) {
      longTemplate.append(
          String.format(
              "{let $p%d : $p%d + 1/}\n{if $p%d}{$p%d}{/if}\n\n", i, i - 1, i - 1, i - 1));
    }
    runTest(longTemplate.toString());
  }

  void runTest(String... lines) {
    TemplateNode template = parseTemplate(lines);
    TemplateAnalysisImpl analysis = TemplateAnalysisImpl.analyze(template);
    // Due to how MsgNodes are compiled and analyzed, we only want to look at representative nodes
    // so we need a complex query
    // first look at all assertions that aren't in a placeholder.
    SoyTreeUtils.allNodes(
            template,
            n ->
                n instanceof MsgPlaceholderNode
                    ? VisitDirective.SKIP_CHILDREN
                    : VisitDirective.CONTINUE)
        .forEach(n -> runTestOnLeafNode(analysis, n));

    // then look at all the message placeholders.  Normalize them
    for (MsgNode msg : SoyTreeUtils.getAllNodesOfType(template, MsgNode.class)) {
      for (MsgSubstUnitNode placeholder : msg.getVarNameToRepNodeMap().values()) {
        if (placeholder instanceof MsgSelectNode || placeholder instanceof MsgPluralNode) {
          // only run on the direct exprs of select/plural, we will find their children as other
          // placeholders.
          for (ExprNode expr : ((ExprHolderNode) placeholder).getExprList()) {
            SoyTreeUtils.allNodes(expr).forEach(n -> runTestOnLeafNode(analysis, n));
          }
        } else {
          // everything else is a normal placeholder
          SoyTreeUtils.allNodes(placeholder).forEach(n -> runTestOnLeafNode(analysis, n));
        }
      }
    }
  }

  private static void runTestOnLeafNode(TemplateAnalysisImpl analysis, Node n) {
    if (n instanceof FunctionNode) {
      FunctionNode functionNode = (FunctionNode) n;
      if (functionNode.getSoyFunction() == NOT_REFED_FUNCTION) {
        checkNotReferenced(analysis, functionNode.getChild(0));
      } else if (functionNode.getSoyFunction() == REFED_FUNCTION) {
        checkReferenced(analysis, functionNode.getChild(0));
      }
    }
  }

  private static void checkNotReferenced(TemplateAnalysisImpl analysis, ExprNode child) {
    if (hasDefinitelyAlreadyBeenAccessed(analysis, child)) {
      fail(
          "Expected reference to "
              + format(child)
              + " to have not been definitely referenced.\n\n"
              + analysis.dumpGraph());
    }
  }

  private static void checkReferenced(TemplateAnalysisImpl analysis, ExprNode child) {
    if (!hasDefinitelyAlreadyBeenAccessed(analysis, child)) {
      fail(
          "Expected reference to "
              + format(child)
              + " to have been definitely referenced.\n\n"
              + analysis.dumpGraph());
    }
  }

  private static boolean hasDefinitelyAlreadyBeenAccessed(
      TemplateAnalysisImpl analysis, ExprNode child) {
    if (child instanceof VarRefNode) {
      return analysis.isResolved((VarRefNode) child);
    }
    if (child instanceof DataAccessNode) {
      return analysis.isResolved((DataAccessNode) child);
    }
    return false;
  }

  private static String format(ExprNode child) {
    SourceLocation sourceLocation = child.getSourceLocation();
    // subtract 2 from the line number since the boilerplate adds 2 lines above the user content
    return child.toSourceString()
        + " at "
        + (sourceLocation.getBeginLine() - 2)
        + ":"
        + sourceLocation.getBeginColumn();
  }

  private static TemplateNode parseTemplate(String... lines) {
    return (TemplateNode)
        SoyFileSetParserBuilder.forFileContents(
                Joiner.on("\n")
                    .join(
                        "{namespace test}",
                        "{template caller}",
                        Joiner.on("\n").join(lines),
                        "{/template}",
                        "",
                        // add an additional template as a callee.
                        "{template foo}",
                        "  {@param? p1 : ?}",
                        "  {@param? p2 : ?}",
                        "  {$p1 + $p2}",
                        "{/template}",
                        ""))
            .addSoyFunction(REFED_FUNCTION)
            .addSoyFunction(NOT_REFED_FUNCTION)
            .errorReporter(ErrorReporter.explodeOnErrorsAndIgnoreDeprecations())
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
        public ImmutableSet<Integer> getValidArgsSizes() {
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
        public ImmutableSet<Integer> getValidArgsSizes() {
          return ImmutableSet.of(1);
        }
      };
}
