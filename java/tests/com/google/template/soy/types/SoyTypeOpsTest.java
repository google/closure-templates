/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.types;

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.SanitizedType.HtmlType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SoyTypeOps.
 *
 */
@RunWith(JUnit4.class)
public class SoyTypeOpsTest {
  public final SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
  public final SoyTypeOps typeOps = new SoyTypeOps(typeRegistry);

  @Test
  public void testLeastCommonType() {
    assertThat(typeOps.computeLowestCommonType(IntType.getInstance(), AnyType.getInstance()))
        .isEqualTo(AnyType.getInstance());
    assertThat(typeOps.computeLowestCommonType(IntType.getInstance(), UnknownType.getInstance()))
        .isEqualTo(UnknownType.getInstance());
    assertThat(typeOps.computeLowestCommonType(UnknownType.getInstance(), IntType.getInstance()))
        .isEqualTo(UnknownType.getInstance());
    assertThat(typeOps.computeLowestCommonType(AnyType.getInstance(), IntType.getInstance()))
        .isEqualTo(AnyType.getInstance());
    assertThat(typeOps.computeLowestCommonType(StringType.getInstance(), HtmlType.getInstance()))
        .isEqualTo(StringType.getInstance());
    assertThat(typeOps.computeLowestCommonType(HtmlType.getInstance(), StringType.getInstance()))
        .isEqualTo(StringType.getInstance());
    assertThat(typeOps.computeLowestCommonType(IntType.getInstance(), FloatType.getInstance()))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
    assertThat(typeOps.computeLowestCommonType(FloatType.getInstance(), IntType.getInstance()))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
  }

  @Test
  public void testLeastCommonTypeArithmetic() {
    SoyType intT = IntType.getInstance();
    SoyType anyT = AnyType.getInstance();
    SoyType unknownT = UnknownType.getInstance();
    SoyType floatT = FloatType.getInstance();
    SoyType stringT = StringType.getInstance();
    SoyType htmlT = HtmlType.getInstance();
    SoyType numberT = SoyTypes.NUMBER_TYPE;

    assertThat(typeOps.computeLowestCommonTypeArithmetic(intT, anyT)).isAbsent();
    assertThat(typeOps.computeLowestCommonTypeArithmetic(anyT, intT)).isAbsent();
    assertThat(typeOps.computeLowestCommonTypeArithmetic(stringT, htmlT)).isAbsent();
    assertThat(typeOps.computeLowestCommonTypeArithmetic(htmlT, stringT)).isAbsent();
    assertThat(typeOps.computeLowestCommonTypeArithmetic(intT, floatT)).hasValue(floatT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(floatT, intT)).hasValue(floatT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(floatT, unknownT)).hasValue(unknownT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(unknownT, floatT)).hasValue(unknownT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(intT, intT)).hasValue(intT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(floatT, floatT)).hasValue(floatT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(floatT, numberT)).hasValue(numberT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(intT, numberT)).hasValue(numberT);
    assertThat(typeOps.computeLowestCommonTypeArithmetic(numberT, numberT)).hasValue(numberT);
  }
}
