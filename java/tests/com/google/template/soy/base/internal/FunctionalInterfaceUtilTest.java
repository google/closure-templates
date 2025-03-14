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

package com.google.template.soy.base.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for FunctionalInterfaceUtil. */
@RunWith(JUnit4.class)
public final class FunctionalInterfaceUtilTest {

  @Test
  public void testIsIdentifier() throws NoSuchMethodException {
    assertThat(FunctionalInterfaceUtil.getMethod(Comparator.class))
        .isEqualTo(Comparator.class.getDeclaredMethod("compare", Object.class, Object.class));
    assertThat(FunctionalInterfaceUtil.getMethod(Ordering.class))
        .isEqualTo(Comparator.class.getDeclaredMethod("compare", Object.class, Object.class));

    assertThat(FunctionalInterfaceUtil.getMethod(Supplier.class))
        .isEqualTo(Supplier.class.getDeclaredMethod("get"));

    assertThat(FunctionalInterfaceUtil.getMethod(List.class)).isNull();
    assertThat(FunctionalInterfaceUtil.getMethod(ImmutableList.class)).isNull();
  }
}
