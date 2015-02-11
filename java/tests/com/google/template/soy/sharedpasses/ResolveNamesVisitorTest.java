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

package com.google.template.soy.sharedpasses;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.SyntaxVersion;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.primitive.UnknownType;

import junit.framework.TestCase;

/**
 * Unit tests for ResolveNamesVisitor.
 *
 */
public class ResolveNamesVisitorTest extends TestCase {

  private static final SoyTypeProvider typeProvider =
      new SoyTypeProvider() {
        @Override
        public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
          if (typeName.equals("unknown")) {
            return UnknownType.getInstance();
          }
          return null;
        }
      };

  private static final SoyTypeRegistry typeRegistry =
      new SoyTypeRegistry(ImmutableSet.of(typeProvider));

  private static ResolveNamesVisitor createResolveNamesVisitorForMaxSyntaxVersion() {
    return createResolveNamesVisitor(SyntaxVersion.V9_9);
  }

  private static ResolveNamesVisitor createResolveNamesVisitor(
      SyntaxVersion declaredSyntaxVersion) {
    return new ResolveNamesVisitor(declaredSyntaxVersion);
  }

  public void testParamNameLookupSuccess() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param pa: bool}",
        "{$pa}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(1);
    assertThat(n.getParams().get(0).localVariableIndex()).isEqualTo(0);
  }

  public void testInjectedParamNameLookupSuccess() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@inject pa: bool}",
        "{$pa}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(1);
    assertThat(n.getInjectedParams().get(0).localVariableIndex()).isEqualTo(0);
  }

  public void testLetNameLookupSuccess() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{let $pa: 1 /}",
        "{$pa}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(1);
    assertThat(((LetValueNode) n.getChild(0)).getVar().localVariableIndex()).isEqualTo(0);
  }

  public void testMultiplLocalsAndScopesNumbering() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{@param pa: bool}",
        "{@param pb: bool}",
        "{let $la: 1 /}",
        "{foreach $item in ['a', 'b']}",
        "  {$pa}{$pb}{$la + $item}",
        "{/foreach}",
        "{let $lb: 1 /}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    // 6 because we have 2 params, 1 let and a foreach loop var which needs 3 slots (variable,
    // index, lastIndex) active within the foreach loop.  the $lb can reuse a slot for the foreach
    // loop variable
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(6);
    assertThat(n.getParams().get(0).localVariableIndex()).isEqualTo(0);
    assertThat(n.getParams().get(1).localVariableIndex()).isEqualTo(1);
    assertThat(((LetValueNode) n.getChild(0)).getVar().localVariableIndex()).isEqualTo(2);
    ForeachNode foreachNode = (ForeachNode) n.getChild(1);
    assertThat(foreachNode.getVar().currentLoopIndexIndex()).isEqualTo(3);
    assertThat(foreachNode.getVar().isLastIteratorIndex()).isEqualTo(4);
    assertThat(foreachNode.getVar().localVariableIndex()).isEqualTo(5);
    // The loop variables are out of scope so we can reuse the 3rd slot
    assertThat(((LetValueNode) n.getChild(2)).getVar().localVariableIndex()).isEqualTo(3);
  }

  public void testMultipleLocals() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{let $la: 1 /}",
        "{let $lb: $la /}",
        "{let $lc: $lb /}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    // 3 because each new $la binding is a 'new variable'
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(3);
    LetValueNode firstLet = (LetValueNode) n.getChild(0);
    LetValueNode secondLet = (LetValueNode) n.getChild(1);
    LetValueNode thirdLet = (LetValueNode) n.getChild(2);
    assertThat(firstLet.getVar().localVariableIndex()).isEqualTo(0);
    assertThat(secondLet.getVar().localVariableIndex()).isEqualTo(1);
    assertThat(thirdLet.getVar().localVariableIndex()).isEqualTo(2);
    assertThat(((VarRefNode) secondLet.getValueExpr().getChild(0)).getDefnDecl())
        .isEqualTo(firstLet.getVar());
    assertThat(((VarRefNode) thirdLet.getValueExpr().getChild(0)).getDefnDecl())
        .isEqualTo(secondLet.getVar());
  }

  public void testVariableNameRedefinition() {
    assertResolveNamesFails(
        "variable $la was already defined",
        constructTemplateSource(
            "{let $la: 1 /}",
            "{let $la: $la /}"));
    assertResolveNamesFails(
        "variable $pa was already defined",
        constructTemplateSource(
            "{@param pa: bool}",
            "{let $pa: not $pa /}"));
    assertResolveNamesFails(
        "variable $la was already defined",
        constructTemplateSource(
            "{let $la: 1 /}",
            "{foreach $item in ['a', 'b']}",
            "  {let $la: $la /}",
            "{/foreach}"));
    // valid, $item and $la are defined in non-overlapping scopes
    SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{foreach $item in ['a', 'b']}",
        "  {let $la: $la /}",
        "{/foreach}",
        "{foreach $item in ['a', 'b']}",
        "  {let $la: $la /}",
        "{/foreach}"));
  }

  public void testLetContentSlotLifetime() {
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyFiles(constructTemplateSource(
        "{let $a}",
        "  {if true}",  // introduce an extra scope
        "    {let $b: 2 /}",
        "    {$b}",
        "  {/if}",
        "{/let}",
        "{$a}"));
    createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
    TemplateNode n = soyTree.getChild(0).getChild(0);
    // 1 because each new $la binding overwrites the prior one
    assertThat(n.getMaxLocalVariableTableSize()).isEqualTo(2);
    LetContentNode aLetNode = (LetContentNode) n.getChild(0);
    assertThat(aLetNode.getVar().localVariableIndex()).isEqualTo(1);
    LetValueNode bLetNode =
        (LetValueNode) ((IfCondNode) ((IfNode) aLetNode.getChild(0)).getChild(0)).getChild(0);
    assertThat(bLetNode.getVar().localVariableIndex()).isEqualTo(0);
  }

  public void testNameLookupFailure() {
    assertResolveNamesFails(
        "Undefined variable",
        constructTemplateSource("{$pa}"));
  }

  /**
   * Helper function that constructs a boilerplate template given a list of body
   * statements to insert into the middle of the template. The body statements will be
   * indented and separated with newlines.
   * @param body The body statements.
   * @return The combined template.
   */
  private String constructTemplateSource(String... body) {
    return "" +
        "{namespace ns autoescape=\"deprecated-noncontextual\"}\n" +
        "/***/\n" +
        "{template .aaa}\n" +
        "  " + Joiner.on("\n   ").join(body) + "\n" +
        "{/template}\n";
  }

  /**
   * Assertions function that checks to make sure that name resolution fails with the
   * expected exception.
   * @param fileContent The template source.
   * @param expectedError The expected failure message (a substring).
   */
  private void assertResolveNamesFails(String expectedError, String fileContent) {
    SoyFileSetNode soyTree =
        SharedTestUtils.parseSoyFiles(typeRegistry, SyntaxVersion.V2_0, false, fileContent);
    try {
      createResolveNamesVisitorForMaxSyntaxVersion().exec(soyTree);
      fail("Expected SoySyntaxException");
    } catch (SoySyntaxException e) {
      String message = e.getMessage();
      assertWithMessage("Expected :'" + message + "' to contain '" + expectedError + "'")
          .that(message.contains(expectedError))
          .isTrue();
    }
  }
}
