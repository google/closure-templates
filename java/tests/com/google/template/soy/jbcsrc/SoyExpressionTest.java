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

package com.google.template.soy.jbcsrc;

import static com.google.template.soy.jbcsrc.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.ExpressionTester.assertThatExpression;
import static com.google.template.soy.jbcsrc.SoyExpression.forSanitizedString;

import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;

import junit.framework.TestCase;

/**
 * Tests for {@link SoyExpression}
 */
public class SoyExpressionTest extends TestCase {

  public void testIntExpressions() {
    SoyExpression expr = SoyExpression.forInt(constant(12L));
    assertThatExpression(expr).evaluatesTo(12L);
    assertThatExpression(expr.box()).evaluatesTo(IntegerData.forValue(12));
    assertThatExpression(expr.box().convert(long.class)).evaluatesTo(12L);

    assertThatExpression(expr.convert(boolean.class)).evaluatesTo(true);
    assertThatExpression(SoyExpression.forInt(constant(0L)).convert(boolean.class))
        .evaluatesTo(false);
    assertThatExpression(expr.convert(String.class)).evaluatesTo("12");
  }

  public void testFloatExpressions() {
    SoyExpression expr = SoyExpression.forFloat(constant(12.34D));
    assertThatExpression(expr).evaluatesTo(12.34D);
    assertThatExpression(expr.box()).evaluatesTo(FloatData.forValue(12.34D));
    assertThatExpression(expr.box().convert(double.class)).evaluatesTo(12.34D);

    assertThatExpression(expr.convert(boolean.class)).evaluatesTo(true);
    assertThatExpression(SoyExpression.forFloat(constant(0D))
        .convert(boolean.class))
        .evaluatesTo(false);

    assertThatExpression(expr.convert(String.class)).evaluatesTo("12.34");
  }

  public void testBooleanExpressions() {
    SoyExpression expr = SoyExpression.FALSE;
    assertThatExpression(expr).evaluatesTo(false);  // sanity
    assertThatExpression(expr.box()).evaluatesTo(BooleanData.FALSE);
    assertThatExpression(expr.box().convert(boolean.class)).evaluatesTo(false);
    assertThatExpression(expr.convert(String.class)).evaluatesTo("false");

    expr = SoyExpression.TRUE;
    assertThatExpression(expr).evaluatesTo(true);
    assertThatExpression(expr.box()).evaluatesTo(BooleanData.TRUE);
    assertThatExpression(expr.box().convert(boolean.class)).evaluatesTo(true);
    assertThatExpression(expr.convert(String.class)).evaluatesTo("true");
  }

  public void testNullExpression() {
    assertThatExpression(SoyExpression.NULL).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box()).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.box().convert(Object.class)).evaluatesTo(null);
    assertThatExpression(SoyExpression.NULL.convert(boolean.class)).evaluatesTo(false);
    assertThatExpression(SoyExpression.NULL.convert(String.class)).evaluatesTo("null");
  }

  public void testSanitizedExpressions() {
    assertThatExpression(forSanitizedString(constant("foo"), ContentKind.ATTRIBUTES).box())
        .evaluatesTo(UnsafeSanitizedContentOrdainer.ordainAsSafe("foo", ContentKind.ATTRIBUTES));
    assertThatExpression(
        forSanitizedString(constant("foo"), ContentKind.ATTRIBUTES).convert(boolean.class))
            .evaluatesTo(true);
  }
}
