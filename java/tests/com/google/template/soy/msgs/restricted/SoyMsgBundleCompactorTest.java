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

package com.google.template.soy.msgs.restricted;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.GrammaticalGender;
import com.google.template.soy.msgs.SoyMsgBundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyMsgBundleCompactor.
 */
@RunWith(JUnit4.class)
public class SoyMsgBundleCompactorTest {

  public static final String LOCALE_XX = "xx";
  public static final String LOCALE_YY = "yy";

  private SoyMsgBundle xxMsgBundle;
  private SoyMsgBundle yyMsgBundle;

  /** Creates a text-only message. */
  private SoyMsg createSimpleMsg(String locale, long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(locale)
        .setIsPlrselMsg(false)
        .setParts(ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Message #" + id)))
        .build();
  }

  /** Creates a message with two parts. */
  private SoyMsg createMessageWithPlaceholder(String locale, long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(locale)
        .setIsPlrselMsg(false)
        .setParts(
            ImmutableList.of(
                SoyMsgRawTextPart.of("Message "), new SoyMsgPlaceholderPart("ph_" + id)))
        .build();
  }

  /** Creates a message that has a select with different cases. */
  private SoyMsg createSelectMsgDifferent(String locale, long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(locale)
        .setIsPlrselMsg(true)
        .setParts(
            ImmutableList.of(
                new SoyMsgSelectPart(
                    "varname",
                    ImmutableList.of(
                        SoyMsgPart.Case.create(
                            "male", ImmutableList.of(SoyMsgRawTextPart.of("Male message " + id))),
                        SoyMsgPart.Case.create(
                            "female",
                            ImmutableList.of(SoyMsgRawTextPart.of("Female message " + id))),
                        SoyMsgPart.Case.create(
                            (String) null,
                            ImmutableList.of(SoyMsgRawTextPart.of("Other message " + id)))))))
        .build();
  }

  /** Creates a message that has a select with identical cases. */
  private SoyMsg createSelectMsgSame(String locale, long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(locale)
        .setIsPlrselMsg(true)
        .setParts(
            ImmutableList.of(
                new SoyMsgSelectPart(
                    "varname",
                    ImmutableList.of(
                        SoyMsgPart.Case.create(
                            "male", ImmutableList.of(SoyMsgRawTextPart.of("Same message " + id))),
                        SoyMsgPart.Case.create(
                            "female", ImmutableList.of(SoyMsgRawTextPart.of("Same message " + id))),
                        SoyMsgPart.Case.create(
                            (String) null,
                            ImmutableList.of(SoyMsgRawTextPart.of("Same message " + id)))))))
        .build();
  }

  /** Creates a message that has a select with identical cases. */
  private SoyMsg createPluralWithRedundantCases(String locale, long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(locale)
        .setIsPlrselMsg(true)
        .setParts(
            ImmutableList.of(
                new SoyMsgPluralPart(
                    "varname",
                    0,
                    ImmutableList.of(
                        SoyMsgPart.Case.create(
                            SoyMsgPluralCaseSpec.forType(SoyMsgPluralCaseSpec.Type.OTHER),
                            ImmutableList.of(SoyMsgRawTextPart.of("unused plural form"))),
                        SoyMsgPart.Case.create(
                            new SoyMsgPluralCaseSpec(1),
                            ImmutableList.of(SoyMsgRawTextPart.of("1 coconut"))),
                        SoyMsgPart.Case.create(
                            new SoyMsgPluralCaseSpec(1),
                            ImmutableList.of(SoyMsgRawTextPart.of("1 coconut, again")))))))
        .build();
  }

  /** Creates a message that has a select with different cases. */
  private SoyMsg createGrammaticalGenderMsg(String locale, long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(locale)
        .setIsPlrselMsg(true)
        .setParts(
            ImmutableList.of(
                new SoyMsgViewerGrammaticalGenderPart(
                    ImmutableList.of(
                        SoyMsgPart.Case.create(
                            GrammaticalGender.MASCULINE,
                            ImmutableList.of(SoyMsgRawTextPart.of("Male message " + id))),
                        SoyMsgPart.Case.create(
                            GrammaticalGender.FEMININE,
                            ImmutableList.of(SoyMsgRawTextPart.of("Female message " + id))),
                        SoyMsgPart.Case.create(
                            GrammaticalGender.OTHER,
                            ImmutableList.of(SoyMsgRawTextPart.of("Other message " + id)))))))
        .build();
  }

  @Before
  public void setUp() throws Exception {

    SoyMsgBundleCompactor compactor = new SoyMsgBundleCompactor();
    xxMsgBundle =
        new SoyMsgBundleImpl(
            LOCALE_XX,
            ImmutableList.of(
                createSimpleMsg(LOCALE_XX, 314),
                createMessageWithPlaceholder(LOCALE_XX, 159),
                createSelectMsgDifferent(LOCALE_XX, 265),
                createSelectMsgDifferent(LOCALE_XX, 266),
                createGrammaticalGenderMsg(LOCALE_XX, 81),
                createSelectMsgSame(LOCALE_XX, 358),
                createPluralWithRedundantCases(LOCALE_XX, 42)));
    xxMsgBundle = compactor.compact(xxMsgBundle);
    yyMsgBundle =
        new SoyMsgBundleImpl(
            LOCALE_YY,
            ImmutableList.of(
                createSimpleMsg(LOCALE_YY, 314),
                createMessageWithPlaceholder(LOCALE_YY, 159),
                createSelectMsgDifferent(LOCALE_YY, 265),
                createSelectMsgDifferent(LOCALE_YY, 266),
                createGrammaticalGenderMsg(LOCALE_YY, 81),
                createSelectMsgSame(LOCALE_YY, 358),
                createPluralWithRedundantCases(LOCALE_YY, 42)));
    yyMsgBundle = compactor.compact(yyMsgBundle);
  }

  @Test
  public void testInterning() {
    assertWithMessage("SoyMsgRawTextPart should be interned")
        .that(yyMsgBundle.getMsgPartsForRendering(314).getPart(0))
        .isSameInstanceAs(xxMsgBundle.getMsgPartsForRendering(314).getPart(0));
    assertWithMessage("SoyMsgRawTextPart should be interned")
        .that(yyMsgBundle.getMsgPartsForRendering(159).getPart(0))
        .isSameInstanceAs(xxMsgBundle.getMsgPartsForRendering(159).getPart(0));
    assertWithMessage("SoyMsgPlaceholderPart should be interned")
        .that(yyMsgBundle.getMsgPartsForRendering(159).getPart(1))
        .isSameInstanceAs(xxMsgBundle.getMsgPartsForRendering(159).getPart(1));
    assertWithMessage("SoyMsgSelectPart should be interned")
        .that(yyMsgBundle.getMsgPartsForRendering(265))
        .isSameInstanceAs(xxMsgBundle.getMsgPartsForRendering(265));
    assertWithMessage("SoyMsgViewerGrammaticalGenderPart should be interned")
        .that(yyMsgBundle.getMsgPartsForRendering(81))
        .isSameInstanceAs(xxMsgBundle.getMsgPartsForRendering(81));
    assertWithMessage("SoyMsgSelectPart should be interned")
        .that(yyMsgBundle.getMsgPartsForRendering(358))
        .isSameInstanceAs(xxMsgBundle.getMsgPartsForRendering(358));

    var select1 = (SoyMsgSelectPartForRendering) xxMsgBundle.getMsgPartsForRendering(265);
    var select2 = (SoyMsgSelectPartForRendering) xxMsgBundle.getMsgPartsForRendering(266);
    assertThat(select2).isNotSameInstanceAs(select1);
    assertWithMessage("Select var names should be interned")
        .that(select2.getSelectVarName())
        .isSameInstanceAs(select1.getSelectVarName());
    assertWithMessage("Case values should be interned")
        .that(select2.toSelectPart().getCases().get(0).spec())
        .isSameInstanceAs(select1.toSelectPart().getCases().get(0).spec());
  }

  @Test
  public void testCaseCollapsing() {
    var differentSelect =
        ((SoyMsgSelectPartForRendering) xxMsgBundle.getMsgPartsForRendering(265)).toSelectPart();
    var sameSelect =
        ((SoyMsgSelectPartForRendering) xxMsgBundle.getMsgPartsForRendering(358)).toSelectPart();
    assertWithMessage("Selects with different cases should not be collapsed")
        .that(differentSelect.getCases().size())
        .isEqualTo(3);
    assertWithMessage("Selects with the same case should be collapsed")
        .that(sameSelect.getCases())
        .containsExactly(
            SoyMsgPart.Case.<String>create(
                null, ImmutableList.of(SoyMsgRawTextPart.of("Same message 358"))));
  }

  @Test
  public void testPluralWithDuplicateCases() {
    assertThat(
            ((SoyMsgPluralPartForRendering) xxMsgBundle.getMsgPartsForRendering(42))
                .lookupCase(1, null))
        .isEqualTo(SoyMsgRawParts.of("1 coconut"));
  }

  @Test
  public void testGrammaticalGender() {
    assertThat(
            ((SoyMsgViewerGrammaticalGenderPartForRendering)
                    xxMsgBundle.getMsgPartsForRendering(81))
                .getSoyMsgRawPartsForGender(GrammaticalGender.MASCULINE))
        .isEqualTo(SoyMsgRawParts.of("Male message 81"));
  }

  @Test
  public void testSoyMsgViewerGrammaticalGenderPartForRendering() {
    SoyMsg msg = createGrammaticalGenderMsg(LOCALE_XX, 123);
    SoyMsgBundle bundle = new SoyMsgBundleImpl(LOCALE_XX, ImmutableList.of(msg));
    SoyMsgBundleCompactor compactor = new SoyMsgBundleCompactor();
    SoyMsgBundle compactedBundle = compactor.compact(bundle);

    SoyMsgViewerGrammaticalGenderPartForRendering genderPart =
        (SoyMsgViewerGrammaticalGenderPartForRendering)
            compactedBundle.getMsgPartsForRendering(123);

    assertThat(genderPart.getSoyMsgRawPartsForGender(GrammaticalGender.MASCULINE))
        .isEqualTo(SoyMsgRawParts.of("Male message 123"));
    assertThat(genderPart.getSoyMsgRawPartsForGender(GrammaticalGender.FEMININE))
        .isEqualTo(SoyMsgRawParts.of("Female message 123"));
    assertThat(genderPart.getSoyMsgRawPartsForGender(GrammaticalGender.OTHER))
        .isEqualTo(SoyMsgRawParts.of("Other message 123"));
  }
}
