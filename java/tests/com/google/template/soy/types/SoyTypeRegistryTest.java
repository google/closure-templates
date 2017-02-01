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

import com.google.common.collect.ImmutableMap;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for SoyTypeRegistry. */
@RunWith(JUnit4.class)
public class SoyTypeRegistryTest {

  private SoyTypeRegistry typeRegistry;

  @Before
  public void setUp() {
    typeRegistry = new SoyTypeRegistry();
  }

  @Test
  public void testPrimitiveTypes() {
    assertThat(typeRegistry.getType("any").getKind()).isEqualTo(SoyType.Kind.ANY);
    assertThat(typeRegistry.getType("null").getKind()).isEqualTo(SoyType.Kind.NULL);
    assertThat(typeRegistry.getType("bool").getKind()).isEqualTo(SoyType.Kind.BOOL);
    assertThat(typeRegistry.getType("int").getKind()).isEqualTo(SoyType.Kind.INT);
    assertThat(typeRegistry.getType("float").getKind()).isEqualTo(SoyType.Kind.FLOAT);
    assertThat(typeRegistry.getType("string").getKind()).isEqualTo(SoyType.Kind.STRING);

    // Check that 'number' type is assignable from both float and int.
    assertThat(typeRegistry.getType("number").isAssignableFrom(IntType.getInstance())).isTrue();
    assertThat(typeRegistry.getType("number").isAssignableFrom(FloatType.getInstance())).isTrue();
  }

  @Test
  public void testCreateListType() {
    ListType listOfInt = typeRegistry.getOrCreateListType(IntType.getInstance());
    ListType listOfInt2 = typeRegistry.getOrCreateListType(IntType.getInstance());
    ListType listOfFloat = typeRegistry.getOrCreateListType(FloatType.getInstance());

    assertThat(listOfInt2).isSameAs(listOfInt);
    assertThat(listOfFloat).isNotSameAs(listOfInt);
  }

  @Test
  public void testCreateMapType() {
    MapType mapOfIntToString =
        typeRegistry.getOrCreateMapType(IntType.getInstance(), StringType.getInstance());
    MapType mapOfIntToString2 =
        typeRegistry.getOrCreateMapType(IntType.getInstance(), StringType.getInstance());
    MapType mapOfIntToInt =
        typeRegistry.getOrCreateMapType(IntType.getInstance(), IntType.getInstance());
    MapType mapOfStringToString =
        typeRegistry.getOrCreateMapType(StringType.getInstance(), StringType.getInstance());

    assertThat(mapOfIntToString2).isSameAs(mapOfIntToString);
    assertThat(mapOfIntToInt).isNotSameAs(mapOfIntToString);
    assertThat(mapOfStringToString).isNotSameAs(mapOfIntToString);
  }

  @Test
  public void testCreateUnionType() {
    SoyType u1 = typeRegistry.getOrCreateUnionType(IntType.getInstance(), FloatType.getInstance());
    SoyType u2 = typeRegistry.getOrCreateUnionType(IntType.getInstance(), FloatType.getInstance());
    SoyType u3 = typeRegistry.getOrCreateUnionType(IntType.getInstance(), StringType.getInstance());

    assertThat(u2).isSameAs(u1);
    assertThat(u3).isNotSameAs(u1);
  }

  @Test
  public void testCreateRecordType() {
    RecordType r1 =
        typeRegistry.getOrCreateRecordType(
            ImmutableMap.<String, SoyType>of(
                "a", IntType.getInstance(), "b", FloatType.getInstance()));
    RecordType r2 =
        typeRegistry.getOrCreateRecordType(
            ImmutableMap.<String, SoyType>of(
                "a", IntType.getInstance(), "b", FloatType.getInstance()));
    RecordType r3 =
        typeRegistry.getOrCreateRecordType(
            ImmutableMap.<String, SoyType>of(
                "a", IntType.getInstance(), "b", StringType.getInstance()));
    RecordType r4 =
        typeRegistry.getOrCreateRecordType(
            ImmutableMap.<String, SoyType>of(
                "a", IntType.getInstance(), "c", FloatType.getInstance()));

    assertThat(r2).isSameAs(r1);
    assertThat(r3).isNotSameAs(r1);
    assertThat(r4).isNotSameAs(r1);
  }
}
