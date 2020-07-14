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

import static com.google.common.base.Strings.lenientFormat;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.template.soy.types.SoyTypes.NUMBER_TYPE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.testing.SoyFileSetParserBuilder;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType.Kind;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for soy types. */
@RunWith(JUnit4.class)
public class SoyTypesTest {
  private static final BoolType BOOL_TYPE = BoolType.getInstance();
  private static final IntType INT_TYPE = IntType.getInstance();
  private static final FloatType FLOAT_TYPE = FloatType.getInstance();
  private static final StringType STRING_TYPE = StringType.getInstance();
  private static final HtmlType HTML_TYPE = HtmlType.getInstance();
  private static final NullType NULL_TYPE = NullType.getInstance();
  private static final AnyType ANY_TYPE = AnyType.getInstance();
  private static final UnknownType UNKNOWN_TYPE = UnknownType.getInstance();
  private static final UriType URI_TYPE = UriType.getInstance();

  @Test
  public void testAnyType() {
    assertThatSoyType("any").isAssignableFrom("null");
    assertThatSoyType("any").isAssignableFrom("any");
    assertThatSoyType("any").isAssignableFrom("?");
    assertThatSoyType("any").isAssignableFrom("string");
    assertThatSoyType("any").isAssignableFrom("int");
  }

  @Test
  public void testUnknownType() {
    assertThatSoyType("?").isAssignableFrom("null");
    assertThatSoyType("?").isAssignableFrom("any");
    assertThatSoyType("?").isAssignableFrom("?");
    assertThatSoyType("?").isAssignableFrom("string");
    assertThatSoyType("?").isAssignableFrom("int");
  }

  @Test
  public void testNullType() {
    assertThatSoyType("null").isAssignableFrom("null");
    assertThatSoyType("null").isNotAssignableFrom("string");
    assertThatSoyType("null").isNotAssignableFrom("int");
    assertThatSoyType("null").isNotAssignableFrom("any");
    assertThatSoyType("null").isNotAssignableFrom("?");
  }

  @Test
  public void testStringType() {
    assertThatSoyType("string").isAssignableFrom("string");
    assertThatSoyType("string").isNotAssignableFrom("int");
    assertThatSoyType("string").isNotAssignableFrom("null");
    assertThatSoyType("string").isNotAssignableFrom("any");
    assertThatSoyType("string").isNotAssignableFrom("?");
  }

  @Test
  public void testPrimitiveTypeEquality() {
    assertThatSoyType("any").isEqualTo("any");
    assertThatSoyType("any").isNotEqualTo("int");
    assertThatSoyType("int").isNotEqualTo("any");
    assertThatSoyType("?").isEqualTo("?");
  }

  @Test
  public void testSanitizedType() {
    assertThatSoyType("string").isNotAssignableFrom("html");
    assertThatSoyType("string").isNotAssignableFrom("css");
    assertThatSoyType("string").isNotAssignableFrom("uri");
    assertThatSoyType("string").isNotAssignableFrom("trusted_resource_uri");
    assertThatSoyType("string").isNotAssignableFrom("attributes");
    assertThatSoyType("string").isNotAssignableFrom("js");

    assertThatSoyType("html").isAssignableFrom("html");
    assertThatSoyType("html").isNotAssignableFrom("int");
    assertThatSoyType("html").isNotAssignableFrom("css");

    assertThatSoyType("css").isAssignableFrom("css");
    assertThatSoyType("css").isNotAssignableFrom("int");
    assertThatSoyType("css").isNotAssignableFrom("html");

    assertThatSoyType("uri").isAssignableFrom("uri");
    assertThatSoyType("uri").isNotAssignableFrom("int");
    assertThatSoyType("uri").isNotAssignableFrom("html");

    assertThatSoyType("trusted_resource_uri").isAssignableFrom("trusted_resource_uri");
    assertThatSoyType("trusted_resource_uri").isNotAssignableFrom("int");
    assertThatSoyType("trusted_resource_uri").isNotAssignableFrom("html");

    assertThatSoyType("attributes").isAssignableFrom("attributes");
    assertThatSoyType("attributes").isNotAssignableFrom("int");
    assertThatSoyType("attributes").isNotAssignableFrom("html");

    assertThatSoyType("js").isAssignableFrom("js");
    assertThatSoyType("js").isNotAssignableFrom("int");
    assertThatSoyType("js").isNotAssignableFrom("html");
  }

  @Test
  public void testUnionType() {
    // Test that it flattens properly
    SoyType utype = UnionType.of(INT_TYPE, UnionType.of(INT_TYPE, NULL_TYPE));
    assertThat(utype.toString()).isEqualTo("int|null");
    assertThatSoyType("int|null").isAssignableFrom("int");
    assertThatSoyType("int|null").isAssignableFrom("null");
    assertThatSoyType("int|null").isNotAssignableFrom("float");
    assertThatSoyType("int|null").isNotAssignableFrom("string");
    assertThatSoyType("int|null").isNotAssignableFrom("any");
    assertThatSoyType("int|null").isNotAssignableFrom("?");
  }

  @Test
  public void testUnionTypeEquality() {
    assertThatSoyType("int|bool").isEqualTo("bool|int");
    assertThatSoyType("int|bool").isNotEqualTo("int|string");
  }

  // Regression test for an old bug where unions on the right hand side where not type checked
  // correctly.
  // See b/74754137
  @Test
  public void testUnionTypeAssignability() {
    assertThatSoyType("list<int|string>").isAssignableFrom("list<int>|list<string>");
    assertThatSoyType("list<int>|list<string>").isNotAssignableFrom("list<int|string>");

    assertThatSoyType("list<[field: int|string]>")
        .isAssignableFrom("list<[field: string]>|list<[field: int]>");
    assertThatSoyType("list<[field: string]>|list<[field: int]>")
        .isNotAssignableFrom("list<[field: int|string]>");

    assertThatSoyType("map<string, int|string>")
        .isAssignableFrom("map<string, int>|map<string, string>");
    assertThatSoyType("map<string, int>|map<string, string>")
        .isNotAssignableFrom("map<string, int|string>");
  }

  // Test that list types are covariant over their element types.
  @Test
  public void testListCovariance() {
    // Legal to assign List<X> to List<X>
    assertThatSoyType("list<any>").isAssignableFrom("list<any>");
    assertThatSoyType("list<string>").isAssignableFrom("list<string>");
    assertThatSoyType("list<int>").isAssignableFrom("list<int>");

    // Legal to assign List<X> to List<Y> where Y <: X
    assertThatSoyType("list<any>").isAssignableFrom("list<string>");
    assertThatSoyType("list<any>").isAssignableFrom("list<int>");

    // Not legal to assign List<X> to List<Y> where !(Y <: X)
    assertThatSoyType("list<int>").isNotAssignableFrom("list<string>");
    assertThatSoyType("list<string>").isNotAssignableFrom("list<int>");
    assertThatSoyType("list<int>").isNotAssignableFrom("list<any>");
    assertThatSoyType("list<string>").isNotAssignableFrom("list<any>");
  }

  @Test
  public void testListTypeEquality() {
    assertThatSoyType("list<any>").isEqualTo("list<any>");
    assertThatSoyType("list<any>").isNotEqualTo("list<string>");
  }

  // Test that map types are covariant over their key types.
  @Test
  public void testMapKeyCovariance() {
    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFrom("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<string, any>")
        .isAssignableFrom("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<int, any>")
        .isAssignableFrom("legacy_object_map<int, any>");

    // Legal to assign Map<X, Z> to Map<Y, Z> where Y <: X
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFrom("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFrom("legacy_object_map<int, any>");

    // Not legal to assign Map<X, Z> to Map<Y, Z> where !(Y <: X)
    assertThatSoyType("legacy_object_map<int, any>")
        .isNotAssignableFrom("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<string, any>")
        .isNotAssignableFrom("legacy_object_map<int, any>");
    assertThatSoyType("legacy_object_map<int, any>")
        .isNotAssignableFrom("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<string, any>")
        .isNotAssignableFrom("legacy_object_map<any, any>");
  }

  // Test that map types are covariant over their value types.
  @Test
  public void testLegacyObjectMapValueCovariance() {
    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFrom("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<any, string>")
        .isAssignableFrom("legacy_object_map<any, string>");
    assertThatSoyType("legacy_object_map<any, int>")
        .isAssignableFrom("legacy_object_map<any, int>");

    // Legal to assign Map<X, Y> to Map<X, Z> where Z <: Y
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFrom("legacy_object_map<any, string>");
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFrom("legacy_object_map<any, int>");

    // Not legal to assign Map<X, Y> to Map<X, Z> where !(Z <: Y)
    assertThatSoyType("legacy_object_map<any, int>")
        .isNotAssignableFrom("legacy_object_map<any, string>");
    assertThatSoyType("legacy_object_map<any, string>")
        .isNotAssignableFrom("legacy_object_map<any, int>");
    assertThatSoyType("legacy_object_map<any, int>")
        .isNotAssignableFrom("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<any, string>")
        .isNotAssignableFrom("legacy_object_map<any, any>");
  }

  // Test that map types are covariant over their value types.
  @Test
  public void testMapValueCovariance() {
    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThatSoyType("map<int,any>").isAssignableFrom("map<int,any>");
    assertThatSoyType("map<int,string>").isAssignableFrom("map<int,string>");
    assertThatSoyType("map<int,int>").isAssignableFrom("map<int,int>");

    // Legal to assign Map<X, Y> to Map<X, Z> where Z <: Y
    assertThatSoyType("map<int,any>").isAssignableFrom("map<int,string>");
    assertThatSoyType("map<int,any>").isAssignableFrom("map<int,int>");

    // Not legal to assign Map<X, Y> to Map<X, Z> where !(Z <: Y)
    assertThatSoyType("map<int,int>").isNotAssignableFrom("map<int,string>");
    assertThatSoyType("map<int,string>").isNotAssignableFrom("map<int,int>");
    assertThatSoyType("map<int,int>").isNotAssignableFrom("map<int,any>");
    assertThatSoyType("map<int,string>").isNotAssignableFrom("map<int,any>");
  }

  @Test
  public void testMapTypeAssignability() {
    assertThat(MapType.of(ANY_TYPE, ANY_TYPE).isAssignableFrom(UNKNOWN_TYPE)).isFalse();
    assertThat(UNKNOWN_TYPE.isAssignableFrom(MapType.of(ANY_TYPE, ANY_TYPE))).isFalse();
  }

  @Test
  public void testLegacyObjectMapTypeEquality() {
    assertThatSoyType("legacy_object_map<any, any>").isEqualTo("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<any, any>").isNotEqualTo("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<any, any>").isNotEqualTo("legacy_object_map<any, string>");
  }

  @Test
  public void testMapTypeEquality() {
    assertThatSoyType("map<string,any>").isEqualTo("map<string,any>");
    assertThatSoyType("map<string,any>").isNotEqualTo("map<int,any>");
    assertThatSoyType("map<string,any>").isNotEqualTo("map<int,any>");
  }

  @Test
  public void testRecordTypeEquality() {
    assertThatSoyType("[a: int, b: any]").isEqualTo("[a: int, b: any]");
    assertThatSoyType("[a: int, b: any]").isNotEqualTo("[a: int, c: any]");
    assertThatSoyType("[a: int, b: any]").isNotEqualTo("[a: int, b: string]");
  }

  @Test
  public void testRecordTypeAssignment() {
    // Same
    assertThatSoyType("[a:int, b:any]").isAssignableFrom("[a:int, b:any]");

    // "b" is subtype
    assertThatSoyType("[a:int, b:any]").isAssignableFrom("[a:int, b:string]");

    // Additional field
    assertThatSoyType("[a:int, b:any]").isAssignableFrom("[a:int, b:string, c:string]");

    // Missing "b"
    assertThatSoyType("[a:int, b:any]").isNotAssignableFrom("[a:int, c:string]");

    // Field type mismatch on a
    assertThatSoyType("[a:int, b:any]").isNotAssignableFrom("[a:string, c:any]");
  }

  @Test
  public void testAllContentKindsCovered() {
    Set<SoyType> types = Sets.newIdentityHashSet();
    for (SanitizedContentKind kind : SanitizedContentKind.values()) {
      SoyType typeForContentKind = SanitizedType.getTypeForContentKind(kind);
      if (kind == SanitizedContentKind.TEXT) {
        assertThat(typeForContentKind).isEqualTo(STRING_TYPE);
      } else {
        assertThat(((SanitizedType) typeForContentKind).getContentKind()).isEqualTo(kind);
      }
      // ensure there is a unique SoyType for every ContentKind
      assertThat(types.add(typeForContentKind)).isTrue();
    }
  }

  @Test
  public void testLowestCommonType() {
    SoyTypeRegistry typeRegistry = SoyTypeRegistryBuilder.create();

    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, ANY_TYPE))
        .isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, UNKNOWN_TYPE))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, UNKNOWN_TYPE, INT_TYPE))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, ANY_TYPE, INT_TYPE))
        .isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, STRING_TYPE, HTML_TYPE))
        .isEqualTo(UnionType.of(STRING_TYPE, HTML_TYPE));
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, FLOAT_TYPE))
        .isEqualTo(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, FLOAT_TYPE, INT_TYPE))
        .isEqualTo(NUMBER_TYPE);
  }

  @Test
  public void testLowestCommonTypeArithmetic() {
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_TYPE, ANY_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(ANY_TYPE, INT_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(STRING_TYPE, HTML_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(HTML_TYPE, STRING_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(ListType.of(INT_TYPE), INT_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                LegacyObjectMapType.of(INT_TYPE, STRING_TYPE), INT_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                RecordType.of(ImmutableMap.of("a", INT_TYPE, "b", FLOAT_TYPE)), INT_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(LegacyObjectMapType.of(FLOAT_TYPE, STRING_TYPE), INT_TYPE),
                FLOAT_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(BOOL_TYPE, INT_TYPE, STRING_TYPE), NUMBER_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(UnionType.of(BOOL_TYPE, INT_TYPE), INT_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(STRING_TYPE, FLOAT_TYPE), INT_TYPE))
        .isEmpty();

    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_TYPE, FLOAT_TYPE))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, INT_TYPE))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, UNKNOWN_TYPE))
        .hasValue(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(UNKNOWN_TYPE, FLOAT_TYPE))
        .hasValue(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_TYPE, INT_TYPE)).hasValue(INT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, FLOAT_TYPE))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, NUMBER_TYPE))
        .hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_TYPE, NUMBER_TYPE))
        .hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, NUMBER_TYPE))
        .hasValue(NUMBER_TYPE);
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorPlusOp() {
    SoyTypes.SoyTypeBinaryOperator plusOp = new SoyTypes.SoyTypePlusOperator();
    /**
     * This is largely the same as computeLowestCommonTypeArithmetic(), but this method is slightly
     * better. There is one difference: float + number will return float type, which is expected.
     * Since number is basically {int|float}; float + int returns float, and float + float returns
     * float, so we always return float. OTOH, computeLowestCommonTypeArithmetic(float, number) will
     * return number.
     */
    // All number types
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_TYPE, plusOp))
        .isEqualTo(INT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(NUMBER_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(NUMBER_TYPE);

    // Unknown types are fine
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);

    // Any string types should be resolved to string
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, STRING_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, BOOL_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, STRING_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, URI_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(HTML_TYPE, STRING_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);

    // Some types are definitely not allowed.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ListType.of(STRING_TYPE), FLOAT_TYPE, plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, LegacyObjectMapType.of(INT_TYPE, STRING_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, RecordType.of(ImmutableMap.of("a", INT_TYPE, "b", FLOAT_TYPE)), plusOp))
        .isNull();

    // Union types should list all possible combinations
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(INT_TYPE, STRING_TYPE), plusOp))
        .isEqualTo(UnionType.of(INT_TYPE, STRING_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, FLOAT_TYPE), UnionType.of(INT_TYPE, STRING_TYPE), plusOp))
        .isEqualTo(UnionType.of(STRING_TYPE, FLOAT_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, INT_TYPE), UnionType.of(INT_TYPE, STRING_TYPE), plusOp))
        .isEqualTo(UnionType.of(STRING_TYPE, INT_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE), UnionType.of(INT_TYPE, STRING_TYPE), plusOp))
        .isEqualTo(STRING_TYPE);

    // If any of these combinations are incompatible, we should return null.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(BOOL_TYPE, FLOAT_TYPE, INT_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE),
                UnionType.of(INT_TYPE, STRING_TYPE, ListType.of(INT_TYPE)),
                plusOp))
        .isNull();

    // Nullable types should be fine. However, null type itself is not allowed.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, NULL_TYPE, plusOp)).isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isEqualTo(INT_TYPE);
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, FLOAT_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isEqualTo(UnionType.of(NULL_TYPE, FLOAT_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isEqualTo(UnionType.of(NULL_TYPE, STRING_TYPE));
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorArithmeticOp() {
    SoyTypes.SoyTypeBinaryOperator plusOp = new SoyTypes.SoyTypeArithmeticOperator();
    // All number types
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_TYPE, plusOp))
        .isEqualTo(INT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(NUMBER_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(NUMBER_TYPE);

    // Unknown types are fine
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);

    // Any string types should be rejected
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, STRING_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, FLOAT_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, NUMBER_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, STRING_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, URI_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(HTML_TYPE, STRING_TYPE, plusOp)).isNull();

    // Arbitrary types are banned
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ListType.of(STRING_TYPE), FLOAT_TYPE, plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, LegacyObjectMapType.of(INT_TYPE, STRING_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, RecordType.of(ImmutableMap.of("a", INT_TYPE, "b", FLOAT_TYPE)), plusOp))
        .isNull();

    // If any of these combinations are incompatible, we should return null.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(BOOL_TYPE, FLOAT_TYPE, INT_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, INT_TYPE),
                UnionType.of(INT_TYPE, STRING_TYPE, ListType.of(INT_TYPE)),
                plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isNull();

    // Nullable types should be fine. However, null type itself is not allowed.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, NULL_TYPE, plusOp)).isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isEqualTo(INT_TYPE);
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, FLOAT_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isEqualTo(UnionType.of(NULL_TYPE, FLOAT_TYPE));
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorEqualOp() {
    SoyTypes.SoyTypeBinaryOperator equalOp = new SoyTypes.SoyTypeEqualComparisonOp();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NUMBER_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, NUMBER_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NUMBER_TYPE, equalOp)).isNotNull();

    // Unknown types are fine
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, UNKNOWN_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, UNKNOWN_TYPE, equalOp))
        .isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ANY_TYPE, STRING_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ANY_TYPE, NULL_TYPE, equalOp)).isNotNull();

    // String-number comparisons are okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, STRING_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, NUMBER_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, URI_TYPE, equalOp)).isNotNull();

    // String-string comparisons are okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, STRING_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(HTML_TYPE, STRING_TYPE, equalOp)).isNotNull();

    // Aribtrary types are not okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ListType.of(STRING_TYPE), FLOAT_TYPE, equalOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, LegacyObjectMapType.of(INT_TYPE, STRING_TYPE), equalOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, RecordType.of(ImmutableMap.of("a", INT_TYPE, "b", FLOAT_TYPE)), equalOp))
        .isNull();

    // Union types might be okay if all combinations are okay.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(INT_TYPE, STRING_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, FLOAT_TYPE),
                UnionType.of(INT_TYPE, STRING_TYPE),
                equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, INT_TYPE), UnionType.of(INT_TYPE, STRING_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE), UnionType.of(INT_TYPE, STRING_TYPE), equalOp))
        .isNotNull();

    // If any of these combinations are incompatible, we should return null.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                LegacyObjectMapType.of(INT_TYPE, STRING_TYPE),
                UnionType.of(BOOL_TYPE, FLOAT_TYPE, INT_TYPE),
                equalOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE),
                UnionType.of(INT_TYPE, STRING_TYPE, ListType.of(INT_TYPE)),
                equalOp))
        .isNull();

    // Nullable types and null type itself are okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, NULL_TYPE, equalOp)).isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                INT_TYPE, UnionType.of(NULL_TYPE, INT_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, FLOAT_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), equalOp))
        .isNotNull();
  }

  @Test
  public void testIsKindOrUnionOfKind() {
    assertThat(SoyTypes.isKindOrUnionOfKind(MapType.ANY_MAP, Kind.MAP)).isTrue();
    assertThat(
            SoyTypes.isKindOrUnionOfKind(
                UnionType.of(
                    VeType.of("my.Proto"), VeType.of("my.OtherProto"), VeType.of("my.LastProto")),
                Kind.VE))
        .isTrue();
    assertThat(
            SoyTypes.isKindOrUnionOfKind(
                UnionType.of(
                    VeType.of("my.Proto"), VeType.of("my.OtherProto"), NullType.getInstance()),
                Kind.VE))
        .isFalse();
    assertThat(SoyTypes.isKindOrUnionOfKind(IntType.getInstance(), Kind.BOOL)).isFalse();
  }

  @Test
  public void testContainsKinds_singleKind() {
    assertThat(SoyTypes.containsKinds(MapType.ANY_MAP, Sets.immutableEnumSet(Kind.MAP))).isTrue();
  }

  @Test
  public void testContainsKinds_matchingUnion() {
    assertThat(
            SoyTypes.containsKinds(
                UnionType.of(
                    VeType.of("my.Proto"), VeType.of("my.OtherProto"), VeType.of("my.LastProto")),
                Sets.immutableEnumSet(Kind.VE)))
        .isTrue();
  }

  @Test
  public void testContainsKinds_nonMatchingUnion() {
    assertThat(
            SoyTypes.containsKinds(
                UnionType.of(
                    VeType.of("my.Proto"), VeType.of("my.OtherProto"), NullType.getInstance()),
                Sets.immutableEnumSet(Kind.INT)))
        .isFalse();
  }

  @Test
  public void testContainsKinds_multipleKinds() {
    assertThat(
            SoyTypes.containsKinds(
                IntType.getInstance(), Sets.immutableEnumSet(Kind.BOOL, Kind.STRING)))
        .isFalse();
  }

  @Test
  public void testContainsKinds_multipleNonMatchingKinds() {
    assertThat(
            SoyTypes.containsKinds(
                IntType.getInstance(), Sets.immutableEnumSet(Kind.BOOL, Kind.INT)))
        .isTrue();
  }

  static SoyTypeSubject assertThatSoyType(String typeString, SoyTypeRegistry registry) {
    return Truth.<SoyTypeSubject, String>assertAbout(
            (meta, subject) -> new SoyTypeSubject(meta, subject, registry))
        .that(typeString);
  }

  static SoyTypeSubject assertThatSoyType(String typeString) {
    return assertThatSoyType(typeString, SoyTypeRegistryBuilder.create());
  }

  static final class SoyTypeSubject extends Subject {
    private final String actual;
    private final SoyTypeRegistry registry;

    SoyTypeSubject(FailureMetadata metadata, String actual, SoyTypeRegistry registry) {
      super(metadata, actual);
      this.actual = actual;
      this.registry = registry;
    }

    void isAssignableFrom(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (!leftType.isAssignableFrom(rightType)) {
        failWithActual("expected to be assignable from", other);
      }
    }

    void isNotAssignableFrom(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (leftType.isAssignableFrom(rightType)) {
        failWithActual("expected not to be assignable from", other);
      }
    }

    @Deprecated
    @Override
    public void isEqualTo(@Nullable Object expected) {
      throw new UnsupportedOperationException("call isEqualTo(String) instead");
    }

    void isEqualTo(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (!leftType.equals(rightType)) {
        failWithActual("expected", other);
      }
      // make sure that assignability is compatible with equality.
      if (!leftType.isAssignableFrom(rightType)) {
        failWithoutActual(
            simpleFact(
                lenientFormat("types are equal, but %s is not assignable from %s", actual, other)));
      }
      if (!rightType.isAssignableFrom(leftType)) {
        failWithoutActual(
            simpleFact(
                lenientFormat("types are equal, but %s is not assignable from %s", other, actual)));
      }
    }

    @Deprecated
    @Override
    public void isNotEqualTo(@Nullable Object unexpected) {
      throw new UnsupportedOperationException("call isEqualTo(String) instead");
    }

    void isNotEqualTo(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (leftType.equals(rightType)) {
        failWithActual("expected not to be", other);
      }
      // make sure that assignability is compatible with equality.
      if (leftType.isAssignableFrom(rightType) && rightType.isAssignableFrom(leftType)) {
        failWithoutActual(
            simpleFact(
                lenientFormat(
                    "types are not equal, but %s and %s are mutally assignable", actual, other)));
      }
    }

    private SoyType parseType(String input) {
      TemplateNode template =
          (TemplateNode)
              SoyFileSetParserBuilder.forTemplateContents(
                      "{@param p : " + input + "|string}\n{$p ? 't' : 'f'}")
                  .typeRegistry(registry)
                  .parse()
                  .fileSet()
                  .getChild(0)
                  .getChild(0);
      SoyType type = Iterables.getOnlyElement(template.getAllParams()).type();
      if (type.equals(StringType.getInstance())
          || type.equals(UnknownType.getInstance())
          || type.equals(AnyType.getInstance())) {
        return type;
      }
      return UnionType.of(
          ((UnionType) type)
              .getMembers().stream()
                  .filter(t -> !t.equals(StringType.getInstance()))
                  .collect(toImmutableList()));
    }
  }
}
