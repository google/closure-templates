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
import com.google.template.soy.base.internal.SoyFileKind;
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
    assertThat(typeRegistry.getType("regexp").getKind()).isEqualTo(SoyType.Kind.REGEXP);
    assertThat(typeRegistry.getType("regexp")).isEqualTo(RegexpType.getInstance());
  }

  @Test
  public void testCreateListType() {
    ListType listOfInt = ListType.of(IntType.getInstance());
    ListType listOfInt2 = ListType.of(IntType.getInstance());
    ListType listOfFloat = ListType.of(FloatType.getInstance());

    assertThat(listOfInt2).isEqualTo(listOfInt);
    assertThat(listOfFloat).isNotEqualTo(listOfInt);
  }

  @Test
  public void testCreateLegacyObjectMapType() {
    LegacyObjectMapType mapOfIntToString =
        LegacyObjectMapType.of(IntType.getInstance(), StringType.getInstance());
    LegacyObjectMapType mapOfIntToString2 =
        LegacyObjectMapType.of(IntType.getInstance(), StringType.getInstance());
    LegacyObjectMapType mapOfIntToInt =
        LegacyObjectMapType.of(IntType.getInstance(), IntType.getInstance());
    LegacyObjectMapType mapOfStringToString =
        LegacyObjectMapType.of(StringType.getInstance(), StringType.getInstance());

    assertThat(mapOfIntToString2).isEqualTo(mapOfIntToString);
    assertThat(mapOfIntToInt).isNotEqualTo(mapOfIntToString);
    assertThat(mapOfStringToString).isNotEqualTo(mapOfIntToString);
  }

  @Test
  public void testCreateUnionType() {
    SoyType u1 = UnionType.of(IntType.getInstance(), FloatType.getInstance());
    SoyType u2 = UnionType.of(IntType.getInstance(), FloatType.getInstance());
    SoyType u3 = UnionType.of(IntType.getInstance(), StringType.getInstance());

    assertThat(u2).isEqualTo(u1);
    assertThat(u3).isNotEqualTo(u1);
  }

  @Test
  public void testCreateRecordType() {
    RecordType r1 =
        RecordType.of(
            ImmutableList.of(
                RecordType.memberOf("a", false, IntType.getInstance()),
                RecordType.memberOf("b", false, FloatType.getInstance())));
    RecordType r2 =
        RecordType.of(
            ImmutableList.of(
                RecordType.memberOf("a", false, IntType.getInstance()),
                RecordType.memberOf("b", false, FloatType.getInstance())));
    RecordType r3 =
        RecordType.of(
            ImmutableList.of(
                RecordType.memberOf("a", false, IntType.getInstance()),
                RecordType.memberOf("b", false, StringType.getInstance())));
    RecordType r4 =
        RecordType.of(
            ImmutableList.of(
                RecordType.memberOf("a", false, IntType.getInstance()),
                RecordType.memberOf("c", false, FloatType.getInstance())));
    RecordType r5 =
        RecordType.of(
            ImmutableList.of(
                RecordType.memberOf("a", false, IntType.getInstance()),
                RecordType.memberOf("c", true, FloatType.getInstance())));

    assertThat(r2).isEqualTo(r1);
    assertThat(r3).isNotEqualTo(r1);
    assertThat(r4).isNotEqualTo(r1);
    assertThat(r4).isNotEqualTo(r5);
  }

  @Test
  public void testNumberType() {
    // Make sure the type registry knows about the special number type
    assertThat(SoyTypes.INT_OR_FLOAT)
        .isEqualTo(UnionType.of(FloatType.getInstance(), IntType.getInstance()));
    assertThat(SoyTypes.INT_OR_FLOAT).isEqualTo(UnionType.of(SoyTypes.INT_OR_FLOAT));
  }

  @Test
  public void testProtoFqnCollision() {
    SoyTypeRegistryBuilder builder =
        new SoyTypeRegistryBuilder()
            .addDescriptors(
                SoyFileKind.DEP,
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
