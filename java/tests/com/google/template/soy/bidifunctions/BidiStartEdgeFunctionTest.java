/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.bidifunctions;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.plugin.java.restricted.testing.SoyJavaSourceFunctionTester;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.shared.SharedRestrictedTestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiStartEdgeFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiStartEdgeFunctionTest {

  @Test
  public void testComputeForJava() {
    // the java source version doesn't use the provider
    BidiStartEdgeFunction fn =
        new BidiStartEdgeFunction(
            () -> {
              throw new UnsupportedOperationException();
            });

    SoyJavaSourceFunctionTester tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.LTR).build();
    assertThat(tester.callFunction()).isEqualTo("left");

    tester =
        new SoyJavaSourceFunctionTester.Builder(fn).withBidiGlobalDir(BidiGlobalDir.RTL).build();
    assertThat(tester.callFunction()).isEqualTo("right");
  }

  @Test
  public void testComputeForJsSrc() {
    BidiStartEdgeFunction ltr = new BidiStartEdgeFunction(Suppliers.ofInstance(BidiGlobalDir.LTR));
    BidiStartEdgeFunction rtl = new BidiStartEdgeFunction(Suppliers.ofInstance(BidiGlobalDir.RTL));
    assertThat(ltr.computeForJsSrc(ImmutableList.<JsExpr>of()))
        .isEqualTo(new JsExpr("'left'", Integer.MAX_VALUE));
    assertThat(rtl.computeForJsSrc(ImmutableList.<JsExpr>of()))
        .isEqualTo(new JsExpr("'right'", Integer.MAX_VALUE));

    BidiStartEdgeFunction codeSnippet =
        new BidiStartEdgeFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET_SUPPLIER);
    assertThat(codeSnippet.computeForJsSrc(ImmutableList.<JsExpr>of()))
        .isEqualTo(
            new JsExpr(
                "(IS_RTL?-1:1) < 0 ? 'right' : 'left'", Operator.CONDITIONAL.getPrecedence()));
  }

  @Test
  public void testComputeForPySrc() {
    BidiStartEdgeFunction codeSnippet =
        new BidiStartEdgeFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_SUPPLIER);

    assertThat(codeSnippet.computeForPySrc(ImmutableList.<PyExpr>of()))
        .isEqualTo(
            new PyExpr(
                "'right' if (-1 if IS_RTL else 1) < 0 else 'left'",
                PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }
}
