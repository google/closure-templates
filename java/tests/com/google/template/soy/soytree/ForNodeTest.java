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

package com.google.template.soy.soytree;

import static com.google.template.soy.soytree.TemplateSubject.assertThatTemplateContent;

import junit.framework.TestCase;

/**
 * Tests for {@link ForNode}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ForNodeTest extends TestCase {

  public void testInvalidForeachUsage() {
    assertThatTemplateContent("{for $x in $var}{/for}\n")
        .causesError(ForNode.INVALID_COMMAND_TEXT)
        .at(1, 1);
  }

  public void testInvalidRangeInFor() {
    String emptyRangeExpressionErrorMessage =
        "parse error at '': expected null, <BOOLEAN>, <INTEGER>, <FLOAT>, <STRING>, not, "
            + "'an identifier', variable, -, [, (, or $ij.";
    assertThatTemplateContent("{for $x in range()}{/for}\n")
        .causesError(emptyRangeExpressionErrorMessage)
        .at(1, 1);
    // ForNodes don't have accurate source location information for their command texts yet.
    // TODO(user): fix.
    assertThatTemplateContent("{for      $x in range()}{/for}")
        .causesError(emptyRangeExpressionErrorMessage)
        .at(1, 1);
    assertThatTemplateContent("{for\n\n\n\n$x in range()}{/for}")
        .causesError(emptyRangeExpressionErrorMessage)
        .at(1, 1);
  }
}
