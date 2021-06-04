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
package com.google.template.soy.shared.internal.gencode;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.base.internal.IndentedLinesBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JavaGenerationUtilsTest {

  @Test
  public void testAppendJavadoc_forceMultiline() {
    String doc = "Test comment.";

    String expectedJavadoc = "    /** Test comment. */\n";

    IndentedLinesBuilder ilb = new IndentedLinesBuilder(2, 4);
    JavaGenerationUtils.appendJavadoc(
        ilb, doc, /* forceMultiline= */ false, /* wrapAt100Chars= */ true);
    assertThat(ilb.toString()).isEqualTo(expectedJavadoc);
  }

  @Test
  public void testAppendJavadoc_wrapAt100Chars() {

    String doc =
        "Blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah"
            + " blah blah blah blah blah blah blahblahblahblahblahblahblahblahblahblahblahblahblah"
            + "blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah"
            + "blahblahblah.";
    String expectedJavadoc =
        "    /**\n"
            + "     * Blah blah blah blah blah blah blah blah blah blah blah blah blah blah blah "
            + "blah blah blah\n"
            + "     * blah blah blah blah blah\n"
            + "     * blahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblahblah"
            + "blahblahblahb\n"
            + "     * lahblahblahblahblahblahblahblahblahblahblahblah.\n"
            + "     */\n";
    IndentedLinesBuilder ilb = new IndentedLinesBuilder(2, 4);
    JavaGenerationUtils.appendJavadoc(
        ilb, doc, /* forceMultiline= */ false, /* wrapAt100Chars= */ true);
    assertThat(ilb.toString()).isEqualTo(expectedJavadoc);
  }

  @Test
  public void testMakeUpperCamelCase() {
    assertThat(JavaGenerationUtils.makeUpperCamelCase("BOO_FOO")).isEqualTo("BooFoo");
    assertThat(JavaGenerationUtils.makeUpperCamelCase("booFoo")).isEqualTo("BooFoo");
    assertThat(JavaGenerationUtils.makeUpperCamelCase("boo-FOO")).isEqualTo("BooFoo");
    assertThat(JavaGenerationUtils.makeUpperCamelCase("boo_foo")).isEqualTo("BooFoo");
    assertThat(JavaGenerationUtils.makeUpperCamelCase("boo_FOO")).isEqualTo("BooFoo");
    assertThat(JavaGenerationUtils.makeUpperCamelCase("BOO_FOO")).isEqualTo("BooFoo");
    assertThat(JavaGenerationUtils.makeUpperCamelCase("BOOFoo")).isEqualTo("BOOFoo");
  }
}
