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
package com.google.template.soy.jbcsrc.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import java.io.IOException;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DetachableContentProviderTest {

  @Test
  public void testInfersTypeFromLoggedKind() {
    SoyValue v =
        new DetachableContentProvider(SanitizedContent.ContentKind.CSS) {
          @Override
          protected StackFrame doRender(
              StackFrame stack, DetachableContentProvider.MultiplexingAppendable appendable) {
            try {
              appendable.append("foo");
              return null;
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }.resolve();
    assertThat(v).isEqualTo(SanitizedContents.constantCss("foo"));
  }

  static class TestDetachableContentProvider extends DetachableContentProvider {
    final Future<String> future1;
    final Future<String> future2;

    TestDetachableContentProvider(Future<String> future1, Future<String> future2) {
      super(SanitizedContent.ContentKind.CSS);
      this.future1 = future1;
      this.future2 = future2;
    }

    @Override
    protected StackFrame doRender(
        StackFrame stack, DetachableContentProvider.MultiplexingAppendable appendable) {
      try {
        switch (stack == null ? 0 : stack.stateNumber) {
          case 0:
            appendable.append("start\n");
            // fall-through
          case 1:
            if (future1.isDone()) {
              appendable.append("future1: ").append(Futures.getUnchecked(future1)).append("\n");
            } else {
              return StackFrame.create(RenderResult.continueAfter(future1), 1);
            }
            // fall-through
          case 2:
            if (future2.isDone()) {
              appendable.append("future2: ").append(Futures.getUnchecked(future2)).append("\n");
            } else {
              return StackFrame.create(RenderResult.continueAfter(future2), 2);
            }
            appendable.append("end\n");
            return null;
          default:
            throw new IllegalStateException();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Test
  public void testDetaching_status() {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    DetachableContentProvider provider = new TestDetachableContentProvider(future1, future2);
    var result = provider.status();
    assertThat(result).isEqualTo(RenderResult.continueAfter(future1));
    future1.set("hello");
    result = provider.status();
    assertThat(result).isEqualTo(RenderResult.continueAfter(future2));
    future2.set("goodbye");
    result = provider.status();
    assertThat(result).isEqualTo(RenderResult.done());
    assertThat(provider.resolve())
        .isEqualTo(SanitizedContents.constantCss("start\nfuture1: hello\nfuture2: goodbye\nend\n"));
  }

  @Test
  public void testDetaching_renderAndResolve() throws IOException {
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    LoggingAdvisingAppendable.BufferingAppendable appendable =
        LoggingAdvisingAppendable.buffering();
    DetachableContentProvider provider = new TestDetachableContentProvider(future1, future2);
    var result = provider.renderAndResolve(appendable);
    assertThat(result).isEqualTo(RenderResult.continueAfter(future1));

    future1.set("hello");
    result = provider.renderAndResolve(appendable);
    assertThat(result).isEqualTo(RenderResult.continueAfter(future2));

    future2.set("goodbye");
    result = provider.renderAndResolve(appendable);
    assertThat(result).isEqualTo(RenderResult.done());
    assertThat(appendable.getAsSoyValue())
        .isEqualTo(SanitizedContents.constantCss("start\nfuture1: hello\nfuture2: goodbye\nend\n"));
  }

  // Demonstrates an issue where if a detachable provider is partially evaluated with multiple
  // appendables, we don't get all the content.  This isn't currently a bug due to how the code is
  // generated because we are guaranteed that once a DetachableContentProvider returns !done we will
  // always call back with the exact same appendable.
  @Test
  public void testDetaching_renderAndResolveWithMultipleAppendables() throws IOException {
    LoggingAdvisingAppendable.BufferingAppendable appendable =
        LoggingAdvisingAppendable.buffering();
    LoggingAdvisingAppendable.BufferingAppendable appendable2 =
        LoggingAdvisingAppendable.buffering();
    SettableFuture<String> future1 = SettableFuture.create();
    SettableFuture<String> future2 = SettableFuture.create();
    DetachableContentProvider provider = new TestDetachableContentProvider(future1, future2);
    var result = provider.renderAndResolve(appendable);
    assertThat(result).isEqualTo(RenderResult.continueAfter(future1));
    assertThat(appendable.toString()).isEqualTo("start\n");

    result = provider.renderAndResolve(appendable2);
    assertThat(result).isEqualTo(RenderResult.continueAfter(future1));
    // sync'd up
    assertThat(appendable2.toString()).isEqualTo("start\n");

    future1.set("hello");
    result = provider.renderAndResolve(appendable);
    assertThat(result).isEqualTo(RenderResult.continueAfter(future2));
    assertThat(appendable.getAsSoyValue())
        .isEqualTo(SanitizedContents.constantCss("start\nfuture1: hello\n"));

    future2.set("goodbye");
    result = provider.renderAndResolve(appendable2);
    assertThat(result).isEqualTo(RenderResult.done());
    assertThat(appendable2.getAsSoyValue())
        .isEqualTo(SanitizedContents.constantCss("start\nfuture1: hello\nfuture2: goodbye\nend\n"));
    // Now complete on the original appendable
    result = provider.renderAndResolve(appendable);
    assertThat(result).isEqualTo(RenderResult.done());
    assertThat(appendable.getAsSoyValue())
        .isEqualTo(SanitizedContents.constantCss("start\nfuture1: hello\nfuture2: goodbye\nend\n"));
  }

}
