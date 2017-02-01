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

package com.google.template.soy;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.MainClassUtils.runInternal;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.MainClassUtils.Main;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.SoyCompilationException;
import com.google.template.soy.error.SoyError;
import com.google.template.soy.error.SoyErrorKind;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MainClassUtils}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
@RunWith(JUnit4.class)
public final class MainClassUtilsTest {

  @Test
  public void testMainMethodThrowsNothing() {
    assertThat(
            runInternal(
                new Main() {
                  @Override
                  public void main() {}
                }))
        .isEqualTo(0);
  }

  @Test
  public void testMainMethodThrowsIOException() {
    assertThat(
            runInternal(
                new Main() {
                  @Override
                  public void main() throws IOException {
                    throw new IOException();
                  }
                }))
        .isEqualTo(1);
  }

  @Test
  public void testMainMethodThrowsUncheckedException() {
    assertThat(
            runInternal(
                new Main() {
                  @Override
                  public void main() throws IOException {
                    throw new RuntimeException();
                  }
                }))
        .isEqualTo(1);
  }

  @Test
  public void testMainMethodReturnsSoyErrorKinds() {
    assertThat(
            runInternal(
                new Main() {
                  @Override
                  public void main() {
                    throw new SoyCompilationException(
                        ImmutableList.of(
                            SoyError.DEFAULT_FACTORY.create(
                                new SourceLocation("foo.soy"), SoyErrorKind.of(""))));
                  }
                }))
        .isEqualTo(1);
  }
}
