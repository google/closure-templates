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

import static java.nio.charset.StandardCharsets.UTF_16;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.msgs.restricted.SoyMsgPart;
import com.google.template.soy.msgs.restricted.SoyMsgPlaceholderPart;
import com.google.template.soy.msgs.restricted.SoyMsgPluralCaseSpec;
import com.google.template.soy.msgs.restricted.SoyMsgPluralPart;
import com.google.template.soy.msgs.restricted.SoyMsgRawTextPart;

import junit.framework.TestCase;

import java.util.Random;
import java.util.Set;

/**
 * Unit tests for SoyMsgIdComputer.
 *
 */
public class SoyMsgIdComputerTest extends TestCase {


  private static final Random RANDOM_GEN = new Random();


  private static final ImmutableList<SoyMsgPart> HELLO_WORLD_MSG_PARTS =
      ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Hello world!"));

  private static final ImmutableList<SoyMsgPart> HELLO_NAME_MSG_PARTS = ImmutableList.of(
      SoyMsgRawTextPart.of("Hello "), new SoyMsgPlaceholderPart("NAME"),
      SoyMsgRawTextPart.of("!"));

  private static final ImmutableList<SoyMsgPart> PLURAL_MSG_PARTS =
      ImmutableList.<SoyMsgPart>of(
          new SoyMsgPluralPart("NUM_0", 0, ImmutableList.of(
              Pair.<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>>of(
                  new SoyMsgPluralCaseSpec(1),
                  ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Once"))),
              Pair.<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>>of(
                  new SoyMsgPluralCaseSpec("few"),
                  ImmutableList.<SoyMsgPart>of(
                      new SoyMsgPlaceholderPart("NUM_1"), SoyMsgRawTextPart.of(" times"))),
              Pair.<SoyMsgPluralCaseSpec, ImmutableList<SoyMsgPart>>of(
                  new SoyMsgPluralCaseSpec("other"),
                  ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Lots"))))));


  public void testFingerprint() {

    Set<Long> seenFps = Sets.newHashSetWithExpectedSize(100);
    byte[] bytes = new byte[20];
    for (int i = 0; i < 100; i++) {
      RANDOM_GEN.nextBytes(bytes);
      String randomString = new String(bytes, 0, 10 + RANDOM_GEN.nextInt(10), UTF_16);
      long fp = SoyMsgIdComputer.fingerprint(randomString);
      assertFalse(seenFps.contains(fp));
      seenFps.add(fp);
    }
  }


  public void testBuildMsgContentStrForMsgIdComputation() {

    assertEquals(
        "Hello world!",
        SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_WORLD_MSG_PARTS, false));
    assertEquals(
        "Hello world!",
        SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_WORLD_MSG_PARTS, true));

    assertEquals(
        "Hello NAME!",
        SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_NAME_MSG_PARTS, false));
    assertEquals(
        "Hello {NAME}!",
        SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(HELLO_NAME_MSG_PARTS, true));

    assertEquals(
        "{NUM_0,plural,=1{Once}few{NUM_1 times}other{Lots}}",
        SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(PLURAL_MSG_PARTS, false));
    assertEquals(
        "{NUM_0,plural,=1{Once}few{{NUM_1} times}other{Lots}}",
        SoyMsgIdComputer.buildMsgContentStrForMsgIdComputation(PLURAL_MSG_PARTS, true));
  }


  public void testKnownMsgIds() {

    // Important: Do not change these hard-coded values. Changes to the algorithm will break
    // backwards compatibility.

    assertEquals(
        3022994926184248873L, SoyMsgIdComputer.computeMsgId(HELLO_WORLD_MSG_PARTS, null, null));
    assertEquals(
        3022994926184248873L,
        SoyMsgIdComputer.computeMsgId(HELLO_WORLD_MSG_PARTS, null, "text/html"));

    assertEquals(
        6936162475751860807L, SoyMsgIdComputer.computeMsgId(HELLO_NAME_MSG_PARTS, null, null));
    assertEquals(
        6936162475751860807L,
        SoyMsgIdComputer.computeMsgId(HELLO_NAME_MSG_PARTS, null, "text/html"));

    assertEquals(
        947930983556630648L, SoyMsgIdComputer.computeMsgId(PLURAL_MSG_PARTS, null, null));
    assertEquals(
        1429579464553183506L,
        SoyMsgIdComputer.computeMsgIdUsingBracedPhs(PLURAL_MSG_PARTS, null, null));

    ImmutableList<SoyMsgPart> archiveMsgParts =
        ImmutableList.<SoyMsgPart>of(SoyMsgRawTextPart.of("Archive"));
    assertEquals(
        7224011416745566687L, SoyMsgIdComputer.computeMsgId(archiveMsgParts, "noun", null));
    assertEquals(
        4826315192146469447L, SoyMsgIdComputer.computeMsgId(archiveMsgParts, "verb", null));

    ImmutableList<SoyMsgPart> unicodeMsgParts = ImmutableList.of(
        new SoyMsgPlaceholderPart("\u2222\uEEEE"), SoyMsgRawTextPart.of("\u9EC4\u607A"));
    assertEquals(
        7971596007260280311L, SoyMsgIdComputer.computeMsgId(unicodeMsgParts, null, null));
    assertEquals(
        5109146044343713753L,
        SoyMsgIdComputer.computeMsgId(unicodeMsgParts, null, "application/javascript"));
  }

}
