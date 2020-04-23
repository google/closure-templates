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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.SoyFileSupplier;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

/** Class responsible for extracting code snippets from soy sources. */
public final class SourceSnippetPrinter {
  /** Represents an item that can be printed as part of a snippet. */
  private static interface Printable {
    /**
     * Returns the string content of this Printable.
     *
     * <p>The output must include trailing new line characters.
     *
     * @param location The information regarding which portion of the Printable should be
     *     highlighted in the output snippet.
     */
    String print(SourceLocation location);

    /** Returns the maximum length of all line numbers in the given SourceLocation. */
    default int lineNumberPadding(SourceLocation location) {
      // Line numbers are positive integers in increasing order. The line number of the last line
      // will have the maximum printed length.
      return String.valueOf(location.getEndLine()).length();
    }
  }

  /** A special Printable that is included in the snippet as a placeholder for omitted lines. */
  public static final Printable ELLIPSIS =
      new Printable() {
        @Override
        public String print(SourceLocation location) {
          return Strings.repeat(" ", lineNumberPadding(location) + 2) + "[...]\n";
        }
      };

  /** A portion of a snippet corresponding to a single line. */
  @AutoValue
  abstract static class SourceLine implements Printable {
    static SourceLine create(int lineNumber, String line) {
      return new AutoValue_SourceSnippetPrinter_SourceLine(lineNumber, line);
    }

    abstract int getLineNumber();

    abstract String getLine();

    @Override
    public String print(SourceLocation location) {
      // This number format aligns line numbers to the right.
      String lineTemplate = "%" + lineNumberPadding(location) + "d: %s";
      String codeLine = String.format(lineTemplate, getLineNumber(), getLine());
      return codeLine + "\n" + getHighlightLine(location) + "\n";
    }

    /**
     * Constructs an additional line that is aligned with the code line, highlighting the
     * corresponding portion of the given SourceLocation.
     */
    private String getHighlightLine(SourceLocation location) {
      if (getLineNumber() < location.getBeginLine() || getLineNumber() > location.getEndLine()) {
        // The SourceLocation does not include this line, no highlighting necessary.
        return "";
      }
      // This is 0-based (unlike SourceLocation, which is 1-based).
      int highlightFrom =
          getLineNumber() == location.getBeginLine() ? location.getBeginColumn() - 1 : 0;
      String initialWhitespace =
          Strings.repeat(" ", lineNumberPadding(location) + ": ".length() + highlightFrom);

      boolean isSingleCharacter = location.getBeginPoint().equals(location.getEndPoint());
      if (isSingleCharacter) {
        // Use a single caret to point to a character.
        return initialWhitespace + '^';
      }
      // This is 0-based and exclusive (unlike SourceLocation, which is 1-based and inclusive).
      int highlightTo =
          getLineNumber() == location.getEndLine()
              ? location.getEndColumn()
              // The snippet also highlights the \n character.
              : getLine().length() + 1;
      int highlightLength = highlightTo - highlightFrom;
      // Underline with tilde characters.
      return initialWhitespace + Strings.repeat("~", highlightLength);
    }
  }

  /** The maximum number of lines that should be printed. */
  private final Optional<Integer> maxLines;

  public SourceSnippetPrinter() {
    this.maxLines = Optional.empty();
  }

  /**
   * @param maxLines The maximum number of lines that should be printed. If a snippet spans more
   *     lines than this number, only the beginning and end portion of the snippet will be returned,
   *     totaling to maxLines lines, with an ellipsis instead of the middle portion. The number must
   *     be at least 2, allowing at least one line before and after the ellipsis.
   */
  public SourceSnippetPrinter(int maxLines) {
    checkArgument(maxLines > 1, "maxLines must be at least 2");
    this.maxLines = Optional.of(maxLines);
  }

  /**
   * Returns a source line snippet highlighting the range of the source location.
   *
   * <p>The snippet will diplay each line of source with its line number and then the range of text
   * highlighted with {@code ~} characters. In the special cases where the range is only one
   * character, use a caret {@code ^} to point to it.
   *
   * <p>For example: <code>
   *  98:    Text {if $a}
   *              ~~~~~~~~
   *  99:           {$a}
   *      ~~~~~~~~~~~~~~~
   * 100:         {/if} Text.
   *      ~~~~~~~~~~~~~
   * </code>
   *
   * <p>If {@link #maxLines} was passed to the constructor, and the number of lines in the snippet
   * would exceed maxLines, only the beginning and end of the snippet, up to maxLines lines, is
   * returned, with an ellipsis in between.
   */
  public Optional<String> getSnippet(SoyFileSupplier soyFileSupplier, SourceLocation location) {
    if (!location.isKnown()) {
      return Optional.empty();
    }

    // Find a snippet of source code associated with the location.
    ImmutableList<Printable> snippetLines = getSourceLines(soyFileSupplier, location);
    if (snippetLines.isEmpty()) {
      return Optional.empty();
    }

    if (this.maxLines.isPresent()) {
      int locationLines = snippetLines.size();
      if (locationLines > this.maxLines.get()) {
        // If maxLines is an odd number, show the extra line before the ellipsis.
        int linesAtTop = (this.maxLines.get() + 1) / 2;
        int linesAtBottom = this.maxLines.get() - linesAtTop;
        snippetLines =
            ImmutableList.<Printable>builder()
                .addAll(snippetLines.subList(0, linesAtTop))
                .add(ELLIPSIS)
                .addAll(snippetLines.subList(locationLines - linesAtBottom, locationLines))
                .build();
      }
    }

    return Optional.of(
        snippetLines.stream().map(line -> line.print(location)).collect(joining("")));
  }

  /**
   * Returns the text of all the lines of the location by reading them from the original source
   * file.
   *
   * <p>Returns a snippet of source code surrounding the given {@link SourceLocation}, or {@link
   * Optional#empty()} if source code is unavailable. (This happens, for example, when anyone uses
   * {@link SourceLocation#UNKNOWN}, which is why no one should use it.)
   */
  private static ImmutableList<Printable> getSourceLines(
      SoyFileSupplier supplier, SourceLocation location) {
    ImmutableList.Builder<Printable> lines = ImmutableList.builder();
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
          lines.add(SourceLine.create(lineNum, line));
        }
      }
      return lines.build();
    } catch (IOException ioe) {
      return ImmutableList.of(); // TODO(lukes): log warning?
    }
  }
}
