/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CodeChunks}. */
@RunWith(JUnit4.class)
public final class CodeChunksTest {

  @Test
  public void testGenerateParamList() {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addParam("foo", "boolean");
    jsDocBuilder.addParam("bar", "number");
    jsDocBuilder.addParam("baz", "!Object<string, *>=");
    String paramList = FunctionDeclaration.generateParamList(jsDocBuilder.build(), false);
    assertThat(paramList).isEqualTo("foo, bar, baz");
  }
}
