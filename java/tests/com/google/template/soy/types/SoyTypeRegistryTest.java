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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.error.SoyInternalCompilerException;
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
    typeRegistry = SoyTypeRegistryBuilder.create();
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
    assertThat(typeRegistry.getType("number").isAssignableFromStrict(IntType.getInstance()))
        .isTrue();
    assertThat(typeRegistry.getType("number").isAssignableFromStrict(FloatType.getInstance()))
        .isTrue();
  }

  @Test
  public void testCreateListType() {
    ListType listOfInt = typeRegistry.getOrCreateListType(IntType.getInstance());
    ListType listOfInt2 = typeRegistry.getOrCreateListType(IntType.getInstance());
    ListType listOfFloat = typeRegistry.getOrCreateListType(FloatType.getInstance());

    assertThat(listOfInt2).isSameInstanceAs(listOfInt);
    assertThat(listOfFloat).isNotSameInstanceAs(listOfInt);
  }

  @Test
  public void testCreateLegacyObjectMapType() {
    LegacyObjectMapType mapOfIntToString =
        typeRegistry.getOrCreateLegacyObjectMapType(
            IntType.getInstance(), StringType.getInstance());
    LegacyObjectMapType mapOfIntToString2 =
        typeRegistry.getOrCreateLegacyObjectMapType(
            IntType.getInstance(), StringType.getInstance());
    LegacyObjectMapType mapOfIntToInt =
        typeRegistry.getOrCreateLegacyObjectMapType(IntType.getInstance(), IntType.getInstance());
    LegacyObjectMapType mapOfStringToString =
        typeRegistry.getOrCreateLegacyObjectMapType(
            StringType.getInstance(), StringType.getInstance());

    assertThat(mapOfIntToString2).isSameInstanceAs(mapOfIntToString);
    assertThat(mapOfIntToInt).isNotSameInstanceAs(mapOfIntToString);
    assertThat(mapOfStringToString).isNotSameInstanceAs(mapOfIntToString);
  }

  @Test
  public void testCreateUnionType() {
    SoyType u1 = typeRegistry.getOrCreateUnionType(IntType.getInstance(), FloatType.getInstance());
    SoyType u2 = typeRegistry.getOrCreateUnionType(IntType.getInstance(), FloatType.getInstance());
    SoyType u3 = typeRegistry.getOrCreateUnionType(IntType.getInstance(), StringType.getInstance());

    assertThat(u2).isSameInstanceAs(u1);
    assertThat(u3).isNotSameInstanceAs(u1);
  }

  @Test
  public void testCreateRecordType() {
    RecordType r1 =
        typeRegistry.getOrCreateRecordType(
            ImmutableList.of(
                RecordType.memberOf("a", IntType.getInstance()),
                RecordType.memberOf("b", FloatType.getInstance())));
    RecordType r2 =
        typeRegistry.getOrCreateRecordType(
            ImmutableList.of(
                RecordType.memberOf("a", IntType.getInstance()),
                RecordType.memberOf("b", FloatType.getInstance())));
    RecordType r3 =
        typeRegistry.getOrCreateRecordType(
            ImmutableList.of(
                RecordType.memberOf("a", IntType.getInstance()),
                RecordType.memberOf("b", StringType.getInstance())));
    RecordType r4 =
        typeRegistry.getOrCreateRecordType(
            ImmutableList.of(
                RecordType.memberOf("a", IntType.getInstance()),
                RecordType.memberOf("c", FloatType.getInstance())));

    assertThat(r2).isSameInstanceAs(r1);
    assertThat(r3).isNotSameInstanceAs(r1);
    assertThat(r4).isNotSameInstanceAs(r1);
  }

  @Test
  public void testNumberType() {
    // Make sure the type registry knows about the special number type
    assertThat(SoyTypes.NUMBER_TYPE)
        .isSameInstanceAs(
            typeRegistry.getOrCreateUnionType(FloatType.getInstance(), IntType.getInstance()));
    assertThat(SoyTypes.NUMBER_TYPE)
        .isSameInstanceAs(typeRegistry.getOrCreateUnionType(SoyTypes.NUMBER_TYPE));
  }

  @Test
  public void testProtoFqnCollision() {
    SoyTypeRegistryBuilder builder =
        new SoyTypeRegistryBuilder()
            .addDescriptors(
                ImmutableList.of(
                    com.google.template.soy.testing.KvPair.getDescriptor().getFile(),
                    com.google.template.soy.testing.collision.KvPair.getDescriptor().getFile()));

    SoyInternalCompilerException e =
        assertThrows(SoyInternalCompilerException.class, builder::build);
    assertThat(e)
        .hasMessageThat()
        .contains("Identical protobuf message FQN 'example.KvPair' found in multiple dependencies");
    assertThat(e).hasMessageThat().contains("collision.proto");
    assertThat(e).hasMessageThat().contains("example.proto");
  }
}
