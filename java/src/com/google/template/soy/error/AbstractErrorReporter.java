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

package com.google.template.soy.error;

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.ForOverride;

/**
 * Abstract ErrorReporter implementation that implements {@link #checkpoint()} and {@link
 * #errorsSince(Checkpoint)} in terms of a template method {@link #getCurrentNumberOfErrors()}.
 */
public abstract class AbstractErrorReporter implements ErrorReporter {

  @ForOverride
  protected abstract int getCurrentNumberOfErrors();

  @Override
  public final Checkpoint checkpoint() {
    return new CheckpointImpl(this, getCurrentNumberOfErrors());
  }

  @Override
  public final boolean errorsSince(Checkpoint checkpoint) {
    CheckpointImpl impl = (CheckpointImpl) checkpoint;
    if (impl.instance != this) {
      throw new IllegalArgumentException(
          "Can only call errorsSince on a Checkpoint instance that was returned from this same "
              + "reporter");
    }
    return getCurrentNumberOfErrors() > impl.errorsSoFar;
  }
  
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("errorCount", getCurrentNumberOfErrors())
        .toString();
  }

  private static final class CheckpointImpl implements Checkpoint {
    final ErrorReporter instance;
    final int errorsSoFar;

    private CheckpointImpl(ErrorReporter instance, int errorsSoFar) {
      this.instance = instance;
      this.errorsSoFar = errorsSoFar;
    }
  }
}
