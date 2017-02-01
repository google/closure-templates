/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.soytree.RawTextNode;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A raw text node divided into a slice for each context found by the inference engine so that later
 * parse passes can take action based on text and attribute boundaries.
 *
 */
public final class SlicedRawTextNode {

  /**
   * A substring of raw text that is exposed to parse passes.
   *
   * <p>This slice is not the entire substring with the same context. Such a thing cannot be
   * statically determined since in a portion of a template that stays in the same context like
   *
   * <pre>
   *   foo {if $cond}bar{else}baz{/if} boo
   * </pre>
   *
   * there might be two possible strings with the same context: {@code "foo bar boo"} and {@code
   * "foo baz boo"}.
   *
   * <p>
   */
  public static final class RawTextSlice {

    /** The context of the slice. */
    public final Context context;

    /** The text node containing the slice. */
    public final SlicedRawTextNode slicedRawTextNode;

    /** The start offset (inclusive) into the text node's text. */
    private int startOffset;

    /** The end offset (exclusive) into the text node's text. */
    private int endOffset;

    RawTextSlice(
        Context context, SlicedRawTextNode slicedRawTextNode, int startOffset, int endOffset) {
      this.context = context;
      this.slicedRawTextNode = slicedRawTextNode;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    /** The start offset (inclusive) into the text node's text. */
    public int getStartOffset() {
      return startOffset;
    }

    /** The length of the slice in {@code char}s. */
    public int getLength() {
      return endOffset - startOffset;
    }

    public SlicedRawTextNode getSlicedRawTextNode() {
      return slicedRawTextNode;
    }

    /**
     * Splits this slice in two at the given offset and returns the slice after the split.
     *
     * @param offset into the slice.
     */
    private RawTextSlice split(int offset) {
      int indexInParent = slicedRawTextNode.slices.indexOf(this);
      if (indexInParent < 0) {
        throw new AssertionError("slice is not in its parent");
      }
      Preconditions.checkElementIndex(offset, getLength(), "slice offset");
      RawTextSlice secondSlice = slicedRawTextNode.insertSlice(indexInParent + 1, context, 0);
      int wholeTextOffset = offset + startOffset;
      secondSlice.startOffset = wholeTextOffset;
      this.endOffset = wholeTextOffset;
      return secondSlice;
    }

    /**
     * Mutates the parse tree by replacing the sliced text node with a text node that includes the
     * given text at the given point within this slice.
     *
     * @param text A string in context context.
     * @param offset An offset between 0 (inclusive) and {@link #getLength()} (exclusive).
     * @param context the context of text.
     * @throws SoyAutoescapeException if inserting text would violate context assumptions made by
     *     the contextual autoescaper.
     */
    public void insertText(int offset, String text) throws SoyAutoescapeException {
      int indexInParent = slicedRawTextNode.slices.indexOf(this);
      if (indexInParent < 0) {
        throw new AssertionError("slice is not in its parent");
      }
      Preconditions.checkElementIndex(offset, getLength(), "slice offset");

      // Figure out at which character offset to insert text.
      int insertionIndex = -1;
      int insertionOffset = -1;

      // Split or recurse as necessary so that we are called at the boundary of a slice.
      if (offset == 0) {
        insertionIndex = indexInParent;
        insertionOffset = startOffset;
      } else if (offset == getLength()) {
        insertionIndex = indexInParent;
        insertionOffset = endOffset;
      } else {
        split(offset).insertText(0, text);
        return;
      }

      // Compute the new raw text and create a node to hold it.
      // We re-use the node ID since we're going to remove the old node and discard it.
      RawTextNode rawTextNode = slicedRawTextNode.getRawTextNode();
      String originalText = rawTextNode.getRawText();
      String replacementText =
          originalText.substring(0, insertionOffset)
              + text
              + originalText.substring(insertionOffset);
      RawTextNode replacementNode =
          new RawTextNode(rawTextNode.getId(), replacementText, rawTextNode.getSourceLocation());

      // Rerun the context update algo so that we can figure out the context of the inserted slices
      // and ensure that the inserted text does not invalidate any of the security assumptions made
      // by the auto-escaper.
      Context startContext = slicedRawTextNode.startContext;
      Context expectedEndContext = slicedRawTextNode.endContext;
      SlicedRawTextNode retyped =
          RawTextContextUpdater.processRawText(replacementNode, startContext);
      Context actualEndContext = retyped.getEndContext();

      if (!expectedEndContext.equals(actualEndContext)) {
        // Inserting the text would invalidate typing assumptions made earlier.
        throw SoyAutoescapeException.createWithNode(
            "Inserting `"
                + text
                + "` would cause text node to end in context "
                + actualEndContext
                + " instead of "
                + expectedEndContext,
            rawTextNode);
      }

      // Now that we know that it's valid to insert the text at that location, replace the text node
      // and insert slices for each of the slices in builder corresponding to characters in text.
      slicedRawTextNode.replaceNode(replacementNode);
      int insertionEndOffset = insertionOffset + text.length();
      for (RawTextSlice slice : retyped.slices) {
        if (slice.endOffset <= insertionOffset) {
          continue;
        }
        if (slice.startOffset >= insertionEndOffset) {
          break;
        }
        int length =
            Math.min(insertionEndOffset, slice.endOffset)
                - Math.max(insertionOffset, slice.startOffset);
        slicedRawTextNode.insertSlice(insertionIndex, slice.context, length);
        // Increment the insertion index to point past the slice just inserted so that
        // we're ready for the next one.
        ++insertionIndex;
      }
    }

    /** The raw text of the slice. */
    public String getRawText() {
      return slicedRawTextNode.rawTextNode.getRawText().substring(startOffset, endOffset);
    }

    /** Adjusts the start and end offsets right by the given amount. */
    void shiftOffsets(int delta) {
      startOffset += delta;
      endOffset += delta;
    }

    /** For debugging. */
    @Override
    public String toString() {
      String rawText = getRawText();
      int id = slicedRawTextNode.rawTextNode.getId();
      // "<rawText>"@<textNodeId> with \ and " escaped.
      return "\"" + rawText.replaceAll("\"|\\\\", "\\\\$0") + "\"#" + id;
    }
  }

  /** The backing raw text node. */
  private RawTextNode rawTextNode;
  /** The context in which the text node starts. */
  private final Context startContext;
  /** The context in which the text node ends. */
  private Context endContext;
  /** A collection of all the slices of a particular raw text node in order. */
  private final List<RawTextSlice> slices = Lists.newArrayList();

  public SlicedRawTextNode(RawTextNode rawTextNode, Context startContext) {
    this.rawTextNode = rawTextNode;
    this.startContext = startContext;
  }

  public RawTextNode getRawTextNode() {
    return rawTextNode;
  }

  public List<RawTextSlice> getSlices() {
    return Collections.unmodifiableList(slices);
  }

  /** The context in which the text node ends. */
  public Context getEndContext() {
    return endContext;
  }

  void setEndContext(Context endContext) {
    this.endContext = endContext;
  }

  /**
   * Called by the builder to add slices as their context becomes known.
   *
   * @param startOffset an offset (inclusive) into the rawTextNode's string content.
   * @param endOffset an offset (exclusive) into the rawTextNode's string content.
   * @param context the context for the slice.
   */
  void addSlice(int startOffset, int endOffset, Context context) {
    int lastSliceIndex = slices.size() - 1;
    // Merge adjacent tokens that don't change context.
    if (lastSliceIndex >= 0) {
      RawTextSlice last = slices.get(lastSliceIndex);
      if (last.endOffset == startOffset && context.equals(last.context)) {
        slices.remove(lastSliceIndex);
        startOffset = last.startOffset;
      }
    }
    slices.add(new RawTextSlice(context, this, startOffset, endOffset));
  }

  /** Replaces the backing node in the parse tree and internally. */
  void replaceNode(RawTextNode replacement) {
    rawTextNode.getParent().replaceChild(rawTextNode, replacement);
    this.rawTextNode = replacement;
  }

  /**
   * Inserts a slice, updating the offsets of any following slices and returns the newly created
   * slice.
   */
  RawTextSlice insertSlice(int index, Context context, int length) {
    if (length < 0) {
      throw new IllegalArgumentException("length " + length + " < 0");
    }
    int startOffset = index == 0 ? 0 : slices.get(index - 1).endOffset;
    for (RawTextSlice follower : slices.subList(index, slices.size())) {
      follower.shiftOffsets(length);
    }
    RawTextSlice slice = new RawTextSlice(context, this, startOffset, startOffset + length);
    slices.add(index, slice);
    return slice;
  }

  @VisibleForTesting
  void mergeAdjacentSlicesWithSameContext() {
    // Rewrite list from left to right merging adjacent slices with the same context.
    int nMerged = 0;
    for (int i = 0, n = slices.size(), next; i < n; i = next, ++nMerged) {
      next = i + 1;
      RawTextSlice slice = slices.get(i);
      // Walk next forward until we see a different context.
      while (next < n && slice.context.equals(slices.get(next).context)) {
        ++next;
      }
      // Modify slices in place to have exactly one slice corresponding to [i, next).
      RawTextSlice merged;
      if (next - i == 1) {
        // If there haven't been modifications since the last merge, don't orphan slices.
        merged = slice;
      } else {
        merged =
            new RawTextSlice(
                slice.context, this, slice.startOffset, slices.get(next - 1).endOffset);
      }
      slices.set(nMerged, merged);
    }
    // Truncate.
    slices.subList(nMerged, slices.size()).clear();
  }

  /**
   * The slices that occur in the context described by the given predicates.
   *
   * <p>The order is deterministic but does not necessarily bear any relationship to the order in
   * which slices can appear in the template's output because it is dependent on the ordering of
   * individual templates in the parsed input.
   *
   * @param slicedRawTextNodes The sliced raw text nodes to search.
   * @param prevContextPredicate Applied to the context before the slice being tested.
   * @param sliceContextPredicate Applied to the context of the slice being tested.
   * @param nextContextPredicate Applied to the context after the slice being tested.
   * @return a list of slices such that input predicates are all true when applied to the contexts
   *     at and around that slice.
   */
  public static List<RawTextSlice> find(
      Iterable<? extends SlicedRawTextNode> slicedTextNodes,
      @Nullable Predicate<? super Context> prevContextPredicate,
      @Nullable Predicate<? super Context> sliceContextPredicate,
      @Nullable Predicate<? super Context> nextContextPredicate) {
    if (prevContextPredicate == null) {
      prevContextPredicate = Predicates.<Context>alwaysTrue();
    }
    if (sliceContextPredicate == null) {
      sliceContextPredicate = Predicates.<Context>alwaysTrue();
    }
    if (nextContextPredicate == null) {
      nextContextPredicate = Predicates.<Context>alwaysTrue();
    }

    ImmutableList.Builder<RawTextSlice> matches = ImmutableList.builder();
    for (SlicedRawTextNode slicedTextNode : slicedTextNodes) {
      // insertText can leave adjacent slices with the same context.
      // Merge slices so that each element in find()'s result list stands alone.
      // This could cause problems with concurrent iteration over two find lists, but the mutators
      // check that a slice is part of its parent so we will fail fast.
      slicedTextNode.mergeAdjacentSlicesWithSameContext();

      Context prevContext = slicedTextNode.startContext;
      List<RawTextSlice> slices = slicedTextNode.slices;
      for (int i = 0, n = slices.size(); i < n; ++i) {
        RawTextSlice current = slices.get(i);
        Context nextContext;
        if (i + 1 < n) {
          nextContext = slices.get(i + 1).context;
        } else {
          nextContext = slicedTextNode.endContext;
        }
        // Apply the predicates.
        if (prevContextPredicate.apply(prevContext)
            && sliceContextPredicate.apply(current.context)
            && nextContextPredicate.apply(nextContext)) {
          matches.add(current);
        }
        prevContext = current.context;
      }
    }
    return matches.build();
  }
}
