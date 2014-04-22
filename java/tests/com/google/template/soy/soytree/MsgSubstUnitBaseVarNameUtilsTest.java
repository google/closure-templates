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

package com.google.template.soy.soytree;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprparse.ExprParseUtils;
import com.google.template.soy.exprtree.ExprRootNode;

import junit.framework.TestCase;

import java.util.List;


/**
 * Unit tests for MsgSubstUnitBaseVarNameUtils.
 *
 */
public class MsgSubstUnitBaseVarNameUtilsTest extends TestCase {


  public void testGenBaseNames() {

    String exprText = "$aaBb";
    assertNaiveBaseNameForExpr("AA_BB", exprText);
    assertShortestBaseNameForExpr("AA_BB", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("AA_BB"), exprText);

    exprText = "$aaBb.ccDd";
    assertNaiveBaseNameForExpr("CC_DD", exprText);
    assertShortestBaseNameForExpr("CC_DD", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("CC_DD", "AA_BB_CC_DD"), exprText);

    exprText = "$ij.aaBb.ccDd";
    assertNaiveBaseNameForExpr("CC_DD", exprText);
    assertShortestBaseNameForExpr("CC_DD", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("CC_DD", "AA_BB_CC_DD"), exprText);

    exprText = "$aaBb?.ccDd0";
    assertNaiveBaseNameForExpr("CC_DD_0", exprText);
    assertShortestBaseNameForExpr("CC_DD_0", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("CC_DD_0", "AA_BB_CC_DD_0"), exprText);

    exprText = "aa_._bb._CC_DD_";
    assertNaiveBaseNameForExpr("CC_DD", exprText);
    assertShortestBaseNameForExpr("CC_DD", exprText);
    assertCandidateBaseNamesForExpr(
        ImmutableList.of("CC_DD", "BB_CC_DD", "AA_BB_CC_DD"), exprText);

    exprText = "length($aaBb)";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("FALLBACK", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.<String>of(), exprText);

    exprText = "$aaBb + 1";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("FALLBACK", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.<String>of(), exprText);

    exprText = "$aaBb0.1.2.ccDd.5";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("CC_DD_5", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("CC_DD_5", "AA_BB_0_1_2_CC_DD_5"), exprText);

    exprText = "$aa0_0bb[1][2]?.cc_dd.ee?[5]";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("EE_5", exprText);
    assertCandidateBaseNamesForExpr(
        ImmutableList.of("EE_5", "CC_DD_EE_5", "AA_0_0_BB_1_2_CC_DD_EE_5"), exprText);

    exprText = "$aa0_0bb['foo'][2]?.cc_dd.ee?[5]";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("EE_5", exprText);
    assertCandidateBaseNamesForExpr(
        ImmutableList.of("EE_5", "CC_DD_EE_5"), exprText);
  }


  /**
   * Private helper for {@code testGenBaseNames()}.
   */
  private void assertNaiveBaseNameForExpr(String expected, String exprText) {
    ExprRootNode<?> exprRoot = ExprParseUtils.parseExprElseThrowSoySyntaxException(exprText, "");
    String actual = MsgSubstUnitBaseVarNameUtils.genNaiveBaseNameForExpr(exprRoot, "FALLBACK");
    MsgNodeTest.assertEquals(expected, actual);
  }


  /**
   * Private helper for {@code testGenBaseNames()}.
   */
  private void assertShortestBaseNameForExpr(String expected, String exprText) {
    ExprRootNode<?> exprRoot = ExprParseUtils.parseExprElseThrowSoySyntaxException(exprText, "");
    String actual = MsgSubstUnitBaseVarNameUtils.genShortestBaseNameForExpr(exprRoot, "FALLBACK");
    MsgNodeTest.assertEquals(expected, actual);
  }


  /**
   * Private helper for {@code testGenBaseNames()}.
   */
  private void assertCandidateBaseNamesForExpr(List<String> expected, String exprText) {
    ExprRootNode<?> exprRoot = ExprParseUtils.parseExprElseThrowSoySyntaxException(exprText, "");
    List<String> actual = MsgSubstUnitBaseVarNameUtils.genCandidateBaseNamesForExpr(exprRoot);
    MsgNodeTest.assertEquals(expected, actual);
  }


  public void testGenNoncollidingBaseNames() {

    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("GENDER"), "$user.gender");
    assertErrorMsgWhenGenNoncollidingBaseNamesForExprs(
        "Cannot generate noncolliding base names for msg placeholders and/or vars:" +
            " found colliding expressions \"$gender\" and \"$ij.gender\".",
        "$gender, $ij.gender");
    assertErrorMsgWhenGenNoncollidingBaseNamesForExprs(
        "Cannot generate noncolliding base names for msg placeholders and/or vars:" +
            " found colliding expressions \"$ij.gender\" and \"$userGender\".",
        "$userGender, $ij.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USERGENDER", "GENDER"), "$usergender, $ij.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_GENDER"), "$userGender, $target.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_GENDER"), "$user.gender, $target.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_0_GENDER", "TARGET_1_GENDER"),
        "$ij.userGender, $target.0?.gender, $target.1?.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_0_GENDER", "TARGET_1_GENDER"),
        "$ij.user.gender, $target[0]?.gender, $target[1]?.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("OWNER_GENDER", "ACTOR_GENDER", "TARGET_GENDER"),
        "$owner.gender, $actor.gender, $target.gender");
  }


  /**
   * Private helper for {@code testGenNoncollidingBaseNames()}.
   */
  private void assertNoncollidingBaseNamesForExprs(List<String> expected, String exprListText) {
    List<ExprRootNode<?>> exprRoots =
        ExprParseUtils.parseExprListElseThrowSoySyntaxException(exprListText, "");
    List<String> actual =
        MsgSubstUnitBaseVarNameUtils.genNoncollidingBaseNamesForExprs(exprRoots, "FALLBACK");
    MsgNodeTest.assertEquals(expected, actual);
  }


  /**
   * Private helper for {@code testGenNoncollidingBaseNames()}.
   */
  private void assertErrorMsgWhenGenNoncollidingBaseNamesForExprs(
      String expectedErrorMsg, String exprListText) {
    List<ExprRootNode<?>> exprRoots =
        ExprParseUtils.parseExprListElseThrowSoySyntaxException(exprListText, "");
    try {
      MsgSubstUnitBaseVarNameUtils.genNoncollidingBaseNamesForExprs(exprRoots, "FALLBACK");
      MsgNodeTest.fail();
    } catch (SoySyntaxException sse) {
      MsgNodeTest.assertTrue(sse.getMessage().contains(expectedErrorMsg));
    }
  }

}
