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
import com.google.template.soy.data.SoyValueProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ListFlatMapMethodTest {

  @Test
  public void testFlatMap() {
    SoyList list =
        (SoyList) SoyValueConverter.INSTANCE.convert(ImmutableList.of(1, 2, 3)).resolve();
    BasicFunctionsRuntime.ArrayCallback identityArrayCallback =
        (val, index, arr) ->
            SoyValueConverter.INSTANCE
                .convert(ImmutableList.of(val, val.longValue() * 2))
                .resolve();

    ImmutableList<? extends SoyValueProvider> result =
        ImmutableList.copyOf(
            BasicFunctionsRuntime.listFlatMap(list, identityArrayCallback).asJavaList());
    assertThat(result).hasSize(6);
    assertThat(result.get(0).resolve().longValue()).isEqualTo(1);
    assertThat(result.get(1).resolve().longValue()).isEqualTo(2);
    assertThat(result.get(2).resolve().longValue()).isEqualTo(2);
    assertThat(result.get(3).resolve().longValue()).isEqualTo(4);
    assertThat(result.get(4).resolve().longValue()).isEqualTo(3);
    assertThat(result.get(5).resolve().longValue()).isEqualTo(6);
  }
}
