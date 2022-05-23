/*
 * Copyright 2020 Google Inc.
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
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.io.IOException;

/**
 * A SoyValueProvider that wraps a BufferingAppendable. Useful for evaluting a block inline but
 * preserving log statements.
 */
public final class BufferedSoyValueProvider implements SoyValueProvider {

  public static BufferedSoyValueProvider create(BufferingAppendable bufferingAppendable) {
    return new BufferedSoyValueProvider(bufferingAppendable);
  }

  private final BufferingAppendable buffer;
  private SoyValue resolvedValue;

  private BufferedSoyValueProvider(BufferingAppendable bufferingAppendable) {
    this.buffer = bufferingAppendable;
  }

  @Override
  public final SoyValue resolve() {
    if (resolvedValue == null) {
      resolvedValue = buffer.getAsSoyValue();
    }
    return resolvedValue;
  }

  @Override
  public final RenderResult status() {
    return RenderResult.done();
  }

  @Override
  public RenderResult renderAndResolve(LoggingAdvisingAppendable appendable, boolean isLast)
      throws IOException {
    buffer.replayOn(appendable);
    return RenderResult.done();
  }
}
