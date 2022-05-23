/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internal.Converters;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SanitizedContentKindTest {

  @Test
  public void testSanitizedContentKind_fromSanitizedContentKindCompatibilityWithContentKind()
      throws Exception {
    // Make sure that there is a 1-1 relationship with ContentKind except for HTML, which has two
    // different SanitizedContentKind (HTML and HTML_ELEMENT)
    for (SanitizedContentKind sck : SanitizedContentKind.values()) {
      Converters.contentKindfromSanitizedContentKind(sck);
    }
    for (ContentKind ck : ContentKind.values()) {
      SanitizedContentKind.valueOf(ck.name());
    }
  }

  @Test
  public void testForAttributeValue() {
    assertThat(SanitizedContentKind.fromAttributeValue("html")).hasValue(SanitizedContentKind.HTML);
    assertThat(SanitizedContentKind.fromAttributeValue("text")).hasValue(SanitizedContentKind.TEXT);
    assertThat(SanitizedContentKind.fromAttributeValue("blarg")).isEmpty();
  }

  @Test
  public void testGetAttributeValues() {
    Set<String> attributeValues = SanitizedContentKind.attributeValues();
    assertThat(attributeValues)
        .containsExactly(
            "attributes",
            "css",
            "html",
            "html_element",
            "js",
            "text",
            "trusted_resource_uri",
            "uri")
        .inOrder();
  }
}
