/*
 * Copyright 2024 Google Inc.
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
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Integer.numberOfLeadingZeros;
import static java.util.Arrays.stream;
import static java.util.Comparator.comparingInt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A hash table for looking up a storage location from a message id.
 *
 * <p>Manages a single table for a set of message bundles.
 */
public final class RenderOnlyMsgIndex {

  @FunctionalInterface
  interface EntryConsumer {
    void accept(long id, SoyMsgRawParts value);
  }

  static final class Accessor {
    private final long[] table;

    private final Object[] values;

    Accessor(long[] table, Object[] values) {
      this.table = table;
      this.values = values;
    }

    @Nullable
    private Object getInternal(long id) {
      int msgIndex = getIndex(table, id);
      if (msgIndex >= 0) {
        var values = this.values;
        if (msgIndex < values.length) {
          return values[msgIndex];
        }
      }
      return null;
    }

    boolean has(long id) {
      return getInternal(id) != null;
    }

    @Nullable
    SoyMsgRawParts getParts(long id) {
      var value = getInternal(id);
      if (value == null || value instanceof SoyMsgRawParts) {
        return (SoyMsgRawParts) value;
      }
      return SoyMsgRawParts.of((String) value);
    }

    @Nullable
    String getBasicTranslation(long id) {
      return (String) getInternal(id);
    }

    void forEach(EntryConsumer consumer) {
      var table = this.table;
      var values = this.values;
      for (int i = 0; i < table.length; i += 2) {
        long id = table[i];
        if (id < 0) {
          continue;
        }
        int index = (int) table[i + 1];
        if (index >= values.length) {
          continue;
        }
        Object v = values[index];
        if (v == null) {
          continue;
        }
        if (v instanceof SoyMsgRawParts) {
          consumer.accept(id, (SoyMsgRawParts) v);
        } else {
          consumer.accept(id, SoyMsgRawParts.of((String) v));
        }
      }
    }
  }

  // Other approaches considered:
  // 1. A swiss table based approach.  This relies on SIMD instructions to quickly filter potential
  // probe matches.  This is not feasible in java today, but project Panama may enable it in the
  // future.
  //
  // 2. Using eytzinger based array layout to implement a more efficient binary search.  This is
  // possible and would cut memory overhead in half, but it is 2-3x slower than the linear probe
  // approach.  So for now the memory overhead is worth the performance.
  //
  // 3. We could use a denser value layout by storing the array indexes in the lowermost bits of the
  // ids in the table.  This would ensure there are no empty slots in the table.  However, this
  // makes hash collisions very complex

  private static final long[] EMPTY_TABLE = new long[] {-1, -1};

  // Message id. `-1` means empty.
  // Power of 2 sizing. Targeting a 50% load factor with alternating keys and values.
  // NOTE: we don't have to support deletions which simplifies things.
  // We use a simple linear probing strategy, but because we construct 'monolithically' we can
  // leverage some simple tricks to short circuit long linear probes.  Notably, we can arrange that
  // all keys that collide are inserted in hash order and then id order as a tie breaker.  This
  // means that we can short circuit the search as soon as the hash bits stop matching or when the
  // hash bits match but the id is greater than the current search id.

  private volatile long[] table;

  @GuardedBy("this")
  private int size;

  public RenderOnlyMsgIndex() {
    this(ImmutableList.of());
  }

  // Construct with the given ids 'pre-seeded'
  public RenderOnlyMsgIndex(List<Long> allIds) {
    // Need to copy to a mutable list since buildTable will sort it.
    var aslist = new ArrayList<Long>(allIds);
    this.table = buildTable(aslist);
    this.size = aslist.size();
  }

  /** Returns the initial index at which the id might be stored. */
  private static int hashIndex(long id, int mask) {
    // You might be thinking, huh, why are we discarding all the upper most bits?
    // We could mix them in using a standard integer hash function, but fundamentally, the id is
    // _already_ a hash of the message contents.  And the hash function in question, as defined by
    // SoyMsgIdComputer already uses a high quality 'mixing algorithm'.  So assuming it achieves a
    // decent 'avalanche effect', we are guaranteed that small changes in input affected the lower
    // bits just as much as the upper bits and thus further 'mixing' isn't valuable.
    return (int) id & mask;
  }

  private static int getMask(int length) {
    // -1 to create a standard mask to select array indexes for our power of 2 sized table.
    // -1 again to ensure we only select even numbered locations for keys
    return length - 2;
  }

  private static int nextKeyIndex(int i, int mask) {
    // +2 to advance to the next key location
    return (i + 2) & mask;
  }

  @VisibleForTesting
  static int tableSize(int newSize) {
    checkArgument(newSize > 0);
    // Pick the next power of 2 that is at least 4x `newSize` this should ensure that our load
    // factor is <=50%
    return 1 << (32 - numberOfLeadingZeros(((newSize << 2) - 1)));
  }

  private static int getIndex(long[] table, long id) {
    // store table locally to ensure we get a consistent view.  We only need to see the writes
    // we have already made, not any racing ones.
    final int mask = getMask(table.length);
    final int bucket = hashIndex(id, mask);
    int index = bucket;
    while (true) {
      long probe = table[index];
      if (probe == id) {
        return (int) table[index + 1];
      } else if (probe < 0) {
        return -1;
      }
      // We have hit a collision cluster.  See if we are in the correct bucket.
      // If we are not, but we used to be, then we are done.  Because we encounter buckets in
      // hashOrder we can detect this with a simple comparison.
      // If we are in the correct bucket, then keep looking, but only if our id is < probe, because
      // keys within a bucket are sorted. this works
      int probeBucket = hashIndex(probe, mask);
      if (probeBucket > bucket) {
        return -1; // we have exhausted our bucket.
      }
      // This implies that we could binarySearch for our id, but we don't know the end points and
      // we expect our 'buckets' to be small.  Exponential search would be the thing to use in this
      // case.
      if (probeBucket == bucket && id < probe) {
        return -1;
      }
      // probeBucket could be < bucket, but that means we need to keep scanning.
      index = nextKeyIndex(index, mask);
    }
  }

  /**
   * Inserts all the messages into the table and return an accessor to look up parts by id.
   *
   * <p>This is a bulk insertion API. It will resize the table if necessary and will be expensive.
   *
   * @param msgs the ids to get indices for.
   * @return the indices to store the messages at. The indices are in the same order as the ids.
   */
  Accessor buildAccessor(ImmutableList<RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg> msgs) {
    long[] table = this.table;
    int[] indices = new int[msgs.size()];
    Arrays.fill(indices, -1);
    int numNew = bulkGet(table, msgs, indices);

    if (numNew == 0) {
      // This is relatively common. We expect to only discover new ids a few times.
      // Basically all locales have the same set of ids, some may be missing a few (lagging
      // translations), but this will be a minority.  In a perfect world we would get all the ids
      // in one go, but we don't live in a perfect world.
      return makeAccessor(table, msgs, indices);
    }

    synchronized (this) {
      // We need to try again within the lock in case we raced with another thread.
      // If we did race then table will have changed, that happens we need to try again.
      if (table != this.table) {
        table = this.table;
        numNew = bulkGet(table, msgs, indices);
        if (numNew == 0) {
          return makeAccessor(table, msgs, indices);
        }
      }
      // When adding new ids, we rewrite the whole table!  Why? This allows us to optimize
      // lookups.  If we get hash collisions then we start spilling over into adjacent slots.  If we
      // get pathological clustering then we might have to do a lot of scanning.  However, if we can
      // insert all ids in 'hash' order, then we can rely on the fact that as soon as the lower hash
      // bits of the ids stop matching we can stop searching, which should short circuit
      // pathological cases.
      int newSize = size + numNew;
      var allIds = new ArrayList<Long>(newSize);
      for (int i = 0; i < msgs.size(); i++) {
        if (indices[i] >= 0) {
          // If we filled in the id earlier it is definitely in the table and we will find it below.
          continue;
        }
        allIds.add(msgs.get(i).id());
      }
      for (int i = 0; i < table.length; i += 2) {
        long id = table[i];
        if (id >= 0) {
          allIds.add(id);
        }
      }
      if (allIds.size() != newSize) {
        throw new IllegalStateException(
            "Expected to find " + newSize + "  ids, but found " + allIds.size());
      }
      table = buildTable(allIds);
      this.size = newSize;
      this.table = table;
    }

    numNew = bulkGet(table, msgs, indices);
    checkState(numNew == 0);
    return makeAccessor(table, msgs, indices);
  }

  /** Builds and returns the hash table that holds all the given ids. */
  private static long[] buildTable(List<Long> allIds) {
    if (allIds.isEmpty()) {
      return EMPTY_TABLE;
    }
    // We want to sort the entries by hash and then id.  This ensures that we can use a simple
    // linear probe strategy and short circuit as soon as the hash bits stop matching.
    // However, java doesn't provide sort operators for primitives with custom comparators. So we
    // have to box everything.
    var newTableSize = tableSize(allIds.size());
    int newTableMask = getMask(newTableSize);
    allIds.sort(
        comparingInt((Long id) -> hashIndex(id, newTableMask)).thenComparingLong(Long::longValue));

    var newTable = new long[newTableSize];
    Arrays.fill(newTable, -1);
    for (int i = 0; i < allIds.size(); i++) {
      long id = allIds.get(i);
      int hashIndex = hashIndex(id, newTableMask);
      int tableIndex = hashIndex;
      while (newTable[tableIndex] >= 0) {
        tableIndex = nextKeyIndex(tableIndex, newTableMask);
      }
      newTable[tableIndex] = id;
      newTable[tableIndex + 1] = i;
    }

    return newTable;
  }

  private static Accessor makeAccessor(
      long[] table,
      ImmutableList<RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg> msgs,
      int[] indices) {
    Object[] values = new Object[stream(indices).max().orElse(-1) + 1];
    for (int i = 0; i < msgs.size(); i++) {
      var parts = msgs.get(i).parts();
      values[indices[i]] =
          parts.numParts() == 1 && parts.getPart(0) instanceof String ? parts.getPart(0) : parts;
    }
    return new Accessor(table, values);
  }

  /**
   * Fills in the `indices` table with all the array indexes for the given messages.
   *
   * @return the number of new messages that were not found in the table.
   */
  private static int bulkGet(
      long[] table,
      ImmutableList<RenderOnlySoyMsgBundleImpl.RenderOnlySoyMsg> msgs,
      int[] indices) {
    int numNew = 0;
    for (int i = 0; i < msgs.size(); i++) {
      int index = getIndex(table, msgs.get(i).id());
      if (index >= 0) {
        indices[i] = index;
      } else {
        numNew++;
      }
    }
    return numNew;
  }

  /** Returns how many ids are in the table. */
  synchronized int size() {
    return size;
  }

  @Override
  public String toString() {
    return "RenderOnlyMsgIndex{tableSize=" + table.length * 8 + " bytes}";
  }
}
