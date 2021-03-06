/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FileDependencyOrderPass}. */
@RunWith(JUnit4.class)
public final class FileDependencyOrderPassTest {

  @Test
  public void testCycle() {
    String alpha = "{namespace alpha}\n" + "{template .t1}\n" + "{/template}\n";

    String beta =
        "{namespace beta}\n"
            + "import * as gamma from 'no-path-3';\n"
            + "{template .t2}\n"
            + "{/template}\n";

    String gamma =
        "{namespace gamma}\n"
            + "import * as beta from 'no-path-2';\n"
            + "{template .t3}\n"
            + "{/template}\n";

    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(alpha, beta, gamma)
        .errorReporter(errorReporter)
        .parse();

    assertThat(errorReporter.getErrors()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message())
        .isEqualTo(
            "Dependency cycle between source files:\nno-path-2\n--> no-path-3\n--> no-path-2");
  }

  @Test
  public void testSort() {
    assertThat(
            sort(
                ImmutableMap.<Integer, Iterable<Integer>>builder()
                    .put(1, list(2))
                    .put(2, list(3))
                    .put(3, list())
                    .build()))
        .containsExactly(3, 2, 1);

    assertThat(
            sort(
                ImmutableMap.<Integer, Iterable<Integer>>builder()
                    .put(1, list(2))
                    .put(2, list())
                    .put(3, list())
                    .build()))
        .containsExactly(2, 3, 1);

    assertThat(
            sort(
                ImmutableMap.<Integer, Iterable<Integer>>builder()
                    .put(1, list())
                    .put(2, list(1, 3))
                    .put(3, list(1))
                    .build()))
        .containsExactly(1, 3, 2);
  }

  private static <T> ImmutableList<T> list(T... items) {
    return ImmutableList.copyOf(items);
  }

  private static <T> ImmutableList<T> sort(Map<T, Iterable<T>> data) {
    return new FileDependencyOrderPass.TopoSort<T>().sort(data.keySet(), data::get);
  }
}
