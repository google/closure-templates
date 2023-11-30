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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Simple {@link com.google.template.soy.error.ErrorReporter} implementation. */
final class ErrorReporterImpl extends ErrorReporter {

  private static final SourceSnippetPrinter snippetPrinter = new SourceSnippetPrinter();

  private final List<RecordedError> reports = new ArrayList<>();
  private int errorCount;
  private final ImmutableMap<SourceLogicalPath, SoyFileSupplier> filePathsToSuppliers;

  ErrorReporterImpl(ImmutableMap<SourceLogicalPath, SoyFileSupplier> filePathsToSuppliers) {
    this.filePathsToSuppliers = filePathsToSuppliers;
  }

  @Override
  public void report(SourceLocation location, SoyErrorKind kind, Object... args) {
    errorCount++;
    reports.add(new RecordedError(location, kind, args, /* isWarning= */ false));
  }

  @Override
  public void warn(SourceLocation location, SoyErrorKind kind, Object... args) {
    reports.add(new RecordedError(location, kind, args, /* isWarning= */ true));
  }

  @Override
  public ImmutableList<SoyError> getReports() {
    return reports.stream().map(r -> r.asSoyError(filePathsToSuppliers)).collect(toImmutableList());
  }

  @Override
  protected ImmutableList<SoyError> getReports(int from, int to) {
    return reports.stream()
        .skip(from)
        .limit(to - from)
        .map(r -> r.asSoyError(filePathsToSuppliers))
        .collect(toImmutableList());
  }

  @Override
  public ImmutableList<SoyError> getErrors() {
    return reports.stream()
        .filter(r -> !r.isWarning)
        .map(r -> r.asSoyError(filePathsToSuppliers))
        .collect(toImmutableList());
  }

  @Override
  public ImmutableList<SoyError> getWarnings() {
    return reports.stream()
        .filter(r -> r.isWarning)
        .map(r -> r.asSoyError(filePathsToSuppliers))
        .collect(toImmutableList());
  }

  @Override
  public void copyTo(ErrorReporter other) {
    for (RecordedError report : reports) {
      report.copyTo(other);
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

  private static final class RecordedError {
    final SourceLocation location;
    final SoyErrorKind kind;
    final Object[] args;
    final boolean isWarning;

    RecordedError(SourceLocation location, SoyErrorKind kind, Object[] args, boolean isWarning) {
      this.location = checkNotNull(location);
      this.kind = checkNotNull(kind);
      this.args = checkNotNull(args);
      this.isWarning = isWarning;
    }

    void copyTo(ErrorReporter other) {
      if (isWarning) {
        other.warn(location, kind, args);
      } else {
        other.report(location, kind, args);
      }
    }

    SoyError asSoyError(ImmutableMap<SourceLogicalPath, SoyFileSupplier> filePathsToSuppliers) {
      Optional<String> snippet =
          Optional
              // Sometimes we report errors against things like plugins, in which case we won't have
              // a file.
              .ofNullable(filePathsToSuppliers.get(location.getFilePath()))
              .flatMap(supplier -> snippetPrinter.getSnippet(supplier, location));
      return SoyError.create(location, kind, kind.format(args), snippet, isWarning);
    }
  }
}
