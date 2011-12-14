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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.javasrc.restricted.JavaExpr;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.restricted.SharedRestrictedTestUtils;

import junit.framework.TestCase;


/**
 * Unit tests for BidiStartEdgeFunction.
 *
 */
public class BidiStartEdgeFunctionTest extends TestCase {


  private static final BidiStartEdgeFunction BIDI_START_EDGE_FUNCTION_FOR_STATIC_LTR =
      new BidiStartEdgeFunction(SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_STATIC_LTR_PROVIDER);

  private static final BidiStartEdgeFunction BIDI_START_EDGE_FUNCTION_FOR_STATIC_RTL =
      new BidiStartEdgeFunction(SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_STATIC_RTL_PROVIDER);

  private static final BidiStartEdgeFunction BIDI_START_EDGE_FUNCTION_FOR_ISRTL_CODE_SNIPPET =
      new BidiStartEdgeFunction(
          SharedRestrictedTestUtils.BIDI_GLOBAL_DIR_FOR_ISRTL_CODE_SNIPPET_PROVIDER);


  public void testComputeForTofu() {

    assertEquals(StringData.forValue("left"),
                 BIDI_START_EDGE_FUNCTION_FOR_STATIC_LTR.computeForTofu(
                     ImmutableList.<SoyData>of()));
    assertEquals(StringData.forValue("right"),
                 BIDI_START_EDGE_FUNCTION_FOR_STATIC_RTL.computeForTofu(
                     ImmutableList.<SoyData>of()));
  }


  public void testComputeForJsSrc() {

    assertEquals(
        new JsExpr("'left'", Integer.MAX_VALUE),
        BIDI_START_EDGE_FUNCTION_FOR_STATIC_LTR.computeForJsSrc(ImmutableList.<JsExpr>of()));
    assertEquals(
        new JsExpr("'right'", Integer.MAX_VALUE),
        BIDI_START_EDGE_FUNCTION_FOR_STATIC_RTL.computeForJsSrc(ImmutableList.<JsExpr>of()));
    assertEquals(
        new JsExpr("(IS_RTL?-1:1) < 0 ? 'right' : 'left'", Operator.CONDITIONAL.getPrecedence()),
        BIDI_START_EDGE_FUNCTION_FOR_ISRTL_CODE_SNIPPET.computeForJsSrc(
            ImmutableList.<JsExpr>of()));
  }


  public void testComputeForJavaSrc() {

    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.StringData.forValue(\"left\")",
            StringData.class, Integer.MAX_VALUE),
        BIDI_START_EDGE_FUNCTION_FOR_STATIC_LTR.computeForJavaSrc(ImmutableList.<JavaExpr>of()));
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.StringData.forValue(\"right\")",
            StringData.class, Integer.MAX_VALUE),
        BIDI_START_EDGE_FUNCTION_FOR_STATIC_RTL.computeForJavaSrc(ImmutableList.<JavaExpr>of()));
    assertEquals(
        new JavaExpr(
            "com.google.template.soy.data.restricted.StringData.forValue(" +
                "com.google.template.soy.internal.i18n.SoyBidiUtils.getBidiFormatter(" +
                "IS_RTL?-1:1).startEdge())",
            StringData.class, Integer.MAX_VALUE),
        BIDI_START_EDGE_FUNCTION_FOR_ISRTL_CODE_SNIPPET.computeForJavaSrc(
            ImmutableList.<JavaExpr>of()));
  }

}
