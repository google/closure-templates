/*
 * Copyright 2019 Google Inc.
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
import static org.junit.Assert.assertThrows;

import com.google.template.soy.base.SourceLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LineCommentNodeTest {

  private static final SourceLocation X = SourceLocation.UNKNOWN;

  @Test
  public void testConstructor() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LineCommentNode(0, "//  does not start with  whitespace", X));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LineCommentNode(0, " \\  does not start with ' //'", X));
  }

  @Test
  public void testToSourceString() {
    LineCommentNode lineComment0 = new LineCommentNode(0, " //  just a line comment", X);
    assertThat(lineComment0.getCommentText()).isEqualTo("just a line comment");
    assertThat(lineComment0.toSourceString()).isEqualTo(" // just a line comment");
  }

  @Test
  public void testEscapedCommentText() {
    LineCommentNode lineComment0 = new LineCommentNode(0, " //  just a /*line*/ comment", X);
    assertThat(lineComment0.getEscapedCommentText()).isEqualTo("just a /*line*&#47; comment");
  }
}
