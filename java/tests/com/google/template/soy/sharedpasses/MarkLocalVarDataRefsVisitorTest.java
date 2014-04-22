/*
 * Copyright 2011 Google Inc.
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

import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;


/**
 * Unit tests for MarkLocalVarDataRefsVisitor and UnmarkLocalVarDataRefsVisitor.
 *
 * @author Kai Huang
 */
public class MarkLocalVarDataRefsVisitorTest extends TestCase {


  public void testMarkAndUnmark() throws Exception {

    String soyCode = "" +
        "{$boo}{$moo}\n" +
        "{foreach $boo in $booze}\n" +
        "  {$boo}{$moo}\n" +
        "  {for $moo in range(0, $moose, $boo)}\n" +
        "    {$boo}{$moo}\n" +
        "  {/for}\n" +
        "{ifempty}\n" +
        "  {$boo}{$moo}\n" +
        "{/foreach}\n" +
        "{$boo}{$moo}\n";
    SoyFileSetNode soyTree = SharedTestUtils.parseSoyCode(soyCode);

    VarRefNode boo1 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 0)).getExprUnion().getExpr().getChild(0);
    VarRefNode moo1 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 1)).getExprUnion().getExpr().getChild(0);
    VarRefNode boozeInForeachTag = (VarRefNode)
        ((ForeachNonemptyNode) SharedTestUtils.getNode(soyTree, 2, 0)).getExpr().getChild(0);
    VarRefNode boo2 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 0))
            .getExprUnion().getExpr().getChild(0);
    VarRefNode moo2 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 1))
            .getExprUnion().getExpr().getChild(0);
    VarRefNode mooseInForTag = (VarRefNode)
        ((ForNode) SharedTestUtils.getNode(soyTree, 2, 0, 2)).getRangeArgs().get(1).getChild(0);
    VarRefNode booInForTag = (VarRefNode)
        ((ForNode) SharedTestUtils.getNode(soyTree, 2, 0, 2)).getRangeArgs().get(2).getChild(0);
    VarRefNode boo3 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 2, 0))
            .getExprUnion().getExpr().getChild(0);
    VarRefNode moo3 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 2, 1))
            .getExprUnion().getExpr().getChild(0);
    VarRefNode boo4 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 1, 0))
            .getExprUnion().getExpr().getChild(0);
    VarRefNode moo4 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 1, 1))
            .getExprUnion().getExpr().getChild(0);
    VarRefNode boo5 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 3)).getExprUnion().getExpr().getChild(0);
    VarRefNode moo5 = (VarRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 4)).getExprUnion().getExpr().getChild(0);

    assertNull(boo1.isLocalVar());
    assertNull(boozeInForeachTag.isLocalVar());
    assertNull(moo2.isLocalVar());
    assertNull(mooseInForTag.isLocalVar());
    assertNull(booInForTag.isLocalVar());
    assertNull(boo3.isLocalVar());
    assertNull(moo4.isLocalVar());
    assertNull(boo4.isLocalVar());

    (new MarkLocalVarDataRefsVisitor()).exec(soyTree);

    assertFalse(boo1.isLocalVar());
    assertFalse(moo1.isLocalVar());
    assertFalse(boozeInForeachTag.isLocalVar());
    assertTrue(boo2.isLocalVar());
    assertFalse(moo2.isLocalVar());
    assertFalse(mooseInForTag.isLocalVar());
    assertTrue(booInForTag.isLocalVar());
    assertTrue(boo3.isLocalVar());
    assertTrue(moo3.isLocalVar());
    assertFalse(boo4.isLocalVar());
    assertFalse(moo4.isLocalVar());
    assertFalse(boo5.isLocalVar());
    assertFalse(moo5.isLocalVar());

    (new UnmarkLocalVarDataRefsVisitor()).exec(soyTree);

    assertNull(boo1.isLocalVar());
    assertNull(boozeInForeachTag.isLocalVar());
    assertNull(moo2.isLocalVar());
    assertNull(mooseInForTag.isLocalVar());
    assertNull(booInForTag.isLocalVar());
    assertNull(boo3.isLocalVar());
    assertNull(moo4.isLocalVar());
    assertNull(boo4.isLocalVar());
  }

}
