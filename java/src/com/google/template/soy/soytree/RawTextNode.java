/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.RawTextNode.SourceOffsets.Reason;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Node representing a contiguous raw text section.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class RawTextNode extends AbstractSoyNode implements StandaloneNode {

  /** The special chars we need to re-escape for toSourceString(). */
  private static final Pattern SPECIAL_CHARS_TO_ESCAPE = Pattern.compile("[\n\r\t{}\u00A0]");

  /** Map from special char to be re-escaped to its special char tag (for toSourceString()). */
  private static final ImmutableMap<String, String> SPECIAL_CHAR_TO_TAG =
      ImmutableMap.<String, String>builder()
          .put("\n", "{\\n}")
          .put("\r", "{\\r}")
          .put("\t", "{\\t}")
          .put("{", "{lb}")
          .put("}", "{rb}")
          .put("\u00A0", "{nbsp}")
          .build();

  /** The raw text string (after processing of special chars and literal blocks). */
  private final String rawText;

  @Nullable private final SourceOffsets offsets;

  @Nullable private HtmlContext htmlContext;

  /**
   * @param id The id for this node.
   * @param rawText The raw text string.
   * @param sourceLocation The node's source location.
   */
  public RawTextNode(int id, String rawText, SourceLocation sourceLocation) {
    this(id, rawText, sourceLocation, SourceOffsets.fromLocation(sourceLocation, rawText.length()));
  }

  public RawTextNode(
      int id, String rawText, SourceLocation sourceLocation, HtmlContext htmlContext) {
    super(id, sourceLocation);
    this.rawText = checkNotNull(rawText);
    this.htmlContext = htmlContext;
    this.offsets = SourceOffsets.fromLocation(sourceLocation, rawText.length());
  }

  public RawTextNode(int id, String rawText, SourceLocation sourceLocation, SourceOffsets offsets) {
    super(id, sourceLocation);
    this.rawText = checkNotNull(rawText);
    this.offsets = offsets;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private RawTextNode(RawTextNode orig, CopyState copyState) {
    super(orig, copyState);
    this.rawText = orig.rawText;
    this.htmlContext = orig.htmlContext;
    this.offsets = orig.offsets;
  }

  /**
   * Gets the HTML source context (typically tag, attribute value, HTML PCDATA, or plain text) which
   * this node emits in. This is used for incremental DOM codegen.
   */
  public HtmlContext getHtmlContext() {
    return Preconditions.checkNotNull(
        htmlContext, "Cannot access HtmlContext before HtmlContextVisitor");
  }

  @Override
  public Kind getKind() {
    return Kind.RAW_TEXT_NODE;
  }

  public void setHtmlContext(HtmlContext value) {
    this.htmlContext = value;
  }

  /** Returns the raw text string (after processing of special chars and literal blocks). */
  public String getRawText() {
    return rawText;
  }

  public boolean isEmpty() {
    return rawText.isEmpty();
  }

  /**
   * Returns true if there was whitespace deleted immediately prior to {@code index}
   *
   * @param index the index in the raw text, this value should be in the range {@code [0,
   *     rawText.length()]} if {@code rawText.length()} is passed, then this is equivalent to asking
   *     if there is trailing whitespace missing.
   * @throws IndexOutOfBoundsException if index is out of range.
   * @return {@code true} if whitespace was dropped
   */
  public boolean missingWhitespaceAt(int index) {
    return offsets == null ? false : offsets.getReasonAt(index) == SourceOffsets.Reason.WHITESPACE;
  }

  public Point locationOf(int i) {
    checkElementIndex(i, rawText.length(), "index");
    if (offsets == null) {
      return Point.UNKNOWN_POINT;
    }
    return offsets.getPoint(rawText, i);
  }

  // TODO(lukes): Move to SourceLocation.
  /** Returns the source location of the given substring. */
  public SourceLocation substringLocation(int start, int end) {
    checkElementIndex(start, rawText.length(), "start");
    checkArgument(start < end);
    checkArgument(end <= rawText.length());
    if (offsets == null) {
      return getSourceLocation();
    }
    return new SourceLocation(
        getSourceLocation().getFilePath(),
        offsets.getPoint(rawText, start),
        // source locations are inclusive, the end locations should point at the last character
        // in the string, whereas substring is usually specified exclusively, so subtract 1 to make
        // up the difference
        offsets.getPoint(rawText, end - 1));
  }

  /**
   * Returns a new RawTextNode that represents the given {@link String#substring(int)} of this raw
   * text node.
   *
   * <p>Unlike {@link String#substring(int)} the range must be non-empty
   *
   * @param newId the new node id to use
   * @param start the start location
   */
  public RawTextNode substring(int newId, int start) {
    return substring(newId, start, rawText.length());
  }

  /**
   * Returns a new RawTextNode that represents the given {@link String#substring(int, int)} of this
   * raw text node.
   *
   * <p>Unlike {@link String#substring(int, int)} the range must be non-empty
   *
   * @param newId the new node id to use
   * @param start the start location
   * @param end the end location
   */
  public RawTextNode substring(int newId, int start, int end) {
    checkArgument(start >= 0, "start (%s) should be greater than or equal to 0", start);
    checkArgument(start < end, "start (%s) should be less that end (%s)", start, end);
    checkArgument(
        end <= rawText.length(),
        "end (%s) should be less than or equal to the length (%s)",
        end,
        rawText.length());
    if (start == 0 && end == rawText.length()) {
      return this;
    }
    String newText = rawText.substring(start, end);
    SourceOffsets newOffsets = null;
    SourceLocation newLocation = getSourceLocation();
    if (offsets != null) {
      newOffsets = offsets.substring(start, end, rawText);
      newLocation = newOffsets.getSourceLocation(getSourceLocation().getFilePath());
    }
    return new RawTextNode(newId, newText, newLocation, newOffsets);
  }

  /** Concatenates the non-empty set of RawTextNodes, preserving source location information. */
  public static RawTextNode concat(List<RawTextNode> nodes) {
    checkArgument(!nodes.isEmpty());
    if (nodes.size() == 1) {
      return nodes.get(0);
    }
    int id = nodes.get(0).getId();
    int numChars = 0;
    int numOffsets = 0;
    for (RawTextNode node : nodes) {
      numChars += node.getRawText().length();
      // if any of the offsets are null don't try to merge offsets
      // offsets are most often null when SourceLocation.UNKNOWN is in use
      if (numOffsets != -1 && node.offsets != null) {
        // since the last index in the array refers to a pseudo end location, we drop it when
        // joining the arrays
        // we don't preserve offsets from empty nodes.
        numOffsets += node.isEmpty() ? 0 : node.offsets.indexes.length - 1;
      } else {
        numOffsets = -1;
      }
    }
    char[] chars = new char[numChars];
    int charsIndex = 0;

    int[] indexes = null;
    int[] lines = null;
    int[] columns = null;
    Reason[] reasons = null;
    // the current index into the offsets arrays
    int offsetsIndex = 0;
    // the length of the string so far

    if (numOffsets > 0) {
      // +1 because we want to preserve the end location of the last node.
      indexes = new int[numOffsets + 1];
      lines = new int[numOffsets + 1];
      columns = new int[numOffsets + 1];
      reasons = new Reason[numOffsets + 1];
    }

    for (int i = 0; i < nodes.size(); i++) {
      RawTextNode node = nodes.get(i);
      String text = node.getRawText();
      text.getChars(0, text.length(), chars, charsIndex);
      if (indexes != null && !text.isEmpty()) {
        // To concatenate we need to approximately just cat the arrays
        // since the last slot in the array corresponds to a pseudo location (the end of the string)
        // we should drop it when splicing.
        // we also need to modify all the indexes in other to be offset by the length of this
        // offset's string.
        SourceOffsets offsets = node.offsets;
        int amountToCopy = offsets.indexes.length;
        if (i < nodes.size() - 1) {
          amountToCopy--;
        }
        System.arraycopy(offsets.lines, 0, lines, offsetsIndex, amountToCopy);
        System.arraycopy(offsets.columns, 0, columns, offsetsIndex, amountToCopy);
        System.arraycopy(offsets.reasons, 0, reasons, offsetsIndex, amountToCopy);

        // do some special handling for the 'reason' of the join point.
        // If the new begin reason is NONE, then use the old end reason.
        if (offsets.reasons[0] == Reason.NONE && i > 0) {
          Reason[] prevReasons = nodes.get(i - 1).offsets.reasons;
          reasons[offsetsIndex] = prevReasons[prevReasons.length - 1];
        }
        // manually copy the indexes over so we can apply the current character index
        for (int indexIndex = 0; indexIndex < amountToCopy; indexIndex++) {
          indexes[indexIndex + offsetsIndex] = offsets.indexes[indexIndex] + charsIndex;
        }
        offsetsIndex += amountToCopy;
      }
      charsIndex += text.length();
    }
    String text = new String(chars);
    SourceLocation location =
        nodes.get(0).getSourceLocation().extend(Iterables.getLast(nodes).getSourceLocation());
    return new RawTextNode(
        id,
        text,
        location,
        indexes == null ? null : new SourceOffsets(indexes, lines, columns, reasons));
  }

  @Override
  public String toSourceString() {

    StringBuffer sb = new StringBuffer();

    // Must escape special chars to create valid source text.
    Matcher matcher = SPECIAL_CHARS_TO_ESCAPE.matcher(rawText);
    while (matcher.find()) {
      String specialCharTag = SPECIAL_CHAR_TO_TAG.get(matcher.group());
      matcher.appendReplacement(sb, Matcher.quoteReplacement(specialCharTag));
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  @Override
  public ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }


  @Override
  public RawTextNode copy(CopyState copyState) {
    return new RawTextNode(this, copyState);
  }

  /**
   * A helper object to calculate source location offsets inside of RawTextNodes.
   *
   * <p>Due to how Soy collapses whitespace and uses non-literal tokens for textual content (e.g.
   * {@code literal} commands and formatting commands like {@code {\n}}). It isn't possible to
   * reconstruct the source location of any given character within a sequence of raw text based
   * purely on start/end locations. This class fulfils the gap by tracking offsets where the
   * sourcelocation changes discontinuously.
   */
  public static final class SourceOffsets {
    /** Records the reason there is an offset at a particular location. */
    public enum Reason {
      /** There is an offset because of a textual command like <code>{sp}</code>. */
      COMMAND,
      /** There is an offset because of a <code>{literal}</code> block. */
      LITERAL,
      /** There is an offset because of a comment. */
      COMMENT,
      /** There is an offset because we performed whitespace joining. */
      WHITESPACE,
      /**
       * There is no offset. This will happen for initial points and final points and possibly
       * others due to {@link #concat} because we performed whitespace joining.
       */
      NONE;
    }

    @Nullable
    static SourceOffsets fromLocation(SourceLocation location, int length) {
      if (!location.isKnown()) {
        // this is lame but a lot of tests construct 'unknown' rawtextnodes
        return null;
      }
      Builder builder = new Builder();
      if (length > 0) {
        builder.add(0, location.getBeginLine(), location.getBeginColumn(), Reason.NONE);
      }
      return builder
          .setEndLocation(location.getEndLine(), location.getEndColumn())
          .build(length, Reason.NONE);
    }

    // These arrays are parallel.

    /** The indexes into the raw text. */
    private final int[] indexes;

    /** The source column associated with the corresponding index in indexes. */
    private final int[] columns;

    /** The source line numbers associated with the corresponding index in indexes. */
    private final int[] lines;

    /** Records the reason why there is a discontinuity in the line numbers at this offset. */
    private final Reason[] reasons;

    private SourceOffsets(int[] indexes, int[] lines, int[] columns, Reason[] reasons) {
      this.indexes = checkNotNull(indexes);
      int prev = -1;
      for (int index : indexes) {
        if (index <= prev) {
          throw new IllegalArgumentException(
              "expected indexes to be monotonically increasing, got: " + Arrays.toString(indexes));
        }
        prev = index;
      }
      this.lines = checkNotNull(lines);
      this.columns = checkNotNull(columns);
      this.reasons = checkNotNull(reasons);
    }

    /** Returns the {@link Point} of the given offset within the given text. */
    Point getPoint(String text, int textIndex) {
      // the returned location is the place in the array where index would be inserted, so in
      // practice it is pointing at the smallest item in the array >= index.
      int location = Arrays.binarySearch(indexes, textIndex);
      // if 'textIndex' isn't in the list it returns (-insertion_point - 1) so if we want to know
      // the insertion point we need to do this transformation
      if (location < 0) {
        location = -(location + 1);
      }
      if (indexes[location] == textIndex) {
        // direct hit!
        return Point.create(lines[location], columns[location]);
      }
      // if it isn't a direct hit, we start at the previous item and walk forward through the array
      // counting character and newlines.
      return getLocationOf(text, location - 1, textIndex);
    }

    /** Returns the reason for a location discontinuity at the given index in the text. */
    Reason getReasonAt(int index) {
      checkElementIndex(index, indexes[indexes.length - 1] + 1);
      int location = Arrays.binarySearch(indexes, index);
      // if 'index' isn't in the list it returns (-insertion_point - 1) in which case we know the
      // reason is NONE
      if (location < 0) {
        return Reason.NONE;
      }
      return reasons[location];
    }

    /**
     * Returns the Point where the character at {@code textIndex} is within the text. The scan
     * starts from {@code lines[startLocation]} which is guaranteed to be < textIndex.
     */
    private Point getLocationOf(String text, int startLocation, int textIndex) {
      int line = lines[startLocation];
      int column = columns[startLocation];
      int start = indexes[startLocation];
      for (int i = start; i < textIndex; i++) {
        char c = text.charAt(i);
        if (c == '\n') {
          line++;
          // N.B. we use 1 based indexes for columns (and lines, though that isn't relevant here)
          column = 1;
        } else if (c == '\r') {
          // look for \n as the next char to handled both \r and \r\n
          if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
            i++;
          }
          line++;
          column = 1;
        } else {
          column++;
        }
      }
      return Point.create(line, column);
    }

    /** Returns a new SourceOffsets object for the given subrange of the text. */
    SourceOffsets substring(int startTextIndex, int endTextIndex, String text) {
      checkArgument(startTextIndex >= 0);
      checkArgument(startTextIndex < endTextIndex);
      checkArgument(endTextIndex <= text.length());
      int substringLength = endTextIndex - startTextIndex;
      // subtract 1 from end since we want the endLocation to point at the last character rather
      // than just beyond it.
      endTextIndex--;

      int startLocation = Arrays.binarySearch(indexes, startTextIndex);
      // if 'startLocation' isn't in the list it returns (-insertion_point -1) so if we want to know
      // the insertion point we need to do this transformation
      if (startLocation < 0) {
        startLocation = -(startLocation + 1);
      }

      // calculate the initial point
      SourceOffsets.Builder builder = new SourceOffsets.Builder();
      int startLine;
      int startColumn;
      Reason startReason;
      // if the index of the startlocation is the start index, set the startLine and startColumn
      // appropriately
      if (indexes[startLocation] == startTextIndex) {
        startLine = lines[startLocation];
        startColumn = columns[startLocation];
        startReason = reasons[startLocation];
      } else {
        // otherwise scan from the previous location forward to 'start'
        startLocation--;
        Point startPoint = getLocationOf(text, startLocation, startTextIndex);
        startLine = startPoint.line();
        startColumn = startPoint.column();
        startReason = Reason.NONE;
      }
      builder.doAdd(0, startLine, startColumn, startReason);

      if (startTextIndex == endTextIndex) {
        // special case
        builder.setEndLocation(startLine, startColumn);
        return builder.build(substringLength, startReason);
      }

      // copy over all offsets, taking care to modify the indexes
      int i = startLocation + 1;
      Reason endReason = Reason.NONE;
      while (true) {
        int index = indexes[i];
        if (index < endTextIndex) {
          builder.doAdd(index - startTextIndex, lines[i], columns[i], reasons[i]);
        } else if (index == endTextIndex) {
          builder.setEndLocation(lines[i], columns[i]);
          endReason = reasons[i];
          break;
        } else if (index > endTextIndex) {
          // to find the end location we need to scan from the previous index
          Point endPoint = getLocationOf(text, i - 1, endTextIndex);
          builder.setEndLocation(endPoint.line(), endPoint.column());
          break;
        }
        i++;
      }

      return builder.build(substringLength, endReason);
    }

    /** Returns the sourcelocation for the whole span. */
    public SourceLocation getSourceLocation(String filePath) {
      return new SourceLocation(
          filePath, lines[0], columns[0], lines[lines.length - 1], columns[columns.length - 1]);
    }

    @Override
    public String toString() {
      return String.format(
          "SourceOffsets{\n  index:\t%s\n  lines:\t%s\n   cols:\t%s\n}",
          Arrays.toString(indexes), Arrays.toString(lines), Arrays.toString(columns));
    }

    /** Builder for SourceOffsets. */
    public static final class Builder {
      private int size;
      private int[] indexes = new int[16];
      private int[] lines = new int[16];
      private int[] columns = new int[16];
      private Reason[] reasons = new Reason[16];
      private int endLine = -1;
      private int endCol = -1;

      public Builder add(int index, int startLine, int startCol, Reason reason) {
        checkArgument(index >= 0, "expected index to be non-negative: %s", index);
        checkArgument(startLine > 0, "expected startLine to be positive: %s", startLine);
        checkArgument(startCol > 0, "expected startCol to be positive: %s", startCol);
        if (size != 0 && index <= indexes[size - 1]) {
          throw new IllegalArgumentException(
              String.format(
                  "expected indexes to be added in increasing order: %d vs %d at %d:%d - %d:%d",
                  index, indexes[size - 1], startLine, startCol, endLine, endCol));
        }
        doAdd(index, startLine, startCol, reason);
        return this;
      }

      /** Update the end location only. */
      public Builder setEndLocation(int endLine, int endCol) {
        checkArgument(endLine > 0, "expected endLine to be positive: %s", endLine);
        checkArgument(endCol > 0, "expected endCol to be positive: %s", endCol);
        this.endLine = endLine;
        this.endCol = endCol;
        return this;
      }

      /** Delete all the offsets starting from the {@code from} index. */
      public Builder delete(int from) {
        // since we store end indexes in the list, we really just want to delete everything strictly
        // after 'from', this way if we leave 'from' as an end point
        int location = Arrays.binarySearch(indexes, 0, size, from);
        // if 'from' isn't in the list it returns (-insertion_point -1) so if we want to know the
        // insertion point we need to do this transformation
        if (location < 0) {
          location = -(location + 1);
        }
        size = location;
        return this;
      }

      public boolean isEmpty() {
        return size == 0;
      }

      /** Returns the ending line number or {@code -1} if it hasn't been set yet. */
      public int endLine() {
        return endLine;
      }

      /** Returns the ending column number or {@code -1} if it hasn't been set yet. */
      public int endColumn() {
        return endCol;
      }

      private void doAdd(int index, int line, int col, Reason reason) {
        if (size == indexes.length) {
          // expand by 1.5x each time
          int newCapacity = size + (size >> 1);
          indexes = Arrays.copyOf(indexes, newCapacity);
          lines = Arrays.copyOf(lines, newCapacity);
          columns = Arrays.copyOf(columns, newCapacity);
          reasons = Arrays.copyOf(reasons, newCapacity);
        }
        indexes[size] = index;
        lines[size] = line;
        columns[size] = col;
        reasons[size] = reason;
        size++;
      }

      /**
       * Builds the {@link SourceOffsets}.
       *
       * @param length the final length of the text.
       */
      public SourceOffsets build(int length, Reason reason) {
        // Set the last index as the length of the string and put the endLine/endCol there.
        // This simplifies some of the logic in SourceOffsets since it allows us to avoid
        // considering the end of the string as a special case.
        doAdd(length, endLine, endCol, reason);

        checkArgument(size > 0, "The builder should be non-empty");
        checkArgument(indexes[0] == 0, "expected first index to be zero, got: %s", indexes[0]);
        SourceOffsets built =
            new SourceOffsets(
                Arrays.copyOf(indexes, size),
                Arrays.copyOf(lines, size),
                Arrays.copyOf(columns, size),
                Arrays.copyOf(reasons, size));
        // by resetting size by 1 we undo the 'doAdd' of the endLine and endCol above and thus this
        // method becomes safe to call multiple times.
        size--;
        return built;
      }
    }
  }
}
