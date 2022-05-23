/*
 * Copyright 2021 Google Inc.
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
package com.google.template.soy.jbcsrc.shared;

import static com.google.common.truth.Truth.assertThat;

import java.lang.invoke.MethodHandle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LargeStringConstantFactoryTest {

  @Test
  public void testConstant() throws Throwable {
    MethodHandle concatHandle =
        LargeStringConstantFactory.bootstrapLargeStringConstant(
                null, null, null, "foo", "bar", "baz")
            .getTarget();
    String constant = (String) concatHandle.invokeExact();
    assertThat(constant).isEqualTo("foobarbaz");
    String again = (String) concatHandle.invokeExact();
    assertThat(again).isSameInstanceAs(constant);
  }
}
