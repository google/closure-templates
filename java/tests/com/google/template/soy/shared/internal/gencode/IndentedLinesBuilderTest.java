/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared.internal.gencode;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for IndentedLinesBuilder.
 */
@RunWith(JUnit4.class)
public final class IndentedLinesBuilderTest {

  @Test
  public void testIndentedLinesBuilder() {

    IndentedLinesBuilder ilb = new IndentedLinesBuilder(null);
    ilb.appendLine("Line 1");
    ilb.increaseIndent();
    ilb.appendLine("Line", ' ', 2);
    ilb.increaseIndent(2);
    ilb.appendLineStart("Line ").appendLineEnd('3');
    ilb.decreaseIndent();
    ilb.appendLine("Line 4");

    assertThat(ilb.getCurrIndentLen()).isEqualTo(4);
    assertThat(ilb.toString()).isEqualTo("Line 1\n  Line 2\n      Line 3\n    Line 4\n");
  }
}
