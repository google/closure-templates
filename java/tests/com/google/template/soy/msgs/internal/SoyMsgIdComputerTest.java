/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.msgs.internal;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_16;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyMsgIdComputer.
 *
 */
@RunWith(JUnit4.class)
public class SoyMsgIdComputerTest {

  private static final Random RANDOM_GEN = new Random();

  private static final ImmutableList<SoyMsgPart> HELLO_WORLD_MSG_PARTS =
      ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Hello world!"));

  private static final ImmutableList<SoyMsgPart> HELLO_NAME_MSG_PARTS =
      ImmutableList.of(
          SoyMsgRawTextPart.of("Hello "),
          new SoyMsgPlaceholderPart("NAME", /* placeholderExample= */ null),
          SoyMsgRawTextPart.of("!"));

  private static final ImmutableList<SoyMsgPart> PLURAL_MSG_PARTS =
      ImmutableList.<SoyMsgPart>of(
          new SoyMsgPluralPart(
              "NUM_0",
              0,
              ImmutableList.of(
                  SoyMsgPluralPart.Case.create(
                      new SoyMsgPluralCaseSpec(1),
                      ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Once"))),
                  SoyMsgPluralPart.Case.create(
                      new SoyMsgPluralCaseSpec(SoyMsgPluralCaseSpec.Type.FEW),
                      ImmutableList.<SoyMsgPart>of(
                          new SoyMsgPlaceholderPart("NUM_1", /* placeholderExample= */ null),
                          SoyMsgRawTextPart.of(" times"))),
                  SoyMsgPluralPart.Case.create(
                      new SoyMsgPluralCaseSpec(SoyMsgPluralCaseSpec.Type.OTHER),
                      ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Lots"))))));

  @Test
  public void testFingerprint() {

    Set<Long> seenFps = Sets.newHashSetWithExpectedSize(100);
    byte[] bytes = new byte[20];
    for (int i = 0; i < 100; i++) {
      RANDOM_GEN.nextBytes(bytes);
      String randomString = new String(bytes, 0, 10 + RANDOM_GEN.nextInt(10), UTF_16);
      long fp = SoyMsgIdComputer.fingerprint(randomString);
      assertThat(seenFps).doesNotContain(fp);
      seenFps.add(fp);
    }
  }

  @Test
  public void testBuildMsgContentStrForMsgIdComputation() {
    assertThat(SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_WORLD_MSG_PARTS, false))
        .isEqualTo("Hello world!");
    assertThat(SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_WORLD_MSG_PARTS, true))
        .isEqualTo("Hello world!");

    assertThat(SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_NAME_MSG_PARTS, false))
        .isEqualTo("Hello NAME!");
    assertThat(SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_NAME_MSG_PARTS, true))
        .isEqualTo("Hello {NAME}!");

    assertThat(SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(PLURAL_MSG_PARTS, false))
        .isEqualTo("{NUM_0,plural,=1{Once}few{NUM_1 times}other{Lots}}");
    assertThat(SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(PLURAL_MSG_PARTS, true))
        .isEqualTo("{NUM_0,plural,=1{Once}few{{NUM_1} times}other{Lots}}");
  }

  @Test
  public void testKnownMsgIds() {

    // Important: Do not change these hard-coded values. Changes to the algorithm will break
    // backwards compatibility.

    assertThat(SoyMsgIdComputer.computeMsgId(HELLO_WORLD_MSG_PARTS, null, null))
        .isEqualTo(3022994926184248873L);
    assertThat(SoyMsgIdComputer.computeMsgId(HELLO_WORLD_MSG_PARTS, null, "text/html"))
        .isEqualTo(3022994926184248873L);

    assertThat(SoyMsgIdComputer.computeMsgId(HELLO_NAME_MSG_PARTS, null, null))
        .isEqualTo(6936162475751860807L);
    assertThat(SoyMsgIdComputer.computeMsgId(HELLO_NAME_MSG_PARTS, null, "text/html"))
        .isEqualTo(6936162475751860807L);

    assertThat(SoyMsgIdComputer.computeMsgId(PLURAL_MSG_PARTS, null, null))
        .isEqualTo(947930983556630648L);
    assertThat(SoyMsgIdComputer.computeMsgIdUsingBracedPhs(PLURAL_MSG_PARTS, null, null))
        .isEqualTo(1429579464553183506L);

    ImmutableList<SoyMsgPart> archiveMsgParts =
        ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Archive"));
    assertThat(SoyMsgIdComputer.computeMsgId(archiveMsgParts, "noun", null))
        .isEqualTo(7224011416745566687L);
    assertThat(SoyMsgIdComputer.computeMsgId(archiveMsgParts, "verb", null))
        .isEqualTo(4826315192146469447L);

    ImmutableList<SoyMsgPart> unicodeMsgParts =
        ImmutableList.of(
            new SoyMsgPlaceholderPart("\u2222\uEEEE", /* placeholderExample= */ null),
            SoyMsgRawTextPart.of("\u9EC4\u607A"));
    assertThat(SoyMsgIdComputer.computeMsgId(unicodeMsgParts, null, null))
        .isEqualTo(7971596007260280311L);
    assertThat(SoyMsgIdComputer.computeMsgId(unicodeMsgParts, null, "application/javascript"))
        .isEqualTo(5109146044343713753L);
  }
}
