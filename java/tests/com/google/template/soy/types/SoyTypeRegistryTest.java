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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;

import junit.framework.TestCase;

/**
 * Unit tests for SoyTypeRegistry.
 */
public class SoyTypeRegistryTest extends TestCase {


  private SoyTypeRegistry typeRegistry;

  @Override protected void setUp() {
    typeRegistry = new SoyTypeRegistry();
  }

  public void testPrimitiveTypes() {
    assertEquals(SoyType.Kind.ANY, typeRegistry.getType("any").getKind());
    assertEquals(SoyType.Kind.NULL, typeRegistry.getType("null").getKind());
    assertEquals(SoyType.Kind.BOOL, typeRegistry.getType("bool").getKind());
    assertEquals(SoyType.Kind.INT, typeRegistry.getType("int").getKind());
    assertEquals(SoyType.Kind.FLOAT, typeRegistry.getType("float").getKind());
    assertEquals(SoyType.Kind.STRING, typeRegistry.getType("string").getKind());

    // Check that 'number' type is assignable from both float and int.
    assertTrue(typeRegistry.getType("number").isAssignableFrom(IntType.getInstance()));
    assertTrue(typeRegistry.getType("number").isAssignableFrom(FloatType.getInstance()));
  }

  public void testCreateListType() {
    ListType listOfInt = typeRegistry.getOrCreateListType(IntType.getInstance());
    ListType listOfInt2 = typeRegistry.getOrCreateListType(IntType.getInstance());
    ListType listOfFloat = typeRegistry.getOrCreateListType(FloatType.getInstance());

    assertSame(listOfInt, listOfInt2);
    assertNotSame(listOfInt, listOfFloat);
  }

  public void testCreateMapType() {
    MapType mapOfIntToString = typeRegistry.getOrCreateMapType(
        IntType.getInstance(), StringType.getInstance());
    MapType mapOfIntToString2 = typeRegistry.getOrCreateMapType(
        IntType.getInstance(), StringType.getInstance());
    MapType mapOfIntToInt = typeRegistry.getOrCreateMapType(
        IntType.getInstance(), IntType.getInstance());
    MapType mapOfStringToString = typeRegistry.getOrCreateMapType(
        StringType.getInstance(), StringType.getInstance());

    assertSame(mapOfIntToString, mapOfIntToString2);
    assertNotSame(mapOfIntToString, mapOfIntToInt);
    assertNotSame(mapOfIntToString, mapOfStringToString);
  }

  public void testCreateUnionType() {
    UnionType u1 = typeRegistry.getOrCreateUnionType(
        IntType.getInstance(),
        FloatType.getInstance());
    UnionType u2 = typeRegistry.getOrCreateUnionType(
        IntType.getInstance(),
        FloatType.getInstance());
    UnionType u3 = typeRegistry.getOrCreateUnionType(
        IntType.getInstance(),
        StringType.getInstance());

    assertSame(u1, u2);
    assertNotSame(u1, u3);
  }

  public void testCreateRecordType() {
    RecordType r1 = typeRegistry.getOrCreateRecordType(
        ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(), "b", FloatType.getInstance()));
    RecordType r2 = typeRegistry.getOrCreateRecordType(
        ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(), "b", FloatType.getInstance()));
    RecordType r3 = typeRegistry.getOrCreateRecordType(
        ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(), "b", StringType.getInstance()));
    RecordType r4 = typeRegistry.getOrCreateRecordType(
        ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(), "c", FloatType.getInstance()));

    assertSame(r1, r2);
    assertNotSame(r1, r3);
    assertNotSame(r1, r4);
  }
}
