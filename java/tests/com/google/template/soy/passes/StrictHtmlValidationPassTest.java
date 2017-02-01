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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.FormattingErrorReporter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StrictHtmlValidationPassTest {
  private FormattingErrorReporter errorReporter;

  @Before
  public void setUp() {
    errorReporter = new FormattingErrorReporter();
  }

  @Test
  public void testStrictHtmlValidationPassWithoutFlag() throws Exception {
    SoyFileSetParserBuilder.forFileContents(
            Joiner.on('\n')
                .join("{namespace ns}", "", "{template .t stricthtml=\"true\"}", "{/template}"))
        .errorReporter(errorReporter)
        .parse();
    assertThat(errorReporter.getErrorMessages()).hasSize(1);
    assertThat(Iterables.getOnlyElement(errorReporter.getErrorMessages()))
        .contains(
            "Strict HTML mode is disabled by default. In order to use stricthtml syntax in your "
                + "Soy template, explicitly pass --enabledExperimentalFeatures=stricthtml to "
                + "compiler.");
  }
}
