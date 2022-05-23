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

package com.google.template.soy.types;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.types.TemplateType.Parameter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for soy types. */
@RunWith(JUnit4.class)
public class TemplateTypeTest {

  @Test
  public void testAttrName() {
    assertThat(Parameter.attrToParamName("an-attr")).isEqualTo("anAttr");
    assertThat(Parameter.attrToParamName("anAttr")).isEqualTo("anattr");

    assertThat(Parameter.paramToAttrName("anAttr")).isEqualTo("an-attr");
    assertThat(Parameter.paramToAttrName("an-attr")).isEqualTo("an-attr");

    assertThat(Parameter.isValidAttrName("attr")).isTrue();
    assertThat(Parameter.isValidAttrName("an-attr")).isTrue();
    assertThat(Parameter.isValidAttrName("anAttr")).isFalse();
    assertThat(Parameter.isValidAttrName("an-attr-")).isFalse();
    assertThat(Parameter.isValidAttrName("-an-attr")).isFalse();
  }
}
