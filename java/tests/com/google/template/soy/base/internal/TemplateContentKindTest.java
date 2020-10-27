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

package com.google.template.soy.base.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TemplateContentKindTest {

  @Test
  public void testForAttributeValue_simpleTypes() {
    assertThat(of("html").getSanitizedContentKind()).isEqualTo(SanitizedContentKind.HTML);
    assertThat(of("text").getSanitizedContentKind()).isEqualTo(SanitizedContentKind.TEXT);
    assertThat(TemplateContentKind.fromAttributeValue("blarg")).isEmpty();
  }

  @Test
  public void testForAttributeValue_element() {
    assertThat(of("html<?>").getSanitizedContentKind())
        .isEqualTo(SanitizedContentKind.HTML_ELEMENT);
  }

  @Test
  public void testAsAttributeValue_simpleTypes() {
    assertThat(of("html").asAttributeValue()).isEqualTo("html");
  }

  @Test
  public void testAsAttributeValue_element() {
    assertThat(of("html<?>").asAttributeValue()).isEqualTo("html<?>");
  }

  @Test
  public void isAssignableFrom() {
    assertThat(of("html").isAssignableFrom(of("html"))).isTrue();
    assertThat(of("html").isAssignableFrom(of("text"))).isFalse();
    assertThat(of("html<?>").isAssignableFrom(of("html<?>"))).isTrue();
    assertThat(of("html<?>").isAssignableFrom(of("html<div>"))).isTrue();
    assertThat(of("html<div>").isAssignableFrom(of("html<?>"))).isFalse();
  }

  private static TemplateContentKind of(String attr) {
    return TemplateContentKind.fromAttributeValue(attr).get();
  }
}
