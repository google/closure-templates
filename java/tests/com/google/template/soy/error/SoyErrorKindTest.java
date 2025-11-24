/*
 * Copyright 2016 Google Inc.
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

import com.google.template.soy.compilermetrics.Impression;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyErrorKindTest {
  private static final String MESSAGE = "Error kind.";

  @Test
  public void errorWithImpression_hasCorrectImpression() {
    assertThat(SoyErrorKind.of(MESSAGE, Impression.MAIN_UNEXPECTED_DIAGNOSTIC).getImpression())
        .isEqualTo(Impression.MAIN_UNEXPECTED_DIAGNOSTIC);
  }

  @Test
  public void deprecationErrorWithImpression_hasCorrectImpression() {
    assertThat(
            SoyErrorKind.deprecation(MESSAGE, Impression.MAIN_UNEXPECTED_DIAGNOSTIC)
                .getImpression())
        .isEqualTo(Impression.MAIN_UNEXPECTED_DIAGNOSTIC);
  }
}
