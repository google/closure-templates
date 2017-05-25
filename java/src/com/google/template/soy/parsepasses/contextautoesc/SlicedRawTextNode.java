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

import com.google.auto.value.AutoValue;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
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
  @AutoValue
  abstract static class RawTextSlice {
    static RawTextSlice create(
        Context context, RawTextNode rawTextNode, int startOffset, int endOffset) {
      return new AutoValue_SlicedRawTextNode_RawTextSlice(
          context, rawTextNode, startOffset, endOffset);
    }

    /** The context of the slice. */
    abstract Context getContext();

    /** The text node containing the slice. */
    abstract RawTextNode getRawTextNode();

    /** The start offset (inclusive) into the text node's text. */
    abstract int getStartOffset();

    /** The end offset (exclusive) into the text node's text. */
    abstract int getEndOffset();

    /** The length of the slice in {@code char}s. */
    public int getLength() {
      return getEndOffset() - getStartOffset();
    }

    /** The raw text of the slice. */
    public String getRawText() {
      return getRawTextNode().getRawText().substring(getStartOffset(), getEndOffset());
    }

    /** For debugging. */
    @Override
    public String toString() {
      String rawText = getRawText();
      int id = getRawTextNode().getId();
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
      if (last.getEndOffset() == startOffset && context.equals(last.getContext())) {
        slices.remove(lastSliceIndex);
        startOffset = last.getStartOffset();
      }
    }
    slices.add(RawTextSlice.create(context, rawTextNode, startOffset, endOffset));
  }

  /**
   * The slices that occur in the context described by the given predicates.
   *
   * <p>The order is deterministic but does not necessarily bear any relationship to the order in
   * which slices can appear in the template's output because it is dependent on the ordering of
   * individual templates in the parsed input.
   *
   * @param slicedTextNodes The sliced raw text nodes to search.
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

    // TODO(lukes): we need to dedupe.  In some cases the inference engine produces duplicates, so
    // we eliminate them here.  This code should all be deleted soon so this is a more expedient fix
    ImmutableSet.Builder<RawTextSlice> matches = ImmutableSet.builder();
    for (SlicedRawTextNode slicedTextNode : slicedTextNodes) {
      Context prevContext = slicedTextNode.startContext;
      List<RawTextSlice> slices = slicedTextNode.slices;
      for (int i = 0, n = slices.size(); i < n; ++i) {
        RawTextSlice current = slices.get(i);
        Context nextContext;
        if (i + 1 < n) {
          nextContext = slices.get(i + 1).getContext();
        } else {
          nextContext = slicedTextNode.endContext;
        }
        // Apply the predicates.
        if (prevContextPredicate.apply(prevContext)
            && sliceContextPredicate.apply(current.getContext())
            && nextContextPredicate.apply(nextContext)) {
          matches.add(current);
        }
        prevContext = current.getContext();
      }
    }
    return matches.build().asList();
  }
}
