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

import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.shared.internal.SharedTestUtils;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;


/**
 * Unit tests for MarkLocalVarDataRefsVisitor and UnmarkLocalVarDataRefsVisitor.
 *
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

    DataRefNode boo1 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 0)).getExprUnion().getExpr().getChild(0);
    DataRefNode moo1 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 1)).getExprUnion().getExpr().getChild(0);
    DataRefNode boozeInForeachTag = (DataRefNode)
        ((ForeachNonemptyNode) SharedTestUtils.getNode(soyTree, 2, 0)).getExpr().getChild(0);
    DataRefNode boo2 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 0))
            .getExprUnion().getExpr().getChild(0);
    DataRefNode moo2 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 1))
            .getExprUnion().getExpr().getChild(0);
    DataRefNode mooseInForTag = (DataRefNode)
        ((ForNode) SharedTestUtils.getNode(soyTree, 2, 0, 2)).getRangeArgs().get(1).getChild(0);
    DataRefNode booInForTag = (DataRefNode)
        ((ForNode) SharedTestUtils.getNode(soyTree, 2, 0, 2)).getRangeArgs().get(2).getChild(0);
    DataRefNode boo3 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 2, 0))
            .getExprUnion().getExpr().getChild(0);
    DataRefNode moo3 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 0, 2, 1))
            .getExprUnion().getExpr().getChild(0);
    DataRefNode boo4 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 1, 0))
            .getExprUnion().getExpr().getChild(0);
    DataRefNode moo4 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 2, 1, 1))
            .getExprUnion().getExpr().getChild(0);
    DataRefNode boo5 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 3)).getExprUnion().getExpr().getChild(0);
    DataRefNode moo5 = (DataRefNode)
        ((PrintNode) SharedTestUtils.getNode(soyTree, 4)).getExprUnion().getExpr().getChild(0);

    assertNull(boo1.isLocalVarDataRef());
    assertNull(boozeInForeachTag.isLocalVarDataRef());
    assertNull(moo2.isLocalVarDataRef());
    assertNull(mooseInForTag.isLocalVarDataRef());
    assertNull(booInForTag.isLocalVarDataRef());
    assertNull(boo3.isLocalVarDataRef());
    assertNull(moo4.isLocalVarDataRef());
    assertNull(boo4.isLocalVarDataRef());

    (new MarkLocalVarDataRefsVisitor()).exec(soyTree);

    assertFalse(boo1.isLocalVarDataRef());
    assertFalse(moo1.isLocalVarDataRef());
    assertFalse(boozeInForeachTag.isLocalVarDataRef());
    assertTrue(boo2.isLocalVarDataRef());
    assertFalse(moo2.isLocalVarDataRef());
    assertFalse(mooseInForTag.isLocalVarDataRef());
    assertTrue(booInForTag.isLocalVarDataRef());
    assertTrue(boo3.isLocalVarDataRef());
    assertTrue(moo3.isLocalVarDataRef());
    assertFalse(boo4.isLocalVarDataRef());
    assertFalse(moo4.isLocalVarDataRef());
    assertFalse(boo5.isLocalVarDataRef());
    assertFalse(moo5.isLocalVarDataRef());

    (new UnmarkLocalVarDataRefsVisitor()).exec(soyTree);

    assertNull(boo1.isLocalVarDataRef());
    assertNull(boozeInForeachTag.isLocalVarDataRef());
    assertNull(moo2.isLocalVarDataRef());
    assertNull(mooseInForTag.isLocalVarDataRef());
    assertNull(booInForTag.isLocalVarDataRef());
    assertNull(boo3.isLocalVarDataRef());
    assertNull(moo4.isLocalVarDataRef());
    assertNull(boo4.isLocalVarDataRef());
  }

}
