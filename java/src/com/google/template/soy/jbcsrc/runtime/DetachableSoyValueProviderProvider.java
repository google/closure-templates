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

package com.google.template.soy.jbcsrc.runtime;

import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetValueNode} and {@code CallParamValueNode}
 * implementations.
 *
 * <p>This class resolves to a {@link SoyValueProvider} and calls {@link
 * SoyValueProvider#renderAndResolve}. If you don't need to box as a value provider, use {@link
 * DetachableSoyValueProvider} instead, which resolves to a {@link SoyValue} and calls {@link
 * SoyValue#render}.
 */
public final class DetachableSoyValueProviderProvider implements SoyValueProvider {
  /** A lambda-able interface for implementing a lazy value block. */
  @FunctionalInterface
  public interface Impl {
    /**
     * Returns either a RenderResult meaning we aren't done or a SoyValueProvider for our final
     * value.
     */
    Object evaluate();
  }

  public static DetachableSoyValueProviderProvider create(Impl impl) {
    return new DetachableSoyValueProviderProvider(impl);
  }

  private Object implOrValueProvider;

  private DetachableSoyValueProviderProvider(Impl impl) {
    this.implOrValueProvider = impl;
  }

  @Override
  public final SoyValue resolve() {
    JbcSrcRuntime.awaitProvider(this);
    return ((SoyValueProvider) implOrValueProvider).resolve();
  }

  @Override
  public final RenderResult status() {
    var local = implOrValueProvider;
    if (local instanceof Impl) {
      var subResult = ((Impl) local).evaluate();
      if (subResult instanceof RenderResult) {
        return (RenderResult) subResult;
      }
      implOrValueProvider = local = subResult;
    }
    return ((SoyValueProvider) local).status();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) throws IOException {
    var local = implOrValueProvider;
    if (local instanceof Impl) {
      var subResult = ((Impl) local).evaluate();
      if (subResult instanceof RenderResult) {
        return (RenderResult) subResult;
      }
      implOrValueProvider = local = subResult;
    }
    return ((SoyValueProvider) local).renderAndResolve(appendable);
  }
}
