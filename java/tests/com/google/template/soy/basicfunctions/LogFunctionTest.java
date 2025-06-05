/*
 * Copyright 2025 Google Inc.
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

package com.google.template.soy.basicfunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for LogFunction. */
@RunWith(JUnit4.class)
public class LogFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    LogFunction logFunction = new LogFunction();

    SoyJavaSourceFunctionTester factory = new SoyJavaSourceFunctionTester(logFunction);
    assertThat(factory.callFunction(-1.0)).isEqualTo(Double.NaN);
    assertThat(factory.callFunction(0.25)).isEqualTo(-1.3862943611198906);
    assertThat(factory.callFunction(8.0)).isEqualTo(2.0794415416798357);
    assertThat((double) factory.callFunction(8.0) / (double) factory.callFunction(2.0))
        .isEqualTo(3.0);
  }
}
