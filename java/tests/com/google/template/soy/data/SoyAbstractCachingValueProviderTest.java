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

package com.google.template.soy.data;

import com.google.template.soy.data.restricted.IntegerData;

import junit.framework.TestCase;

/**
 * Unit test for SoyAbstractCachingValueProvider.
 *
 * @author Garrett Boyer
 */
public class SoyAbstractCachingValueProviderTest extends TestCase {

  private static class TestValueProvider extends SoyAbstractCachingValueProvider {
    private boolean computedAlready = false;
    private int number;

    TestValueProvider(int number) {
      this.number = number;
    }

    @Override protected SoyValue compute() {
      if (computedAlready) {
        throw new IllegalStateException("Caching was expected");
      }
      computedAlready = true;
      return IntegerData.forValue(number);
    }
  }

  public void testRepeatedCalls() {
    TestValueProvider value = new TestValueProvider(1);
    // Will fail if the underlying one is called twice.
    assertEquals(1, value.resolve().integerValue());
    assertEquals(1, value.resolve().integerValue());
  }

  public void testEquals() {
    assertTrue(new TestValueProvider(1).equals(new TestValueProvider(1)));
    assertFalse(new TestValueProvider(1).equals(new TestValueProvider(2)));
    assertFalse(new TestValueProvider(1).equals(null));
    assertFalse(new TestValueProvider(1).equals(new Object()));
  }
}
