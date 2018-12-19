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

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple {@link com.google.template.soy.error.ErrorReporter} implementation.
 *
 * @author brndn@google.com (Brendan Linn)
 */
final class ErrorReporterImpl extends ErrorReporter {

  private final List<RecordedError> reports = new ArrayList<>();
  private int errorCount;
  private final ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers;

  ErrorReporterImpl(ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
    this.filePathsToSuppliers = filePathsToSuppliers;
  }

  @Override
  public void report(SourceLocation location, SoyErrorKind kind, Object... args) {
    errorCount++;
    reports.add(new RecordedError(location, kind, args, /*isWarning=*/ false));
  }

  @Override
  public void warn(SourceLocation location, SoyErrorKind kind, Object... args) {
    reports.add(new RecordedError(location, kind, args, /*isWarning=*/ true));
  }

  @Override
  public ImmutableList<SoyError> getErrors() {
    ImmutableList.Builder<SoyError> builder = ImmutableList.builder();
    for (RecordedError report : reports) {
      if (!report.isWarning) {
        builder.add(report.asSoyError(filePathsToSuppliers));
      }
    }
    return builder.build();
  }

  @Override
  public ImmutableList<SoyError> getWarnings() {
    ImmutableList.Builder<SoyError> builder = ImmutableList.builder();
    for (RecordedError report : reports) {
      if (report.isWarning) {
        builder.add(report.asSoyError(filePathsToSuppliers));
      }
    }
    return builder.build();
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

    SoyError asSoyError(ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
      return SoyError.create(
          location, kind, kind.format(args), getSnippet(filePathsToSuppliers), isWarning);
    }

    /** Returns a source line snippet with a caret pointing at the error column offset. */
    private Optional<String> getSnippet(
        ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
      // Try to find a snippet of source code associated with the exception and print it.
      Optional<String> snippet = getSourceLine(filePathsToSuppliers);
      // TODO(user): this is a result of calling SoySyntaxException#createWithoutMetaInfo,
      // which occurs almost 100 times. Clean them up.
      if (snippet.isPresent()) {
        StringBuilder builder = new StringBuilder();
        builder.append(snippet.get()).append("\n");
        // Print a caret below the error.
        // TODO(brndn): SourceLocation.beginColumn is occasionally -1. Review all SoySyntaxException
        // instantiations and ensure the SourceLocation is well-formed.
        int beginColumn = Math.max(location.getBeginColumn(), 1);
        String caretLine = Strings.repeat(" ", beginColumn - 1) + "^";
        builder.append(caretLine).append("\n");
        return Optional.of(builder.toString());
      }
      return Optional.absent();
    }

    /**
     * Returns a snippet of source code surrounding the given {@link SourceLocation}, or {@link
     * Optional#absent()} if source code is unavailable. (This happens, for example, when anyone
     * uses {@link SourceLocation#UNKNOWN}, which is why no one should use it.)
     */
    Optional<String> getSourceLine(ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
      // Try to find a snippet of source code associated with the exception and print it.
      SoyFileSupplier supplier = filePathsToSuppliers.get(location.getFilePath());
      if (supplier == null) {
        return Optional.absent();
      }
      String result;
      try (BufferedReader reader = new BufferedReader(supplier.open())) {
        // Line numbers are 1-indexed
        for (int linenum = 1; linenum < location.getBeginLine(); ++linenum) {
          // Skip preceding lines
          reader.readLine();
        }
        result = reader.readLine(); // returns null on EOF
      } catch (IOException ioe) {
        return Optional.absent();
      }
      return Optional.fromNullable(result);
    }
  }
}
