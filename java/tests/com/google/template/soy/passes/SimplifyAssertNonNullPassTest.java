/*
 * Copyright 2020 Google Inc.
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

import com.google.template.soy.exprtree.testing.ExpressionParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SimplifyAssertNonNullPassTest {

  @Test
  public void shouldRemoveNonNullAssertions() {
    assertThat(
            new ExpressionParser("$r!.a!.b!.c")
                .withParam("r", "null|[a: null|[b: null|[c: null|string]]]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r.a.b.c");
  }

  @Test
  public void shouldLeaveFinalNonNullAssertion() {
    assertThat(
            new ExpressionParser("$r!.a!")
                .withParam("r", "null|[a: null|string]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r.a!");
  }

  @Test
  public void shouldRemoveIfFollowedByNullSafeAccess() {
    assertThat(
            new ExpressionParser("$r!.a?.b.c")
                .withParam("r", "null|[a: null|[b: null|[c: null|string]]]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r.a?.b.c");
  }

  @Test
  public void shouldRemoveIfPrecededByNullSafeAccess() {
    assertThat(
            new ExpressionParser("$r.a?.b!.c")
                .withParam("r", "null|[a: null|[b: null|[c: null|string]]]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r.a?.b.c");
  }

  @Test
  public void shouldLeaveFinalNonNullAssertionAfterNullSafeAccess() {
    assertThat(
            new ExpressionParser("$r?.a.b.c!")
                .withParam("r", "null|[a: null|[b: null|[c: null|string]]]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r?.a.b.c!");
  }

  @Test
  public void shouldLeaveFinalAndRemoveOthersAfterNullSafeAccess() {
    assertThat(
            new ExpressionParser("$r?.a.b!.c!")
                .withParam("r", "null|[a: null|[b: null|[c: null|string]]]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r?.a.b.c!");
  }

  @Test
  public void shouldRemoveWithParentheses() {
    assertThat(
            new ExpressionParser("($r.a.b)!.c!")
                .withParam("r", "null|[a: null|[b: null|[c: null|string]]]")
                .withExperimentalFeatures("enableNonNullAssertionOperator")
                .parse()
                .toSourceString())
        .isEqualTo("$r.a.b.c!");
  }
}
