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

package com.google.template.soy.data.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterators;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.restricted.StringData;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class IterableImplTest {

  @Test
  public void testLazyAdaptorIsIdempotent() {
    Iterable<String> itr = () -> Iterators.forArray("Hello");
    var soyItr = IterableImpl.forJavaIterable(itr, SoyValueConverter.INSTANCE::convert);
    assertThat(soyItr.javaIterator().next()).isEqualTo(StringData.forValue("Hello"));
    assertThat(soyItr.javaIterator().next()).isSameInstanceAs(soyItr.javaIterator().next());
  }

  @Test
  public void testIterable() {
    Iterable<String> itr = () -> Iterators.forArray("Hello", "World");
    var soyItr = IterableImpl.forJavaIterable(itr, SoyValueConverter.INSTANCE::convert);

    var list = new ArrayList<SoyValueProvider>();
    for (SoyValueProvider item : soyItr.asJavaIterable()) {
      list.add(item);
    }
    assertThat(list).containsExactly(StringData.forValue("Hello"), StringData.forValue("World"));
  }
}
