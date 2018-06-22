/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.internal.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.internal.base.UnescapeUtils.unescapeHtml;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UnescapeUtilsTest {

  @Test
  public void testUnescapeHtml() throws Exception {
    assertThat(unescapeHtml("&#33;")).isEqualTo("!");
    assertThat(unescapeHtml("&#x21;")).isEqualTo("!");
    assertThat(unescapeHtml("&#x20;")).isEqualTo(" ");
    assertThat(unescapeHtml("&amp;")).isEqualTo("&");
    assertThat(unescapeHtml("a&amp;b&#x20;c")).isEqualTo("a&b c");
  }
}
