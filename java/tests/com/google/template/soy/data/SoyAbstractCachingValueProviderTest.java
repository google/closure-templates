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

import com.google.template.soy.data.SoyAbstractCachingValueProvider.ValueAssertion;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jbcsrc.api.RenderResult;

import junit.framework.TestCase;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit test for SoyAbstractCachingValueProvider.
 *
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

    @Override public RenderResult status() {
      return RenderResult.done();
    }
  }

  public void testRepeatedCalls() {
    TestValueProvider value = new TestValueProvider(1);
    assertFalse(value.isComputed());
    // Will fail if the underlying one is called twice.
    assertEquals(1, value.resolve().integerValue());
    assertTrue(value.isComputed());
    assertEquals(1, value.resolve().integerValue());
  }

  public void testValueAssertions() {
    final AtomicInteger counter = new AtomicInteger();
    ValueAssertion assertion = new ValueAssertion() {
      @Override public void check(SoyValue value) {
        counter.incrementAndGet();
        if (value.integerValue() < 0) {
          throw new IllegalStateException("boom");
        }
      }
    };
    TestValueProvider badValue = new TestValueProvider(-1);
    badValue.addValueAssertion(assertion);
    try {
      badValue.resolve();
      fail();
    } catch (IllegalStateException e) {
      assertEquals("boom", e.getMessage());
      assertEquals(1, counter.get());
    }
    // Errors are not cached
    try {
      badValue.resolve();
      fail();
    } catch (IllegalStateException e) {
      assertEquals("Caching was expected", e.getMessage());
    }
    counter.set(0);
    TestValueProvider goodValue = new TestValueProvider(1);
    goodValue.addValueAssertion(assertion);
    // Will fail if the underlying one is called twice.
    assertEquals(1, goodValue.resolve().integerValue());
    assertEquals(1, counter.get());
    // successes are cached
    assertEquals(1, goodValue.resolve().integerValue());
    assertEquals(1, counter.get());
  }

  private static final class SimpleAssertion extends ValueAssertion {
    boolean hasBeenCalled;
    @Override public void check(SoyValue value) {
      hasBeenCalled = true;
    }
  }

  public void testValueAssertions_multipleAssertions() {
    SimpleAssertion assertion1 = new SimpleAssertion();
    SimpleAssertion assertion2 = new SimpleAssertion();
    SimpleAssertion assertion3 = new SimpleAssertion();
    TestValueProvider value = new TestValueProvider(1);
    value.addValueAssertion(assertion1);
    value.addValueAssertion(assertion2);
    value.addValueAssertion(assertion3);
    assertEquals(1, value.resolve().integerValue());
    assertTrue(assertion1.hasBeenCalled);
    assertTrue(assertion2.hasBeenCalled);
    assertTrue(assertion3.hasBeenCalled);
  }
}
