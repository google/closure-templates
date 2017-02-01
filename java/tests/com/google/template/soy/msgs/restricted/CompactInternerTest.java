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

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the compact interner.
 *
 */
@RunWith(JUnit4.class)
public class CompactInternerTest {

  public static final String LOCALE = "xx";

  private CompactInterner interner;

  @Before
  public void setUp() throws Exception {
    interner = new CompactInterner();
  }

  @Test
  public void testSimple() {
    String firstHello = new String("hello");
    String firstGoodbye = new String("goodbye");
    interner.intern(firstHello);
    interner.intern(firstGoodbye);
    assertSame(firstHello, interner.intern(firstHello));
    assertSame(firstHello, interner.intern(new String("hello")));
    assertSame(firstGoodbye, interner.intern(firstGoodbye));
    assertSame(firstGoodbye, interner.intern(new String("goodbye")));
  }

  @Test
  public void testThousandsOfItems() {
    // A deterministic test with enough iterations to catch dumb errors.
    int iterations = 100000;
    Long[] longs = new Long[iterations];
    String[] strings = new String[iterations];

    // Place the items in the interner the first time.
    for (int i = 0; i < iterations; i++) {
      longs[i] = new Long(i);
      assertSame(longs[i], interner.intern(longs[i]));
      strings[i] = "String Number " + i;
      assertSame(strings[i], interner.intern(strings[i]));
    }

    // Second time, make sure we get the same objects.
    for (int i = 0; i < iterations; i++) {
      assertSame(longs[i], interner.intern(new Long(i)));
      assertSame(strings[i], interner.intern("String Number " + i));
    }
  }

  @Test
  public void testPerformance() {
    // Do some testing. The number of iterations has no effect on these metrics.
    int iterations = 100000;
    double maxCost = 0;
    double maxOverhead = 0;

    // Prime the table first.
    int i = 0;
    for (; i < iterations / 10; i++) {
      interner.intern(new Integer(i));
    }

    // Now, expand the table to the full size. During this time, we record the maximum cost and
    // overhead. Taking the max overhead is important because the table's properties vary
    // dramatically right before and after a rehash, and this way we're guaranteed to see the worst
    // cases.
    for (; i < iterations; i++) {
      interner.intern(new Integer(i));
      maxCost = Math.max(maxCost, interner.getAverageCollisions());
      maxOverhead = Math.max(maxOverhead, interner.getOverhead());
    }

    assertTrue(
        "Cost was "
            + maxCost
            + " but should have been under "
            + CompactInterner.getAverageCollisionsBound(),
        maxCost <= CompactInterner.getAverageCollisionsBound());
    assertTrue(
        "Overhead was "
            + maxOverhead
            + " but should have been under "
            + CompactInterner.getWorstCaseOverhead(),
        maxOverhead < CompactInterner.getWorstCaseOverhead());
  }
}
