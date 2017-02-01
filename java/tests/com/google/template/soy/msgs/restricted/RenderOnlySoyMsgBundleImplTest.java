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
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.template.soy.msgs.SoyMsgBundle;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RenderOnlySoyMsgBundleImpl.
 *
 */
@RunWith(JUnit4.class)
public class RenderOnlySoyMsgBundleImplTest {

  public static final String LOCALE = "xx";

  private ImmutableList<SoyMsg> testMessages;

  private SoyMsgBundle bundle;

  /** Creates a text-only message. */
  private SoyMsg createSimpleMsg(long id) {
    return new SoyMsg(id, LOCALE, false, ImmutableList.of(SoyMsgRawTextPart.of("Message #" + id)));
  }

  /** Creates a message with two parts. */
  private SoyMsg createMessageWithPlaceholder(long id) {
    return new SoyMsg(
        id,
        LOCALE,
        false,
        ImmutableList.of(SoyMsgRawTextPart.of("Message "), new SoyMsgPlaceholderPart("ph_" + id)));
  }

  /** Creates a message that has a select. */
  private SoyMsg createSelectMsg(long id) {
    return new SoyMsg(
        id,
        LOCALE,
        true,
        ImmutableList.of(
            new SoyMsgSelectPart(
                "varname",
                ImmutableList.of(
                    SoyMsgPart.Case.create(
                        "male", ImmutableList.of(SoyMsgRawTextPart.of("Male message " + id))),
                    SoyMsgPart.Case.create(
                        "female",
                        ImmutableList.of(SoyMsgRawTextPart.of("Female message " + id)))))));
  }

  @Before
  public void setUp() throws Exception {

    testMessages =
        ImmutableList.of(
            createSimpleMsg(314),
            createSimpleMsg(159),
            createSimpleMsg(265),
            createSimpleMsg(358),
            createSimpleMsg(979),
            createMessageWithPlaceholder(323),
            createMessageWithPlaceholder(846),
            createMessageWithPlaceholder(264),
            createSelectMsg(832),
            createSelectMsg(791),
            createSelectMsg(6065559473112027469L));
    bundle = new RenderOnlySoyMsgBundleImpl(LOCALE, testMessages);
  }

  @Test
  public void testBasic() {
    assertEquals(LOCALE, bundle.getLocaleString());
    assertEquals(testMessages.size(), bundle.getNumMsgs());
  }

  @Test
  public void testGetMsg() {
    for (SoyMsg message : testMessages) {
      SoyMsg actual = bundle.getMsg(message.getId());
      assertEquals(message, actual);
    }
  }

  @Test
  public void testIterator() {
    List<SoyMsg> actualMessages = Lists.newArrayList();
    long lastId = -1;
    for (SoyMsg message : bundle) {
      actualMessages.add(message);
      assertTrue("Messages should be in ID order.", message.getId() > lastId);
      lastId = message.getId();
    }
    // Test the size first, to protect against dupes.
    assertEquals(testMessages.size(), actualMessages.size());
    assertEquals(testMessages.size(), ImmutableSet.copyOf(actualMessages).size());
    // Now assert they contain the same messages.
    assertEquals(ImmutableSet.copyOf(testMessages), ImmutableSet.copyOf(actualMessages));
  }

  @Test
  public void testCopy() {
    // Take advantage of the fact that SoyMsgBundle actually implements Iterable<SoyMsg>.
    SoyMsgBundle copy = new RenderOnlySoyMsgBundleImpl(LOCALE, bundle);
    assertEquals(LOCALE, copy.getLocaleString());
    assertEquals(testMessages.size(), bundle.getNumMsgs());
    // Test they contain the same elements in the same order, while also taking advantage of the
    // fact it implements Iterable<SoyMsg>.
    assertEquals(ImmutableList.copyOf(bundle), ImmutableList.copyOf(copy));
  }
}
