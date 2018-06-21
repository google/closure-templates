/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.msgs;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.msgs.SoyMsgIdConverter.convertSoyMessageIdStrings;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyMsgIdConverterTest {

  @Test
  public void testConvertEmptyList() throws Exception {
    assertThat(convertSoyMessageIdStrings(ImmutableList.of())).isEmpty();
  }

  @Test
  public void testConvertSuccessfulForKnownString() throws Exception {
    assertThat(convertSoyMessageIdStrings(ImmutableList.of("URTo5tnzILU=")))
        .containsExactly(5842550694803087541L);
  }

  @Test
  public void testMultipleIdsPreserveOrder() throws Exception {
    assertThat(
            convertSoyMessageIdStrings(
                ImmutableList.of(
                    "URTo5tnzILU=",
                    "a-WqMgFbuu4=",
                    "D3hMAFrdrnc=",
                    "ehM7NQSNNkw=",
                    "GmWS9t-N7Mk=")))
        .containsExactly(
            5842550694803087541L,
            7774807463472904942L,
            1114724472182386295L,
            8796439596080379468L,
            1902088006644133065L);
  }

  @Test
  public void testRepeatedIdsPreserved() throws Exception {
    assertThat(
            convertSoyMessageIdStrings(
                ImmutableList.of(
                    "URTo5tnzILU=",
                    "a-WqMgFbuu4=",
                    "URTo5tnzILU=",
                    "a-WqMgFbuu4=",
                    "a-WqMgFbuu4=")))
        .containsExactly(
            5842550694803087541L,
            7774807463472904942L,
            5842550694803087541L,
            7774807463472904942L,
            7774807463472904942L);
  }

  @Test
  public void testShortInputThrowsException() throws Exception {
    try {
      convertSoyMessageIdStrings(ImmutableList.of("Zm9v"));
      fail();
    } catch (IllegalArgumentException expected) {}
  }

  @Test
  public void testLongInputThrowsException() throws Exception {
    try {
      convertSoyMessageIdStrings(ImmutableList.of("VGhpc0lzQVZlcnlMb25nU3RyaW5n"));
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("was of invalid size");
    }
  }

  @Test
  public void nonUrlSafeBase64Fails() throws Exception {
    String validBase64UrlSafe = "a-WqMgFbuu4=";
    String validBase64 =
        BaseEncoding.base64().encode(BaseEncoding.base64Url().decode(validBase64UrlSafe));

    try {
      convertSoyMessageIdStrings(ImmutableList.of(validBase64));
      fail();
    } catch (IllegalArgumentException expected) {}
  }
}
