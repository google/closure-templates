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

package com.google.template.soy.jssrc.dsl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.dsl.Expressions.number;
import static com.google.template.soy.jssrc.dsl.Expressions.stringLiteral;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CodeChunkUtils}. */
@RunWith(JUnit4.class)
public final class CodeChunkUtilsTest {

  @Test
  public void testConcatChunks() {
    CodeChunk result =
        Expressions.concat(
            ImmutableList.of(
                stringLiteral("blah").plus(stringLiteral("blah")),
                stringLiteral("bleh").plus(stringLiteral("bleh")),
                number(2).times(number(8))));
    assertThat(result.getCode(FormatOptions.JSSRC))
        .isEqualTo("'blah' + 'blah' + ('bleh' + 'bleh') + 2 * 8;");
  }

  @Test
  public void testConcatChunks_twice() {
    Expression result =
        Expressions.concat(
            ImmutableList.of(
                stringLiteral("a"), id("x").assign(stringLiteral("b")), stringLiteral("b")));
    Expression result2 =
        Expressions.concat(
            ImmutableList.of(
                stringLiteral("a"), id("x").assign(stringLiteral("b")), stringLiteral("b")));
    assertThat(Expressions.concat(ImmutableList.of(result, result2)).getCode(FormatOptions.JSSRC))
        .isEqualTo("'a' + (x = 'b') + 'b' + 'a' + (x = 'b') + 'b';");
  }

  @Test
  public void testConcatChunksRightAssociative() {
    CodeChunk result =
        Expressions.concat(
            ImmutableList.of(
                stringLiteral("a"), id("x").assign(stringLiteral("b")), stringLiteral("c")));
    assertThat(result.getCode(FormatOptions.JSSRC)).isEqualTo("'a' + (x = 'b') + 'c';");
  }

  @Test
  public void testConcatChunksForceString() {
    CodeChunk result = Expressions.concatForceString(ImmutableList.of(number(2), number(2)));
    assertThat(result.getCode(FormatOptions.JSSRC)).isEqualTo("'' + 2 + 2;");
  }

  @Test
  public void testConcatChunksForceString_chunksHavePlusOps() {
    CodeChunk result =
        Expressions.concatForceString(ImmutableList.of(number(2), number(2).plus(number(3))));
    assertThat(result.getCode(FormatOptions.JSSRC)).isEqualTo("'' + 2 + (2 + 3);");
  }

  @Test
  public void testGenerateParamList() {
    JsDoc.Builder jsDocBuilder = JsDoc.builder();
    jsDocBuilder.addParam("foo", "boolean");
    jsDocBuilder.addParam("bar", "number");
    jsDocBuilder.addParam("baz", "!Object<string, *>=");
    String paramList = CodeChunkUtils.generateParamList(jsDocBuilder.build(), false);
    assertThat(paramList).isEqualTo("foo, bar, baz");
  }
}
