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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link com.google.template.soy.error.ErrorReporter} implementation that formats
 * {@link com.google.template.soy.error.SoyError}s without attaching source locations.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class FormattingErrorReporter implements ErrorReporter {

  private final List<String> errorMessages = new ArrayList<>();

  @Override
  public void report(SourceLocation sourceLocation, SoyError error, Object... args) {
    errorMessages.add(error.format(args));
  }

  @Override
  public Checkpoint checkpoint() {
    return new CheckpointImpl(errorMessages.size());
  }

  @Override
  public boolean errorsSince(Checkpoint checkpoint) {
    return errorMessages.size() > ((CheckpointImpl) checkpoint).numErrors;
  }

  public ImmutableList<String> getErrorMessages() {
    return ImmutableList.copyOf(errorMessages);
  }

  private static final class CheckpointImpl implements Checkpoint {
    private final int numErrors;

    private CheckpointImpl(int numErrors) {
      this.numErrors = numErrors;
    }
  }
}
