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

package com.google.template.soy.soyparse;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple {@link ErrorReporter} implementation.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public class ErrorReporterImpl implements ErrorReporter {
  protected final List<SoySyntaxException> errors = new ArrayList<>();

  @Override
  public void report(SourceLocation sourceLocation, SoyError error, String... args) {
    errors.add(SoySyntaxException.createWithMetaInfo(error.format(args), sourceLocation));
  }

  @Override
  public Checkpoint checkpoint() {
    return new Checkpoint(errors.size());
  }

  @Override
  public boolean errorsSince(Checkpoint checkpoint) {
    return errors.size() > checkpoint.numErrors;
  }

  /**
   * Returns the full list of errors reported to this error reporter.
   * TODO(user): only a couple top-level places in the codebase should be able to use this.
   * Make package-private once the error refactoring is complete.
   */
  public ImmutableCollection<? extends SoySyntaxException> getErrors() {
    return ImmutableList.copyOf(errors);
  }
}
