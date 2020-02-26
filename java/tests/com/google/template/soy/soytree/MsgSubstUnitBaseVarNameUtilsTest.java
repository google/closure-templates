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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.soyparse.SoyFileParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link MsgSubstUnitBaseVarNameUtils}.
 *
 */
@RunWith(JUnit4.class)
public final class MsgSubstUnitBaseVarNameUtilsTest {

  @Test
  public void testGenBaseNames() {

    String exprText = "$aaBb";
    assertNaiveBaseNameForExpr("AA_BB", exprText);
    assertShortestBaseNameForExpr("AA_BB", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("AA_BB"), exprText);

    exprText = "$aaBb.ccDd";
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
    assertCandidateBaseNamesForExpr(ImmutableList.of("CC_DD", "BB_CC_DD", "AA_BB_CC_DD"), exprText);

    exprText = "length($aaBb)";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("FALLBACK", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of(), exprText);

    exprText = "($aaBb + 1)";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("FALLBACK", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of(), exprText);

    exprText = "$aa0_0bb[1][2]?.cc_dd.ee?[5]";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("EE_5", exprText);
    assertCandidateBaseNamesForExpr(
        ImmutableList.of("EE_5", "CC_DD_EE_5", "AA_0_0_BB_1_2_CC_DD_EE_5"), exprText);

    exprText = "$aa0_0bb['foo'][2]?.cc_dd.ee?[5]";
    assertNaiveBaseNameForExpr("FALLBACK", exprText);
    assertShortestBaseNameForExpr("EE_5", exprText);
    assertCandidateBaseNamesForExpr(ImmutableList.of("EE_5", "CC_DD_EE_5"), exprText);
  }

  private void assertNaiveBaseNameForExpr(String expected, String exprText) {
    ExprNode exprRoot = parse(exprText);
    String actual = MsgSubstUnitBaseVarNameUtils.genNaiveBaseNameForExpr(exprRoot, "FALLBACK");
    assertEquals(expected, actual);
  }

  private void assertShortestBaseNameForExpr(String expected, String exprText) {
    ExprNode exprRoot = parse(exprText);
    String actual = MsgSubstUnitBaseVarNameUtils.genShortestBaseNameForExpr(exprRoot, "FALLBACK");
    assertEquals(expected, actual);
  }

  private void assertCandidateBaseNamesForExpr(List<String> expected, String exprText) {
    ExprNode exprRoot = parse(exprText);
    List<String> actual = MsgSubstUnitBaseVarNameUtils.genCandidateBaseNamesForExpr(exprRoot);
    assertEquals(expected, actual);
  }

  @Test
  public void testGenNoncollidingBaseNames() {
    assertNoncollidingBaseNamesForExprs(ImmutableList.of("GENDER"), "$user.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_GENDER"), "$userGender", "$target.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_GENDER"), "$user.gender", "$target.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("USER_GENDER", "TARGET_0_GENDER", "TARGET_1_GENDER"),
        "$user.gender",
        "$target[0]?.gender",
        "$target[1]?.gender");
    assertNoncollidingBaseNamesForExprs(
        ImmutableList.of("OWNER_GENDER", "ACTOR_GENDER", "TARGET_GENDER"),
        "$owner.gender",
        "$actor.gender",
        "$target.gender");
  }

  private void assertNoncollidingBaseNamesForExprs(List<String> expected, String... exprTexts) {
    List<ExprNode> exprs = new ArrayList<>();
    for (String exprText : exprTexts) {
      exprs.add(parse(exprText));
    }

    List<String> actual =
        MsgSubstUnitBaseVarNameUtils.genNoncollidingBaseNamesForExprs(
            exprs, "FALLBACK", ErrorReporter.exploding());
    assertEquals(expected, actual);
  }

  private ExprNode parse(String exprText) {
    return SoyFileParser.parseExpression(
        exprText,
        ErrorReporter.exploding());
  }
}
