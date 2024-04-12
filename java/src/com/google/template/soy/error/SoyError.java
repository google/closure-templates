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

import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;

/** Generating by calling {@link ErrorReporter#report} or {@link ErrorReporter#warn}. */
@AutoValue
public abstract class SoyError implements Comparable<SoyError> {
  static SoyError create(
      SourceLocation location, SoyErrorKind kind, Object[] args, boolean isWarning) {
    return new AutoValue_SoyError(location, kind, ImmutableList.copyOf(args), isWarning);
  }

  public abstract SourceLocation location();

  public abstract SoyErrorKind errorKind();

  protected abstract ImmutableList<?> getArgs();

  public abstract boolean isWarning();

  public String message() {
    return errorKind().format(getArgs().toArray());
  }

  @Override
  public int compareTo(SoyError o) {
    return comparing(SoyError::location).thenComparing(SoyError::message).compare(this, o);
  }
}
