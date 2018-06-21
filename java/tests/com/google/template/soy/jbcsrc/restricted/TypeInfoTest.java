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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.template.soy.jbcsrc.restricted.TypeInfo}. */
@RunWith(JUnit4.class)
public final class TypeInfoTest {
  static class Inner {}

  @Test
  public void testSimpleName() {
    assertThat(TypeInfo.create(TypeInfoTest.class).simpleName()).isEqualTo("TypeInfoTest");
    assertThat(TypeInfo.create(TypeInfoTest.class.getName()).simpleName())
        .isEqualTo("TypeInfoTest");

    assertThat(TypeInfo.create(Inner.class).simpleName()).isEqualTo("Inner");
    assertThat(TypeInfo.create(Inner.class.getName()).simpleName()).isEqualTo("Inner");
  }

  @Test
  public void testInnerClass() {
    assertThat(TypeInfo.create(TypeInfoTest.class).innerClass("Inner"))
        .isEqualTo(TypeInfo.create(Inner.class));
  }
}
