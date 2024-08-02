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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.restricted.RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for RenderOnlySoyMsgBundleImpl.
 */
@RunWith(JUnit4.class)
public class RenderOnlySoyMsgBundleImplTest {

  public static final String LOCALE = "xx";

  private ImmutableList<SoyMsg> testMessages;

  private SoyMsgBundle bundle;

  /** Creates a text-only message. */
  private SoyMsg createSimpleMsg(long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(LOCALE)
        .setIsPlrselMsg(false)
        .setParts(ImmutableList.of(SoyMsgRawTextPart.of("Message #" + id)))
        .build();
  }

  /** Creates a message with two parts. */
  private SoyMsg createMessageWithPlaceholder(long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(LOCALE)
        .setIsPlrselMsg(false)
        .setParts(
            ImmutableList.of(
                SoyMsgRawTextPart.of("Message"), new SoyMsgPlaceholderPart("ph_" + id)))
        .build();
  }

  /** Creates a message that has a select. */
  private SoyMsg createSelectMsg(long id) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(LOCALE)
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
                            ImmutableList.of(SoyMsgRawTextPart.of("Female message " + id)))))))
        .build();
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
    bundle =
        new RenderOnlySoyMsgBundleImpl(
            new RenderOnlyMsgIndex(),
            LOCALE,
            testMessages.stream().map(RenderOnlySoyMsg::create).collect(toImmutableList()));
  }

  @Test
  public void testBasic() {
    assertThat(bundle.getLocaleString()).isEqualTo(LOCALE);
    assertThat(bundle.isRtl()).isFalse();
    assertThat(bundle.getNumMsgs()).isEqualTo(testMessages.size());
  }

  @Test
  public void testIsRtl() {
    var index = new RenderOnlyMsgIndex();
    assertThat(new RenderOnlySoyMsgBundleImpl(index, "ar", ImmutableList.of()).isRtl()).isTrue();
    assertThat(new RenderOnlySoyMsgBundleImpl(index, "iw", ImmutableList.of()).isRtl()).isTrue();
    assertThat(new RenderOnlySoyMsgBundleImpl(index, "fr", ImmutableList.of()).isRtl()).isFalse();
    assertThat(new RenderOnlySoyMsgBundleImpl(index, "en", ImmutableList.of()).isRtl()).isFalse();
  }

  @Test
  public void testGetMsg() {
    for (SoyMsg message : testMessages) {
      SoyMsg actual = bundle.getMsg(message.getId());
      assertThat(actual).isEqualTo(message);
    }
  }

  @Test
  public void testIterator() {
    List<SoyMsg> actualMessages = Lists.newArrayList();
    long lastId = -1;
    for (SoyMsg message : bundle) {
      actualMessages.add(message);
      assertWithMessage("Messages should be in ID order.").that(message.getId() > lastId).isTrue();
      lastId = message.getId();
    }
    // Test the size first, to protect against dupes.
    assertThat(actualMessages).hasSize(testMessages.size());
    assertThat((int) actualMessages.stream().distinct().count()).isEqualTo(testMessages.size());
    // Now assert they contain the same messages.
    assertThat(actualMessages).containsExactlyElementsIn(testMessages);
  }

  @Test
  public void testCopy() {
    // Take advantage of the fact that SoyMsgBundle actually implements Iterable<SoyMsg>.
    SoyMsgBundle copy = new RenderOnlySoyMsgBundleImpl(new RenderOnlyMsgIndex(), LOCALE, bundle);
    assertThat(copy.getLocaleString()).isEqualTo(LOCALE);
    assertThat(bundle).hasSize(testMessages.size());
    assertThat(copy).containsExactlyElementsIn(bundle).inOrder();
  }

  @Test
  @SuppressWarnings("ReturnValueIgnored")
  public void testEmptyBundlesDontOverAllocate() {
    // Prior issue introduced the possibility of mistaken large allocations for empty bundles.
    // This tries to OOM the test in the presence of such issues.
    IntStream.range(1, 10000)
        .mapToObj(
            i -> new RenderOnlySoyMsgBundleImpl(new RenderOnlyMsgIndex(), "fr", ImmutableList.of()))
        .collect(toImmutableList());
  }

  @Test
  public void testEmptyBundlesGetMsgReturnsNull() {
    assertThat(
            new RenderOnlySoyMsgBundleImpl(new RenderOnlyMsgIndex(), "fr", ImmutableList.of())
                .getMsg(123L))
        .isNull();
  }

  @Test
  public void testLargerBundle() {
    // Tests the hash table behavior.
    ImmutableList<SoyMsg> msgs =
        IntStream.range(1, 10000).mapToObj(this::createSimpleMsg).collect(toImmutableList());
    SoyMsgBundle largeBundle =
        new RenderOnlySoyMsgBundleImpl(
            new RenderOnlyMsgIndex(),
            LOCALE,
            msgs.stream().map(RenderOnlySoyMsg::create).collect(toImmutableList()));
    for (var msg : msgs) {
      assertThat(largeBundle.getMsg(msg.getId())).isEqualTo(msg);
    }
  }

  @Test
  public void dropsUnsupportedIcuMessages() {
    var bundle =
        new RenderOnlySoyMsgBundleImpl(
            new RenderOnlyMsgIndex(),
            LOCALE,
            new SoyMsgBundleImpl(
                LOCALE,
                ImmutableList.of(
                    SoyMsg.builder()
                        .setId(1)
                        .setLocaleString(LOCALE)
                        .setIsPlrselMsg(true)
                        .setParts(
                            ImmutableList.of(
                                new SoyMsgSelectPart(
                                    "varname",
                                    ImmutableList.of(
                                        SoyMsgPart.Case.create(
                                            "male",
                                            ImmutableList.of(SoyMsgRawTextPart.of("Male message"))),
                                        SoyMsgPart.Case.create(
                                            "female",
                                            ImmutableList.of(
                                                SoyMsgRawTextPart.of("Female message"))))),
                                new SoyMsgSelectPart(
                                    "varname",
                                    ImmutableList.of(
                                        SoyMsgPart.Case.create(
                                            "male",
                                            ImmutableList.of(SoyMsgRawTextPart.of("Male message"))),
                                        SoyMsgPart.Case.create(
                                            "female",
                                            ImmutableList.of(
                                                SoyMsgRawTextPart.of("Female message")))))))
                        .build())));
    assertThat(bundle.getMsg(1)).isNull();
    assertThat(bundle.getNumMsgs()).isEqualTo(0);
  }

  // Tests a bug where the message index contained a mapping for a message that one bundle didn't
  // have.
  @Test
  public void testOutOfRangeMiss() {
    var index = new RenderOnlyMsgIndex();
    SoyMsgBundle smallBundle =
        new RenderOnlySoyMsgBundleImpl(
            index,
            LOCALE,
            IntStream.range(0, 2)
                .mapToObj(this::createSimpleMsg)
                .map(RenderOnlySoyMsg::create)
                .collect(toImmutableList()));
    SoyMsgBundle largeBundle =
        new RenderOnlySoyMsgBundleImpl(
            index,
            LOCALE,
            IntStream.range(2, 10)
                .mapToObj(this::createSimpleMsg)
                .map(RenderOnlySoyMsg::create)
                .collect(toImmutableList()));
    assertThat(smallBundle.getMsg(9L)).isNull();
    assertThat(largeBundle.getMsg(9L)).isEqualTo(createSimpleMsg(9L));
  }
}
