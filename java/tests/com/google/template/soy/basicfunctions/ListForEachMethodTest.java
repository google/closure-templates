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
import com.google.template.soy.data.SoyValueConverter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ListForEachMethodTest {

  @Test
  public void testForEach() {
    SoyList list =
        (SoyList) SoyValueConverter.INSTANCE.convert(ImmutableList.of(1, 2, 3)).resolve();
    final long[] sum = {0};
    BasicFunctionsRuntime.ArrayCallback sumCallback =
        (val, index, arr) -> {
          sum[0] += val.longValue();
          return null; // Return value is ignored.
        };

    BasicFunctionsRuntime.listForEach(list, sumCallback);
    assertThat(sum[0]).isEqualTo(6);
  }
}
