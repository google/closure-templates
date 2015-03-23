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

import com.google.template.soy.exprparse.ExpressionParser;

import junit.framework.TestCase;

/**
 * Tests for {@link ForNode}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ForNodeTest extends TestCase {

  public void testInvalidRangeInFor() {
    assertThatTemplateContent("{for $x in range()}{/for}\n")
        .causesError(ExpressionParser.INVALID_EXPRESSION_LIST)
        .at(1, 1);
    // ForNodes don't have accurate source location information for their command texts yet.
    // TODO(user): fix.
    assertThatTemplateContent("{for      $x in range()}{/for}")
        .causesError(ExpressionParser.INVALID_EXPRESSION_LIST)
        .at(1, 1);
    assertThatTemplateContent("{for\n\n\n\n$x in range()}{/for}")
        .causesError(ExpressionParser.INVALID_EXPRESSION_LIST)
        .at(1, 1);
  }
}
