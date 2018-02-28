/*
 * Copyright 2011 Google Inc.
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
import com.google.common.collect.Sets;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverterUtility;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.DictImpl.RuntimeType;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.types.UnknownType;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for KeysFunction.
 *
 */
@RunWith(JUnit4.class)
public class KeysFunctionTest {

  @Test
  public void testComputeForJava() {
    KeysFunction keysFunction = new KeysFunction();

    SoyValue map =
        SoyValueConverterUtility.newDict(
            "boo", "bar", "foo", 2, "goo", SoyValueConverterUtility.newDict("moo", 4));
    SoyValue result = keysFunction.computeForJava(ImmutableList.of(map));

    assertThat(result).isInstanceOf(SoyList.class);
    SoyList resultAsList = (SoyList) result;
    assertThat(resultAsList.length()).isEqualTo(3);

    Set<String> resultItems = Sets.newHashSet();
    for (SoyValueProvider itemProvider : resultAsList.asJavaList()) {
      resultItems.add(itemProvider.resolve().stringValue());
    }
    assertThat(resultItems).containsExactly("boo", "foo", "goo");
  }

  @Test
  public void testComputeForJbcSrc() {
    KeysFunction keysFunction = new KeysFunction();
    // empty map becomes empty list
    assertThatExpression(
            keysFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(
                    SoyExpression.forSoyValue(
                        UnknownType.getInstance(),
                        MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
                            MethodRef.IMMUTABLE_MAP_OF.get(0).invoke(),
                            FieldRef.enumReference(RuntimeType.LEGACY_OBJECT_MAP_OR_RECORD)
                                .accessor())))))
        .evaluatesTo(ImmutableList.of());
    assertThatExpression(
            keysFunction.computeForJbcSrc(
                /*context=*/ null,
                ImmutableList.of(
                    SoyExpression.forSoyValue(
                        UnknownType.getInstance(),
                        MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(
                            BytecodeUtils.newLinkedHashMap(
                                ImmutableList.<Expression>of(
                                    BytecodeUtils.constant("a"), BytecodeUtils.constant("b")),
                                ImmutableList.<Expression>of(
                                    FieldRef.NULL_PROVIDER.accessor(),
                                    FieldRef.NULL_PROVIDER.accessor())),
                            FieldRef.enumReference(RuntimeType.LEGACY_OBJECT_MAP_OR_RECORD)
                                .accessor())))))
        .evaluatesTo(ImmutableList.of(StringData.forValue("a"), StringData.forValue("b")));
  }

  @Test
  public void testComputeForJsSrc() {
    KeysFunction keysFunction = new KeysFunction();
    JsExpr expr = new JsExpr("JS_CODE", Integer.MAX_VALUE);
    assertThat(keysFunction.computeForJsSrc(ImmutableList.of(expr)))
        .isEqualTo(new JsExpr("soy.$$getMapKeys(JS_CODE)", Integer.MAX_VALUE));
  }

  @Test
  public void testComputeForPySrc() {
    KeysFunction keysFunction = new KeysFunction();
    PyExpr dict = new PyExpr("dictionary", Integer.MAX_VALUE);
    assertThat(keysFunction.computeForPySrc(ImmutableList.of(dict)))
        .isEqualTo(new PyListExpr("(dictionary).keys()", Integer.MAX_VALUE));
  }
}
