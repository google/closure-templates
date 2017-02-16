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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ForNode}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@RunWith(JUnit4.class)
public final class ForNodeTest {

  @Test
  public void testInvalidForeachUsage() {
    assertThatTemplateContent("{for $x in $var}{/for}")
        .causesError("Invalid 'for' command text")
        .at(1, 1);
  }

  @Test
  public void testForRangeOutOfRange() {
    assertThatTemplateContent("{for $x in range(2147483648, 2147483649, 2147483650)}{/for}")
        .causesError("Range specification is too large: 2,147,483,648")
        .causesError("Range specification is too large: 2,147,483,649")
        .causesError("Range specification is too large: 2,147,483,650")
        .at(1, 1);
  }
}
