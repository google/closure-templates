/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.bidifunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiEndEdgeFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiEndEdgeFunctionTest {

  @Test
  public void testComputeForJavaSource() {
    // the java source version doesn't use the provider
    BidiEndEdgeFunction fn = new BidiEndEdgeFunction();

    SoyJavaSourceFunctionTester tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.LTR).build();
    assertThat(tester.callFunction()).isEqualTo("right");

    tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.RTL).build();
    assertThat(tester.callFunction()).isEqualTo("left");
  }

}
