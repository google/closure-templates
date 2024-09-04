/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.jbcsrc.runtime;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime.MsgRenderer;
import com.google.template.soy.msgs.restricted.PlaceholderName;
import com.google.template.soy.msgs.restricted.SoyMsgRawParts;
import java.util.function.ToIntFunction;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JbcSrcRuntimeTest {

  private static ToIntFunction<PlaceholderName> placeholderIndexFunction(PlaceholderName... names) {
    return (n) -> {
      for (int i = 0; i < names.length; i++) {
        if (n == names[i]) {
          return i;
        }
      }
      throw new IllegalArgumentException();
    };
  }

  @Test
  public void testMessageRendering() {
    var name = PlaceholderName.create("NAME");
    MsgRenderer renderer =
        new MsgRenderer(
            SoyMsgRawParts.builder().add("Hello ").add(name).add(".").build(),
            placeholderIndexFunction(name),
            ImmutableList.of(StringData.forValue("world")),
            /* htmlEscape= */ false,
            /* endPlaceholderToStartPlaceholder= */ null);

    assertRendersAs(renderer, "Hello world.");
  }

  @Test
  public void testMessageRendering_orderConstraints() {
    var start = PlaceholderName.create("LINK_START");
    var end = PlaceholderName.create("LINK_END");
    MsgRenderer renderer =
        new MsgRenderer(
            SoyMsgRawParts.builder().add("Hello ").add(start).add("world.").add(end).build(),
            placeholderIndexFunction(start, end),
            ImmutableList.of(StringData.forValue("<a>"), StringData.forValue("</a>")),
            /* htmlEscape= */ false,
            /* endPlaceholderToStartPlaceholder= */ ImmutableSetMultimap.of(end, start));

    assertRendersAs(renderer, "Hello <a>world.</a>");
  }

  @Test
  public void testMessageRender_orderingConstraints_reversed() {
    // imagine that the translator has reordered the placeholders incorrectly
    var start = PlaceholderName.create("LINK_START");
    var end = PlaceholderName.create("LINK_END");
    MsgRenderer renderer =
        new MsgRenderer(
            SoyMsgRawParts.builder().add("Hello ").add(end).add("world.").add(start).build(),
            placeholderIndexFunction(start, end),
            ImmutableList.of(StringData.forValue("<a>"), StringData.forValue("</a>")),
            /* htmlEscape= */ false,
            /* endPlaceholderToStartPlaceholder= */ ImmutableSetMultimap.of(end, start));

    assertThat(assertThrows(IllegalStateException.class, renderer::status))
        .hasMessageThat()
        .isEqualTo("Expected placeholder 'LINK_END' to come after one of [LINK_START]");
  }

  @Test
  public void testMessageRender_orderingConstraints_missingStart() {
    // imagine that the translator has dropped a start placeholder
    var start = PlaceholderName.create("LINK_START");
    var end = PlaceholderName.create("LINK_END");
    MsgRenderer renderer =
        new MsgRenderer(
            SoyMsgRawParts.builder().add("Hello ").add("world.").add(end).build(),
            placeholderIndexFunction(start, end),
            ImmutableList.of(StringData.forValue("<a>"), StringData.forValue("</a>")),
            /* htmlEscape= */ false,
            /* endPlaceholderToStartPlaceholder= */ ImmutableSetMultimap.of(end, start));

    assertThat(assertThrows(IllegalStateException.class, renderer::status))
        .hasMessageThat()
        .isEqualTo("Expected placeholder 'LINK_END' to come after one of [LINK_START]");
  }

  @Test
  public void testMessageRender_orderingConstraints_missingEnd() {
    // imagine that the translator has dropped an end placeholder
    var start = PlaceholderName.create("LINK_START");
    var end = PlaceholderName.create("LINK_END");
    MsgRenderer renderer =
        new MsgRenderer(
            SoyMsgRawParts.builder().add("Hello ").add(start).add("world.").build(),
            placeholderIndexFunction(start, end),
            ImmutableList.of(StringData.forValue("<a>"), StringData.forValue("</a>")),
            /* htmlEscape= */ false,
            /* endPlaceholderToStartPlaceholder= */ ImmutableSetMultimap.of(end, start));

    assertThat(assertThrows(IllegalStateException.class, renderer::status))
        .hasMessageThat()
        .isEqualTo(
            "The following placeholders never had their matching placeholders rendered:"
                + " [PlaceholderName{LINK_START}]");
  }

  @Test
  public void testMessageRender_orderingConstraints_oneEndForMultipleStarts() {
    // This is fairly common in real soy templates since the end tags always match they get one
    // placeholder
    var start1 = PlaceholderName.create("LINK_START_1");
    var start2 = PlaceholderName.create("LINK_START_2");
    var end = PlaceholderName.create("LINK_END");
    MsgRenderer renderer =
        new MsgRenderer(
            SoyMsgRawParts.builder()
                .add(start1)
                .add("Hello ")
                .add(end)
                .add(start2)
                .add("world.")
                .add(end)
                .build(),
            placeholderIndexFunction(start1, start2, end),
            ImmutableList.of(
                StringData.forValue("<a href='./foo'>"),
                StringData.forValue("<a href='./bar'>"),
                StringData.forValue("</a>")),
            /* htmlEscape= */ false,
            /* endPlaceholderToStartPlaceholder= */ ImmutableSetMultimap.of(
                end, start1, end, start2));
    // renders fine
    assertRendersAs(renderer, "<a href='./foo'>Hello </a><a href='./bar'>world.</a>");
  }

  static class FakeProvider extends SoyValueProvider {
    RenderResult result;
    int calls;

    FakeProvider() {}

    FakeProvider(RenderResult result) {
      this.result = result;
    }

    @Override
    public RenderResult renderAndResolve(LoggingAdvisingAppendable advisingAppendable) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SoyValue resolve() {
      throw new UnsupportedOperationException();
    }

    @Override
    public RenderResult status() {
      calls++;
      return result;
    }
  }

  @Test
  public void testAwaitProvider_done() {
    FakeProvider provider = new FakeProvider(RenderResult.done());
    JbcSrcRuntime.awaitProvider(provider);
    assertThat(provider.calls).isEqualTo(1);
  }

  @Test
  public void testAwaitProvider_limited() {
    FakeProvider provider = new FakeProvider(RenderResult.limited());
    assertThrows(AssertionError.class, () -> JbcSrcRuntime.awaitProvider(provider));
    assertThat(provider.calls).isEqualTo(1);
  }

  @Test
  public void testAwaitProvider_detachOnce() {
    FakeProvider provider =
        new FakeProvider() {
          @Override
          public RenderResult status() {
            return calls++ == 0
                ? RenderResult.continueAfter(immediateFuture("hello"))
                : RenderResult.done();
          }
        };
    JbcSrcRuntime.awaitProvider(provider);
    assertThat(provider.calls).isEqualTo(2);
  }

  @Test
  public void testAwaitProvider_detachManyTimes() {
    FakeProvider provider =
        new FakeProvider() {
          @Override
          public RenderResult status() {
            return calls++ < 19
                ? RenderResult.continueAfter(immediateFuture("hello"))
                : RenderResult.done();
          }
        };
    JbcSrcRuntime.awaitProvider(provider);
    assertThat(provider.calls).isEqualTo(20);
  }

  private void assertRendersAs(MsgRenderer renderer, String expected) {
    assertThat(renderer.status().isDone()).isTrue();
    assertThat(renderer.resolve().coerceToString()).isEqualTo(expected);
  }
}
