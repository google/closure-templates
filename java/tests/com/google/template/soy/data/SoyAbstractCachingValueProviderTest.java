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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.template.soy.data.SoyAbstractCachingValueProvider.ValueAssertion;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for SoyAbstractCachingValueProvider.
 *
 */
@RunWith(JUnit4.class)
public class SoyAbstractCachingValueProviderTest {

  private static class TestValueProvider extends SoyAbstractCachingValueProvider {
    private boolean computedAlready = false;
    private int number;

    TestValueProvider(int number) {
      this.number = number;
    }

    @Override
    protected SoyValue compute() {
      if (computedAlready) {
        throw new IllegalStateException("Caching was expected");
      }
      computedAlready = true;
      return IntegerData.forValue(number);
    }

    @Override
    public RenderResult status() {
      return RenderResult.done();
    }
  }

  @Test
  public void testRepeatedCalls() {
    TestValueProvider value = new TestValueProvider(1);
    assertThat(value.isComputed()).isFalse();
    // Will fail if the underlying one is called twice.
    assertThat(value.resolve().integerValue()).isEqualTo(1);
    assertThat(value.isComputed()).isTrue();
    assertThat(value.resolve().integerValue()).isEqualTo(1);
  }

  @Test
  public void testValueAssertions() {
    final AtomicInteger counter = new AtomicInteger();
    ValueAssertion assertion =
        new ValueAssertion() {
          @Override
          public void check(SoyValue value) {
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
      assertThat(e).hasMessageThat().isEqualTo("boom");
      assertThat(counter.get()).isEqualTo(1);
    }
    // Errors are not cached
    try {
      badValue.resolve();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().isEqualTo("Caching was expected");
    }
    counter.set(0);
    TestValueProvider goodValue = new TestValueProvider(1);
    goodValue.addValueAssertion(assertion);
    // Will fail if the underlying one is called twice.
    assertThat(goodValue.resolve().integerValue()).isEqualTo(1);
    assertThat(counter.get()).isEqualTo(1);
    // successes are cached
    assertThat(goodValue.resolve().integerValue()).isEqualTo(1);
    assertThat(counter.get()).isEqualTo(1);
  }

  private static final class SimpleAssertion extends ValueAssertion {
    boolean hasBeenCalled;

    @Override
    public void check(SoyValue value) {
      hasBeenCalled = true;
    }
  }

  @Test
  public void testValueAssertions_multipleAssertions() {
    SimpleAssertion assertion1 = new SimpleAssertion();
    SimpleAssertion assertion2 = new SimpleAssertion();
    SimpleAssertion assertion3 = new SimpleAssertion();
    TestValueProvider value = new TestValueProvider(1);
    value.addValueAssertion(assertion1);
    value.addValueAssertion(assertion2);
    value.addValueAssertion(assertion3);
    assertThat(value.resolve().integerValue()).isEqualTo(1);
    assertThat(assertion1.hasBeenCalled).isTrue();
    assertThat(assertion2.hasBeenCalled).isTrue();
    assertThat(assertion3.hasBeenCalled).isTrue();
  }
}
