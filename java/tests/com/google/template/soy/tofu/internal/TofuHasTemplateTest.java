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

package com.google.template.soy.tofu.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.shared.internal.NoOpScopedData;
import com.google.template.soy.tofu.SoyTofu;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for `hasTemplate` method of {@link SoyTofu}. */
@RunWith(JUnit4.class)
public class TofuHasTemplateTest {
  private static final String SOY_FILE =
      Joiner.on('\n')
          .join(
              "{namespace sample}",
              "",
              "/** */",
              "{template .example}",
              "  hello world",
              "{/template}");

  private SoyTofu tofu;

  @Before
  public void setUp() throws Exception {
    tofu =
        new BaseTofu(
            new NoOpScopedData(),
            SoyFileSetParserBuilder.forFileContents(SOY_FILE).parse().fileSet(),
            ImmutableMap.of());
  }

  @Test
  public void testHasTemplate() throws Exception {
    assertThat(tofu.hasTemplate("sample.example")).isTrue();
    assertThat(tofu.hasTemplate("some.unrecognized.path")).isFalse();
  }
}
