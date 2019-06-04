/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.shared.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ShortCircuitablesTest {

  @Test
  public void testFilterDirectivesForKind() throws Exception {
    TestShortCirccuitableDirective allowsHtml = k -> k == ContentKind.HTML;
    TestShortCirccuitableDirective allowsText = k -> k == ContentKind.TEXT;
    TestDirective o = new TestDirective() {};

    assertThat(
            ShortCircuitables.filterDirectivesForKind(
                ContentKind.HTML, ImmutableList.of(allowsHtml)))
        .isEmpty();
    assertThat(
            ShortCircuitables.filterDirectivesForKind(
                ContentKind.CSS, ImmutableList.of(allowsHtml)))
        .containsExactly(allowsHtml);
    assertThat(
            ShortCircuitables.filterDirectivesForKind(
                ContentKind.HTML, ImmutableList.of(allowsHtml, allowsHtml)))
        .isEmpty();
    assertThat(
            ShortCircuitables.filterDirectivesForKind(
                ContentKind.HTML, ImmutableList.of(allowsHtml, o, allowsHtml)))
        .containsExactly(o, allowsHtml)
        .inOrder();
    assertThat(
            ShortCircuitables.filterDirectivesForKind(
                ContentKind.HTML, ImmutableList.of(allowsHtml, allowsText, allowsHtml)))
        .containsExactly(allowsText, allowsHtml)
        .inOrder();
  }

  interface TestDirective extends SoyPrintDirective {
    @Override
    default String getName() {
      return "|test";
    }

    @Override
    default Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of();
    }
  }

  @FunctionalInterface
  interface TestShortCirccuitableDirective extends TestDirective, ShortCircuitable {}
}
