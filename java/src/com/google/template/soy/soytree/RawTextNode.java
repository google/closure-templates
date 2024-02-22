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
import static com.google.common.base.Utf8.encodedLength;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLocation.Point;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.RawTextNode.SourceOffsets.Reason;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Node representing a contiguous raw text section.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class RawTextNode extends AbstractSoyNode
    implements HtmlContext.HtmlContextHolder, StandaloneNode {

  /** Specifies how this text node was created. */
  public enum Provenance {
    /** The node is a regular text node (not a literal or special command character). */
    NORMAL,

    /**
     * The node represents a single command character (e.g. "{sp}" or "{\t}"). This type allows the
     * formatter to distinguish between "{sp}" and " ", for example.
     */
    COMMAND_CHARACTER,

    /** The node was created from a {literal} command */
    LITERAL,

    /**
     * The node was created via calling {@link #substring(int, int)} on a node that was created with
     * a {literal} command.
     */
    LITERAL_SUBSTRING,

    /**
     * The node was created via calling {@link #concat}, probably during
     * CombineConsecutiveRawTextNodesPass.
     */
    CONCATENATED
  }

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
          .buildOrThrow();

  /** The raw text string (after processing of special chars and literal blocks). */
  private final String rawText;

  // For COMMAND_CHARACTER nodes only. The character this node represents (e.g. "{sp}" or "{nbsp}").
  private final Optional<CommandChar> commandChar;

  /** Whether this raw text was created from the {literal} command. */
  private final Provenance provenance;

  @Nullable private final SourceOffsets offsets;

  @Nullable private HtmlContext htmlContext;

  /**
   * @param id The id for this node.
   * @param rawText The raw text string.
   * @param sourceLocation The node's source location.
   */
  public RawTextNode(int id, String rawText, SourceLocation sourceLocation) {
    this(
        id,
        rawText,
        Optional.empty(),
        sourceLocation,
        SourceOffsets.fromLocation(sourceLocation, rawText.length()),
        Provenance.NORMAL);
  }

  public RawTextNode(int id, String rawText, SourceLocation sourceLocation, SourceOffsets offsets) {
    this(id, rawText, Optional.empty(), sourceLocation, offsets, Provenance.NORMAL);
  }

  private RawTextNode(
      int id,
      String rawText,
      SourceLocation sourceLocation,
      SourceOffsets offsets,
      Provenance provenance) {
    this(id, rawText, Optional.empty(), sourceLocation, offsets, provenance);
  }

  private RawTextNode(
      int id,
      String rawText,
      Optional<CommandChar> commandChar,
      SourceLocation sourceLocation,
      @Nullable SourceOffsets offsets,
      Provenance provenance) {
    super(id, sourceLocation);
    this.rawText = rawText;
    this.commandChar = commandChar;
    this.provenance = provenance;
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
    this.commandChar = orig.commandChar;
    this.provenance = orig.provenance;
    this.htmlContext = orig.htmlContext;
    this.offsets = orig.offsets;
  }

  public static RawTextNode newLiteral(int id, String rawText, SourceLocation loc) {
    SourceOffsets.Builder builder = new SourceOffsets.Builder();
    if (rawText.length() > 0) {
      builder.add(0, loc.getBeginPoint(), Reason.NONE);
    }
    SourceOffsets offsets =
        builder.setEndLocation(loc.getEndPoint()).build(rawText.length(), Reason.LITERAL);
    return new RawTextNode(id, rawText, loc, offsets, Provenance.LITERAL);
  }

  public static RawTextNode newCommandCharNode(
      int id, CommandChar commandChar, SourceLocation loc) {
    SourceOffsets.Builder offsetsBuilder = new SourceOffsets.Builder();
    if (!commandChar.equals(CommandChar.NIL)) {
      // {nil} has length 0, so we only need one index (the "end" index below).
      offsetsBuilder.add(0, loc.getBeginPoint(), Reason.NONE);
    }
    SourceOffsets offsets =
        offsetsBuilder
            .setEndLocation(loc.getEndPoint())
            .build(commandChar.processedString().length(), Reason.COMMAND);

    return new RawTextNode(
        id,
        /* rawText= */ commandChar.processedString(),
        Optional.of(commandChar),
        loc,
        offsets,
        Provenance.COMMAND_CHARACTER);
  }

  /**
   * Gets the HTML source context (typically tag, attribute value, HTML PCDATA, or plain text) which
   * this node emits in. This is used for incremental DOM codegen.
   */
  @Override
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

  public Provenance getProvenance() {
    return provenance;
  }

  /** Whether or not this node represents a command character (e.g. "{sp}"). */
  public boolean isCommandCharacter() {
    return provenance.equals(Provenance.COMMAND_CHARACTER);
  }

  /** Whether this node represents the {nil} command character. */
  public boolean isNilCommandChar() {
    return commandChar.isPresent() && commandChar.get().equals(CommandChar.NIL);
  }

  /**
   * Gets the command character this node represents. Should only be called for nodes of type
   * COMMAND_CHARACTER.
   */
  public CommandChar getCommandChar() {
    return commandChar.get();
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
    return offsets != null && offsets.getReasonAt(index) == Reason.WHITESPACE;
  }

  /**
   * Returns true if there was a text command such as {sp} or {nil} immediately prior to {@code
   * index}.
   *
   * @param index the index in the raw text, this value should be in the range {@code [0,
   *     rawText.length()]} if {@code rawText.length()} is passed, then this is equivalent to asking
   *     if it ends with a commamnd.
   * @throws IndexOutOfBoundsException if index is out of range.
   * @return {@code true} if command executed
   */
  public boolean commandAt(int index) {
    return offsets != null && offsets.getReasonAt(index) == Reason.COMMAND;
  }

  @Nullable
  public Reason getReasonAt(int index) {
    return offsets == null ? null : offsets.getReasonAt(index);
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
    return new RawTextNode(
        newId,
        newText,
        newLocation,
        newOffsets,
        provenance == Provenance.LITERAL ? Provenance.LITERAL_SUBSTRING : Provenance.NORMAL);
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
    Point[] points = null;
    Reason[] reasons = null;
    // the current index into the offsets arrays
    int offsetsIndex = 0;
    // the length of the string so far

    if (numOffsets > 0) {
      // +1 because we want to preserve the end location of the last node.
      indexes = new int[numOffsets + 1];
      points = new Point[numOffsets + 1];
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
        System.arraycopy(offsets.points, 0, points, offsetsIndex, amountToCopy);
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
        indexes == null ? null : new SourceOffsets(indexes, points, reasons),
        Provenance.CONCATENATED);
  }

  @Override
  public String toSourceString() {
    // If it's a command character node, don't use the rawText (which is already processed). Use the
    // character's source string instead.
    if (this.provenance.equals(Provenance.COMMAND_CHARACTER)) {
      return this.commandChar.get().sourceString();
    }

    StringBuffer sb = new StringBuffer();
    if (provenance == Provenance.LITERAL) {
      sb.append("{literal}").append(rawText).append("{/literal}");
    } else {
      // Must escape special chars to create valid source text.
      Matcher matcher = SPECIAL_CHARS_TO_ESCAPE.matcher(rawText);
      while (matcher.find()) {
        String specialCharTag = SPECIAL_CHAR_TO_TAG.get(matcher.group());
        matcher.appendReplacement(sb, Matcher.quoteReplacement(specialCharTag));
      }
      matcher.appendTail(sb);
    }

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
      NONE
    }

    @Nullable
    static SourceOffsets fromLocation(SourceLocation location, int length) {
      if (!location.isKnown()) {
        // this is lame but a lot of tests construct 'unknown' rawtextnodes
        return null;
      }
      Builder builder = new Builder();
      if (length > 0) {
        builder.add(0, location.getBeginPoint(), Reason.NONE);
      }
      return builder.setEndLocation(location.getEndPoint()).build(length, Reason.NONE);
    }

    // These arrays are parallel.

    /** The indexes into the raw text. */
    private final int[] indexes;

    /** The source points associated with the corresponding index in indexes. */
    private final Point[] points;

    /** Records the reason why there is a discontinuity in the line numbers at this offset. */
    private final Reason[] reasons;

    private SourceOffsets(int[] indexes, Point[] points, Reason[] reasons) {
      this.indexes = checkNotNull(indexes);
      int prev = -1;
      for (int index : indexes) {
        if (index <= prev) {
          throw new IllegalArgumentException(
              "expected indexes to be monotonically increasing, got: " + Arrays.toString(indexes));
        }
        prev = index;
      }
      this.points = checkNotNull(points);
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
        return points[location];
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
      Point point = points[startLocation];
      int line = point.line();
      int column = point.column();
      int bytes = point.byteOffset();

      int start = indexes[startLocation];
      int i = start;
      for (; i < textIndex; i++) {
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
      return Point.create(line, column, bytes + encodedLength(text.substring(start, i - 1)));
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
      Point startPoint;
      Reason startReason;
      // if the index of the startlocation is the start index, set the startLine and startColumn
      // appropriately
      if (indexes[startLocation] == startTextIndex) {
        startPoint = points[startLocation];
        startReason = reasons[startLocation];
      } else {
        // otherwise scan from the previous location forward to 'start'
        startLocation--;
        startPoint = getLocationOf(text, startLocation, startTextIndex);
        startReason = Reason.NONE;
      }
      builder.doAdd(0, startPoint, startReason);

      if (startTextIndex == endTextIndex) {
        // special case
        builder.setEndLocation(startPoint);

        Reason afterLastCharacter =
            endTextIndex + 1 == text.length() ? getReasonAt(text.length()) : startReason;

        return builder.build(substringLength, /* reason= */ afterLastCharacter);
      }

      // copy over all offsets, taking care to modify the indexes
      int i = startLocation + 1;
      Reason endReason = Reason.NONE;
      while (true) {
        int index = indexes[i];
        if (index < endTextIndex) {
          builder.doAdd(index - startTextIndex, points[i], reasons[i]);
        } else if (index == endTextIndex) {
          builder.setEndLocation(points[i]);
          endReason = reasons[i];
          break;
        } else if (index > endTextIndex) {
          // to find the end location we need to scan from the previous index
          Point endPoint = getLocationOf(text, i - 1, endTextIndex);
          builder.setEndLocation(endPoint);
          break;
        }
        i++;
      }
      if (endTextIndex + 1 == text.length()) {
        endReason = getReasonAt(text.length());
      }

      return builder.build(substringLength, endReason);
    }

    /** Returns the sourcelocation for the whole span. */
    public SourceLocation getSourceLocation(SourceFilePath filePath) {
      return new SourceLocation(filePath, points[0], points[points.length - 1]);
    }

    @Override
    public String toString() {
      return String.format(
          "SourceOffsets{\n  index:\t%s\n  points:\t%s\n}",
          Arrays.toString(indexes), Arrays.toString(points));
    }

    /** Builder for SourceOffsets. */
    public static final class Builder {
      private int size;
      private int[] indexes = new int[16];
      private Point[] points = new Point[16];
      private Reason[] reasons = new Reason[16];
      private Point endPoint = Point.UNKNOWN_POINT;

      @CanIgnoreReturnValue
      public Builder add(int index, Point point, Reason reason) {
        checkArgument(index >= 0, "expected index to be non-negative: %s", index);
        checkArgument(point.isKnown(), "expected point to be known: %s", point);
        if (size != 0 && index <= indexes[size - 1]) {
          throw new IllegalArgumentException(
              String.format(
                  "expected indexes to be added in increasing order: %d vs %d at %s - %s",
                  index, indexes[size - 1], point, endPoint));
        }
        doAdd(index, point, reason);
        return this;
      }

      /** Update the end location only. */
      @CanIgnoreReturnValue
      public Builder setEndLocation(Point endPoint) {
        checkArgument(endPoint.isKnown(), "expected endPoint to be known: %s", endPoint);
        this.endPoint = endPoint;
        return this;
      }

      /** Delete all the offsets starting from the {@code from} index. */
      @CanIgnoreReturnValue
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

      /** Returns the ending point or {@code Point.UNKNOWN_POINT} if it hasn't been set yet. */
      public Point endPoint() {
        return endPoint;
      }

      private void doAdd(int index, Point point, Reason reason) {
        if (size == indexes.length) {
          // expand by 1.5x each time
          int newCapacity = size + (size >> 1);
          indexes = Arrays.copyOf(indexes, newCapacity);
          points = Arrays.copyOf(points, newCapacity);
          reasons = Arrays.copyOf(reasons, newCapacity);
        }
        indexes[size] = index;
        points[size] = point;
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
        doAdd(length, endPoint, reason);

        checkArgument(size > 0, "The builder should be non-empty");
        checkArgument(indexes[0] == 0, "expected first index to be zero, got: %s", indexes[0]);
        SourceOffsets built =
            new SourceOffsets(
                Arrays.copyOf(indexes, size),
                Arrays.copyOf(points, size),
                Arrays.copyOf(reasons, size));
        // by resetting size by 1 we undo the 'doAdd' of the endLine and endCol above and thus this
        // method becomes safe to call multiple times.
        size--;
        return built;
      }
    }
  }
}
