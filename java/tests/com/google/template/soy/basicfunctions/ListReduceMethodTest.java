/*
 * Copyright 2024 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.restricted.IntegerData;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ListReduceMethodTest {

  @Test
  public void testReduce() {
    SoyList list =
        (SoyList) SoyValueConverter.INSTANCE.convert(ImmutableList.of(1, 2, 3)).resolve();
    BasicFunctionsRuntime.ArrayReduceCallback sumCallback =
        (acc, val, index, arr) -> IntegerData.forValue(acc.longValue() + val.longValue());

    SoyValue result = BasicFunctionsRuntime.listReduce(list, sumCallback);
    assertThat(result.longValue()).isEqualTo(6);
  }

  @Test
  public void testReduceWithInitialValue() {
    SoyList list =
        (SoyList) SoyValueConverter.INSTANCE.convert(ImmutableList.of(1, 2, 3)).resolve();
    BasicFunctionsRuntime.ArrayReduceCallback sumCallback =
        (acc, val, index, arr) -> IntegerData.forValue(acc.longValue() + val.longValue());

    SoyValue result = BasicFunctionsRuntime.listReduce(list, sumCallback, IntegerData.forValue(10));
    assertThat(result.longValue()).isEqualTo(16);
  }
}
