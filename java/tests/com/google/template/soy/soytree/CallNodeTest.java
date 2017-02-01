/*
 * Copyright 2010 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.FormattingErrorReporter;
import com.google.template.soy.exprparse.SoyParsingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for CallNode.
 *
 */
@RunWith(JUnit4.class)
public final class CallNodeTest {

  /** Escaping list of directive names. */
  private static final ImmutableList<String> NO_ESCAPERS = ImmutableList.of();

  @Test
  public void testCommandText() {

    checkCommandText("foo");
    checkCommandText(".foo data=\"all\"");
    checkCommandText(" .baz data=\"$x\"", ".baz data=\"$x\"");

    FormattingErrorReporter errorReporter = new FormattingErrorReporter();
    new CallBasicNode.Builder(0, SourceLocation.UNKNOWN)
        .commandText(".foo.bar data=\"$x\"")
        .build(SoyParsingContext.empty(errorReporter, "fake.namspace"));
    assertThat(errorReporter.getErrorMessages())
        .containsExactly("Invalid callee name \".foo.bar\" for 'call' command.");
  }

  @Test
  public void testSetEscapingDirectiveNames() {
    CallBasicNode callNode =
        new CallBasicNode.Builder(0, SourceLocation.UNKNOWN)
            .commandText(".foo")
            .build(SoyParsingContext.exploding());
    assertThat(callNode.getEscapingDirectiveNames()).isEmpty();
    callNode.setEscapingDirectiveNames(ImmutableList.of("hello", "world"));
    assertEquals(ImmutableList.of("hello", "world"), callNode.getEscapingDirectiveNames());
    callNode.setEscapingDirectiveNames(ImmutableList.of("bye", "world"));
    assertEquals(ImmutableList.of("bye", "world"), callNode.getEscapingDirectiveNames());
  }

  private void checkCommandText(String commandText) {
    checkCommandText(commandText, commandText);
  }

  private static void checkCommandText(String commandText, String expectedCommandText) {

    CallBasicNode callNode =
        new CallBasicNode.Builder(0, SourceLocation.UNKNOWN)
            .commandText(commandText)
            .build(SoyParsingContext.exploding());
    if (callNode.getCalleeName() == null) {
      callNode.setCalleeName("testNamespace" + callNode.getSrcCalleeName());
    }

    CallBasicNode normCallNode =
        new CallBasicNode.Builder(0, SourceLocation.UNKNOWN)
            .calleeName(callNode.getCalleeName())
            .sourceCalleeName(callNode.getSrcCalleeName())
            .dataAttribute(callNode.dataAttribute())
            .userSuppliedPlaceholderName(callNode.getUserSuppliedPhName())
            .syntaxVersionBound(callNode.getSyntaxVersionUpperBound())
            .escapingDirectiveNames(NO_ESCAPERS)
            .build(SoyParsingContext.exploding());

    assertThat(normCallNode.getCommandText()).isEqualTo(expectedCommandText);
    assertThat(normCallNode.getSyntaxVersionUpperBound())
        .isEqualTo(callNode.getSyntaxVersionUpperBound());
    assertThat(normCallNode.getCalleeName()).isEqualTo(callNode.getCalleeName());
    assertThat(normCallNode.dataAttribute()).isEqualTo(callNode.dataAttribute());
  }
}
