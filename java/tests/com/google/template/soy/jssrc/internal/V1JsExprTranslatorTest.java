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

import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;

import junit.framework.TestCase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Unit tests for V1JsExprTranslator.
 *
 */
public class V1JsExprTranslatorTest extends TestCase {


  private static final Deque<Map<String, JsExpr>> LOCAL_VAR_TRANSLATIONS =
      new ArrayDeque<Map<String, JsExpr>>();
  static {
    Map<String, JsExpr> frame = Maps.newHashMap();
    // Let 'goo' simulate a local variable from a 'foreach' loop.
    frame.put("goo", new JsExpr("gooData8", Integer.MAX_VALUE));
    frame.put("goo__isFirst", new JsExpr("gooIndex8 == 0", Operator.EQUAL.getPrecedence()));
    frame.put("goo__isLast", new JsExpr("gooIndex8 == gooListLen8 - 1",
                                        Operator.EQUAL.getPrecedence()));
    frame.put("goo__index", new JsExpr("gooIndex8", Integer.MAX_VALUE));
    LOCAL_VAR_TRANSLATIONS.push(frame);
  }


  public void testDataRef() throws Exception {

    runTestHelper("$boo",
                  new JsExpr("opt_data.boo", Integer.MAX_VALUE));
    runTestHelper("$boo.goo",
                  new JsExpr("opt_data.boo.goo", Integer.MAX_VALUE));
    runTestHelper("$goo",
                  new JsExpr("gooData8", Integer.MAX_VALUE));
    runTestHelper("$goo.boo",
                  new JsExpr("gooData8.boo", Integer.MAX_VALUE));
    runTestHelper("$boo.0.1.foo.2",
                  new JsExpr("opt_data.boo[0][1].foo[2]", Integer.MAX_VALUE));
    runTestHelper("$boo[$foo][$goo+1]",
                  new JsExpr("opt_data.boo[opt_data.foo][gooData8+1]", Integer.MAX_VALUE),
                  true /* lenient */);
  }


  public void testOperators() throws Exception {

    runTestHelper("not $boo or true and $goo",
                  new JsExpr("! opt_data.boo || true && gooData8", Operator.OR.getPrecedence()));
    runTestHelper("( (8-4) + (2-1) )",
                  new JsExpr("( (8-4) + (2-1) )", Operator.PLUS.getPrecedence()));
  }


  public void testFunctions() throws Exception {

    runTestHelper("isFirst($goo)",
                  new JsExpr("(gooIndex8 == 0)", Operator.EQUAL.getPrecedence()));
    runTestHelper("not isLast($goo)",
                  new JsExpr("! (gooIndex8 == gooListLen8 - 1)", Operator.NOT.getPrecedence()),
                  true /* lenient */);
    runTestHelper("index($goo) + 1",
                  new JsExpr("gooIndex8 + 1", Operator.PLUS.getPrecedence()));
    runTestHelper("$boo.length",
                  new JsExpr("opt_data.boo.length", Integer.MAX_VALUE));
  }


  public void runTestHelper(String soyExpr, JsExpr expectedJsExpr) throws Exception {
    runTestHelper(soyExpr, expectedJsExpr, false);
  }


  public void runTestHelper(String soyExpr, JsExpr expectedJsExpr, boolean shouldBeLenient)
      throws Exception {

    JsExpr actualJsExpr = V1JsExprTranslator.translateToJsExpr(
        soyExpr, SourceLocation.UNKNOWN, LOCAL_VAR_TRANSLATIONS, ExplodingErrorReporter.get());
    assertThat(actualJsExpr.getText()).isEqualTo(expectedJsExpr.getText());
    if (shouldBeLenient) {
      assertThat(actualJsExpr.getPrecedence() < expectedJsExpr.getPrecedence()).isTrue();
    } else {
      assertThat(actualJsExpr.getPrecedence()).isEqualTo(expectedJsExpr.getPrecedence());
    }
  }

}
