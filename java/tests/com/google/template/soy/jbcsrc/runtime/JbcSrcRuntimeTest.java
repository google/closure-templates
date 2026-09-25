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
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyFutureValueProvider;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime.MsgRenderer;
import com.google.template.soy.jbcsrc.shared.StackFrame;
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

  @Test
  public void testNullishChecks() {
    assertThat(JbcSrcRuntime.isNonSoyNullish(null)).isFalse();
    assertThat(JbcSrcRuntime.isNonSoyNull(null)).isFalse();
    assertThat(JbcSrcRuntime.isNonSoyUndefined(null)).isFalse();

    assertThat(JbcSrcRuntime.isNonSoyNullish(NullData.INSTANCE)).isFalse();
    assertThat(JbcSrcRuntime.isNonSoyNull(NullData.INSTANCE)).isFalse();
    assertThat(JbcSrcRuntime.isNonSoyUndefined(NullData.INSTANCE)).isTrue();

    assertThat(JbcSrcRuntime.isNonSoyNullish(UndefinedData.INSTANCE)).isFalse();
    assertThat(JbcSrcRuntime.isNonSoyNull(UndefinedData.INSTANCE)).isTrue();
    assertThat(JbcSrcRuntime.isNonSoyUndefined(UndefinedData.INSTANCE)).isFalse();

    StringData stringValue = StringData.forValue("foo");
    assertThat(JbcSrcRuntime.isNonSoyNullish(stringValue)).isTrue();
    assertThat(JbcSrcRuntime.isNonSoyNull(stringValue)).isTrue();
    assertThat(JbcSrcRuntime.isNonSoyUndefined(stringValue)).isTrue();

    DetachableContentProvider contentProvider =
        new DetachableContentProvider(ContentKind.HTML) {
          @Override
          protected StackFrame doRender(
              StackFrame frame, DetachableContentProvider.MultiplexingAppendable appendable) {
            throw new AssertionError("doRender should not be called for nullish checks!");
          }
        };
    assertThat(JbcSrcRuntime.isNonSoyNullish(contentProvider)).isTrue();
    assertThat(JbcSrcRuntime.isNonSoyNull(contentProvider)).isTrue();
    assertThat(JbcSrcRuntime.isNonSoyUndefined(contentProvider)).isTrue();
  }

  @Test
  public void testDcpEqualityFastPath() {
    DetachableContentProvider htmlProvider1 =
        new DetachableContentProvider(ContentKind.HTML) {
          @Override
          protected StackFrame doRender(
              StackFrame frame, DetachableContentProvider.MultiplexingAppendable appendable) {
            throw new AssertionError(
                "doRender should not be called for fast-path equality checks!");
          }
        };

    DetachableContentProvider htmlProvider2 =
        new DetachableContentProvider(ContentKind.HTML) {
          @Override
          protected StackFrame doRender(
              StackFrame frame, DetachableContentProvider.MultiplexingAppendable appendable) {
            throw new AssertionError(
                "doRender should not be called for fast-path equality checks!");
          }
        };

    StringData stringValue = StringData.forValue("foo");

    // Strict equality (===)
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, htmlProvider1))
        .isEqualTo(Boolean.TRUE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, null))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(null, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, NullData.INSTANCE))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(NullData.INSTANCE, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, UndefinedData.INSTANCE))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(UndefinedData.INSTANCE, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, stringValue))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(stringValue, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, htmlProvider2))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(stringValue, NullData.INSTANCE)).isNull();

    // Loose equality (==)
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, htmlProvider1))
        .isEqualTo(Boolean.TRUE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, null)).isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(null, htmlProvider1)).isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, NullData.INSTANCE))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(NullData.INSTANCE, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, UndefinedData.INSTANCE))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(UndefinedData.INSTANCE, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    // Comparing HTML to string or another HTML for loose equality requires evaluating content.
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, stringValue)).isNull();
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(stringValue, htmlProvider1)).isNull();
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, htmlProvider2)).isNull();

    // Futures
    SoyFutureValueProvider nullFuture =
        new SoyFutureValueProvider(immediateFuture(null), SoyValueConverter.INSTANCE::convert);
    SoyFutureValueProvider stringFuture =
        new SoyFutureValueProvider(immediateFuture("foo"), SoyValueConverter.INSTANCE::convert);

    // Strict equality with futures
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, nullFuture))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(nullFuture, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(htmlProvider1, stringFuture))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(stringFuture, htmlProvider1))
        .isEqualTo(Boolean.FALSE);

    // Loose equality with completed futures
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, nullFuture))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(nullFuture, htmlProvider1))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, stringFuture)).isNull();
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(stringFuture, htmlProvider1)).isNull();

    // Loose equality with pending future (returns null so detacher handles it)
    SettableFuture<String> pendingFuture = SettableFuture.create();
    SoyFutureValueProvider pendingFutureProvider =
        new SoyFutureValueProvider(pendingFuture, SoyValueConverter.INSTANCE::convert);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(htmlProvider1, pendingFutureProvider)).isNull();
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(pendingFutureProvider, htmlProvider1)).isNull();

    // ContentKind.TEXT provider
    DetachableContentProvider textProvider =
        new DetachableContentProvider(ContentKind.TEXT) {
          @Override
          protected StackFrame doRender(
              StackFrame frame, DetachableContentProvider.MultiplexingAppendable appendable) {
            throw new AssertionError(
                "doRender should not be called for fast-path equality checks!");
          }
        };

    // Strict equality: text provider does NOT short-circuit to FALSE against string future
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(textProvider, nullFuture))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(nullFuture, textProvider))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(textProvider, stringFuture)).isNull();
    assertThat(JbcSrcRuntime.checkTripleEqualDcpFastPath(stringFuture, textProvider)).isNull();

    // Loose equality: text provider short-circuits against null, falls back for strings
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(textProvider, nullFuture))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(nullFuture, textProvider))
        .isEqualTo(Boolean.FALSE);
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(textProvider, stringFuture)).isNull();
    assertThat(JbcSrcRuntime.checkEqualDcpFastPath(stringFuture, textProvider)).isNull();
  }
}
