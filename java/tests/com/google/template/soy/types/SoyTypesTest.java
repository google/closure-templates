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
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
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
  private static final NullData NULL_DATA = NullData.INSTANCE;
  private static final BooleanData BOOLEAN_DATA = BooleanData.TRUE;
  private static final StringData STRING_DATA = StringData.forValue("foo");
  private static final IntegerData INTEGER_DATA = IntegerData.forValue(2);
  private static final FloatData FLOAT_DATA = FloatData.forValue(2.0);
  private static final SanitizedContent HTML_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe("html", SanitizedContent.ContentKind.HTML, null);
  private static final SanitizedContent ATTRIBUTES_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe(
          "attrs", SanitizedContent.ContentKind.ATTRIBUTES, null);
  private static final SanitizedContent CSS_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe("css", SanitizedContent.ContentKind.CSS, null);
  private static final SanitizedContent URI_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe("uri", SanitizedContent.ContentKind.URI, null);
  private static final SanitizedContent TRUSTED_RESOURCE_URI_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe(
          "trusted_resource_uri", SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI, null);
  private static final SanitizedContent JS_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe("js", SanitizedContent.ContentKind.JS, null);
  private static final SoyList LIST_DATA = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newList();
  private static final SoyMap MAP_DATA = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newDict();
  private static final SoyDict DICT_DATA = SoyValueConverter.UNCUSTOMIZED_INSTANCE.newDict();

  @Test
  public void testAnyType() {
    assertThat(AnyType.getInstance().isAssignableFrom(NullType.getInstance())).isTrue();
    assertThat(AnyType.getInstance().isAssignableFrom(AnyType.getInstance())).isTrue();
    assertThat(AnyType.getInstance().isAssignableFrom(UnknownType.getInstance())).isTrue();
    assertThat(AnyType.getInstance().isAssignableFrom(StringType.getInstance())).isTrue();
    assertThat(AnyType.getInstance().isAssignableFrom(IntType.getInstance())).isTrue();
  }

  @Test
  public void testUnknownType() {
    assertThat(UnknownType.getInstance().isAssignableFrom(NullType.getInstance())).isTrue();
    assertThat(UnknownType.getInstance().isAssignableFrom(AnyType.getInstance())).isTrue();
    assertThat(UnknownType.getInstance().isAssignableFrom(UnknownType.getInstance())).isTrue();
    assertThat(UnknownType.getInstance().isAssignableFrom(StringType.getInstance())).isTrue();
    assertThat(UnknownType.getInstance().isAssignableFrom(IntType.getInstance())).isTrue();
  }

  @Test
  public void testNullType() {
    assertThat(NullType.getInstance().isAssignableFrom(NullType.getInstance())).isTrue();
    assertThat(NullType.getInstance().isAssignableFrom(StringType.getInstance())).isFalse();
    assertThat(NullType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(NullType.getInstance().isAssignableFrom(AnyType.getInstance())).isFalse();
    assertThat(NullType.getInstance().isAssignableFrom(UnknownType.getInstance())).isFalse();
  }

  @Test
  public void testStringType() {
    assertThat(StringType.getInstance().isAssignableFrom(StringType.getInstance())).isTrue();
    assertThat(StringType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(StringType.getInstance().isAssignableFrom(NullType.getInstance())).isFalse();
    assertThat(StringType.getInstance().isAssignableFrom(AnyType.getInstance())).isFalse();
    assertThat(StringType.getInstance().isAssignableFrom(UnknownType.getInstance())).isFalse();
  }

  @Test
  public void testPrimitiveTypeEquality() {
    assertThat(AnyType.getInstance().equals(AnyType.getInstance())).isTrue();
    assertThat(AnyType.getInstance().equals(IntType.getInstance())).isFalse();
    assertThat(IntType.getInstance().equals(AnyType.getInstance())).isFalse();
    assertThat(UnknownType.getInstance().equals(UnknownType.getInstance())).isTrue();
  }

  @Test
  public void testSanitizedType() {
    assertThat(StringType.getInstance().isAssignableFrom(HtmlType.getInstance())).isTrue();
    assertThat(StringType.getInstance().isAssignableFrom(CssType.getInstance())).isTrue();
    assertThat(StringType.getInstance().isAssignableFrom(UriType.getInstance())).isTrue();
    assertThat(StringType.getInstance().isAssignableFrom(TrustedResourceUriType.getInstance()))
        .isTrue();
    assertThat(StringType.getInstance().isAssignableFrom(AttributesType.getInstance())).isTrue();
    assertThat(StringType.getInstance().isAssignableFrom(JsType.getInstance())).isTrue();

    assertThat(HtmlType.getInstance().isAssignableFrom(HtmlType.getInstance())).isTrue();
    assertThat(HtmlType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(HtmlType.getInstance().isAssignableFrom(CssType.getInstance())).isFalse();

    assertThat(CssType.getInstance().isAssignableFrom(CssType.getInstance())).isTrue();
    assertThat(CssType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(CssType.getInstance().isAssignableFrom(HtmlType.getInstance())).isFalse();

    assertThat(UriType.getInstance().isAssignableFrom(UriType.getInstance())).isTrue();
    assertThat(UriType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(UriType.getInstance().isAssignableFrom(HtmlType.getInstance())).isFalse();

    assertThat(
            TrustedResourceUriType.getInstance()
                .isAssignableFrom(TrustedResourceUriType.getInstance()))
        .isTrue();
    assertThat(TrustedResourceUriType.getInstance().isAssignableFrom(IntType.getInstance()))
        .isFalse();
    assertThat(TrustedResourceUriType.getInstance().isAssignableFrom(HtmlType.getInstance()))
        .isFalse();

    assertThat(AttributesType.getInstance().isAssignableFrom(AttributesType.getInstance()))
        .isTrue();
    assertThat(AttributesType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(AttributesType.getInstance().isAssignableFrom(HtmlType.getInstance())).isFalse();

    assertThat(JsType.getInstance().isAssignableFrom(JsType.getInstance())).isTrue();
    assertThat(JsType.getInstance().isAssignableFrom(IntType.getInstance())).isFalse();
    assertThat(JsType.getInstance().isAssignableFrom(HtmlType.getInstance())).isFalse();
  }

  @Test
  public void testUnionType() {
    // Test that it flattens properly
    SoyType utype =
        UnionType.of(
            IntType.getInstance(), UnionType.of(IntType.getInstance(), NullType.getInstance()));
    assertThat(utype.toString()).isEqualTo("int|null");
    assertThat(utype.isAssignableFrom(IntType.getInstance())).isTrue();
    assertThat(utype.isAssignableFrom(NullType.getInstance())).isTrue();
    assertThat(utype.isAssignableFrom(FloatType.getInstance())).isFalse();
    assertThat(utype.isAssignableFrom(StringType.getInstance())).isFalse();
    assertThat(utype.isAssignableFrom(AnyType.getInstance())).isFalse();
    assertThat(utype.isAssignableFrom(UnknownType.getInstance())).isFalse();
  }

  @Test
  public void testUnionTypeEquality() {
    assertThat(
            UnionType.of(IntType.getInstance(), BoolType.getInstance())
                .equals(UnionType.of(BoolType.getInstance(), IntType.getInstance())))
        .isTrue();
    assertThat(
            UnionType.of(IntType.getInstance(), BoolType.getInstance())
                .equals(UnionType.of(IntType.getInstance(), StringType.getInstance())))
        .isFalse();
  }

  // Test that list types are covariant over their element types.
  @Test
  public void testListCovariance() {
    ListType listOfAny = ListType.of(AnyType.getInstance());
    ListType listOfString = ListType.of(StringType.getInstance());
    ListType listOfInt = ListType.of(IntType.getInstance());

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
    ListType listOfAny = ListType.of(AnyType.getInstance());
    ListType listOfAny2 = ListType.of(AnyType.getInstance());
    ListType listOfString = ListType.of(StringType.getInstance());

    assertThat(listOfAny.equals(listOfAny2)).isTrue();
    assertThat(listOfAny.equals(listOfString)).isFalse();
  }

  // Test that map types are covariant over their key types.
  @Test
  public void testMapKeyCovariance() {
    MapType mapOfAnyToAny = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    MapType mapOfIntToAny = MapType.of(IntType.getInstance(), AnyType.getInstance());

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
    MapType mapOfAnyToAny = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfAnyToString = MapType.of(AnyType.getInstance(), StringType.getInstance());
    MapType mapOfAnyToInt = MapType.of(AnyType.getInstance(), IntType.getInstance());

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
    MapType mapOfAnyToAny = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfAnyToAny2 = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    MapType mapOfAnyToString = MapType.of(AnyType.getInstance(), StringType.getInstance());

    assertThat(mapOfAnyToAny.equals(mapOfAnyToAny2)).isTrue();
    assertThat(mapOfAnyToAny.equals(mapOfStringToAny)).isFalse();
    assertThat(mapOfAnyToAny.equals(mapOfAnyToString)).isFalse();
  }

  @Test
  public void testRecordTypeEquality() {
    RecordType r1 =
        RecordType.of(
            ImmutableMap.<String, SoyType>of(
                "a", IntType.getInstance(), "b", AnyType.getInstance()));

    assertThat(
            r1.equals(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", IntType.getInstance(), "b", AnyType.getInstance()))))
        .isTrue();
    assertThat(
            r1.equals(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", IntType.getInstance(), "c", AnyType.getInstance()))))
        .isFalse();
    assertThat(
            r1.equals(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", IntType.getInstance(), "b", StringType.getInstance()))))
        .isFalse();
  }

  @Test
  public void testRecordTypeAssignment() {
    RecordType r1 =
        RecordType.of(
            ImmutableMap.<String, SoyType>of(
                "a", IntType.getInstance(), "b", AnyType.getInstance()));

    // Same
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", IntType.getInstance(), "b", AnyType.getInstance()))))
        .isTrue();

    // "b" is subtype
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", IntType.getInstance(), "b", StringType.getInstance()))))
        .isTrue();

    // Additional field
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a",
                        IntType.getInstance(),
                        "b",
                        StringType.getInstance(),
                        "c",
                        StringType.getInstance()))))
        .isTrue();

    // Missing "b"
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", IntType.getInstance(), "c", AnyType.getInstance()))))
        .isFalse();

    // Field type mismatch
    assertThat(
            r1.isAssignableFrom(
                RecordType.of(
                    ImmutableMap.<String, SoyType>of(
                        "a", StringType.getInstance(), "b", AnyType.getInstance()))))
        .isFalse();
  }

  @Test
  public void testAnyTypeIsInstance() {
    assertIsInstance(
        AnyType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testUnknownTypeIsInstance() {
    assertIsInstance(
        UnknownType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testNullTypeIsInstance() {
    assertIsInstance(NullType.getInstance(), NULL_DATA);
    assertIsNotInstance(
        NullType.getInstance(),
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testBoolTypeIsInstance() {
    assertIsInstance(BoolType.getInstance(), BOOLEAN_DATA);
    assertIsNotInstance(
        BoolType.getInstance(),
        NULL_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testStringTypeIsInstance() {
    assertIsInstance(
        StringType.getInstance(),
        STRING_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA);
    assertIsNotInstance(
        StringType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        INTEGER_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testIntTypeIsInstance() {
    assertIsInstance(IntType.getInstance(), INTEGER_DATA);
    assertIsNotInstance(
        IntType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testFloatTypeIsInstance() {
    assertIsInstance(FloatType.getInstance(), FLOAT_DATA);
    assertIsNotInstance(
        FloatType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testSanitizedTypeIsInstance() {
    assertIsInstance(SanitizedType.HtmlType.getInstance(), HTML_DATA);
    assertIsNotInstance(
        SanitizedType.HtmlType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);

    assertIsInstance(SanitizedType.AttributesType.getInstance(), ATTRIBUTES_DATA);
    assertIsNotInstance(
        SanitizedType.AttributesType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);

    assertIsInstance(SanitizedType.CssType.getInstance(), CSS_DATA);
    assertIsNotInstance(
        SanitizedType.CssType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);

    assertIsInstance(SanitizedType.UriType.getInstance(), URI_DATA);
    assertIsNotInstance(
        SanitizedType.UriType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);

    assertIsInstance(SanitizedType.TrustedResourceUriType.getInstance(), TRUSTED_RESOURCE_URI_DATA);
    assertIsNotInstance(
        SanitizedType.TrustedResourceUriType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        JS_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);

    assertIsInstance(SanitizedType.JsType.getInstance(), JS_DATA);
    assertIsNotInstance(
        SanitizedType.JsType.getInstance(),
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        LIST_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testAllContentKindsCovered() {
    Set<SoyType> types = Sets.newIdentityHashSet();
    for (ContentKind kind : ContentKind.values()) {
      SoyType typeForContentKind = SanitizedType.getTypeForContentKind(kind);
      if (kind == ContentKind.TEXT) {
        assertEquals(StringType.getInstance(), typeForContentKind);
      } else {
        assertEquals(kind, ((SanitizedType) typeForContentKind).getContentKind());
      }
      // ensure there is a unique SoyType for every ContentKind
      assertTrue(types.add(typeForContentKind));
    }
  }

  @Test
  public void testListTypeIsInstance() {
    ListType listOfString = ListType.of(StringType.getInstance());
    assertIsInstance(listOfString, LIST_DATA);
    assertIsNotInstance(
        listOfString,
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA,
        MAP_DATA,
        DICT_DATA);
  }

  @Test
  public void testMapTypeIsInstance() {
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    assertIsInstance(mapOfStringToAny, MAP_DATA, LIST_DATA);
    assertIsNotInstance(
        mapOfStringToAny,
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA);
  }

  @Test
  public void testRecordTypeIsInstance() {
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    assertIsInstance(mapOfStringToAny, MAP_DATA, DICT_DATA);
    assertIsNotInstance(
        mapOfStringToAny,
        NULL_DATA,
        BOOLEAN_DATA,
        STRING_DATA,
        INTEGER_DATA,
        FLOAT_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        JS_DATA);
  }

  @Test
  public void testUnionTypeIsInstance() {
    SoyType utype = UnionType.of(IntType.getInstance(), StringType.getInstance());
    assertIsInstance(
        utype,
        INTEGER_DATA,
        STRING_DATA,
        HTML_DATA,
        ATTRIBUTES_DATA,
        CSS_DATA,
        URI_DATA,
        TRUSTED_RESOURCE_URI_DATA,
        JS_DATA);
    assertIsNotInstance(utype, NULL_DATA, BOOLEAN_DATA, FLOAT_DATA, LIST_DATA, MAP_DATA, DICT_DATA);
  }

  @Test
  public void testLeastCommonType() {
    SoyTypeRegistry typeRegistry = new SoyTypeRegistry();

    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, IntType.getInstance(), AnyType.getInstance()))
        .isEqualTo(AnyType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, IntType.getInstance(), UnknownType.getInstance()))
        .isEqualTo(UnknownType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, UnknownType.getInstance(), IntType.getInstance()))
        .isEqualTo(UnknownType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, AnyType.getInstance(), IntType.getInstance()))
        .isEqualTo(AnyType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, StringType.getInstance(), HtmlType.getInstance()))
        .isEqualTo(StringType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, HtmlType.getInstance(), StringType.getInstance()))
        .isEqualTo(StringType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, IntType.getInstance(), FloatType.getInstance()))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
    assertThat(
            SoyTypes.computeLowestCommonType(
                typeRegistry, FloatType.getInstance(), IntType.getInstance()))
        .isEqualTo(UnionType.of(IntType.getInstance(), FloatType.getInstance()));
  }

  @Test
  public void testLeastCommonTypeArithmetic() {
    SoyType intT = IntType.getInstance();
    SoyType anyT = AnyType.getInstance();
    SoyType unknownT = UnknownType.getInstance();
    SoyType floatT = FloatType.getInstance();
    SoyType stringT = StringType.getInstance();
    SoyType htmlT = HtmlType.getInstance();
    SoyType numberT = SoyTypes.NUMBER_TYPE;

    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(intT, anyT)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(anyT, intT)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(stringT, htmlT)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(htmlT, stringT)).isAbsent();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(intT, floatT)).hasValue(floatT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(floatT, intT)).hasValue(floatT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(floatT, unknownT)).hasValue(unknownT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(unknownT, floatT)).hasValue(unknownT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(intT, intT)).hasValue(intT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(floatT, floatT)).hasValue(floatT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(floatT, numberT)).hasValue(numberT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(intT, numberT)).hasValue(numberT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(numberT, numberT)).hasValue(numberT);
  }

  private static void assertIsInstance(SoyType type, SoyValue... values) {
    for (SoyValue value : values) {
      assertWithMessage(
              "Expected value of type "
                  + value.getClass().getName()
                  + " to be an instance of Soy type "
                  + type)
          .that(type.isInstance(value))
          .isTrue();
    }
  }

  private static void assertIsNotInstance(SoyType type, SoyValue... values) {
    for (SoyValue value : values) {
      assertWithMessage(
              "Expected value of type "
                  + value.getClass().getName()
                  + " to NOT be an instance of Soy type "
                  + type)
          .that(type.isInstance(value))
          .isFalse();
    }
  }
}
