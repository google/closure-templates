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

package com.google.template.soy.jssrc.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.jssrc.dsl.Expression.id;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link JsCodeBuilder}.
 *
 */
@RunWith(JUnit4.class)
public final class JsCodeBuilderTest {

  @Test
  public void testOutputVarWithConcat() {
    JsCodeBuilder jcb = new JsCodeBuilder().pushOutputVar("output");
    jcb.initOutputVarIfNecessary();
    assertThat(jcb.getCode()).isEqualTo("let output = '';\n");
    jcb.pushOutputVar("param5").setOutputVarInited().initOutputVarIfNecessary(); // nothing added
    assertThat(jcb.getCode()).isEqualTo("let output = '';\n");

    jcb = new JsCodeBuilder().pushOutputVar("output").addChunkToOutputVar(id("boo"));
    assertThat(jcb.getCode()).isEqualTo("let output = '' + boo;\n");
    jcb.pushOutputVar("param5")
        .setOutputVarInited()
        .addChunksToOutputVar(ImmutableList.of(
            id("a").minus(id("b")),
            id("c").minus(id("d")),
            id("e").times(id("f"))));
    assertThat(jcb.getCode())
        .isEqualTo("let output = '' + boo;\nparam5 += a - b + (c - d) + e * f;\n");
  }
}
