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

import com.google.common.collect.ImmutableList;
import com.google.inject.util.Providers;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyExprUtils;
import com.google.template.soy.shared.SharedRestrictedTestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for BidiMarkFunction.
 *
 */
@RunWith(JUnit4.class)
public class BidiMarkFunctionTest {

  private static final BidiMarkFunction BIDI_MARK_FUNCTION_FOR_STATIC_LTR =
      new BidiMarkFunction(Providers.of(BidiGlobalDir.LTR));

  private static final BidiMarkFunction BIDI_MARK_FUNCTION_FOR_STATIC_RTL =
      new BidiMarkFunction(Providers.of(BidiGlobalDir.RTL));

  @Test
  public void testComputeForJava() {
    assertThat(BIDI_MARK_FUNCTION_FOR_STATIC_LTR.computeForJava(ImmutableList.<SoyValue>of()))
        .isEqualTo(StringData.forValue("\u200E"));
    assertThat(BIDI_MARK_FUNCTION_FOR_STATIC_RTL.computeForJava(ImmutableList.<SoyValue>of()))
        .isEqualTo(StringData.forValue("\u200F"));
  }

  @Test
  public void testComputeForJsSrc() {
    assertThat(BIDI_MARK_FUNCTION_FOR_STATIC_LTR.computeForJsSrc(ImmutableList.<JsExpr>of()))
        .isEqualTo(new JsExpr("'\\u200E'", Integer.MAX_VALUE));
    assertThat(BIDI_MARK_FUNCTION_FOR_STATIC_RTL.computeForJsSrc(ImmutableList.<JsExpr>of()))
        .isEqualTo(new JsExpr("'\\u200F'", Integer.MAX_VALUE));

    BidiMarkFunction codeSnippet =
        new BidiMarkFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_JS_ISRTL_CODE_SNIPPET_PROVIDER);
    assertThat(codeSnippet.computeForJsSrc(ImmutableList.<JsExpr>of()))
        .isEqualTo(
            new JsExpr(
                "(IS_RTL?-1:1) < 0 ? '\\u200F' : '\\u200E'", Operator.CONDITIONAL.getPrecedence()));
  }

  @Test
  public void testComputeForPySrc() {
    BidiMarkFunction codeSnippet =
        new BidiMarkFunction(
            SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_PY_ISRTL_CODE_SNIPPET_PROVIDER);

    assertThat(codeSnippet.computeForPySrc(ImmutableList.<PyExpr>of()))
        .isEqualTo(
            new PyExpr(
                "'\\u200F' if (-1 if IS_RTL else 1) < 0 else '\\u200E'",
                PyExprUtils.pyPrecedenceForOperator(Operator.CONDITIONAL)));
  }
}
