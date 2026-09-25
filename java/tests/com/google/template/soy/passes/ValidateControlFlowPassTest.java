/*
 * Copyright 2026 Google Inc.
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

import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ValidateControlFlowPassTest {

  @Test
  public void testValidLabeledLoops() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            """
            {namespace ns}
            {extern testFn: () => null}
              {autoimpl}
                {for $i in [1, 2, 3] label="outer"}
                  {while true label="inner"}
                    {break outer /}
                    {continue inner /}
                  {/while}
                {/for}
                {return null /}
              {/autoimpl}
            {/extern}
            """)
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isEmpty();
  }

  @Test
  public void testValidSiblingLoopsWithSameLabel() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            """
            {namespace ns}
            {extern testFn: () => null}
              {autoimpl}
                {for $i in [1, 2, 3] label="loop"}
                  {break loop /}
                {/for}
                {for $j in [4, 5, 6] label="loop"}
                  {continue loop /}
                {/for}
                {return null /}
              {/autoimpl}
            {/extern}
            """)
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).isEmpty();
  }

  @Test
  public void testBreakOutsideLoop() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            """
            {namespace ns}
            {extern testFn: () => null}
              {autoimpl}
                {break /}
                {return null /}
              {/autoimpl}
            {/extern}
            """)
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).hasSize(1);
    assertThat(reporter.getErrors().get(0).message())
        .isEqualTo("{break} or {break <label>} must be inside a loop.");
  }

  @Test
  public void testContinueOutsideLoop() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            """
            {namespace ns}
            {extern testFn: () => null}
              {autoimpl}
                {continue /}
                {return null /}
              {/autoimpl}
            {/extern}
            """)
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).hasSize(1);
    assertThat(reporter.getErrors().get(0).message())
        .isEqualTo("{continue} or {continue <label>} must be inside a loop.");
  }

  @Test
  public void testUnknownLoopLabel() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            """
            {namespace ns}
            {extern testFn: () => null}
              {autoimpl}
                {for $i in [1, 2, 3] label="outer"}
                  {break nonexistent /}
                {/for}
                {return null /}
              {/autoimpl}
            {/extern}
            """)
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).hasSize(1);
    assertThat(reporter.getErrors().get(0).message())
        .isEqualTo("Undefined loop label 'nonexistent'. No enclosing loop found with this label.");
  }

  @Test
  public void testDuplicateLoopLabel() {
    ErrorReporter reporter = ErrorReporter.create();
    SoyFileSetParserBuilder.forFileContents(
            """
            {namespace ns}
            {extern testFn: () => null}
              {autoimpl}
                {for $i in [1, 2, 3] label="myLoop"}
                  {while true label="myLoop"}
                    {break myLoop /}
                  {/while}
                {/for}
                {return null /}
              {/autoimpl}
            {/extern}
            """)
        .errorReporter(reporter)
        .parse();
    assertThat(reporter.getErrors()).hasSize(1);
    assertThat(reporter.getErrors().get(0).message())
        .isEqualTo("Duplicate loop label 'myLoop'. An enclosing loop already has this label.");
  }
}
