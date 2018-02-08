/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.msgs.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.msgs.internal.IcuSyntaxUtils.icuEscape;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for IcuSyntaxUtils.
 *
 */
@RunWith(JUnit4.class)
public class IcuSyntaxUtilsTest {

  @Test
  public void testIcuEscape() {
    assertThat(icuEscape("")).isEmpty();
    assertThat(icuEscape("Hello world!")).isEqualTo("Hello world!");
    assertThat(icuEscape("Don't")).isEqualTo("Don't");
    // no escape because we disable ICU '#'
    assertThat(icuEscape("#5")).isEqualTo("#5");
    assertThat(icuEscape("Don''t")).isEqualTo("Don'''t");
    assertThat(icuEscape("Don'''t")).isEqualTo("Don'''''t");
    assertThat(icuEscape("the '")).isEqualTo("the ''");
    assertThat(icuEscape("the ''")).isEqualTo("the ''''");
    assertThat(icuEscape("'#5")).isEqualTo("''#5");
    assertThat(icuEscape("Set {0, 1, ...}")).isEqualTo("Set '{'0, 1, ...'}'");
    assertThat(icuEscape("Set {don't}")).isEqualTo("Set '{'don't'}'");
    assertThat(icuEscape("Set '{0, 1, ...}'")).isEqualTo("Set '''{'0, 1, ...'}'''");
  }
}
