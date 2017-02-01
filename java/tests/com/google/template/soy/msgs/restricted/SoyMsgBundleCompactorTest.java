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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.SoyMsgBundle;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyMsgBundleCompactor.
 *
 */
@RunWith(JUnit4.class)
public class SoyMsgBundleCompactorTest {

  public static final String LOCALE_XX = "xx";
  public static final String LOCALE_YY = "yy";

  private SoyMsgBundle xxMsgBundle;
  private SoyMsgBundle yyMsgBundle;

  /** Creates a text-only message. */
  private SoyMsg createSimpleMsg(String locale, long id) {
    return new SoyMsg(
        id, locale, false, ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Message #" + id)));
  }

  /** Creates a message with two parts. */
  private SoyMsg createMessageWithPlaceholder(String locale, long id) {
    return new SoyMsg(
        id,
        locale,
        false,
        ImmutableList.of(SoyMsgRawTextPart.of("Message "), new SoyMsgPlaceholderPart("ph_" + id)));
  }

  /** Creates a message that has a select with different cases. */
  private SoyMsg createSelectMsgDifferent(String locale, long id) {
    return new SoyMsg(
        id,
        locale,
        true,
        ImmutableList.of(
            new SoyMsgSelectPart(
                "varname",
                ImmutableList.of(
                    SoyMsgPart.Case.create(
                        "male", ImmutableList.of(SoyMsgRawTextPart.of("Male message " + id))),
                    SoyMsgPart.Case.create(
                        "female", ImmutableList.of(SoyMsgRawTextPart.of("Female message " + id))),
                    SoyMsgPart.Case.create(
                        (String) null,
                        ImmutableList.of(SoyMsgRawTextPart.of("Other message " + id)))))));
  }

  /** Creates a message that has a select with identical cases. */
  private SoyMsg createSelectMsgSame(String locale, long id) {
    return new SoyMsg(
        id,
        locale,
        true,
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
                        ImmutableList.of(SoyMsgRawTextPart.of("Same message " + id)))))));
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
                createSelectMsgSame(LOCALE_XX, 358)));
    xxMsgBundle = compactor.compact(xxMsgBundle);
    yyMsgBundle =
        new SoyMsgBundleImpl(
            LOCALE_YY,
            ImmutableList.of(
                createSimpleMsg(LOCALE_YY, 314),
                createMessageWithPlaceholder(LOCALE_YY, 159),
                createSelectMsgDifferent(LOCALE_YY, 265),
                createSelectMsgDifferent(LOCALE_YY, 266),
                createSelectMsgSame(LOCALE_YY, 358)));
    yyMsgBundle = compactor.compact(yyMsgBundle);
  }

  @Test
  public void testInterning() {
    assertSame(
        "SoyMsgRawTextPart should be interned",
        xxMsgBundle.getMsg(314).getParts().get(0),
        yyMsgBundle.getMsg(314).getParts().get(0));
    assertSame(
        "SoyMsgRawTextPart should be interned",
        xxMsgBundle.getMsg(159).getParts().get(0),
        yyMsgBundle.getMsg(159).getParts().get(0));
    assertSame(
        "SoyMsgPlaceholderPart should be interned",
        xxMsgBundle.getMsg(159).getParts().get(1),
        yyMsgBundle.getMsg(159).getParts().get(1));
    assertSame(
        "SoyMsgSelectPart should be interned",
        xxMsgBundle.getMsg(265).getParts().get(0),
        yyMsgBundle.getMsg(265).getParts().get(0));
    assertSame(
        "SoyMsgSelectPart should be interned",
        xxMsgBundle.getMsg(358).getParts().get(0),
        yyMsgBundle.getMsg(358).getParts().get(0));

    SoyMsgSelectPart select1 = (SoyMsgSelectPart) xxMsgBundle.getMsg(265).getParts().get(0);
    SoyMsgSelectPart select2 = (SoyMsgSelectPart) xxMsgBundle.getMsg(266).getParts().get(0);
    assertNotSame(select1, select2);
    assertSame(
        "Select var names should be interned",
        select1.getSelectVarName(),
        select2.getSelectVarName());
    assertSame(
        "Case values should be interned",
        select1.getCases().get(0).spec(),
        select2.getCases().get(0).spec());
  }

  @Test
  public void testCaseCollapsing() {
    SoyMsgSelectPart differentSelect = (SoyMsgSelectPart) xxMsgBundle.getMsg(265).getParts().get(0);
    SoyMsgSelectPart sameSelect = (SoyMsgSelectPart) xxMsgBundle.getMsg(358).getParts().get(0);
    assertEquals(
        "Selects with different cases should not be collapsed",
        3,
        differentSelect.getCases().size());
    assertEquals(
        "Selects with the same case should be collapsed",
        ImmutableList.of(
            SoyMsgPart.Case.<String>create(
                null, ImmutableList.of(SoyMsgRawTextPart.of("Same message 358")))),
        sameSelect.getCases());
  }
}
