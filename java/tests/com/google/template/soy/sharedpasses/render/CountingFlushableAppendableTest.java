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

import static com.google.common.truth.Truth.assertThat;

import java.io.Flushable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test case for {@link CountingFlushableAppendable}. */
@RunWith(JUnit4.class)
public class CountingFlushableAppendableTest {

  @Test
  public void testAppendAndFlush() throws Exception {
    final StringBuilder progress = new StringBuilder();
    Flushable flushable =
        new Flushable() {
          @Override
          public void flush() {
            progress.append("F");
          }
        };

    CountingFlushableAppendable c = new CountingFlushableAppendable(progress, flushable);
    assertThat(c.getAppendedCountSinceLastFlush()).isEqualTo(0);
    c.append("12");
    assertThat(c.getAppendedCountSinceLastFlush()).isEqualTo(2);
    c.append("3");
    assertThat(c.getAppendedCountSinceLastFlush()).isEqualTo(3);
    c.flush();
    assertThat(c.getAppendedCountSinceLastFlush()).isEqualTo(0);
    c.append('c');
    assertThat(c.getAppendedCountSinceLastFlush()).isEqualTo(1);
    c.append("123", 1, 2);
    assertThat(progress.toString()).isEqualTo("123Fc2");
  }
}
