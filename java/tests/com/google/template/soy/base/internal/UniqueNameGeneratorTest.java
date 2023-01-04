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
import com.google.common.collect.ImmutableSet;
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
      nameSet.exact("foo<int>");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("contains dangerous characters!");
    }
    assertThat(nameSet.has("foo<int>")).isFalse();

    nameSet.exact("foo");
    try {
      nameSet.exact("foo");
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("already claimed!");
    }
    nameSet.exactLenient("foo");
    assertThat(nameSet.has("foo")).isTrue();
    assertThat(nameSet.generate("foo")).isEqualTo("foo%1");
    assertThat(nameSet.generate("foo")).isEqualTo("foo%2");
  }

  @Test
  public void testClassNames() {
    UniqueNameGenerator nameSet = new UniqueNameGenerator(CharMatcher.anyOf("$"), "%");
    try {
      nameSet.exact("foo$"); // '$' is considered dangerous
      fail();
    } catch (IllegalArgumentException expected) {
      assertThat(expected).hasMessageThat().contains("contains dangerous characters!");
    }
    nameSet.exact("foo");
  }

  @Test
  public void testHasName() {
    UniqueNameGenerator nameSet = new UniqueNameGenerator(CharMatcher.none(), "%");
    String foo = nameSet.generate("foo");
    String foo2 = nameSet.generate("foo");
    String foo3 = nameSet.generate("foo");
    assertThat(foo).isEqualTo("foo");
    assertThat(foo).isNotEqualTo(foo2);
    assertThat(foo).isNotEqualTo(foo3);
    assertThat(foo2).isNotEqualTo(foo3);
    assertThat(nameSet.has("foo")).isTrue();
    assertThat(nameSet.has(foo)).isTrue();
    assertThat(nameSet.has(foo2)).isTrue();
    assertThat(nameSet.has(foo3)).isTrue();
  }

  @Test
  public void testSequence() {
    UniqueNameGenerator nameSet =
        new UniqueNameGenerator(CharMatcher.none(), "", ImmutableSet.of("private"));
    assertThat(nameSet.generate("foo")).isEqualTo("foo");
    assertThat(nameSet.generate("foo")).isEqualTo("foo1");
    assertThat(nameSet.generate("foo")).isEqualTo("foo2");
    assertThat(nameSet.generate("foo8")).isEqualTo("foo8");
    assertThat(nameSet.generate("foo")).isEqualTo("foo9");
    assertThat(nameSet.generate("foo5")).isEqualTo("foo5");
    assertThat(nameSet.generate("foo")).isEqualTo("foo10");

    assertThat(nameSet.generate("private")).isEqualTo("private1");

    assertThat(nameSet.branch().generate("private")).isEqualTo("private2");
    assertThat(nameSet.branch().generate("private")).isEqualTo("private2");
  }
}
