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

package com.google.template.soy.error;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ErrorReporterImpl} */
@RunWith(JUnit4.class)
public final class ErrorReporterImplTest {

  private static final SoyErrorKind ERROR = SoyErrorKind.of("Oh noes.");

  @Test
  public void testCheckpoint() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    Checkpoint cp = reporter.checkpoint();
    assertThat(reporter.errorsSince(cp)).isFalse();

    reporter.report(SourceLocation.UNKNOWN, ERROR);
    assertThat(reporter.errorsSince(cp)).isTrue();
    cp = reporter.checkpoint();

    reporter.warn(SourceLocation.UNKNOWN, ERROR);
    assertThat(reporter.errorsSince(cp)).isFalse();
  }

  @Test
  public void testCheckpoint_differentInstances() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    Checkpoint cp = reporter.checkpoint();
    try {
      ErrorReporter.createForTest().errorsSince(cp);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testWarn() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    reporter.warn(SourceLocation.UNKNOWN, ERROR);
    assertThat(reporter.getWarnings().get(0).toString())
        .isEqualTo("unknown:-1: warning: Oh noes.\n");
  }

  @Test
  public void testCopyTo() {
    ErrorReporter reporter = ErrorReporter.createForTest();
    reporter.warn(SourceLocation.UNKNOWN, ERROR);
    reporter.report(SourceLocation.UNKNOWN, ERROR);
    ErrorReporter secondReporter = ErrorReporter.createForTest();

    Checkpoint cp = secondReporter.checkpoint();
    assertThat(secondReporter.errorsSince(cp)).isFalse();
    assertThat(secondReporter.hasErrors()).isFalse();

    reporter.copyTo(secondReporter);

    assertThat(secondReporter.errorsSince(cp)).isTrue();
    assertThat(secondReporter.hasErrors()).isTrue();
    assertThat(secondReporter.getWarnings().get(0).toString())
        .isEqualTo("unknown:-1: warning: Oh noes.\n");
    assertThat(secondReporter.getErrors().get(0).toString())
        .isEqualTo("unknown:-1: error: Oh noes.\n");
  }
}
