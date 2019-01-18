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

package com.google.template.soy.pysrc.internal;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.plugin.python.restricted.SoyPythonSourceFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PythonValueFactoryImplTest {

  @Test
  public void testConstant() {
    PythonValueFactoryImpl factory = createFactory();
    assertThat(factory.constant(1).toString()).isEqualTo("1");
    assertThat(factory.constant(1.1).toString()).isEqualTo("1.1");
    assertThat(factory.constant(false).toString()).isEqualTo("False");
    assertThat(factory.constant("false").toString()).isEqualTo("'false'");
    assertThat(factory.constantNull().toString()).isEqualTo("None");
  }

  @Test
  public void testCall() {
    PythonValueFactoryImpl factory = createFactory();
    assertThat(
            factory
                .global("Math")
                .getProp("pow")
                .call(factory.constant(1), factory.constant(2))
                .toString())
        .isEqualTo("Math.pow(1, 2)");
  }

  static PyExpr applyFunction(SoyPythonSourceFunction fn, PyExpr... args) {
    return createFactory()
        .applyFunction(SourceLocation.UNKNOWN, "foo", fn, ImmutableList.<PyExpr>copyOf(args));
  }

  static PythonValueFactoryImpl createFactory() {
    return new PythonValueFactoryImpl(ErrorReporter.exploding(), BidiGlobalDir.LTR);
  }
}
