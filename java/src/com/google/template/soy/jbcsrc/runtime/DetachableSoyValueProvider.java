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

import static com.google.common.base.Preconditions.checkState;

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
 * <p>This class resolves to a {@link SoyValue} and calls {@link SoyValue#render}. If you need to
 * resolve to a {@link SoyValueProvider} to call {@link SoyValueProvider#renderAndResolve}, use
 * {@link DetachableSoyValueProviderProvider} instead.
 */
public abstract class DetachableSoyValueProvider implements SoyValueProvider {
  private SoyValue resolvedValue;

  @Override
  public final SoyValue resolve() {
    SoyValue local = resolvedValue;
    if (local == null) {
      JbcSrcRuntime.awaitProvider(this);
      local = resolvedValue;
      checkState(local != null, "awaiting didn't resolve provider");
    }
    return local;
  }

  @Override
  public final RenderResult status() {
    if (resolvedValue != null) {
      return RenderResult.done();
    }
    Object result = evaluate();
    // The SoyValue hierarchy is complex, so `instanceof` will be slower, however, the value is
    // non-null and RenderResult is final so this check will be faster.
    if (result.getClass() == RenderResult.class) {
      return (RenderResult) result;
    }
    resolvedValue = (SoyValue) result;
    return RenderResult.done();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable) throws IOException {
    RenderResult status = status();
    if (status.isDone()) {
      resolvedValue.render(appendable);
      return RenderResult.done();
    }
    return status;
  }

  /**
   * Overridden by generated subclasses to implement lazy detachable resolution.
   *
   * @return a RenderResult when not done and a SoyValue when complete
   */
  protected abstract Object evaluate();
}
