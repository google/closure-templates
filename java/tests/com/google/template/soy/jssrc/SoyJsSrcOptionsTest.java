/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.jssrc;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyJsSrcOptions.
 *
 */
@RunWith(JUnit4.class)
public final class SoyJsSrcOptionsTest {

  @Test
  public void testClone() {

    SoyJsSrcOptions options = new SoyJsSrcOptions();
    options.setShouldGenerateGoogMsgDefs(true);
    options.setBidiGlobalDir(1);

    SoyJsSrcOptions clonedOptions = options.clone();
    assertThat(clonedOptions.getBidiGlobalDir()).isEqualTo(options.getBidiGlobalDir());
    assertThat(clonedOptions.getUseGoogIsRtlForBidiGlobalDir())
        .isEqualTo(options.getUseGoogIsRtlForBidiGlobalDir());
    assertThat(clonedOptions.shouldGenerateGoogMsgDefs())
        .isEqualTo(options.shouldGenerateGoogMsgDefs());
  }
}
