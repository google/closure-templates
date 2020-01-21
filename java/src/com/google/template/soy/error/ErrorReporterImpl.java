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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

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

    /**
     * Returns a source line snippet highlighting the error location.
     *
     * <p>The snippet will diplay each line of source with its line number and then the range of
     * text highlighted with {@code ~} characters. In the special cases where the range is only one
     * character, use a caret {@code ^} to point to it.
     */
    private Optional<String> getSnippet(
        ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
      if (!location.isKnown()) {
        return Optional.empty();
      }
      // Try to find a snippet of source code associated with the exception and print it.
      ImmutableList<String> snippetLines = getSourceLines(filePathsToSuppliers);
      // Each line of source text will begin with the line number.
      // format the number
      ImmutableList<String> linePrefixes =
          IntStream.rangeClosed(location.getBeginLine(), location.getEndLine())
              .mapToObj(i -> String.format("%d: ", i))
              .collect(toImmutableList());
      // measure their lengths to find the max
      int maxLength = linePrefixes.stream().mapToInt(p -> p.length()).max().getAsInt();
      // left pad
      linePrefixes =
          linePrefixes.stream()
              .map(p -> Strings.repeat(" ", maxLength - p.length()) + p)
              .collect(toImmutableList());

      String prefixPadding = Strings.repeat(" ", maxLength);
      StringBuilder builder = new StringBuilder();
      int curLine = location.getBeginLine();
      int startColumn = location.getBeginColumn();
      for (int i = 0; i < snippetLines.size(); i++) {
        String prefix = linePrefixes.get(i);
        String line = snippetLines.get(i);
        builder.append(prefix).append(line).append('\n');
        // add spaces to account for the prefix, and then char line up to the start column
        builder.append(prefixPadding).append(Strings.repeat(" ", startColumn - 1));
        int endColumn;
        if (curLine == location.getEndLine()) {
          endColumn = location.getEndColumn();
        } else {
          endColumn = line.length() + 1;
        }
        if (endColumn == startColumn && location.getBeginLine() == location.getEndLine()) {
          // if it is just one character, use a caret
          builder.append('^');
        } else {
          // otherwise 'underline' with tilda characters
          // +1 because endColumn is inclusive
          builder.append(Strings.repeat("~", endColumn - startColumn + 1));
        }
        builder.append('\n');
        startColumn = 1;
        curLine++;
      }
      String result = builder.toString();
      return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    /**
     * Returns the text of all the lines of the location by reading them from the original source
     * files.
     *
     * <p>Returns a snippet of source code surrounding the given {@link SourceLocation}, or {@link
     * Optional#empty()} if source code is unavailable. (This happens, for example, when anyone uses
     * {@link SourceLocation#UNKNOWN}, which is why no one should use it.)
     */
    ImmutableList<String> getSourceLines(
        ImmutableMap<String, SoyFileSupplier> filePathsToSuppliers) {
      // Try to find a snippet of source code associated with the exception and print it.
      SoyFileSupplier supplier = filePathsToSuppliers.get(location.getFilePath());
      if (supplier == null) {
        // sometimes we report errors against things like plugins, in which case we won't have a
        // file
        return ImmutableList.of();
      }
      ImmutableList.Builder<String> lines = ImmutableList.builder();
      try (BufferedReader reader = new BufferedReader(supplier.open())) {
        // Line numbers are 1-indexed and inclusive of end lines
        for (int lineNum = 1; lineNum <= location.getEndLine(); ++lineNum) {
          // Skip preceding lines
          String line = reader.readLine();
          if (line == null) {
            // eof, warn if happens too early?
            break;
          }
          if (lineNum >= location.getBeginLine()) {
            lines.add(line);
          }
        }
        return lines.build();
      } catch (IOException ioe) {
        return ImmutableList.of(); // TODO(lukes): log warning?
      }
    }
  }
}
