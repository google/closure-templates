/*
 * Copyright 2013 Google Inc.
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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MainEntryPointUtilsTest {

  @Test
  public void testBuildMsgsFilePath() {
    assertThat(
            MainEntryPointUtils.buildFilePath(
                "/xxx/yyy/zzz/soy_translated_{LOCALE}.xlf", "pt-BR", null))
        .isEqualTo("/xxx/yyy/zzz/soy_translated_pt-BR.xlf");
    assertThat(
            MainEntryPointUtils.buildFilePath("ccc/ddd/soy_translated_{LOCALE}.xlf", "pt-BR", null))
        .isEqualTo("ccc/ddd/soy_translated_pt-BR.xlf");
  }

  @Test
  public void testBuildOutputFilePath() {
    assertThat(
            MainEntryPointUtils.buildFilePath(
                "xxx/{INPUT_DIRECTORY}{INPUT_FILE_NAME}.js", null, "aaa/bbb/ccc/ddd/file.name.soy"))
        .isEqualTo("xxx/aaa/bbb/ccc/ddd/file.name.soy.js");
    assertThat(
            MainEntryPointUtils.buildFilePath(
                "www/xxx/{INPUT_DIRECTORY}yyy/zzz/{INPUT_FILE_NAME_NO_EXT}__{LOCALE_LOWER_CASE}.js",
                "pt-BR",
                "aaa/bbb/ccc/ddd/filename.soy"))
        .isEqualTo("www/xxx/aaa/bbb/ccc/ddd/yyy/zzz/filename__pt_br.js");
  }
}
