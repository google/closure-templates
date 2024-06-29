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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Simple {@link com.google.template.soy.error.ErrorReporter} implementation. */
final class ErrorReporterImpl extends ErrorReporter {

  private final List<SoyError> reports = new ArrayList<>();
  private int errorCount;

  ErrorReporterImpl() {}

  @Override
  public void report(SourceLocation location, SoyErrorKind kind, Object... args) {
    errorCount++;
    reports.add(SoyError.create(location, kind, args, /* isWarning= */ false));
  }

  @Override
  public void warn(SourceLocation location, SoyErrorKind kind, Object... args) {
    reports.add(SoyError.create(location, kind, args, /* isWarning= */ true));
  }

  @Override
  public ImmutableList<SoyError> getReports() {
    return ImmutableList.copyOf(reports);
  }

  @Override
  protected ImmutableList<SoyError> getReports(int from, int to) {
    return reports.stream()
        .skip(from)
        .limit(to - from)
        .collect(toImmutableList());
  }

  @Override
  public ImmutableList<SoyError> getErrors() {
    return reports.stream().filter(r -> !r.isWarning()).collect(toImmutableList());
  }

  @Override
  public ImmutableList<SoyError> getWarnings() {
    return reports.stream().filter(SoyError::isWarning).collect(toImmutableList());
  }

  @Override
  public void copyTo(ErrorReporter other) {
    for (SoyError report : reports) {
      if (report.isWarning()) {
        other.warn(report.location(), report.errorKind(), report.getArgs().toArray());
      } else {
        other.report(report.location(), report.errorKind(), report.getArgs().toArray());
      }
    }
  }

  @Override
  int getCurrentNumberOfErrors() {
    return errorCount;
  }

  @Override
  int getCurrentNumberOfReports() {
    return reports.size();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("errors", errorCount)
        .add("warnings", reports.size() - errorCount)
        .toString();
  }
}
