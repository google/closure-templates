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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.jssrc.dsl.Expression;
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
  private static final ImmutableMap<String, Expression> LOCAL_VAR_TRANSLATIONS =
      ImmutableMap.of("goo", id("gooData8"));

  @Test
  public void testDataRef() {
    runTestHelper("$boo", "opt_data.boo");
    runTestHelper("$boo.goo", "opt_data.boo.goo");
    runTestHelper("$goo", "gooData8");
    runTestHelper("$goo.boo", "gooData8.boo");
    // We used to have special support for turning .<Number> into bracket access, but such syntax is
    // no longer supported as this test demonstrates
    runTestHelper("$boo.0.1.foo.2", "opt_data.boo.0.1.foo.2");
    runTestHelper("$boo[$foo][$goo+1]", "opt_data.boo[opt_data.foo][gooData8+1]");
  }

  private static void runTestHelper(String soyExpr, String expectedJsExpr) {
    JsExpr actualJsExpr =
        V1JsExprTranslator.translateToJsExpr(
            soyExpr,
            SourceLocation.UNKNOWN,
            SoyToJsVariableMappings.startingWith(LOCAL_VAR_TRANSLATIONS),
            ErrorReporter.exploding());
    assertThat(actualJsExpr.getText()).isEqualTo("(" + expectedJsExpr + ")");
    assertThat(actualJsExpr.getPrecedence()).isEqualTo(Integer.MAX_VALUE);
  }
}
