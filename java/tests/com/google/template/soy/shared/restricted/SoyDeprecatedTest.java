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

package com.google.template.soy.shared.restricted;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.error.ErrorReporter;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyDeprecatedTest {

  @Test
  public void testSoyDeprecated() throws Exception {

    ErrorReporter reporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forTemplateContents("{deprecated() |deprecated}")
        .addSoyFunction(new DeprecatedFunction())
        .addPrintDirective(new DeprecatedPrintDirective())
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isEmpty();
    assertThat(reporter.getWarnings()).hasSize(2);
    assertThat(reporter.getWarnings().get(0).message())
        .isEqualTo("deprecated is deprecated: please stop using this function.");
    assertThat(reporter.getWarnings().get(1).message())
        .isEqualTo("|deprecated is deprecated: please stop using this directive.");
  }

  @SoyDeprecated("please stop using this function.")
  static final class DeprecatedFunction implements SoyFunction {
    @Override
    public String getName() {
      return "deprecated";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }
  }

  @SoyDeprecated("please stop using this directive.")
  static final class DeprecatedPrintDirective implements SoyPrintDirective {
    @Override
    public String getName() {
      return "|deprecated";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }
  }
}
