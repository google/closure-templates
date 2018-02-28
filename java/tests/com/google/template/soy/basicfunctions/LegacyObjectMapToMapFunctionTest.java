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
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyLegacyObjectMap;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.internal.DictImpl.RuntimeType;
import com.google.template.soy.data.internal.SoyMapImpl;
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

/** Tests for {@link LegacyObjectMapToMapFunction}. */
@RunWith(JUnit4.class)
public final class LegacyObjectMapToMapFunctionTest {

  private static final LegacyObjectMapToMapFunction LEGACY_OBJECT_MAP_TO_MAP =
      new LegacyObjectMapToMapFunction();
  private static final SoyValueConverter CONVERTER = SoyValueConverter.INSTANCE;

  @Test
  public void computeForJava() {
    SoyLegacyObjectMap legacyObjectMap =
        SoyValueConverterUtility.newDict("x", "y", "z", SoyValueConverterUtility.newDict("xx", 2));
    SoyMapImpl map =
        SoyMapImpl.forProviderMap(
            ImmutableMap.of(
                StringData.forValue("x"), CONVERTER.convert("y"),
                StringData.forValue("z"), SoyValueConverterUtility.newDict("xx", 2)));
    SoyMapImpl convertedMap =
        (SoyMapImpl) LEGACY_OBJECT_MAP_TO_MAP.computeForJava(ImmutableList.of(legacyObjectMap));
    assertThat(map.get(StringData.forValue("x")))
        .isEqualTo(convertedMap.get(StringData.forValue("x")));
  }

  @Test
  public void computeForJsSrc() {
    JsExpr legacyObjectMap = new JsExpr("legacyObjectMap", Integer.MAX_VALUE);
    JsExpr map = LEGACY_OBJECT_MAP_TO_MAP.computeForJsSrc(ImmutableList.of(legacyObjectMap));
    assertThat(map.getText()).isEqualTo("soy.newmaps.$$legacyObjectMapToMap(legacyObjectMap)");
  }

  @Test
  public void computeForPySrc() {
    PyExpr legacyObjectMap = new PyExpr("legacy_object_map", Integer.MAX_VALUE);
    PyExpr map = LEGACY_OBJECT_MAP_TO_MAP.computeForPySrc(ImmutableList.of(legacyObjectMap));
    assertThat(map).isEqualTo(legacyObjectMap); // TODO(b/69064788): fix
  }

  @Test
  public void computeForJbcSrc() {
    assertThatExpression(
            LEGACY_OBJECT_MAP_TO_MAP.computeForJbcSrc(
                null /* context */,
                ImmutableList.of(
                    SoyExpression.forSoyValue(
                        UnknownType.getInstance(),
                        MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
                            BytecodeUtils.newLinkedHashMap(
                                ImmutableList.of(
                                    BytecodeUtils.constant("a"), BytecodeUtils.constant("b")),
                                ImmutableList.of(
                                    FieldRef.NULL_PROVIDER.accessor(),
                                    FieldRef.NULL_PROVIDER.accessor())),
                            FieldRef.enumReference(RuntimeType.LEGACY_OBJECT_MAP_OR_RECORD)
                                .accessor())))))
        .evaluatesToInstanceOf(SoyMapImpl.class);
  }
}
