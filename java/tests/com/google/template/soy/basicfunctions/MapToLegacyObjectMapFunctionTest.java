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
import static com.google.template.soy.jbcsrc.restricted.testing.ExpressionTester.assertThatExpression;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.SoyMapImpl;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.types.primitive.UnknownType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link MapToLegacyObjectMapFunction}. */
@RunWith(JUnit4.class)
public final class MapToLegacyObjectMapFunctionTest {

  private static final MapToLegacyObjectMapFunction MAP_TO_LEGACY_OBJECT_MAP =
      new MapToLegacyObjectMapFunction();
  private static final SoyValueConverter CONVERTER = SoyValueConverter.UNCUSTOMIZED_INSTANCE;

  @Test
  public void computeForJava() {
    SoyMapImpl map =
        SoyMapImpl.forProviderMap(
            ImmutableSortedMap.of(
                "x", CONVERTER.convert("y"), "z", SoyValueConverterUtility.newDict("xx", 2)));
    SoyDict expectedMap =
        SoyValueConverterUtility.newDict("x", "y", "z", SoyValueConverterUtility.newDict("xx", 2));
    SoyDict convertedMap = (SoyDict) MAP_TO_LEGACY_OBJECT_MAP.computeForJava(ImmutableList.of(map));
    // maps use instance equality to match Javascript behavior
    assertThat(map.getItem(StringData.forValue("x")))
        .isEqualTo(convertedMap.getItem(StringData.forValue("x")));
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
    assertThat(legacyObjectMap).isEqualTo(map); // TODO(b/69064788): fix
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
                                    BytecodeUtils.constant("a"), BytecodeUtils.constant("b")),
                                ImmutableList.of(
                                    FieldRef.NULL_PROVIDER.accessor(),
                                    FieldRef.NULL_PROVIDER.accessor())))))))
        .evaluatesToInstanceOf(DictImpl.class);
  }
}
