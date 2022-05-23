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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyDeprecatedTest {

  @Test
  public void testSoyDeprecated() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forTemplateContents(
            "{@param s:string}\n"
                + "{deprecatedF() |deprecated}\n"
                + "{deprecatedSrcF($s)}\n"
                + "{$s.strM()}\n"
                + "{$s.deprecatedM()}")
        .addSoyFunction(new DeprecatedFunction())
        .addSoySourceFunction(new DeprecatedSourceFunction())
        .addSoySourceFunction(new DeprecatedSourceMethod())
        .addPrintDirective(new DeprecatedPrintDirective())
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isEmpty();

    ImmutableList<SoyError> warnings = ImmutableList.sortedCopyOf(reporter.getWarnings());
    assertThat(warnings).hasSize(4);
    assertThat(warnings.get(0).message())
        .isEqualTo("deprecatedF is deprecated: please stop using this function.");
    assertThat(warnings.get(1).message())
        .isEqualTo("|deprecated is deprecated: please stop using this directive.");
    assertThat(warnings.get(2).message())
        .isEqualTo("deprecatedSrcF is deprecated: use method syntax instead");
    assertThat(warnings.get(3).message()).isEqualTo("deprecatedM is deprecated: just stop");
  }

  @SoyDeprecated("please stop using this function.")
  static final class DeprecatedFunction implements SoyFunction {
    @Override
    public String getName() {
      return "deprecatedF";
    }

    @Override
    public Set<Integer> getValidArgsSizes() {
      return ImmutableSet.of(0);
    }
  }

  @SoyFunctionSignature(
      name = "deprecatedSrcF",
      deprecatedWarning = "use method syntax instead",
      value = @Signature(parameterTypes = "string", returnType = "string"))
  @SoyMethodSignature(baseType = "string", name = "strM", value = @Signature(returnType = "string"))
  static final class DeprecatedSourceFunction implements SoySourceFunction {}

  @SoyMethodSignature(
      baseType = "string",
      name = "deprecatedM",
      value = @Signature(returnType = "string"),
      deprecatedWarning = "just stop")
  static final class DeprecatedSourceMethod implements SoySourceFunction {}

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
