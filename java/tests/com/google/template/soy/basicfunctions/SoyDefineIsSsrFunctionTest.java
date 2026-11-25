/*
 * Copyright 2026 Google Inc.
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

package com.google.template.soy.basicfunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.shared.internal.SoyCompilerContext;
import com.google.template.soy.shared.internal.SoyCompilerContext.OutputTarget;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link SoyDefineIsSsrFunction}. */
@RunWith(JUnit4.class)
public final class SoyDefineIsSsrFunctionTest {

  @Test
  public void testEval() {
    SoyDefineIsSsrFunction fn = new SoyDefineIsSsrFunction();
    assertThat(fn.eval(ImmutableList.of(), SoyCompilerContext.create(OutputTarget.JBCSRC)))
        .isEqualTo(BooleanData.TRUE);
    assertThat(fn.eval(ImmutableList.of(), SoyCompilerContext.create(OutputTarget.TOFU)))
        .isEqualTo(BooleanData.TRUE);
    assertThat(fn.eval(ImmutableList.of(), SoyCompilerContext.create(OutputTarget.JSSRC)))
        .isEqualTo(BooleanData.FALSE);
    assertThat(fn.eval(ImmutableList.of(), SoyCompilerContext.create(OutputTarget.IDOM)))
        .isEqualTo(BooleanData.FALSE);
    assertThat(fn.eval(ImmutableList.of(), SoyCompilerContext.create(OutputTarget.PYSRC)))
        .isEqualTo(BooleanData.FALSE);
    assertThat(fn.eval(ImmutableList.of(), SoyCompilerContext.create(OutputTarget.NONE)))
        .isEqualTo(BooleanData.FALSE);
  }
}
