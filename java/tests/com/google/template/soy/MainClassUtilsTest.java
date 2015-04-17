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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.base.internal.SoyFileSupplier;
import com.google.template.soy.error.ErrorPrettyPrinter;

import junit.framework.TestCase;

import java.io.IOException;

/**
 * Tests for {@link MainClassUtils}.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class MainClassUtilsTest extends TestCase {

  public void testMainMethodThrowsNothing() {
    assertThat(runInternal(new Main() {
      @Override
      public CompilationResult main() throws IOException {
        return new CompilationResult(
            ImmutableList.<SoySyntaxException>of(),
            new ErrorPrettyPrinter(ImmutableList.<SoyFileSupplier>of()));
      }
    })).isEqualTo(0);
  }

  public void testMainMethodThrowsIOException() {
    assertThat(runInternal(new Main() {
      @Override
      public CompilationResult main() throws IOException {
        throw new IOException();
      }
    })).isEqualTo(1);
  }

  public void testMainMethodThrowsUncheckedException() {
    assertThat(runInternal(new Main() {
      @Override
      public CompilationResult main() throws IOException {
        throw new RuntimeException();
      }
    })).isEqualTo(1);
  }

  public void testMainMethodReturnsSoyErrors() {
    assertThat(runInternal(new Main() {
      @Override
      public CompilationResult main() throws IOException {
        return new CompilationResult(
            ImmutableList.of(SoySyntaxException.createWithoutMetaInfo("OOPS")),
            new ErrorPrettyPrinter(ImmutableList.<SoyFileSupplier>of()));
      }
    })).isEqualTo(1);
  }

}
