/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.sharedpasses.render;

import junit.framework.TestCase;

import java.io.Flushable;

/**
 * Test case for {@link CountingFlushableAppendable}.
 */
public class CountingFlushableAppendableTest extends TestCase {

  public void testAppendAndFlush() throws Exception {
    final StringBuilder progress = new StringBuilder();
    Flushable flushable = new Flushable() {
      @Override public void flush() {
        progress.append("F");
      }
    };

    CountingFlushableAppendable c = new CountingFlushableAppendable(progress, flushable);
    assertEquals(0, c.getAppendedCountSinceLastFlush());
    c.append("12");
    assertEquals(2, c.getAppendedCountSinceLastFlush());
    c.append("3");
    assertEquals(3, c.getAppendedCountSinceLastFlush());
    c.flush();
    assertEquals(0, c.getAppendedCountSinceLastFlush());
    c.append('c');
    assertEquals(1, c.getAppendedCountSinceLastFlush());
    c.append("123", 1, 2);
    assertEquals("123Fc2", progress.toString());
  }
}
