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
import static com.google.template.soy.types.SoyTypes.NUMBER_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.SanitizedType.AttributesType;
import com.google.template.soy.types.primitive.SanitizedType.CssType;
import com.google.template.soy.types.primitive.SanitizedType.HtmlType;
import com.google.template.soy.types.primitive.SanitizedType.JsType;
import com.google.template.soy.types.primitive.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.primitive.SanitizedType.UriType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import java.util.Set;
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
  private static final CssType CSS_TYPE = CssType.getInstance();
  private static final JsType JS_TYPE = JsType.getInstance();
  private static final AttributesType ATTRIBUTES_TYPE = AttributesType.getInstance();
  private static final NullType NULL_TYPE = NullType.getInstance();
  private static final AnyType ANY_TYPE = AnyType.getInstance();
  private static final UnknownType UNKNOWN_TYPE = UnknownType.getInstance();
  private static final TrustedResourceUriType TRUSTED_RESOURCE_URI_TYPE =
      TrustedResourceUriType.getInstance();
  private static final UriType URI_TYPE = UriType.getInstance();

  @Test
  public void testAnyType() {
    assertThat(ANY_TYPE.isAssignableFrom(NULL_TYPE)).isTrue();
    assertThat(ANY_TYPE.isAssignableFrom(ANY_TYPE)).isTrue();
    assertThat(ANY_TYPE.isAssignableFrom(UNKNOWN_TYPE)).isTrue();
    assertThat(ANY_TYPE.isAssignableFrom(STRING_TYPE)).isTrue();
    assertThat(ANY_TYPE.isAssignableFrom(INT_TYPE)).isTrue();
  }

  @Test
  public void testUnknownType() {
    assertThat(UNKNOWN_TYPE.isAssignableFrom(NULL_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isAssignableFrom(ANY_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isAssignableFrom(UNKNOWN_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isAssignableFrom(STRING_TYPE)).isTrue();
    assertThat(UNKNOWN_TYPE.isAssignableFrom(INT_TYPE)).isTrue();
  }

  @Test
  public void testNullType() {
    assertThat(NULL_TYPE.isAssignableFrom(NULL_TYPE)).isTrue();
    assertThat(NULL_TYPE.isAssignableFrom(STRING_TYPE)).isFalse();
    assertThat(NULL_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(NULL_TYPE.isAssignableFrom(ANY_TYPE)).isFalse();
    assertThat(NULL_TYPE.isAssignableFrom(UNKNOWN_TYPE)).isFalse();
  }

  @Test
  public void testStringType() {
    assertThat(STRING_TYPE.isAssignableFrom(STRING_TYPE)).isTrue();
    assertThat(STRING_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(STRING_TYPE.isAssignableFrom(NULL_TYPE)).isFalse();
    assertThat(STRING_TYPE.isAssignableFrom(ANY_TYPE)).isFalse();
    assertThat(STRING_TYPE.isAssignableFrom(UNKNOWN_TYPE)).isFalse();
  }

  @Test
  public void testPrimitiveTypeEquality() {
    assertThat(ANY_TYPE.equals(ANY_TYPE)).isTrue();
    assertThat(ANY_TYPE.equals(INT_TYPE)).isFalse();
    assertThat(INT_TYPE.equals(ANY_TYPE)).isFalse();
    assertThat(UNKNOWN_TYPE.equals(UNKNOWN_TYPE)).isTrue();
  }

  @Test
  public void testSanitizedType() {
    assertThat(STRING_TYPE.isAssignableFrom(HTML_TYPE)).isTrue();
    assertThat(STRING_TYPE.isAssignableFrom(CSS_TYPE)).isTrue();
    assertThat(STRING_TYPE.isAssignableFrom(URI_TYPE)).isTrue();
    assertThat(STRING_TYPE.isAssignableFrom(TRUSTED_RESOURCE_URI_TYPE)).isTrue();
    assertThat(STRING_TYPE.isAssignableFrom(ATTRIBUTES_TYPE)).isTrue();
    assertThat(STRING_TYPE.isAssignableFrom(JS_TYPE)).isTrue();

    assertThat(HTML_TYPE.isAssignableFrom(HTML_TYPE)).isTrue();
    assertThat(HTML_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(HTML_TYPE.isAssignableFrom(CSS_TYPE)).isFalse();

    assertThat(CSS_TYPE.isAssignableFrom(CSS_TYPE)).isTrue();
    assertThat(CSS_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(CSS_TYPE.isAssignableFrom(HTML_TYPE)).isFalse();

    assertThat(URI_TYPE.isAssignableFrom(URI_TYPE)).isTrue();
    assertThat(URI_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(URI_TYPE.isAssignableFrom(HTML_TYPE)).isFalse();

    assertThat(TRUSTED_RESOURCE_URI_TYPE.isAssignableFrom(TRUSTED_RESOURCE_URI_TYPE)).isTrue();
    assertThat(TRUSTED_RESOURCE_URI_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(TRUSTED_RESOURCE_URI_TYPE.isAssignableFrom(HTML_TYPE)).isFalse();

    assertThat(ATTRIBUTES_TYPE.isAssignableFrom(ATTRIBUTES_TYPE)).isTrue();
    assertThat(ATTRIBUTES_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(ATTRIBUTES_TYPE.isAssignableFrom(HTML_TYPE)).isFalse();

    assertThat(JS_TYPE.isAssignableFrom(JS_TYPE)).isTrue();
    assertThat(JS_TYPE.isAssignableFrom(INT_TYPE)).isFalse();
    assertThat(JS_TYPE.isAssignableFrom(HTML_TYPE)).isFalse();
  }

  @Test
  public void testUnionType() {
    // Test that it flattens properly
    SoyType utype = UnionType.of(INT_TYPE, UnionType.of(INT_TYPE, NULL_TYPE));
    assertThat(utype.toString()).isEqualTo("int|null");
    assertThat(utype.isAssignableFrom(INT_TYPE)).isTrue();
    assertThat(utype.isAssignableFrom(NULL_TYPE)).isTrue();
    assertThat(utype.isAssignableFrom(FLOAT_TYPE)).isFalse();
    assertThat(utype.isAssignableFrom(STRING_TYPE)).isFalse();
    assertThat(utype.isAssignableFrom(ANY_TYPE)).isFalse();
    assertThat(utype.isAssignableFrom(UNKNOWN_TYPE)).isFalse();
  }

  @Test
  public void testUnionTypeEquality() {
    assertThat(UnionType.of(INT_TYPE, BOOL_TYPE).equals(UnionType.of(BOOL_TYPE, INT_TYPE)))
        .isTrue();
    assertThat(UnionType.of(INT_TYPE, BOOL_TYPE).equals(UnionType.of(INT_TYPE, STRING_TYPE)))
        .isFalse();
  }

  // Test that list types are covariant over their element types.
  @Test
  public void testListCovariance() {
    ListType listOfAny = ListType.of(ANY_TYPE);
    ListType listOfString = ListType.of(STRING_TYPE);
    ListType listOfInt = ListType.of(INT_TYPE);

    // Legal to assign List<X> to List<X>
    assertThat(listOfAny.isAssignableFrom(listOfAny)).isTrue();
    assertThat(listOfString.isAssignableFrom(listOfString)).isTrue();
    assertThat(listOfInt.isAssignableFrom(listOfInt)).isTrue();

    // Legal to assign List<X> to List<Y> where Y <: X
    assertThat(listOfAny.isAssignableFrom(listOfString)).isTrue();
    assertThat(listOfAny.isAssignableFrom(listOfInt)).isTrue();

    // Not legal to assign List<X> to List<Y> where !(Y <: X)
    assertThat(listOfInt.isAssignableFrom(listOfString)).isFalse();
    assertThat(listOfString.isAssignableFrom(listOfInt)).isFalse();
    assertThat(listOfInt.isAssignableFrom(listOfAny)).isFalse();
    assertThat(listOfString.isAssignableFrom(listOfAny)).isFalse();
  }

  @Test
  public void testListTypeEquality() {
    ListType listOfAny = ListType.of(ANY_TYPE);
    ListType listOfAny2 = ListType.of(ANY_TYPE);
    ListType listOfString = ListType.of(STRING_TYPE);

    assertThat(listOfAny.equals(listOfAny2)).isTrue();
    assertThat(listOfAny.equals(listOfString)).isFalse();
  }

  // Test that map types are covariant over their key types.
  @Test
  public void testMapKeyCovariance() {
    MapType mapOfAnyToAny = MapType.of(ANY_TYPE, ANY_TYPE);
    MapType mapOfStringToAny = MapType.of(STRING_TYPE, ANY_TYPE);
    MapType mapOfIntToAny = MapType.of(INT_TYPE, ANY_TYPE);

    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThat(mapOfAnyToAny.isAssignableFrom(mapOfAnyToAny)).isTrue();
    assertThat(mapOfStringToAny.isAssignableFrom(mapOfStringToAny)).isTrue();
    assertThat(mapOfIntToAny.isAssignableFrom(mapOfIntToAny)).isTrue();

    // Legal to assign Map<X, Z> to Map<Y, Z> where Y <: X
    assertThat(mapOfAnyToAny.isAssignableFrom(mapOfStringToAny)).isTrue();
    assertThat(mapOfAnyToAny.isAssignableFrom(mapOfIntToAny)).isTrue();

    // Not legal to assign Map<X, Z> to Map<Y, Z> where !(Y <: X)
    assertThat(mapOfIntToAny.isAssignableFrom(mapOfStringToAny)).isFalse();
    assertThat(mapOfStringToAny.isAssignableFrom(mapOfIntToAny)).isFalse();
    assertThat(mapOfIntToAny.isAssignableFrom(mapOfAnyToAny)).isFalse();
    assertThat(mapOfStringToAny.isAssignableFrom(mapOfAnyToAny)).isFalse();
  }

  // Test that map types are covariant over their value types.
  @Test
  public void testMapValueCovariance() {
    MapType mapOfAnyToAny = MapType.of(ANY_TYPE, ANY_TYPE);
    MapType mapOfAnyToString = MapType.of(ANY_TYPE, STRING_TYPE);
    MapType mapOfAnyToInt = MapType.of(ANY_TYPE, INT_TYPE);

    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThat(mapOfAnyToAny.isAssignableFrom(mapOfAnyToAny)).isTrue();
    assertThat(mapOfAnyToString.isAssignableFrom(mapOfAnyToString)).isTrue();
    assertThat(mapOfAnyToInt.isAssignableFrom(mapOfAnyToInt)).isTrue();

    // Legal to assign Map<X, Y> to Map<X, Z> where Z <: Y
    assertThat(mapOfAnyToAny.isAssignableFrom(mapOfAnyToString)).isTrue();
    assertThat(mapOfAnyToAny.isAssignableFrom(mapOfAnyToInt)).isTrue();

    // Not legal to assign Map<X, Y> to Map<X, Z> where !(Z <: Y)
    assertThat(mapOfAnyToInt.isAssignableFrom(mapOfAnyToString)).isFalse();
    assertThat(mapOfAnyToString.isAssignableFrom(mapOfAnyToInt)).isFalse();
    assertThat(mapOfAnyToInt.isAssignableFrom(mapOfAnyToAny)).isFalse();
    assertThat(mapOfAnyToString.isAssignableFrom(mapOfAnyToAny)).isFalse();
  }

  @Test
  public void testMapTypeEquality() {
    MapType mapOfAnyToAny = MapType.of(ANY_TYPE, ANY_TYPE);
    MapType mapOfAnyToAny2 = MapType.of(ANY_TYPE, ANY_TYPE);
    MapType mapOfStringToAny = MapType.of(STRING_TYPE, ANY_TYPE);
    MapType mapOfAnyToString = MapType.of(ANY_TYPE, STRING_TYPE);

    assertThat(mapOfAnyToAny.equals(mapOfAnyToAny2)).isTrue();
    assertThat(mapOfAnyToAny.equals(mapOfStringToAny)).isFalse();
    assertThat(mapOfAnyToAny.equals(mapOfAnyToString)).isFalse();
  }

  @Test
  public void testRecordTypeEquality() {
    RecordType r1 = RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "b", ANY_TYPE));

    assertThat(
            r1.equals(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "b", ANY_TYPE))))
        .isTrue();
    assertThat(
            r1.equals(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "c", ANY_TYPE))))
        .isFalse();
    assertThat(
            r1.equals(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "b", STRING_TYPE))))
        .isFalse();
  }

  @Test
  public void testRecordTypeAssignment() {
    RecordType r1 = RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "b", ANY_TYPE));

    // Same
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "b", ANY_TYPE))))
        .isTrue();

    // "b" is subtype
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "b", STRING_TYPE))))
        .isTrue();

    // Additional field
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", INT_TYPE, "b", STRING_TYPE, "c", STRING_TYPE))))
        .isTrue();

    // Missing "b"
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", INT_TYPE, "c", ANY_TYPE))))
        .isFalse();

    // Field type mismatch
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(ImmutableMap.<String, SoyType>of("a", STRING_TYPE, "b", ANY_TYPE))))
        .isFalse();
  }

  @Test
  public void testAllContentKindsCovered() {
    Set<SoyType> types = Sets.newIdentityHashSet();
    for (SanitizedContentKind kind : SanitizedContentKind.values()) {
      SoyType typeForContentKind = SanitizedType.getTypeForContentKind(kind);
      if (kind == SanitizedContentKind.TEXT) {
        assertEquals(STRING_TYPE, typeForContentKind);
      } else {
        assertEquals(kind, ((SanitizedType) typeForContentKind).getContentKind());
      }
      // ensure there is a unique SoyType for every ContentKind
      assertTrue(types.add(typeForContentKind));
    }
  }

  @Test
  public void testLowestCommonType() {
    SoyTypeRegistry typeRegistry = new SoyTypeRegistry();

    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, ANY_TYPE))
        .isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, UNKNOWN_TYPE))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, UNKNOWN_TYPE, INT_TYPE))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, ANY_TYPE, INT_TYPE))
        .isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, STRING_TYPE, HTML_TYPE))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, HTML_TYPE, STRING_TYPE))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, FLOAT_TYPE))
        .isEqualTo(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, FLOAT_TYPE, INT_TYPE))
        .isEqualTo(NUMBER_TYPE);
  }

  @Test
  public void testLowestCommonTypeArithmetic() {
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_TYPE, ANY_TYPE)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(ANY_TYPE, INT_TYPE)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(STRING_TYPE, HTML_TYPE)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(HTML_TYPE, STRING_TYPE)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(ListType.of(INT_TYPE), INT_TYPE))
        .isAbsent();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(MapType.of(INT_TYPE, STRING_TYPE), INT_TYPE))
        .isAbsent();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                RecordType.of(ImmutableMap.of("a", INT_TYPE, "b", FLOAT_TYPE)), INT_TYPE))
        .isAbsent();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(MapType.of(FLOAT_TYPE, STRING_TYPE), INT_TYPE), FLOAT_TYPE))
        .isAbsent();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(BOOL_TYPE, INT_TYPE, STRING_TYPE), NUMBER_TYPE))
        .isAbsent();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(UnionType.of(BOOL_TYPE, INT_TYPE), INT_TYPE))
        .isAbsent();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(STRING_TYPE, FLOAT_TYPE), INT_TYPE))
        .isAbsent();

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
                INT_TYPE, MapType.of(INT_TYPE, STRING_TYPE), plusOp))
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
                INT_TYPE, MapType.of(INT_TYPE, STRING_TYPE), plusOp))
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
}
