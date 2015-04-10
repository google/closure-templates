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

package com.google.template.soy.jbcsrc;

import static com.google.common.truth.Truth.assertThat;

import junit.framework.TestCase;

/**
 * Tests for {@link UniqueNameGenerator}
 */
public class UniqueNameGeneratorTest extends TestCase {

  public void testFieldNames() {
    UniqueNameGenerator nameSet = UniqueNameGenerator.forFieldNames();
    try {
      nameSet.claimName("foo<int>");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("contains dangerous characters!");
    }
    nameSet.claimName("foo");
    try {
      nameSet.claimName("foo");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("already claimed!");
    }

    assertEquals("foo%1", nameSet.generateName("foo"));
    assertEquals("foo%2", nameSet.generateName("foo"));
  }

  public void testClassNames() {
    UniqueNameGenerator nameSet = UniqueNameGenerator.forClassNames();
    try {
      nameSet.claimName("foo$");  // '$' is considered
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected.getMessage()).contains("contains dangerous characters!");
    }
    nameSet.claimName("foo");
  }
}
