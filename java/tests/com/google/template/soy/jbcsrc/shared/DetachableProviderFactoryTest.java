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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.runtime.DetachableContentProvider;
import com.google.template.soy.jbcsrc.runtime.DetachableSoyValueProvider;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DetachableProviderFactoryTest {
  private static final MethodHandles.Lookup lookup = MethodHandles.lookup();

  public static Object simpleMethod(boolean optimistic, String capture) {
    assertThat(optimistic).isFalse();
    return StringData.forValue(capture);
  }

  @Test
  public void testWrapWithLazyProvider() throws Throwable {
    DetachableSoyValueProvider provider =
        (DetachableSoyValueProvider)
            DetachableProviderFactory.bootstrapDetachableSoyValueProvider(
                    lookup,
                    "simpleMethod",
                    methodType(DetachableSoyValueProvider.class, String.class))
                .getTarget()
                .invokeExact("SomeValue");
    assertThat(provider.resolve()).isEqualTo(StringData.forValue("SomeValue"));
  }

  @Test
  public void testWrapMultipleTimesReturnsSameClass() throws Throwable {
    DetachableSoyValueProvider provider1 =
        (DetachableSoyValueProvider)
            DetachableProviderFactory.bootstrapDetachableSoyValueProvider(
                    lookup,
                    "simpleMethod",
                    methodType(DetachableSoyValueProvider.class, String.class))
                .getTarget()
                .invokeExact("SomeValue");
    DetachableSoyValueProvider provider2 =
        (DetachableSoyValueProvider)
            DetachableProviderFactory.bootstrapDetachableSoyValueProvider(
                    lookup,
                    "simpleMethod",
                    methodType(DetachableSoyValueProvider.class, String.class))
                .getTarget()
                .invokeExact("SomeOtherValue");
    assertThat(provider1.getClass()).isEqualTo(provider2.getClass());
    assertThat(provider1.resolve()).isEqualTo(StringData.forValue("SomeValue"));
    assertThat(provider2.resolve()).isEqualTo(StringData.forValue("SomeOtherValue"));
  }

  public static Object detachingMethod(boolean optimistic, ListenableFuture<String> capture)
      throws Exception {
    assertThat(optimistic).isFalse();
    if (capture.isDone()) {
      return StringData.forValue(capture.get());
    } else {
      return RenderResult.continueAfter(capture);
    }
  }

  @Test
  public void testDetachingMethod() throws Throwable {
    SettableFuture<String> future = SettableFuture.create();
    DetachableSoyValueProvider provider =
        (DetachableSoyValueProvider)
            DetachableProviderFactory.bootstrapDetachableSoyValueProvider(
                    lookup,
                    "detachingMethod",
                    methodType(DetachableSoyValueProvider.class, ListenableFuture.class))
                .getTarget()
                .invokeExact((ListenableFuture<String>) future);
    assertThat(provider.status()).isEqualTo(RenderResult.continueAfter(future));
    future.set("from the future");
    assertThat(provider.status()).isEqualTo(RenderResult.done());
    assertThat(provider.resolve()).isEqualTo(StringData.forValue("from the future"));
  }

  public static StackFrame renderMethod(
      StackFrame stackFrame,
      String capture,
      DetachableContentProvider.MultiplexingAppendable appendable)
      throws IOException {
    appendable.append(capture);

    return null;
  }

  @Test
  public void testRenderMethod() throws Throwable {
    DetachableContentProvider provider =
        (DetachableContentProvider)
            DetachableProviderFactory.bootstrapDetachableContentProvider(
                    lookup,
                    "renderMethod",
                    methodType(
                        DetachableContentProvider.class,
                        StackFrame.class,
                        String.class,
                        DetachableContentProvider.MultiplexingAppendable.class))
                .getTarget()
                .invokeExact(
                    (StackFrame) null,
                    "render me please",
                    DetachableContentProvider.MultiplexingAppendable.create(
                        SanitizedContent.ContentKind.HTML));

    assertThat(provider.status()).isEqualTo(RenderResult.done());
    assertThat(provider.resolve()).isEqualTo(SanitizedContents.constantHtml("render me please"));
  }

}
