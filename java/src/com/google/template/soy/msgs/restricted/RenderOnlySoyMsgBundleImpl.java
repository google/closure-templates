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

package com.google.template.soy.msgs.restricted;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.Immutable;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.ibm.icu.util.ULocale;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;

/**
 * Represents all renderable messages in a locale.
 *
 * <p>This saves significant memory from the normal SoyMsgBundleImpl, but doesn't store details like
 * message descriptions. This also has small runtime performance penalties, such as using binary
 * search instead of hash tables, constructing wrapper objects on the fly, and computing properties
 * of the message instead of storing them.
 *
 */
@Immutable
public final class RenderOnlySoyMsgBundleImpl extends SoyMsgBundle {

  /** The language/locale string of this bundle's messages. */
  private final String localeString;

  private final ULocale locale;
  private final boolean isRtl;

  // Using parallel collections saves memory versus using a Map, because it avoids:
  // * having to wrap the longs in a new Long(), and
  // * avoids wrapping the key/value pair in an Entry.
  // Also, using a sorted collection utilizes memory better, since unlike a hash table, you
  // need neither a linked list nor empty spaces in the hash table.

  /** Sorted array of message ID's that can be binary searched. */
  @SuppressWarnings("Immutable")
  private final long[] ids;

  /**
   * List containing the message parts. See {@link #partRanges} for an explanation for how they
   * correspond to {@link #ids}.
   */
  private final ImmutableList<SoyMsgPart> values;

  /**
   * Contains index-ranges for parts belonging to messages.
   *
   * <p>For instance, for a message with ID {@code ids[n]}, the SoyMsgPart values belonging to that
   * message are the sublist of {@code values} from {@code partRanges[n]} (inclusive} to {@code
   * partRanges[n+1]} (exclusive).
   */
  @SuppressWarnings("Immutable")
  private final int[] partRanges;

  /*
   * This implements a very nearly free dense hashtable of SoyMsgs.
   * - It bucket-sorts entries by the low bits of IDs.
   * - The buckets themselves are sorted by ID and are binary searchable.
   * - The number of buckets varies by the number of SoyMsgs in the bundle.
   * - The cost of the hash table is 32bits/(2**BUCKET_SHIFT) == 0.5bits per entry. This is *much*
   *   smaller than a typical HashMap.
   *
   * If the messages hash evenly by masking their low bits (empircally, they do), then this
   * approaches the performance of a HashMap. If they all hash to the same bucket, it performs no
   * worse than the previous binary-search-the-whole-thing impementation.
   */

  /**
   * Describes the target number of members per bucket, approximately 2^BUCKET_SHIFT per bucket.
   *
   * <p>This number was settled on after benchmarking. Increasing it will decrease the memory
   * footprint of bucketBoundaries and increase hash-bucket crowding. Every time the shift shrinks
   * by one, footprint doubles.
   *
   * <p>Outcomes for different values:
   *
   * <ul>
   *   <li>1 - 68ns (expanded memory footprint)
   *   <li>2 - 66ns
   *   <li>3 - 70ns
   *   <li>4 - 73ns
   *   <li>5 - 75ns
   *   <li>6 - 77ns (chosen option)
   *   <li>7 - 82ns
   *   <li>8 - 84ns
   *   <li>9 - 87ns
   *   <li>10 - 95ns
   *   <li>24 - 128ns (low memory footprint, binary search everything)
   * </ul>
   */
  private static final int BUCKET_SHIFT = 6;

  /** This is both the mask used to map IDs to buckets. It's also the number of buckets-1. */
  private final int bucketMask;

  /** The bucket is the range [bucketBoundaries[bucketKey], bucketBoundaries[bucketKey]) in ids. */
  @SuppressWarnings("Immutable")
  private final int[] bucketBoundaries;

  /** Returns the bucket index of the given ID. */
  private int bucketOf(long msgId) {
    return ((int) msgId) & bucketMask;
  }

  /**
   * Constructs a map of render-only soy messages. This implementation saves memory but doesn't
   * store all fields necessary during extraction.
   *
   * @param localeString The language/locale string of this bundle of messages, or null if unknown.
   *     Should only be null for bundles newly extracted from source files. Should always be set for
   *     bundles parsed from message files/resources.
   * @param msgs The list of messages. List order will become the iteration order. Duplicate message
   *     ID's are not permitted.
   */
  public RenderOnlySoyMsgBundleImpl(@Nullable String localeString, Iterable<SoyMsg> msgs) {

    this.localeString = localeString;
    this.locale = localeString == null ? null : new ULocale(localeString);
    this.isRtl = BidiGlobalDir.forStaticLocale(localeString) == BidiGlobalDir.RTL;

    // This creates the mask. Basically, take the high-bit and fill in the bits below it.
    int maskHigh = Integer.highestOneBit(Iterables.size(msgs));
    this.bucketMask = (maskHigh | (maskHigh - 1)) >>> BUCKET_SHIFT;
    int numBuckets = maskHigh == 0 ? 0 : this.bucketMask + 1;

    // Sorts by bucket (low bits within the mask) and breaks ties with the full ID.
    Comparator<SoyMsg> bucketComparator =
        Comparator.comparingInt((SoyMsg m) -> bucketOf(m.getId())).thenComparingLong(SoyMsg::getId);
    ImmutableList<SoyMsg> sortedMsgs = ImmutableList.sortedCopyOf(bucketComparator, msgs);

    // Scan the sorted list to discover bucket boundaries and place them into the boundaries array.
    bucketBoundaries = new int[numBuckets + 1];
    for (int bucket = 0, idx = 0; bucket < numBuckets; bucket++) {
      bucketBoundaries[bucket] = idx;
      for (;
          (idx < sortedMsgs.size()) && (bucketOf(sortedMsgs.get(idx).getId()) == bucket);
          idx++) {}
    }
    bucketBoundaries[numBuckets] = sortedMsgs.size();

    ids = new long[sortedMsgs.size()];
    ImmutableList.Builder<SoyMsgPart> partsBuilder = ImmutableList.builder();
    partRanges = new int[sortedMsgs.size() + 1];
    partRanges[0] = 0; // The first range always starts at the beginning of the list.
    long priorId = sortedMsgs.isEmpty() ? -1L : sortedMsgs.get(0).getId() - 1L;
    int runningPartCount = 0;
    for (int i = 0, c = sortedMsgs.size(); i < c; i++) {
      SoyMsg msg = sortedMsgs.get(i);
      ImmutableList<SoyMsgPart> parts = msg.getParts();

      checkArgument(
          msg.getId() != priorId, "Duplicate messages are not permitted in the render-only impl.");
      checkArgument(
          MsgPartUtils.hasPlrselPart(parts) == msg.isPlrselMsg(),
          "Message's plural/select status is inconsistent -- internal compiler bug.");

      priorId = msg.getId();
      ids[i] = msg.getId();
      partsBuilder.addAll(parts);
      runningPartCount += parts.size();
      partRanges[i + 1] = runningPartCount; // runningPartCount is the end of range, hence +1
    }

    // This will build the collections in the same order as the sorted map.
    values = partsBuilder.build();
  }

  /** Copies a RenderOnlySoyMsgBundleImpl, replacing only the localeString. */
  public RenderOnlySoyMsgBundleImpl(
      @Nullable String localeString, RenderOnlySoyMsgBundleImpl exemplar) {

    this.localeString = localeString;
    this.locale = localeString == null ? null : new ULocale(localeString);
    this.isRtl = BidiGlobalDir.forStaticLocale(localeString) == BidiGlobalDir.RTL;
    this.bucketMask = exemplar.bucketMask;
    this.bucketBoundaries = exemplar.bucketBoundaries;
    this.ids = exemplar.ids;
    this.values = exemplar.values;
    this.partRanges = exemplar.partRanges;
  }

  /** Brings a message back to life from only its ID and parts. */
  // The constructor guarantees the type of ImmutableList.
  private SoyMsg resurrectMsg(long id, ImmutableList<SoyMsgPart> parts) {
    return SoyMsg.builder()
        .setId(id)
        .setLocaleString(localeString)
        .setIsPlrselMsg(MsgPartUtils.hasPlrselPart(parts))
        .setParts(parts)
        .build();
  }

  @Override
  public String getLocaleString() {
    return localeString;
  }

  @Override
  @Nullable
  public ULocale getLocale() {
    return locale;
  }

  @Override
  public boolean isRtl() {
    return isRtl;
  }

  private ImmutableList<SoyMsgPart> partsForIndex(int index) {
    int startInclusive = partRanges[index];
    int endExclusive = partRanges[index + 1];
    return values.subList(startInclusive, endExclusive);
  }

  @Override
  public SoyMsg getMsg(long msgId) {
    int index = binarySearch(msgId);
    return index >= 0 ? resurrectMsg(msgId, partsForIndex(index)) : null;
  }

  @Override
  public ImmutableList<SoyMsgPart> getMsgParts(long msgId) {
    int index = binarySearch(msgId);
    return index >= 0 ? partsForIndex(index) : ImmutableList.of();
  }

  private int binarySearch(long key) {
    int bucket = bucketOf(key);
    int low = bucketBoundaries[bucket];
    int high = bucketBoundaries[bucket + 1];
    return Arrays.binarySearch(ids, low, high, key);
  }

  @Override
  public int getNumMsgs() {
    return ids.length;
  }

  @Override
  public Iterator<SoyMsg> iterator() {
    return new Iterator<SoyMsg>() {
      int index = 0;

      @Override
      public boolean hasNext() {
        return index < ids.length;
      }

      @Override
      public SoyMsg next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        SoyMsg result = resurrectMsg(ids[index], partsForIndex(index));
        index++;
        return result;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Iterator is immutable");
      }
    };
  }
}
