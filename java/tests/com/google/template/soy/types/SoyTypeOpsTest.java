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

import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.SanitizedType.HtmlType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import junit.framework.TestCase;

/**
 * Unit tests for SoyTypeOps.
 *
 */
public class SoyTypeOpsTest extends TestCase {
  public final SoyTypeRegistry typeRegistry = new SoyTypeRegistry();
  public final SoyTypeOps typeOps = new SoyTypeOps(typeRegistry);

  public void testLeastCommonType() {
    assertEquals(AnyType.getInstance(),
        typeOps.computeLeastCommonType(IntType.getInstance(), AnyType.getInstance()));
    assertEquals(UnknownType.getInstance(),
        typeOps.computeLeastCommonType(IntType.getInstance(), UnknownType.getInstance()));
    assertEquals(UnknownType.getInstance(),
        typeOps.computeLeastCommonType(UnknownType.getInstance(), IntType.getInstance()));
    assertEquals(AnyType.getInstance(),
        typeOps.computeLeastCommonType(AnyType.getInstance(), IntType.getInstance()));
    assertEquals(StringType.getInstance(),
        typeOps.computeLeastCommonType(StringType.getInstance(), HtmlType.getInstance()));
    assertEquals(StringType.getInstance(),
        typeOps.computeLeastCommonType(HtmlType.getInstance(), StringType.getInstance()));
    assertEquals(UnionType.of(IntType.getInstance(), FloatType.getInstance()),
        typeOps.computeLeastCommonType(IntType.getInstance(), FloatType.getInstance()));
    assertEquals(UnionType.of(IntType.getInstance(), FloatType.getInstance()),
        typeOps.computeLeastCommonType(FloatType.getInstance(), IntType.getInstance()));
  }

  public void testLeastCommonTypeArithmetic() {
    assertEquals(AnyType.getInstance(),
        typeOps.computeLeastCommonTypeArithmetic(IntType.getInstance(), AnyType.getInstance()));
    assertEquals(AnyType.getInstance(),
        typeOps.computeLeastCommonTypeArithmetic(AnyType.getInstance(), IntType.getInstance()));
    assertEquals(StringType.getInstance(),
        typeOps.computeLeastCommonTypeArithmetic(StringType.getInstance(), HtmlType.getInstance()));
    assertEquals(StringType.getInstance(),
        typeOps.computeLeastCommonTypeArithmetic(HtmlType.getInstance(), StringType.getInstance()));
    assertEquals(FloatType.getInstance(),
        typeOps.computeLeastCommonTypeArithmetic(IntType.getInstance(), FloatType.getInstance()));
    assertEquals(FloatType.getInstance(),
        typeOps.computeLeastCommonTypeArithmetic(FloatType.getInstance(), IntType.getInstance()));
  }
}
