/*
 * Copyright 2024 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.msgs.restricted.RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class RenderOnlyMsgIndexTest {

  /** Creates a text-only message. */
  private RenderOnlySoyMsg createSimpleMsg(long id) {
    return RenderOnlySoyMsg.create(id, SoyMsgRawParts.of("Message #" + id));
  }

  @Test
  public void testBasic() {
    var index = new RenderOnlyMsgIndex();
    var ids =
        ImmutableList.of(
            createSimpleMsg(1),
            createSimpleMsg(2),
            createSimpleMsg(3),
            createSimpleMsg(4),
            createSimpleMsg(5));
    var accessor = index.buildAccessor(ids);
    assertThat(accessor.getBasicTranslation(1)).isEqualTo("Message #1");
    assertThat(accessor.getBasicTranslation(2)).isEqualTo("Message #2");
    assertThat(accessor.getBasicTranslation(3)).isEqualTo("Message #3");
    assertThat(accessor.getBasicTranslation(4)).isEqualTo("Message #4");
    assertThat(accessor.getBasicTranslation(5)).isEqualTo("Message #5");
  }

  @Test
  public void testMultipleRounds() {
    var index = new RenderOnlyMsgIndex();
    var ids =
        ImmutableList.of(
            createSimpleMsg(1),
            createSimpleMsg(2),
            createSimpleMsg(3),
            createSimpleMsg(4),
            createSimpleMsg(5));

    var accessor1 = index.buildAccessor(ids.subList(0, 3));
    var accessor2 = index.buildAccessor(ids.subList(0, 5));

    assertThat(accessor1.getBasicTranslation(1)).isEqualTo("Message #1");
    assertThat(accessor1.getBasicTranslation(2)).isEqualTo("Message #2");
    assertThat(accessor1.getBasicTranslation(3)).isEqualTo("Message #3");
    assertThat(accessor1.getBasicTranslation(4)).isNull();
    assertThat(accessor1.getBasicTranslation(5)).isNull();

    assertThat(accessor2.getBasicTranslation(1)).isEqualTo("Message #1");
    assertThat(accessor2.getBasicTranslation(2)).isEqualTo("Message #2");
    assertThat(accessor2.getBasicTranslation(3)).isEqualTo("Message #3");
    assertThat(accessor2.getBasicTranslation(4)).isEqualTo("Message #4");
    assertThat(accessor2.getBasicTranslation(5)).isEqualTo("Message #5");
  }

  // force some collisions to test our recovering algorithm
  @Test
  public void testCollisions() {

    // With a size of 2 the table will be 4 slots. so we will have a collision at index 0.
    var accessor =
        new RenderOnlyMsgIndex()
            .buildAccessor(ImmutableList.of(createSimpleMsg(0), createSimpleMsg(4)));

    assertThat(accessor.getBasicTranslation(0)).isEqualTo("Message #0");
    assertThat(accessor.getBasicTranslation(4)).isEqualTo("Message #4");

    // With a size of 4 the table will be 8 slots. so we will have collisions at index 0 and 1, but
    // the collisions at 0 will override the collisions at 1.
    accessor =
        new RenderOnlyMsgIndex()
            .buildAccessor(
                ImmutableList.of(
                    createSimpleMsg(0),
                    createSimpleMsg(1),
                    createSimpleMsg(8),
                    createSimpleMsg(9)));
    assertThat(accessor.getBasicTranslation(0)).isEqualTo("Message #0");
    assertThat(accessor.getBasicTranslation(1)).isEqualTo("Message #1");
    assertThat(accessor.getBasicTranslation(8)).isEqualTo("Message #8");
    assertThat(accessor.getBasicTranslation(9)).isEqualTo("Message #9");

    // With a size of 8 the table will be 16 slots. so we will have collisions at indices 0, 1, and
    // 2, and 3. but there will be so many collisions that almost everything ends up in the wrong
    // spot.
    accessor =
        new RenderOnlyMsgIndex()
            .buildAccessor(
                ImmutableList.of(
                    createSimpleMsg(0),
                    createSimpleMsg(1),
                    createSimpleMsg(2),
                    createSimpleMsg(3),
                    createSimpleMsg(16),
                    createSimpleMsg(17),
                    createSimpleMsg(18),
                    createSimpleMsg(19)));
    assertThat(accessor.getBasicTranslation(0)).isEqualTo("Message #0");
    assertThat(accessor.getBasicTranslation(1)).isEqualTo("Message #1");
    assertThat(accessor.getBasicTranslation(2)).isEqualTo("Message #2");
    assertThat(accessor.getBasicTranslation(3)).isEqualTo("Message #3");
    assertThat(accessor.getBasicTranslation(16)).isEqualTo("Message #16");
    assertThat(accessor.getBasicTranslation(17)).isEqualTo("Message #17");
    assertThat(accessor.getBasicTranslation(18)).isEqualTo("Message #18");
    assertThat(accessor.getBasicTranslation(19)).isEqualTo("Message #19");
  }

  // Repro test for a bug where we raced constructing a table and miscalculated the size of the
  // table.
  @Test
  public void testRaceCondition() throws Exception {
    var index = new RenderOnlyMsgIndex();
    var ids = IntStream.range(0, 10000).mapToObj(this::createSimpleMsg).collect(toImmutableList());

    var executor = Executors.newFixedThreadPool(5);
    try {
      var futures =
          IntStream.range(0, 5)
              .mapToObj(i -> executor.submit(() -> index.buildAccessor(ids)))
              .collect(toImmutableList());
      for (var future : futures) {
        future.get();
      }
    } finally {
      executor.shutdown();
    }
    assertThat(index.size()).isEqualTo(10000);
  }
}
