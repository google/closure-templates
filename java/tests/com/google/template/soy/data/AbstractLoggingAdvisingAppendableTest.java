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
package com.google.template.soy.data;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.LoggingAdvisingAppendable.BufferingAppendable;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AbstractLoggingAdvisingAppendable} */
@RunWith(JUnit4.class)
public final class AbstractLoggingAdvisingAppendableTest {
  private static final LogStatement LOGONLY = LogStatement.create(1, null, /* logOnly= */ true);
  private static final LogStatement NOT_LOGONLY =
      LogStatement.create(1, null, /* logOnly= */ false);

  @Test
  public void testLogonly_logonly_above_regular() throws IOException {
    BufferingAppendable buffering = LoggingAdvisingAppendable.buffering();
    buffering.append("a");
    buffering.enterLoggableElement(LOGONLY);
    buffering.append("b");
    buffering.enterLoggableElement(NOT_LOGONLY);
    buffering.append("c");
    buffering.exitLoggableElement();
    buffering.append("d");
    buffering.exitLoggableElement();
    buffering.append("e");
    assertThat(buffering.toString()).isEqualTo("ae");
  }

  @Test
  public void testLogonly_regular_above_logong() throws IOException {
    BufferingAppendable buffering = LoggingAdvisingAppendable.buffering();
    buffering.append("a");
    buffering.enterLoggableElement(NOT_LOGONLY);
    buffering.append("b");
    buffering.enterLoggableElement(LOGONLY);
    buffering.append("c");
    buffering.exitLoggableElement();
    buffering.append("d");
    buffering.exitLoggableElement();
    buffering.append("e");
    assertThat(buffering.toString()).isEqualTo("abde");
  }

  @Test
  public void testLogonly_logonly_before_regular() throws IOException {
    BufferingAppendable buffering = LoggingAdvisingAppendable.buffering();
    buffering.enterLoggableElement(LOGONLY);
    buffering.append("logonly");
    buffering.exitLoggableElement();
    buffering.enterLoggableElement(NOT_LOGONLY);
    buffering.append("hello");
    buffering.exitLoggableElement();
    assertThat(buffering.toString()).isEqualTo("hello");
  }

  @Test
  public void testLogonly_logonly_after_regular() throws IOException {
    // test against the buffering version since it is a simple concrete implementation.
    BufferingAppendable buffering = LoggingAdvisingAppendable.buffering();
    buffering.enterLoggableElement(NOT_LOGONLY);
    buffering.append("hello");
    buffering.exitLoggableElement();
    buffering.enterLoggableElement(LOGONLY);
    buffering.append("logonly");
    buffering.exitLoggableElement();
    assertThat(buffering.toString()).isEqualTo("hello");
  }

  @Test
  public void testLogonly_deeplyNested() throws IOException {
    // test against the buffering version since it is a simple concrete implementation.
    BufferingAppendable buffering = LoggingAdvisingAppendable.buffering();
    buffering.append("a");
    for (int i = 0; i < 1024; i++) {
      buffering.enterLoggableElement(LOGONLY);
      buffering.append("b");
      buffering.exitLoggableElement();
    }
    buffering.append("c");
    assertThat(buffering.toString()).isEqualTo("ac");
  }

  @Test
  public void testAppliesEscapersToPlaceholder() throws IOException {
    BufferingAppendable buffering = LoggingAdvisingAppendable.buffering();
    buffering.appendLoggingFunctionInvocation(
        LoggingFunctionInvocation.create("foo", "placeholder", ImmutableList.of()),
        ImmutableList.of(Functions.forMap(ImmutableMap.of("placeholder", "replacement"))));
    assertThat(buffering.toString()).isEqualTo("replacement");
  }
}
