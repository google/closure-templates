/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.sharedpasses;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soyparse.ErrorReporterImpl;
import com.google.template.soy.soyparse.ExplodingErrorReporter;
import com.google.template.soy.soytree.SoyFileSetNode;

import junit.framework.TestCase;

/**
 * Unit tests for AssertStrictAutoescapingVisitor.
 *
 */
public final class AssertStrictAutoescapingVisitorTest extends TestCase {

  public void testStrictTemplate() {
    String soyCode = "{namespace foo.bar autoescape=\"strict\"}\n"
        + "{template name=\"foo\" autoescape=\"strict\"}\n"
        + "  {$boo}\n"
        + "{/template}\n";

    assertThat(executeStrictCheck(soyCode)).isNull();


    soyCode = "{namespace foo.bar}\n"
        + "{template name=\"foo\"}\n"
        + "  {$boo}\n"
        + "{/template}\n";

    assertThat(executeStrictCheck(soyCode)).isNull();
  }

  public void testNonStrictNamespace() {
    String soyCode = "{namespace foo.bar autoescape=\"deprecated-contextual\"}\n"
        + "{template name=\"foo\" autoescape=\"strict\"}\n"
        + "  {$boo}\n"
        + "{/template}\n";

    assertThat(executeStrictCheck(soyCode)).isNotNull();
  }

  public void testNonStrictTemplate() {
    String soyCode = "{namespace foo.bar autoescape=\"strict\"}\n"
        + "{template name=\"foo\" autoescape=\"deprecated-contextual\"}\n"
        + "  {$boo}\n"
        + "{/template}\n";

    assertThat(executeStrictCheck(soyCode)).isNotNull();
  }

  public void testNonDeclaredTemplate() {
    String soyCode = "{namespace foo.bar autoescape=\"deprecated-noncontextual\"}\n"
        + "{template name=\"foo\"}\n"
        + "  {$boo}\n"
        + "{/template}\n";

    assertThat(executeStrictCheck(soyCode)).isNotNull();
  }

  /**
   * Parse soyCode and execute the AssertStrictAutoescapingVisitor check on the output.
   *
   * @param soyCode The input code.
   * @return A SoySyntaxException if thrown by the visitor.
   */
  private SoySyntaxException executeStrictCheck(String soyCode) {
    SoyFileSetNode soyTree = SoyFileSetParserBuilder.forFileContents(soyCode)
        .doRunInitialParsingPasses(false)
        .errorReporter(ExplodingErrorReporter.get())
        .parse();
    ErrorReporterImpl errorReporter = new ErrorReporterImpl();
    new AssertStrictAutoescapingVisitor(errorReporter).exec(soyTree);
    return Iterables.getFirst(errorReporter.getErrors(), null);
  }
}
