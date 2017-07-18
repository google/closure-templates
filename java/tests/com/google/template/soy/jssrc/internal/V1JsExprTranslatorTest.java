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
import static com.google.template.soy.jssrc.dsl.CodeChunk.id;

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.restricted.JsExpr;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link V1JsExprTranslator}.
 *
 */
@RunWith(JUnit4.class)
public final class V1JsExprTranslatorTest {

  // Let 'goo' simulate a local variable from a 'foreach' loop.
  private static final ImmutableMap<String, CodeChunk.WithValue> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.<String, CodeChunk.WithValue>builder()
          .put(
              "goo",
              id("gooData8"))
          .build();

  @Test
  public void testDataRef() {
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

  @Test
  public void testOperators() {
    runTestHelper("not $boo or true and $goo",
                  new JsExpr("! opt_data.boo || true && gooData8", Operator.OR.getPrecedence()));
    runTestHelper("( (8-4) + (2-1) )",
                  new JsExpr("( (8-4) + (2-1) )", Operator.PLUS.getPrecedence()));
  }

  private static void runTestHelper(String soyExpr, JsExpr expectedJsExpr) {
    runTestHelper(soyExpr, expectedJsExpr, false);
  }

  private static void runTestHelper(
      String soyExpr, JsExpr expectedJsExpr, boolean shouldBeLenient) {
    JsExpr actualJsExpr =
        V1JsExprTranslator.translateToJsExpr(
            soyExpr,
            SourceLocation.UNKNOWN,
            SoyToJsVariableMappings.startingWith(LOCAL_VAR_TRANSLATIONS),
            ErrorReporter.exploding());
    assertThat(actualJsExpr.getText()).isEqualTo(expectedJsExpr.getText());
    if (shouldBeLenient) {
      assertThat(actualJsExpr.getPrecedence() < expectedJsExpr.getPrecedence()).isTrue();
    } else {
      assertThat(actualJsExpr.getPrecedence()).isEqualTo(expectedJsExpr.getPrecedence());
    }
  }
}
