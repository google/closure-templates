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

package com.google.template.soy.error;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.IntStream;

/** Class responsible for extracting code snippets from soy sources. */
public final class SourceSnippetPrinter {
  /**
   * Returns a source line snippet highlighting the range of the source location.
   *
   * <p>The snippet will diplay each line of source with its line number and then the range of text
   * highlighted with {@code ~} characters. In the special cases where the range is only one
   * character, use a caret {@code ^} to point to it.
   */
  public Optional<String> getSnippet(SoyFileSupplier soyFileSupplier, SourceLocation location) {
    if (!location.isKnown()) {
      return Optional.empty();
    }
    // Find a snippet of source code associated with the location and print it.
    ImmutableList<String> snippetLines = getSourceLines(soyFileSupplier, location);
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
   * file.
   *
   * <p>Returns a snippet of source code surrounding the given {@link SourceLocation}, or {@link
   * Optional#empty()} if source code is unavailable. (This happens, for example, when anyone uses
   * {@link SourceLocation#UNKNOWN}, which is why no one should use it.)
   */
  private static ImmutableList<String> getSourceLines(
      SoyFileSupplier supplier, SourceLocation location) {
    ImmutableList.Builder<String> lines = ImmutableList.builder();
    try (BufferedReader reader = new BufferedReader(supplier.open())) {
      // Line numbers are 1-indexed and inclusive of end lines
      for (int lineNum = 1; lineNum <= location.getEndLine(); ++lineNum) {
        String line = reader.readLine();
        if (line == null) {
          // eof, warn if happens too early?
          break;
        }
        // Skip preceding lines
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
