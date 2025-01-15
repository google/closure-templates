/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.logging;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.html.types.SafeUrls;
import com.google.template.soy.logging.SoyLogger.LoggingAttrs;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SoyLoggerTest {

  @Test
  public void loggingAttr_basic() {
    var loggingAttrs =
        LoggingAttrs.builder()
            .addDataAttribute("data-foo", "bar")
            .addAnchorHref(SafeUrls.fromConstant("./go"))
            .addAnchorPing(SafeUrls.fromConstant("./log"))
            .build();
    assertThat(loggingAttrs.toString()).isEqualTo("data-foo=\"bar\" href=\"./go\" ping=\"./log\"");
  }

  @Test
  public void loggingAttr_testBadDataAttr() {
    var e =
        assertThrows(
            IllegalArgumentException.class,
            () -> LoggingAttrs.builder().addDataAttribute("foo", "bar"));
    assertThat(e).hasMessageThat().isEqualTo("data attribute key must start with 'data-'.");
  }

  @Test
  public void loggingAttr_testBadAnchorHref() {
    var e =
        assertThrows(
            IllegalStateException.class,
            () ->
                LoggingAttrs.builder()
                    .addAnchorHref(SafeUrls.fromConstant("./go"))
                    .build()
                    .writeTo(/* isAnchorTag= */ false, new StringBuilder()));
    assertThat(e)
        .hasMessageThat()
        .isEqualTo("logger attempted to add anchor attributes to a non-anchor element.");
  }
}
