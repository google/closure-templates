/*
 * Copyright 2017 Google Inc.
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
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionTester.assertThatExpression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.types.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link MapToLegacyObjectMapFunction}. */
@RunWith(JUnit4.class)
public final class MapToLegacyObjectMapFunctionTest {

  private static final MapToLegacyObjectMapFunction MAP_TO_LEGACY_OBJECT_MAP =
      new MapToLegacyObjectMapFunction();
  private static final SoyValueConverter CONVERTER = SoyValueConverter.INSTANCE;

  @Test
  public void computeForJava() {
    SoyMapImpl map =
        SoyMapImpl.forProviderMap(
            ImmutableMap.of(
                IntegerData.forValue(42), CONVERTER.convert("y"),
                StringData.forValue("z"), SoyValueConverterUtility.newDict("xx", 2)));
    SoyDict expectedMap =
        SoyValueConverterUtility.newDict("42", "y", "z", SoyValueConverterUtility.newDict("xx", 2));
    SoyDict convertedMap = (SoyDict) MAP_TO_LEGACY_OBJECT_MAP.computeForJava(ImmutableList.of(map));
    // Keys are coerced to strings in the legacy object map.
    assertThat(expectedMap.getItem(StringData.forValue("42")))
        .isEqualTo(convertedMap.getItem(StringData.forValue("42")));
  }

  @Test
  public void computeForJsSrc() {
    JsExpr map = new JsExpr("map", Integer.MAX_VALUE);
    JsExpr legacyObjectMap = MAP_TO_LEGACY_OBJECT_MAP.computeForJsSrc(ImmutableList.of(map));
    assertThat(legacyObjectMap.getText()).isEqualTo("soy.map.$$mapToLegacyObjectMap(map)");
  }

  @Test
  public void computeForPySrc() {
    PyExpr map = new PyExpr("map", Integer.MAX_VALUE);
    PyExpr legacyObjectMap = MAP_TO_LEGACY_OBJECT_MAP.computeForPySrc(ImmutableList.of(map));
    assertThat(legacyObjectMap.getText()).isEqualTo("runtime.map_to_legacy_object_map(map)");
  }

  @Test
  public void computeForJbcSrc() {
    assertThatExpression(
            MAP_TO_LEGACY_OBJECT_MAP.computeForJbcSrc(
                null /* context */,
                ImmutableList.of(
                    SoyExpression.forSoyValue(
                        UnknownType.getInstance(),
                        MethodRef.MAP_IMPL_FOR_PROVIDER_MAP.invoke(
                            BytecodeUtils.newLinkedHashMap(
                                ImmutableList.of(
                                    SoyExpression.forString(constant("a")).box(),
                                    SoyExpression.forInt(constant(101L)).box()),
                                ImmutableList.of(
                                    FieldRef.NULL_PROVIDER.accessor(),
                                    FieldRef.NULL_PROVIDER.accessor())))))))
        .evaluatesToInstanceOf(DictImpl.class);
  }
}
