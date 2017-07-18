/*
 * Copyright 2012 Google Inc.
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
import static org.junit.Assert.assertEquals;

import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.msgs.internal.MsgUtils;
import com.google.template.soy.shared.SharedTestUtils;
import com.google.template.soy.soytree.MsgNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RewriteGenderMsgsVisitor.
 *
 */
@RunWith(JUnit4.class)
public final class RewriteGenderMsgsVisitorTest {

  @Test
  public void testCannotMixGendersAndSelect() {
    String soyCode =
        ""
            + "{@param userGender : ?}\n"
            + "{@param targetGender : ?}\n"
            + "{$userGender}\n"
            + "{msg genders=\"$userGender\" desc=\"Button text.\"}\n"
            + "  {select $targetGender}\n"
            + "    {case 'female'}Reply to her\n"
            + "    {case 'male'}Reply to him\n"
            + "    {default}Reply to them\n"
            + "  {/select}\n"
            + "{/msg}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(errorReporter)
        .parse()
        .fileSet();
    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .contains("Cannot mix 'genders' attribute with 'select' command in the same message.");
  }

  @Test
  public void testErrorIfCannotGenNoncollidingBaseNames() {
    String soyCode =
        ""
            + "{@param userGender : ?}\n"
            + "{@param gender : ?}\n"
            + "{@param owner : ?}\n"
            + "{msg genders=\"$userGender, $gender\" desc=\"Button text.\"}\n"
            + "  You joined {$owner}'s community.\n"
            + "{/msg}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(errorReporter)
        .parse()
        .fileSet();
    List<String> actualMessages = new ArrayList<>();
    for (SoyError error : errorReporter.getErrors()) {
      actualMessages.add(error.message());
    }
    assertThat(actualMessages)
        .contains(
            "Cannot generate noncolliding base names for vars. "
                + "Colliding expressions: '$gender' and '$userGender'. "
                + "Add explicit base names with the 'phname' attribute.");
  }

  @Test
  public void testMaxThreeGenders() {
    String soyCode =
        ""
            + "{@param userGender : ?}\n"
            + "{@param targetGender1 : ?}\n"
            + "{@param targetGender2 : ?}\n"
            + "{@param groupOwnerGender : ?}\n"
            + "{@param targetName1 : ?}\n"
            + "{@param targetName2 : ?}\n"
            + "{@param groupOwnerName : ?}\n"
            + "{msg genders=\"$userGender, $targetGender1, $targetGender2, $groupOwnerGender\""
            + "    desc=\"...\"}\n"
            + "  You added {$targetName1} and {$targetName2} to {$groupOwnerName}'s group.\n"
            + "{/msg}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(errorReporter).parse();
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo("Attribute 'genders' should contain 1-3 expressions.");
  }

  @Test
  public void testMaxTwoGendersWithPlural() {
    String soyCode =
        ""
            + "{@param userGender : ?}\n"
            + "{@param gender1 : ?}\n"
            + "{@param gender2 : ?}\n"
            + "{@param numPhotos : ?}\n"
            + "{@param name1 : ?}\n"
            + "{@param name2 : ?}\n"
            + "{msg genders=\"$userGender, $gender1, $gender2\" desc=\"\"}\n"
            + "  {plural $numPhotos}\n"
            + "    {case 1}Find {$name1}'s face in {$name2}'s photo\n"
            + "    {default}Find {$name1}'s face in {$name2}'s photos\n"
            + "  {/plural}\n"
            + "{/msg}\n";
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forTemplateContents(soyCode)
        .errorReporter(errorReporter)
        .parse()
        .fileSet();
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "In a msg with 'plural', the 'genders' attribute can contain at most 2 expressions"
                + " (otherwise, combinatorial explosion would cause a gigantic generated"
                + " message).");
  }

  @Test
  public void testRewriteSimple() {

    String soyCode =
        ""
            + "{@param userGender : ?}\n"
            + "{msg genders=\"$userGender\" desc=\"Button text.\"}\n"
            + "  Save\n"
            + "{/msg}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();

    // After.
    MsgNode msgAfterRewrite = (MsgNode) SharedTestUtils.getNode(soyTree, 0, 0);
    assertEquals(
        // Note: Still has genders="..." in command text.
        "{msg desc=\"Button text.\" genders=\"$userGender\"}"
            + "{select $userGender}{case 'female'}Save{case 'male'}Save{default}Save{/select}",
        msgAfterRewrite.toSourceString());

    // ------ Test that it has same msg id as equivalent msg using 'select'. ------

    String soyCodeUsingSelect =
        ""
            + "{@param userGender : ?}\n"
            + "{msg desc=\"Button text.\"}\n"
            + "  {select $userGender}\n"
            + "    {case 'female'}Save\n"
            + "    {case 'male'}Save\n"
            + "    {default}Save\n"
            + "  {/select}\n"
            + "{/msg}\n";
    SoyFileSetNode soyTreeUsingSelect =
        SoyFileSetParserBuilder.forTemplateContents(soyCodeUsingSelect).parse().fileSet();
    MsgNode msgUsingSelect = (MsgNode) SharedTestUtils.getNode(soyTreeUsingSelect, 0, 0);
    assertThat(MsgUtils.computeMsgIdForDualFormat(msgAfterRewrite))
        .isEqualTo(MsgUtils.computeMsgIdForDualFormat(msgUsingSelect));
  }

  @Test
  public void testRewriteWithPlural() {

    String soyCode =
        ""
            + "{@param num : ?}\n"
            + "{@param userGender : ?}\n"
            + "{msg genders=\"$userGender\" desc=\"...\"}\n"
            + "  {plural $num}{case 1}Send it{default}Send {$num}{/plural}\n"
            + "{/msg}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();
    // After.
    MsgNode msgAfterRewrite = (MsgNode) SharedTestUtils.getNode(soyTree, 0, 0);
    assertEquals(
        // Note: Still has genders="..." in command text.
        "{msg desc=\"...\" genders=\"$userGender\"}"
            + "{select $userGender}"
            + "{case 'female'}{plural $num}{case 1}Send it{default}Send {$num}{/plural}"
            + "{case 'male'}{plural $num}{case 1}Send it{default}Send {$num}{/plural}"
            + "{default}{plural $num}{case 1}Send it{default}Send {$num}{/plural}"
            + "{/select}",
        msgAfterRewrite.toSourceString());

    // ------ Test that it has same msg id as equivalent msg using 'select'. ------

    String soyCodeUsingSelect =
        ""
            + "{@param num : ?}\n"
            + "{@param userGender : ?}\n"
            + "{msg desc=\"...\"}\n"
            + "  {select $userGender}\n"
            + "    {case 'female'}{plural $num}{case 1}Send it{default}Send {$num}{/plural}\n"
            + "    {case 'male'}{plural $num}{case 1}Send it{default}Send {$num}{/plural}\n"
            + "    {default}{plural $num}{case 1}Send it{default}Send {$num}{/plural}\n"
            + "  {/select}\n"
            + "{/msg}\n";
    SoyFileSetNode soyTreeUsingSelect =
        SoyFileSetParserBuilder.forTemplateContents(soyCodeUsingSelect).parse().fileSet();
    MsgNode msgUsingSelect = (MsgNode) SharedTestUtils.getNode(soyTreeUsingSelect, 0, 0);
    assertThat(MsgUtils.computeMsgIdForDualFormat(msgAfterRewrite))
        .isEqualTo(MsgUtils.computeMsgIdForDualFormat(msgUsingSelect));
  }

  @Test
  public void testRewriteWithThreeGendersAndNoncollidingSelectVarNames() {

    String soyCode =
        ""
            + "{@param target : ?}\n"
            + "{msg genders=\"$ij.userGender, $target[0].gender, $target[1].gender\" "
            + "desc=\"...\"}\n"
            + "  You starred {$target[0].name}'s photo in {$target[1].name}'s album.\n"
            + "{/msg}\n";

    ErrorReporter boom = ErrorReporter.exploding();
    SoyFileSetNode soyTree =
        SoyFileSetParserBuilder.forTemplateContents(soyCode).errorReporter(boom).parse().fileSet();
    // After.
    MsgNode msgAfterRewrite = (MsgNode) SharedTestUtils.getNode(soyTree, 0, 0);

    String expectedInnerSelectSrc =
        ""
            + "{select $target[1].gender phname=\"TARGET_1_GENDER\"}"
            + "{case 'female'}You starred {$target[0].name}'s photo in {$target[1].name}'s album."
            + "{case 'male'}You starred {$target[0].name}'s photo in {$target[1].name}'s album."
            + "{default}You starred {$target[0].name}'s photo in {$target[1].name}'s album."
            + "{/select}";
    String expectedMsgSrc =
        ""
            +
            // Note: Still has genders="..." in command text.
            "{msg desc=\"...\" genders=\"$ij.userGender, $target[0].gender, $target[1].gender\"}"
            + "{select $ij.userGender}"
            + // note: 'phname' not specified because generated is same
            "{case 'female'}"
            + "{select $target[0].gender phname=\"TARGET_0_GENDER\"}"
            + "{case 'female'}"
            + expectedInnerSelectSrc
            + "{case 'male'}"
            + expectedInnerSelectSrc
            + "{default}"
            + expectedInnerSelectSrc
            + "{/select}"
            + "{case 'male'}"
            + "{select $target[0].gender phname=\"TARGET_0_GENDER\"}"
            + "{case 'female'}"
            + expectedInnerSelectSrc
            + "{case 'male'}"
            + expectedInnerSelectSrc
            + "{default}"
            + expectedInnerSelectSrc
            + "{/select}"
            + "{default}"
            + "{select $target[0].gender phname=\"TARGET_0_GENDER\"}"
            + "{case 'female'}"
            + expectedInnerSelectSrc
            + "{case 'male'}"
            + expectedInnerSelectSrc
            + "{default}"
            + expectedInnerSelectSrc
            + "{/select}"
            + "{/select}";
    assertThat(msgAfterRewrite.toSourceString()).isEqualTo(expectedMsgSrc);
  }
}
