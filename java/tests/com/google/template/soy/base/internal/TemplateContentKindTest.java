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
    assertThat(TemplateContentKind.fromAttributeValue("html").get().getSanitizedContentKind())
        .isEqualTo(SanitizedContentKind.HTML);
    assertThat(TemplateContentKind.fromAttributeValue("text").get().getSanitizedContentKind())
        .isEqualTo(SanitizedContentKind.TEXT);
    assertThat(TemplateContentKind.fromAttributeValue("blarg")).isEmpty();
  }

  @Test
  public void testForAttributeValue_element() {
    assertThat(TemplateContentKind.fromAttributeValue("html<?>").get().getSanitizedContentKind())
        .isEqualTo(SanitizedContentKind.HTML);
  }

  @Test
  public void testAsAttributeValue_simpleTypes() {
    assertThat(TemplateContentKind.fromAttributeValue("html").get().asAttributeValue())
        .isEqualTo("html");
  }

  @Test
  public void testAsAttributeValue_element() {
    assertThat(TemplateContentKind.fromAttributeValue("html<?>").get().asAttributeValue())
        .isEqualTo("html<?>");
  }
}
