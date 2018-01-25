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

package com.google.template.soy.base.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.CharMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link UniqueNameGenerator} */
@RunWith(JUnit4.class)
public final class UniqueNameGeneratorTest {

  @Test
  public void testFieldNames() {
    UniqueNameGenerator nameSet = new UniqueNameGenerator(CharMatcher.anyOf("<>"), "%");
    try {
      nameSet.claimName("foo<int>");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("contains dangerous characters!");
    }
    assertThat(nameSet.hasName("foo<int>")).isFalse();

    nameSet.claimName("foo");
    try {
      nameSet.claimName("foo");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("already claimed!");
    }
    assertThat(nameSet.hasName("foo")).isTrue();
    assertThat(nameSet.generateName("foo")).isEqualTo("foo%1");
    assertThat(nameSet.generateName("foo")).isEqualTo("foo%2");
  }

  @Test
  public void testClassNames() {
    UniqueNameGenerator nameSet = new UniqueNameGenerator(CharMatcher.anyOf("$"), "%");
    try {
      nameSet.claimName("foo$"); // '$' is considered dangerous
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("contains dangerous characters!");
    }
    nameSet.claimName("foo");
  }

  @Test
  public void testHasName() {
    UniqueNameGenerator nameSet = new UniqueNameGenerator(CharMatcher.none(), "%");
    String foo = nameSet.generateName("foo");
    String foo2 = nameSet.generateName("foo");
    String foo3 = nameSet.generateName("foo");
    assertThat(foo).isEqualTo("foo");
    assertThat(foo).isNotEqualTo(foo2);
    assertThat(foo).isNotEqualTo(foo3);
    assertThat(foo2).isNotEqualTo(foo3);
    assertThat(nameSet.hasName("foo")).isTrue();
    assertThat(nameSet.hasName(foo)).isTrue();
    assertThat(nameSet.hasName(foo2)).isTrue();
    assertThat(nameSet.hasName(foo3)).isTrue();
  }
}
