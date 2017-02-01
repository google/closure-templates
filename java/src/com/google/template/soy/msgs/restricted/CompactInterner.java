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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.Random;

/**
 * An memory-efficient object canonicalizer.
 *
 * <p>This functions similarly to string.intern(). It has extremely low memory overhead of a little
 * over one reference per item interned. Standard HashMap based interners use many bytes per object
 * interned, partially defeating the memory savings of interning.
 *
 */
final class CompactInterner {

  /** Initial size of the table. */
  @VisibleForTesting static final int INITIAL_SIZE = 1024;

  /**
   * The maximum expected number of collisions to tolerate before growing.
   *
   * <p>Increasing this number reduces memory usage at the expense of more equals() checks for both
   * cache hits and misses. The max load factor of the table is 1 / (MAX_EXPECTED_COLLISION_COUNT +
   * 1).
   */
  private static final int MAX_EXPECTED_COLLISION_COUNT = 4;

  /**
   * The denominator of the current size when growing the table.
   *
   * <p>Increasing this number results in better memory utilization at the expense of more frequent
   * rehashing.
   *
   * <p>Increasing this value results in a linear increase in the amortized number of equals()
   * checks for a miss because of the increased frequency of rehashes, but has no effect on cache
   * hits. Proof: Let D be GROWTH_DENOMINATOR. Right before a rehash, the total number of times any
   * item has been hashed is computed by assuming all items in the table have been hashed once, and
   * D/(D+1) have been hashed at least twice, and (D/D+1)^2 have been hashed thrice, etc. Since the
   * sum of a geometric series on x is 1/(1-x), the number of hashes per item inserted is exactly
   * 1/(1 - (D/D+1)), or (D+1)/(D + 1 - D) or exactly D+1. The number of rehashes per item is
   * exactly D, since its first insertion was not a rehash.
   */
  private static final int GROWTH_DENOMINATOR = 4;

  /** Hash table of all the items interned. */
  private Object[] table;

  /** Number of items in the table. */
  private int count;

  /** The total number of collisions, including collisions incurred during a rehash. */
  private long collisions;

  public CompactInterner() {
    table = new Object[INITIAL_SIZE];
    count = 0;
    collisions = 0;
  }

  /**
   * Returns either the input, or an instance that equals it that was previously passed to this
   * method.
   *
   * <p>This operation performs in amortized constant time.
   */
  @SuppressWarnings("unchecked") // If a.equals(b) then a and b have the same type.
  public synchronized <T> T intern(T value) {
    Preconditions.checkNotNull(value);

    // Use a pseudo-random number generator to mix up the high and low bits of the hash code.
    Random generator = new java.util.Random(value.hashCode());
    int tries = 0;

    while (true) {
      int index = generator.nextInt(table.length);
      Object candidate = table[index];

      if (candidate == null) {
        // Found a good place to hash it.
        count++;
        collisions += tries;
        table[index] = value;
        rehashIfNeeded();
        return value;
      }

      if (candidate.equals(value)) {
        Preconditions.checkArgument(
            value.getClass() == candidate.getClass(),
            "Interned objects are equals() but different classes: %s and %s",
            value,
            candidate);
        return (T) candidate;
      }

      tries++;
    }
  }

  /** Doubles the table size. */
  private void rehashIfNeeded() {
    int currentSize = table.length;
    if (currentSize - count >= currentSize / (MAX_EXPECTED_COLLISION_COUNT + 1)) {
      // Still enough overhead.
      return;
    }

    Object[] oldTable = table;
    // Grow the table so it increases by 1 / GROWTH_DENOMINATOR.
    int newSize = currentSize + currentSize / GROWTH_DENOMINATOR;

    table = new Object[newSize];
    count = 0;

    for (Object element : oldTable) {
      if (element != null) {
        intern(element);
      }
    }
  }

  @VisibleForTesting
  double getAverageCollisions() {
    return 1.0 * collisions / count;
  }

  @VisibleForTesting
  static double getAverageCollisionsBound() {
    // NOTE: I'm sure there are research papers dedicated to open addressed hashing and load
    // factors but I've not been able to find them quickly. This is some rough empirical work
    // to make sure we get reasonable performance in tests.
    double x = Math.max(MAX_EXPECTED_COLLISION_COUNT, GROWTH_DENOMINATOR);
    return x * Math.log1p(x) + 1;
  }

  @VisibleForTesting
  double getOverhead() {
    return 1.0 * (table.length - count) / count;
  }

  @VisibleForTesting
  static final double getWorstCaseOverhead() {
    // This is the proportion of null entries to non-null entries right after a rehash.
    return (1 + 1.0 / MAX_EXPECTED_COLLISION_COUNT) * (1 + 1.0 / GROWTH_DENOMINATOR) - 1;
  }
}
