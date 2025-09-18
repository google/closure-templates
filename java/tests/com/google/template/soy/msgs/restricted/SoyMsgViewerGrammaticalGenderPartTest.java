/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.msgs.restricted;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.GrammaticalGender;
import com.google.template.soy.msgs.restricted.SoyMsgPart.Case;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyMsgViewerGrammaticalGenderPartTest {

  private static final SoyMsgRawTextPart FEMININE_PART = SoyMsgRawTextPart.of("feminine");
  private static final SoyMsgRawTextPart MASCULINE_PART = SoyMsgRawTextPart.of("masculine");
  private static final SoyMsgRawTextPart OTHER_PART = SoyMsgRawTextPart.of("other");

  private static final Case<GrammaticalGender> FEMININE_CASE =
      Case.create(GrammaticalGender.FEMININE, ImmutableList.of(FEMININE_PART));
  private static final Case<GrammaticalGender> MASCULINE_CASE =
      Case.create(GrammaticalGender.MASCULINE, ImmutableList.of(MASCULINE_PART));
  private static final Case<GrammaticalGender> OTHER_CASE =
      Case.create(GrammaticalGender.OTHER, ImmutableList.of(OTHER_PART));

  @Test
  public void getCases() {
    SoyMsgViewerGrammaticalGenderPart part =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getCases())
        .containsExactly(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE)
        .inOrder();
  }

  @Test
  public void getPartsForGender_exactMatch() {
    SoyMsgViewerGrammaticalGenderPart part =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getPartsForGender(GrammaticalGender.FEMININE)).containsExactly(FEMININE_PART);
    assertThat(part.getPartsForGender(GrammaticalGender.MASCULINE)).containsExactly(MASCULINE_PART);
  }

  @Test
  public void getPartsForGender_fallbackToOther() {
    SoyMsgViewerGrammaticalGenderPart part =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getPartsForGender(GrammaticalGender.NEUTER)).containsExactly(OTHER_PART);
  }

  @Test
  public void getPartsForGender_fallbackToFirst() {
    SoyMsgViewerGrammaticalGenderPart part =
        new SoyMsgViewerGrammaticalGenderPart(ImmutableList.of(FEMININE_CASE, MASCULINE_CASE));
    assertThat(part.getPartsForGender(GrammaticalGender.NEUTER)).containsExactly(FEMININE_PART);
  }

  @Test
  public void getPartsForGender_unspecified() {
    SoyMsgViewerGrammaticalGenderPart part =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getPartsForGender(GrammaticalGender.UNSPECIFIED)).containsExactly(OTHER_PART);
  }

  @Test
  public void equals() {
    SoyMsgViewerGrammaticalGenderPart part1 =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPart part2 =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPart part3 =
        new SoyMsgViewerGrammaticalGenderPart(ImmutableList.of(FEMININE_CASE, MASCULINE_CASE));

    assertThat(part1).isEqualTo(part2);
    assertThat(part1).isNotEqualTo(part3);
    assertThat(part2).isNotEqualTo(part3);
  }

  @Test
  public void testHashCode() {
    SoyMsgViewerGrammaticalGenderPart part1 =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPart part2 =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPart part3 =
        new SoyMsgViewerGrammaticalGenderPart(ImmutableList.of(FEMININE_CASE, MASCULINE_CASE));

    assertThat(part1.hashCode()).isEqualTo(part2.hashCode());
    assertThat(part1.hashCode()).isNotEqualTo(part3.hashCode());
  }
}
