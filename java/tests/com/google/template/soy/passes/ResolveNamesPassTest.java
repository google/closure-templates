/*
 * Copyright 2013 Google Inc.
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

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyTypeRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for ResolveNamesPass.
 *
 */
@RunWith(JUnit4.class)
public final class ResolveNamesPassTest {

  private static final SoyTypeRegistry typeRegistry = new SoyTypeRegistry();

  @Test
  public void testParamNameLookupSuccess() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource("{@param pa: bool}", "{$pa ? 1 : 0}"))
            .parse()
            .fileSet();
    runPass(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(1);
    assertThat(n.getParams().get(0).localVariableIndex()).isEqualTo(0);
  }

  @Test
  public void testInjectedParamNameLookupSuccess() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource("{@inject pa: bool}", "{$pa ? 1 : 0}"))
            .parse()
            .fileSet();
    runPass(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(1);
    assertThat(n.getInjectedParams().get(0).localVariableIndex()).isEqualTo(0);
  }

  @Test
  public void testLetNameLookupSuccess() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(constructTemplateSource("{let $pa: 1 /}", "{$pa}"))
            .parse()
            .fileSet();
    runPass(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(1);
    assertThat(((LetValueNode) n.getChild(0)).getVar().localVariableIndex()).isEqualTo(0);
  }

  @Test
  public void testMultipleLocalsAndScopesNumbering() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{@param pa: bool}",
                    "{@param pb: bool}",
                    "{let $la: 1 /}",
                    "{for $item in ['a', 'b']}",
                    "  {$pa ? 1 : 0}{$pb ? 1 : 0}{$la + $item}",
                    "{/for}",
                    "{let $lb: 1 /}"))
            .parse()
            .fileSet();
    runPass(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    // 6 because we have 2 params, 1 let and a foreach loop var which needs 3 slots (variable,
    // index, lastIndex) active within the foreach loop.  the $lb can reuse a slot for the foreach
    // loop variable
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(6);
    assertThat(n.getParams().get(0).localVariableIndex()).isEqualTo(0);
    assertThat(n.getParams().get(1).localVariableIndex()).isEqualTo(1);
    assertThat(((LetValueNode) n.getChild(0)).getVar().localVariableIndex()).isEqualTo(2);
    ForNonemptyNode forNonemptyNode = (ForNonemptyNode) ((ForNode) n.getChild(1)).getChild(0);
    assertThat(forNonemptyNode.getVar().localVariableIndex()).isEqualTo(3);
    assertThat(forNonemptyNode.getVar().currentLoopIndexIndex()).isEqualTo(4);
    assertThat(forNonemptyNode.getVar().isLastIteratorIndex()).isEqualTo(5);
    // The loop variables are out of scope so we can reuse the 3rd slot
    assertThat(((LetValueNode) n.getChild(2)).getVar().localVariableIndex()).isEqualTo(3);
  }

  @Test
  public void testMultipleLocals() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource("{let $la: 1 /}", "{let $lb: $la /}", "{let $lc: $lb /}"))
            .parse()
            .fileSet();
    runPass(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    // 3 because each new $la binding is a 'new variable'
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(3);
    LetValueNode firstLet = (LetValueNode) n.getChild(0);
    LetValueNode secondLet = (LetValueNode) n.getChild(1);
    LetValueNode thirdLet = (LetValueNode) n.getChild(2);
    assertThat(firstLet.getVar().localVariableIndex()).isEqualTo(0);
    assertThat(secondLet.getVar().localVariableIndex()).isEqualTo(1);
    assertThat(thirdLet.getVar().localVariableIndex()).isEqualTo(2);
    assertThat(((VarRefNode) secondLet.getExpr().getRoot()).getDefnDecl())
        .isEqualTo(firstLet.getVar());
    assertThat(((VarRefNode) thirdLet.getExpr().getRoot()).getDefnDecl())
        .isEqualTo(secondLet.getVar());
  }

  @Test
  public void testVariableNameRedefinition() {
    assertResolveNamesFails(
        "Variable '$la' already defined at line 4.",
        constructTemplateSource("{let $la: 1 /}", "{let $la: $la /}"));
    assertResolveNamesFails(
        "Variable '$pa' already defined at line 4.",
        constructTemplateSource("{@param pa: bool}", "{let $pa: not $pa /}"));
    assertResolveNamesFails(
        "Variable '$la' already defined at line 4.",
        constructTemplateSource(
            "{let $la: 1 /}", "{for $item in ['a', 'b']}", "  {let $la: $la /}", "{/for}"));
    assertResolveNamesFails(
        "Variable '$group' already defined at line 4.",
        constructTemplateSource(
            "{@param group: string}", "{for $group in ['a', 'b']}", "  {$group}", "{/for}"));
    // valid, $item and $la are defined in non-overlapping scopes
    SoyFileSetParserBuilder.forFileContents(
            constructTemplateSource(
                "{for $item in ['a', 'b']}",
                "  {let $la: 1 /}",
                "{/for}",
                "{for $item in ['a', 'b']}",
                "  {let $la: 1 /}",
                "{/for}"))
        .parse()
        .fileSet();
  }

  @Test
  public void testAccidentalGlobalReference() {
    assertResolveNamesFails(
        "Found global reference aliasing a local variable 'group', did you mean '$group'?",
        constructTemplateSource("{@param group: string}", "{if group}{$group}{/if}"));
    assertResolveNamesFails(
        "Found global reference aliasing a local variable 'group', did you mean '$group'?",
        constructTemplateSource("{let $group: 'foo' /}", "{if group}{$group}{/if}"));
    assertResolveNamesFails(
        "Unbound global 'global'.",
        constructTemplateSource("{let $local: 'foo' /}", "{if global}{$local}{/if}"));
  }

  @Test
  public void testLetContentSlotLifetime() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(
                constructTemplateSource(
                    "{let $a kind=\"text\"}",
                    "  {if true}", // introduce an extra scope
                    "    {let $b: 2 /}",
                    "    {$b}",
                    "  {/if}",
                    "{/let}",
                    "{$a}"))
            .parse()
            .fileSet();
    runPass(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    // 1 because each new $la binding overwrites the prior one
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(2);
    LetContentNode aLetNode = (LetContentNode) n.getChild(0);
    assertThat(aLetNode.getVar().localVariableIndex()).isEqualTo(1);
    LetValueNode bLetNode =
        (LetValueNode) ((IfCondNode) ((IfNode) aLetNode.getChild(0)).getChild(0)).getChild(0);
    assertThat(bLetNode.getVar().localVariableIndex()).isEqualTo(0);
  }


  @Test
  public void testLetReferencedInsideAttributeValue() {
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forFileContents(constructTemplateSource("{let $t: 1 /}<{$t}>"))
            .parse()
            .fileSet();
    TemplateNode n = soyTree.getChild(0).getChild(0);
    VarRefNode node = Iterables.getOnlyElement(SoyTreeUtils.getAllNodesOfType(n, VarRefNode.class));
    assertThat(node.getDefnDecl().kind()).isEqualTo(VarDefn.Kind.LOCAL_VAR);
  }

  private void runPass(SoyFileSetNode soyTree) {
    for (SoyFileNode file : soyTree.getChildren()) {
      new ResolveNamesPass(ErrorReporter.exploding()).run(file, soyTree.getNodeIdGenerator());
    }
  }

  /**
   * Helper function that constructs a boilerplate template given a list of body statements to
   * insert into the middle of the template. The body statements will be indented and separated with
   * newlines.
   *
   * @param body The body statements.
   * @return The combined template.
   */
  private static String constructTemplateSource(String... body) {
    return ""
        + "{namespace ns}\n"
        + "/***/\n"
        + "{template .aaa stricthtml=\"false\"}\n"
        + "  "
        + Joiner.on("\n   ").join(body)
        + "\n"
        + "{/template}\n";
  }

  private void assertResolveNamesFails(String expectedError, String fileContent) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(fileContent)
        .errorReporter(errorReporter)
        .typeRegistry(typeRegistry)
        .parse();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(errorReporter.getErrors().get(0).message()).isEqualTo(expectedError);
  }
}
