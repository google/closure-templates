/*
 * Copyright 2012 Google Inc.
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

import com.google.template.soy.data.SanitizedContent.ContentKind;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for UnsafeSanitizedContentOrdainer utility class.
 *
 */
@RunWith(JUnit4.class)
public class UnsafeSanitizedContentOrdainerTest {

  @Test
  public void testOrdainAsSafe() {
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT))
        .isEqualTo(SanitizedContents.unsanitizedText("Hello World", null));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello <b>World</b>", ContentKind.HTML))
        .isEqualTo(SanitizedContent.create("Hello <b>World</b>", ContentKind.HTML, null));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello_world();", ContentKind.JS))
        .isEqualTo(SanitizedContent.create("hello_world();", ContentKind.JS, Dir.LTR));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello_world();", ContentKind.CSS))
        .isEqualTo(SanitizedContent.create("hello_world();", ContentKind.CSS, Dir.LTR));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello/world", ContentKind.URI))
        .isEqualTo(SanitizedContent.create("hello/world", ContentKind.URI, Dir.LTR));
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("hello=world", ContentKind.ATTRIBUTES))
        .isEqualTo(SanitizedContent.create("hello=world", ContentKind.ATTRIBUTES, Dir.LTR));
  }

  @Test
  public void testOrdainAsSafeWithDir() {
    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT, Dir.LTR))
        .isEqualTo(SanitizedContents.unsanitizedText("Hello World", Dir.LTR));
    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafe("Hello World", ContentKind.TEXT, Dir.RTL))
        .isEqualTo(SanitizedContents.unsanitizedText("Hello World", Dir.RTL));
    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "Hello World", ContentKind.TEXT, Dir.NEUTRAL))
        .isEqualTo(SanitizedContents.unsanitizedText("Hello World", Dir.NEUTRAL));
  }
}
