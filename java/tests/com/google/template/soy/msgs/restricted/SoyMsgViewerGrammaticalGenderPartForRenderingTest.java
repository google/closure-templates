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
import com.google.template.soy.msgs.restricted.SoyMsgRawParts.RawCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyMsgViewerGrammaticalGenderPartForRenderingTest {

  private static final SoyMsgRawParts FEMININE_PARTS = SoyMsgRawParts.of("feminine");
  private static final SoyMsgRawParts MASCULINE_PARTS = SoyMsgRawParts.of("masculine");
  private static final SoyMsgRawParts OTHER_PARTS = SoyMsgRawParts.of("other");

  private static final RawCase<GrammaticalGender> FEMININE_CASE =
      RawCase.create(GrammaticalGender.FEMININE, FEMININE_PARTS);
  private static final RawCase<GrammaticalGender> MASCULINE_CASE =
      RawCase.create(GrammaticalGender.MASCULINE, MASCULINE_PARTS);
  private static final RawCase<GrammaticalGender> OTHER_CASE =
      RawCase.create(GrammaticalGender.OTHER, OTHER_PARTS);

  @Test
  public void getCases() {
    SoyMsgViewerGrammaticalGenderPartForRendering part =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getCases()).containsExactly(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE);
  }

  @Test
  public void getPartsForGender_exactMatch() {
    SoyMsgViewerGrammaticalGenderPartForRendering part =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getSoyMsgRawPartsForGender(GrammaticalGender.FEMININE))
        .isEqualTo(FEMININE_PARTS);
    assertThat(part.getSoyMsgRawPartsForGender(GrammaticalGender.MASCULINE))
        .isEqualTo(MASCULINE_PARTS);
  }

  @Test
  public void getPartsForGender_fallbackToOther() {
    SoyMsgViewerGrammaticalGenderPartForRendering part =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getSoyMsgRawPartsForGender(GrammaticalGender.NEUTER)).isEqualTo(OTHER_PARTS);
  }

  @Test
  public void getPartsForGender_fallbackToFirst() {
    SoyMsgViewerGrammaticalGenderPartForRendering part =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE));
    assertThat(part.getSoyMsgRawPartsForGender(GrammaticalGender.NEUTER)).isEqualTo(FEMININE_PARTS);
  }

  @Test
  public void getPartsForGender_unspecified() {
    SoyMsgViewerGrammaticalGenderPartForRendering part =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    assertThat(part.getSoyMsgRawPartsForGender(GrammaticalGender.UNSPECIFIED))
        .isEqualTo(OTHER_PARTS);
  }

  @Test
  public void toGenderPart() {
    SoyMsgViewerGrammaticalGenderPartForRendering part =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPart expected =
        new SoyMsgViewerGrammaticalGenderPart(
            ImmutableList.of(
                SoyMsgPart.Case.create(
                    GrammaticalGender.FEMININE, ImmutableList.of(SoyMsgRawTextPart.of("feminine"))),
                SoyMsgPart.Case.create(
                    GrammaticalGender.MASCULINE,
                    ImmutableList.of(SoyMsgRawTextPart.of("masculine"))),
                SoyMsgPart.Case.create(
                    GrammaticalGender.OTHER, ImmutableList.of(SoyMsgRawTextPart.of("other")))));
    assertThat(part.toGenderPart()).isEqualTo(expected);
  }

  @Test
  public void equals() {
    SoyMsgViewerGrammaticalGenderPartForRendering part1 =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPartForRendering part2 =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPartForRendering part3 =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE));

    assertThat(part1).isEqualTo(part2);
    assertThat(part1).isNotEqualTo(part3);
    assertThat(part2).isNotEqualTo(part3);
  }

  @Test
  public void testHashCode() {
    SoyMsgViewerGrammaticalGenderPartForRendering part1 =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPartForRendering part2 =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE, OTHER_CASE));
    SoyMsgViewerGrammaticalGenderPartForRendering part3 =
        new SoyMsgViewerGrammaticalGenderPartForRendering(
            ImmutableList.of(FEMININE_CASE, MASCULINE_CASE));

    assertThat(part1.hashCode()).isEqualTo(part2.hashCode());
    assertThat(part1.hashCode()).isNotEqualTo(part3.hashCode());
  }
}
