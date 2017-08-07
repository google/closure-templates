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
import com.google.template.soy.jbcsrc.api.RenderResult.Type;
import java.io.IOException;

/**
 * A special implementation of {@link SoyValueProvider} to use as a shared base class for the {@code
 * jbcsrc} implementations of the generated {@code LetValueNode} and {@code CallParamValueNode}
 * implementations.
 */
public abstract class DetachableSoyValueProvider implements SoyValueProvider {
  // TOMBSTONE marks this field as uninitialized which allows it to accept 'null' as a valid value.
  protected SoyValue resolvedValue = TombstoneValue.INSTANCE;

  @Override
  public final SoyValue resolve() {
    SoyValue local = resolvedValue;
    checkState(
        local != TombstoneValue.INSTANCE, "called resolve() before status() returned ready.");
    return local;
  }

  @Override
  public final RenderResult status() {
    if (resolvedValue != TombstoneValue.INSTANCE) {
      return RenderResult.done();
    }
    return doResolve();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable, boolean isLast)
      throws IOException {
    RenderResult result = status();
    if (result.type() == Type.DONE) {
      SoyValue resolved = resolve();
      if (resolved == null) {
        appendable.append("null");
      } else {
        resolved.render(appendable);
      }
    }
    return result;
  }

  /** Overridden by generated subclasses to implement lazy detachable resolution. */
  protected abstract RenderResult doResolve();
}
