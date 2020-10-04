/*
 * Copyright 2020 Google LLC
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

import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.base.Joiner;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.base.internal.SoyFileSupplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SourceSnippetPrinterTest {
  private static final Joiner JOINER = Joiner.on('\n');

  private static final SourceFilePath SOY_FILE_PATH = SourceFilePath.create("/example/file.soy");

  /**
   * Fake file content to be used by the SourceSnippetPrinter. This is not a valid soy file content,
   * but is useful for constructing expected results.
   */
  private static final String SOY_FILE_CONTENT =
      JOINER.join(
          // SourceLocation is 1-based.
          "1:1       1:11      1:21      1:31      1:41      1:51      1:61      1:71",
          "2:1       2:11      2:21      2:31      2:41      2:51      2:61      2:71",
          "3:1       3:11      3:21      3:31      3:41      3:51      3:61      3:71",
          "4:1       4:11      4:21      4:31      4:41      4:51      4:61      4:71",
          "5:1       5:11      5:21      5:31      5:41      5:51      5:61      5:71",
          "6:1       6:11      6:21      6:31      6:41      6:51      6:61      6:71",
          "7:1       7:11      7:21      7:31      7:41      7:51      7:61      7:71",
          "8:1       8:11      8:21      8:31      8:41      8:51      8:61      8:71",
          "9:1       9:11      9:21      9:31      9:41      9:51      9:61      9:71",
          "10:1      10:11     10:21     10:31     10:41     10:51     10:61     10:71",
          "");

  private static final SoyFileSupplier SOY_FILE_SUPPLIER =
      SoyFileSupplier.Factory.create(SOY_FILE_CONTENT, SOY_FILE_PATH);

  private SourceSnippetPrinter printer;

  @Before
  public void setUp() {
    printer = new SourceSnippetPrinter();
  }

  /** Verifies that empty Optionals are returned for UNKNOWN SourceLocations. */
  @Test
  public void getSnippet_unknownLocation() throws Exception {
    SourceLocation location = SourceLocation.UNKNOWN;
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location)).isEmpty();
  }

  /**
   * Verifies that a snippet for a single point is rendered as a caret pointing to the point's
   * column.
   */
  @Test
  public void getSnippet_point() throws Exception {
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 4, /* beginColumn= */ 51, /* endLine= */ 4, /* endColumn= */ 51);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "4: 4:1       4:11      4:21      4:31      4:41      4:51      4:61      4:71",
                "                                                     ^",
                ""));
  }

  /**
   * Verifies that a snippet for a SourceLocation on a single line underlines the corresponding
   * range on that line.
   */
  @Test
  public void getSnippet_singleLineRange() throws Exception {
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 4, /* beginColumn= */ 21, /* endLine= */ 4, /* endColumn= */ 51);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "4: 4:1       4:11      4:21      4:31      4:41      4:51      4:61      4:71",
                "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                ""));
  }

  /**
   * Verifies that a snippet for a SourceLocation across multiple lines underlines the whole range
   * of lines, with correct begin and end points.
   */
  @Test
  public void getSnippet_multiLineRange() throws Exception {
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 3, /* beginColumn= */ 21, /* endLine= */ 5, /* endColumn= */ 51);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "3: 3:1       3:11      3:21      3:31      3:41      3:51      3:61      3:71",
                "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "4: 4:1       4:11      4:21      4:31      4:41      4:51      4:61      4:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "5: 5:1       5:11      5:21      5:31      5:41      5:51      5:61      5:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                ""));
  }

  /**
   * Verifies that the line numbers are correctly aligned to the right, without shifting the source
   * lines.
   */
  @Test
  public void getSnippet_multiLineRange_lineNumberPadding() throws Exception {
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 9, /* beginColumn= */ 1, /* endLine= */ 10, /* endColumn= */ 1);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                // 9 is padded to be aligned with 10.
                " 9: 9:1       9:11      9:21      9:31      9:41      9:51      9:61      9:71",
                "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "10: 10:1      10:11     10:21     10:31     10:41     10:51     10:61     10:71",
                "    ~",
                ""));
  }

  /**
   * Verifies that a snippet for a SourceLocation that includes a blank line will render and
   * underline the blank as a single whitespace character.
   */
  @Test
  public void getSnippet_multiLineRange_blankLine() throws Exception {
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 1, /* beginColumn= */ 4, /* endLine= */ 3, /* endColumn= */ 8);
    assertThat(
            printer.getSnippet(
                SoyFileSupplier.Factory.create(
                    JOINER.join(
                        "First line",
                        // Blank line.
                        "",
                        "Third line"),
                    SOY_FILE_PATH),
                location))
        .hasValue(
            JOINER.join(
                "1: First line",
                "      ~~~~~~~~",
                "2: ",
                "   ~",
                "3: Third line",
                "   ~~~~~~~~",
                ""));
  }

  /** Verifies that the SourceSnippetPrinter cannot be instantiated with a maxLines lower than 2. */
  @Test
  public void getSnippet_max1Lines() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> new SourceSnippetPrinter(/* maxLines= */ 1));
  }

  /**
   * Verifies that a snippet for a SourceLocation spanning more lines than maxLines will contain an
   * ellipsis in the middle.
   */
  @Test
  public void getSnippet_ellipsis_evenMaxLines() throws Exception {
    printer = new SourceSnippetPrinter(/* maxLines= */ 2);
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 3, /* beginColumn= */ 21, /* endLine= */ 6, /* endColumn= */ 51);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "3: 3:1       3:11      3:21      3:31      3:41      3:51      3:61      3:71",
                "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "   [...]",
                "6: 6:1       6:11      6:21      6:31      6:41      6:51      6:61      6:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                ""));
  }

  /**
   * Verifies that a snippet for a SourceLocation spanning more lines than maxLines will contain an
   * ellipsis in the middle.
   *
   * <p>This also verifies that for an odd maxLines, the extra line is printed on the upper portion
   * of the snippet.
   */
  @Test
  public void getSnippet_ellipsis_oddMaxLines() throws Exception {
    printer = new SourceSnippetPrinter(/* maxLines= */ 3);
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 3, /* beginColumn= */ 21, /* endLine= */ 6, /* endColumn= */ 51);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "3: 3:1       3:11      3:21      3:31      3:41      3:51      3:61      3:71",
                "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "4: 4:1       4:11      4:21      4:31      4:41      4:51      4:61      4:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "   [...]",
                "6: 6:1       6:11      6:21      6:31      6:41      6:51      6:61      6:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                ""));
  }

  /**
   * Verifies that a snippet for a SourceLocation spanning exactly maxLines, the snippet doesn't
   * include an ellipsis.
   */
  @Test
  public void getSnippet_ellipsis_noEllipsisForMaxLines() throws Exception {
    printer = new SourceSnippetPrinter(/* maxLines= */ 3);
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 3, /* beginColumn= */ 21, /* endLine= */ 5, /* endColumn= */ 51);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "3: 3:1       3:11      3:21      3:31      3:41      3:51      3:61      3:71",
                "                       ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "4: 4:1       4:11      4:21      4:31      4:41      4:51      4:61      4:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "5: 5:1       5:11      5:21      5:31      5:41      5:51      5:61      5:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                ""));
  }

  /**
   * Verifies that a snippet for a SourceLocation spanning many lines, the ellipsis behavior
   * correctly prints multiple lines at the beginning and end of the snippet, and the ellipsis
   * correctly skips multiple lines.
   */
  @Test
  public void getSnippet_ellipsis_longRange() throws Exception {
    printer = new SourceSnippetPrinter(/* maxLines= */ 6);
    SourceLocation location =
        makeSourceLocation(
            /* beginLine= */ 1, /* beginColumn= */ 61, /* endLine= */ 9, /* endColumn= */ 11);
    assertThat(printer.getSnippet(SOY_FILE_SUPPLIER, location))
        .hasValue(
            JOINER.join(
                "1: 1:1       1:11      1:21      1:31      1:41      1:51      1:61      1:71",
                "                                                               ~~~~~~~~~~~~~~~",
                "2: 2:1       2:11      2:21      2:31      2:41      2:51      2:61      2:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "3: 3:1       3:11      3:21      3:31      3:41      3:51      3:61      3:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "   [...]",
                "7: 7:1       7:11      7:21      7:31      7:41      7:51      7:61      7:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "8: 8:1       8:11      8:21      8:31      8:41      8:51      8:61      8:71",
                "   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~",
                "9: 9:1       9:11      9:21      9:31      9:41      9:51      9:61      9:71",
                "   ~~~~~~~~~~~",
                ""));
  }

  private static SourceLocation makeSourceLocation(
      int beginLine, int beginColumn, int endLine, int endColumn) {
    Point begin = Point.create(beginLine, beginColumn);
    Point end = Point.create(endLine, endColumn);
    return new SourceLocation(SOY_FILE_PATH, begin, end);
  }
}
