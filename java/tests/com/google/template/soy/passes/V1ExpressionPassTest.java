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

package com.google.template.soy.passes;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParser.ParseResult;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class V1ExpressionPassTest {

  private static final String WRAPPER = "{namespace ns}\n\n{template .a}\n%s\n{/template}\n";

  @Test
  public void testVar() throws Exception {
    assertRewrites(
        "  {@param a: ?}\n  {@param c: ?}\n{v1Expression('$a.b($c)')}",
        "  {@param a: ?}\n  {@param c: ?}\n{v1Expression('$a.b($c)', $a, $c)}");
  }

  @Test
  public void testIj() throws Exception {
    assertRewritingFails(
        "{v1Expression('$ij')}", "'v1Expression' does not support using the '$ij' variable.");
  }

  private void assertRewrites(String input, String output) {
    ParseResult result =
        SoyFileSetParserBuilder.forFileContents(String.format(WRAPPER, input))
            .allowV1Expression(true)
            .parse();
    assertThat(result.fileSet().getChild(0).toSourceString())
        .isEqualTo(String.format(WRAPPER, output));
  }

  private void assertRewritingFails(String input, String error) {
    ErrorReporter errorReporter = ErrorReporter.createForTest();
    SoyFileSetParserBuilder.forFileContents(String.format(WRAPPER, input))
        .allowV1Expression(true)
        .errorReporter(errorReporter)
        .parse();
    assertThat(Iterables.getOnlyElement(errorReporter.getErrors()).message()).isEqualTo(error);
  }
}
