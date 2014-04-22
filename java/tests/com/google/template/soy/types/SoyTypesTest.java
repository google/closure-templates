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
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueHelper;
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
import com.google.template.soy.types.primitive.SanitizedType.UriType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import junit.framework.TestCase;


/**
 * Unit tests for soy types.
 */
public class SoyTypesTest extends TestCase {
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
  private static final SanitizedContent JS_DATA =
      UnsafeSanitizedContentOrdainer.ordainAsSafe("js", SanitizedContent.ContentKind.JS, null);
  private static final SoyList LIST_DATA = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyList();
  private static final SoyMap MAP_DATA = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyDict();
  private static final SoyDict DICT_DATA = SoyValueHelper.UNCUSTOMIZED_INSTANCE.newEasyDict();


  public void testAnyType() {
    assertTrue(AnyType.getInstance().isAssignableFrom(NullType.getInstance()));
    assertTrue(AnyType.getInstance().isAssignableFrom(AnyType.getInstance()));
    assertTrue(AnyType.getInstance().isAssignableFrom(UnknownType.getInstance()));
    assertTrue(AnyType.getInstance().isAssignableFrom(StringType.getInstance()));
    assertTrue(AnyType.getInstance().isAssignableFrom(IntType.getInstance()));
  }


  public void testUnknownType() {
    assertTrue(UnknownType.getInstance().isAssignableFrom(NullType.getInstance()));
    assertTrue(UnknownType.getInstance().isAssignableFrom(AnyType.getInstance()));
    assertTrue(UnknownType.getInstance().isAssignableFrom(UnknownType.getInstance()));
    assertTrue(UnknownType.getInstance().isAssignableFrom(StringType.getInstance()));
    assertTrue(UnknownType.getInstance().isAssignableFrom(IntType.getInstance()));
  }


  public void testNullType() {
    assertTrue(NullType.getInstance().isAssignableFrom(NullType.getInstance()));
    assertFalse(NullType.getInstance().isAssignableFrom(StringType.getInstance()));
    assertFalse(NullType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(NullType.getInstance().isAssignableFrom(AnyType.getInstance()));
    assertFalse(NullType.getInstance().isAssignableFrom(UnknownType.getInstance()));
  }


  public void testStringType() {
    assertTrue(StringType.getInstance().isAssignableFrom(StringType.getInstance()));
    assertFalse(StringType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(StringType.getInstance().isAssignableFrom(NullType.getInstance()));
    assertFalse(StringType.getInstance().isAssignableFrom(AnyType.getInstance()));
    assertFalse(StringType.getInstance().isAssignableFrom(UnknownType.getInstance()));
  }


  public void testPrimitiveTypeEquality() {
    assertTrue(AnyType.getInstance().equals(AnyType.getInstance()));
    assertFalse(AnyType.getInstance().equals(IntType.getInstance()));
    assertFalse(IntType.getInstance().equals(AnyType.getInstance()));
    assertTrue(UnknownType.getInstance().equals(UnknownType.getInstance()));
  }


  public void testSanitizedType() {
    assertTrue(StringType.getInstance().isAssignableFrom(HtmlType.getInstance()));
    assertTrue(StringType.getInstance().isAssignableFrom(CssType.getInstance()));
    assertTrue(StringType.getInstance().isAssignableFrom(UriType.getInstance()));
    assertTrue(StringType.getInstance().isAssignableFrom(AttributesType.getInstance()));
    assertTrue(StringType.getInstance().isAssignableFrom(JsType.getInstance()));

    assertTrue(HtmlType.getInstance().isAssignableFrom(HtmlType.getInstance()));
    assertFalse(HtmlType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(HtmlType.getInstance().isAssignableFrom(CssType.getInstance()));

    assertTrue(CssType.getInstance().isAssignableFrom(CssType.getInstance()));
    assertFalse(CssType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(CssType.getInstance().isAssignableFrom(HtmlType.getInstance()));

    assertTrue(UriType.getInstance().isAssignableFrom(UriType.getInstance()));
    assertFalse(UriType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(UriType.getInstance().isAssignableFrom(HtmlType.getInstance()));

    assertTrue(AttributesType.getInstance().isAssignableFrom(AttributesType.getInstance()));
    assertFalse(AttributesType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(AttributesType.getInstance().isAssignableFrom(HtmlType.getInstance()));

    assertTrue(JsType.getInstance().isAssignableFrom(JsType.getInstance()));
    assertFalse(JsType.getInstance().isAssignableFrom(IntType.getInstance()));
    assertFalse(JsType.getInstance().isAssignableFrom(HtmlType.getInstance()));
  }


  public void testUnionType() {
    // Test that it flattens properly
    UnionType utype = UnionType.of(
        IntType.getInstance(),
        UnionType.of(IntType.getInstance(), NullType.getInstance()));
    assertEquals("int|null", utype.toString());
    assertTrue(utype.isAssignableFrom(IntType.getInstance()));
    assertTrue(utype.isAssignableFrom(NullType.getInstance()));
    assertFalse(utype.isAssignableFrom(FloatType.getInstance()));
    assertFalse(utype.isAssignableFrom(StringType.getInstance()));
    assertFalse(utype.isAssignableFrom(AnyType.getInstance()));
    assertFalse(utype.isAssignableFrom(UnknownType.getInstance()));
  }


  public void testUnionTypeEquality() {
    assertTrue(
        UnionType.of(IntType.getInstance(), BoolType.getInstance()).equals(
            UnionType.of(BoolType.getInstance(), IntType.getInstance())));
    assertFalse(
        UnionType.of(IntType.getInstance(), BoolType.getInstance()).equals(
            UnionType.of(IntType.getInstance(), StringType.getInstance())));
  }


  // Test that list types are covariant over their element types.
  public void testListCovariance() {
    ListType listOfAny = ListType.of(AnyType.getInstance());
    ListType listOfString = ListType.of(StringType.getInstance());
    ListType listOfInt = ListType.of(IntType.getInstance());

    // Legal to assign List<X> to List<X>
    assertTrue(listOfAny.isAssignableFrom(listOfAny));
    assertTrue(listOfString.isAssignableFrom(listOfString));
    assertTrue(listOfInt.isAssignableFrom(listOfInt));

    // Legal to assign List<X> to List<Y> where Y <: X
    assertTrue(listOfAny.isAssignableFrom(listOfString));
    assertTrue(listOfAny.isAssignableFrom(listOfInt));

    // Not legal to assign List<X> to List<Y> where !(Y <: X)
    assertFalse(listOfInt.isAssignableFrom(listOfString));
    assertFalse(listOfString.isAssignableFrom(listOfInt));
    assertFalse(listOfInt.isAssignableFrom(listOfAny));
    assertFalse(listOfString.isAssignableFrom(listOfAny));
  }


  public void testListTypeEquality() {
    ListType listOfAny = ListType.of(AnyType.getInstance());
    ListType listOfAny2 = ListType.of(AnyType.getInstance());
    ListType listOfString = ListType.of(StringType.getInstance());

    assertTrue(listOfAny.equals(listOfAny2));
    assertFalse(listOfAny.equals(listOfString));
  }


  // Test that map types are covariant over their key types.
  public void testMapKeyCovariance() {
    MapType mapOfAnyToAny = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    MapType mapOfIntToAny = MapType.of(IntType.getInstance(), AnyType.getInstance());

    // Legal to assign Map<X, Y> to Map<X, Y>
    assertTrue(mapOfAnyToAny.isAssignableFrom(mapOfAnyToAny));
    assertTrue(mapOfStringToAny.isAssignableFrom(mapOfStringToAny));
    assertTrue(mapOfIntToAny.isAssignableFrom(mapOfIntToAny));

    // Legal to assign Map<X, Z> to Map<Y, Z> where Y <: X
    assertTrue(mapOfAnyToAny.isAssignableFrom(mapOfStringToAny));
    assertTrue(mapOfAnyToAny.isAssignableFrom(mapOfIntToAny));

    // Not legal to assign Map<X, Z> to Map<Y, Z> where !(Y <: X)
    assertFalse(mapOfIntToAny.isAssignableFrom(mapOfStringToAny));
    assertFalse(mapOfStringToAny.isAssignableFrom(mapOfIntToAny));
    assertFalse(mapOfIntToAny.isAssignableFrom(mapOfAnyToAny));
    assertFalse(mapOfStringToAny.isAssignableFrom(mapOfAnyToAny));
  }


  // Test that map types are covariant over their value types.
  public void testMapValueCovariance() {
    MapType mapOfAnyToAny = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfAnyToString = MapType.of(AnyType.getInstance(), StringType.getInstance());
    MapType mapOfAnyToInt = MapType.of(AnyType.getInstance(), IntType.getInstance());

    // Legal to assign Map<X, Y> to Map<X, Y>
    assertTrue(mapOfAnyToAny.isAssignableFrom(mapOfAnyToAny));
    assertTrue(mapOfAnyToString.isAssignableFrom(mapOfAnyToString));
    assertTrue(mapOfAnyToInt.isAssignableFrom(mapOfAnyToInt));

    // Legal to assign Map<X, Y> to Map<X, Z> where Z <: Y
    assertTrue(mapOfAnyToAny.isAssignableFrom(mapOfAnyToString));
    assertTrue(mapOfAnyToAny.isAssignableFrom(mapOfAnyToInt));

    // Not legal to assign Map<X, Y> to Map<X, Z> where !(Z <: Y)
    assertFalse(mapOfAnyToInt.isAssignableFrom(mapOfAnyToString));
    assertFalse(mapOfAnyToString.isAssignableFrom(mapOfAnyToInt));
    assertFalse(mapOfAnyToInt.isAssignableFrom(mapOfAnyToAny));
    assertFalse(mapOfAnyToString.isAssignableFrom(mapOfAnyToAny));
  }


  public void testMapTypeEquality() {
    MapType mapOfAnyToAny = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfAnyToAny2 = MapType.of(AnyType.getInstance(), AnyType.getInstance());
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    MapType mapOfAnyToString = MapType.of(AnyType.getInstance(), StringType.getInstance());

    assertTrue(mapOfAnyToAny.equals(mapOfAnyToAny2));
    assertFalse(mapOfAnyToAny.equals(mapOfStringToAny));
    assertFalse(mapOfAnyToAny.equals(mapOfAnyToString));
  }


  public void testRecordTypeEquality() {
    RecordType r1 = RecordType.of(ImmutableMap.<String, SoyType>of(
        "a", IntType.getInstance(), "b", AnyType.getInstance()));

    assertTrue(r1.equals(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "b", AnyType.getInstance()))));
    assertFalse(r1.equals(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "c", AnyType.getInstance()))));
    assertFalse(r1.equals(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "b", StringType.getInstance()))));
  }


  public void testRecordTypeAssignment() {
    RecordType r1 = RecordType.of(ImmutableMap.<String, SoyType>of(
        "a", IntType.getInstance(), "b", AnyType.getInstance()));

    // Same
    assertTrue(r1.isAssignableFrom(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "b", AnyType.getInstance()))));

    // "b" is subtype
    assertTrue(r1.isAssignableFrom(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "b", StringType.getInstance()))));

    // Additional field
    assertTrue(r1.isAssignableFrom(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "b", StringType.getInstance(),
            "c", StringType.getInstance()))));

    // Missing "b"
    assertFalse(r1.isAssignableFrom(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", IntType.getInstance(),
            "c", AnyType.getInstance()))));

    // Field type mismatch
    assertFalse(r1.isAssignableFrom(
        RecordType.of(ImmutableMap.<String, SoyType>of(
            "a", StringType.getInstance(),
            "b", AnyType.getInstance()))));

  }


  public void testAnyTypeIsInstance() {
    assertIsInstance(AnyType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testUnknownTypeIsInstance() {
    assertIsInstance(UnknownType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testNullTypeIsInstance() {
    assertIsInstance(NullType.getInstance(), NULL_DATA);
    assertIsNotInstance(NullType.getInstance(),
        BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testBoolTypeIsInstance() {
    assertIsInstance(BoolType.getInstance(), BOOLEAN_DATA);
    assertIsNotInstance(BoolType.getInstance(),
        NULL_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testStringTypeIsInstance() {
    assertIsInstance(StringType.getInstance(),
        STRING_DATA, HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA);
    assertIsNotInstance(StringType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, INTEGER_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testIntTypeIsInstance() {
    assertIsInstance(IntType.getInstance(), INTEGER_DATA);
    assertIsNotInstance(IntType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testFloatTypeIsInstance() {
    assertIsInstance(FloatType.getInstance(), FLOAT_DATA);
    assertIsNotInstance(FloatType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testSanitizedTypeIsInstance() {
    assertIsInstance(SanitizedType.HtmlType.getInstance(), HTML_DATA);
    assertIsNotInstance(SanitizedType.HtmlType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);

    assertIsInstance(SanitizedType.AttributesType.getInstance(), ATTRIBUTES_DATA);
    assertIsNotInstance(SanitizedType.AttributesType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, CSS_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);

    assertIsInstance(SanitizedType.CssType.getInstance(), CSS_DATA);
    assertIsNotInstance(SanitizedType.CssType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, URI_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);

    assertIsInstance(SanitizedType.UriType.getInstance(), URI_DATA);
    assertIsNotInstance(SanitizedType.UriType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, JS_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);

    assertIsInstance(SanitizedType.JsType.getInstance(), JS_DATA);
    assertIsNotInstance(SanitizedType.JsType.getInstance(),
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  public void testListTypeIsInstance() {
    ListType listOfString = ListType.of(StringType.getInstance());
    assertIsInstance(listOfString, LIST_DATA);
    assertIsNotInstance(listOfString,
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA,
        MAP_DATA, DICT_DATA);
  }


  public void testMapTypeIsInstance() {
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    assertIsInstance(mapOfStringToAny, MAP_DATA, LIST_DATA);
    assertIsNotInstance(mapOfStringToAny,
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA);
  }


  public void testRecordTypeIsInstance() {
    MapType mapOfStringToAny = MapType.of(StringType.getInstance(), AnyType.getInstance());
    assertIsInstance(mapOfStringToAny, MAP_DATA, DICT_DATA);
    assertIsNotInstance(mapOfStringToAny,
        NULL_DATA, BOOLEAN_DATA, STRING_DATA, INTEGER_DATA, FLOAT_DATA,
        HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA);
  }


  public void testUnionTypeIsInstance() {
    UnionType utype = UnionType.of(IntType.getInstance(), StringType.getInstance());
    assertIsInstance(utype,
        INTEGER_DATA, STRING_DATA, HTML_DATA, ATTRIBUTES_DATA, CSS_DATA, URI_DATA, JS_DATA);
    assertIsNotInstance(utype,
        NULL_DATA, BOOLEAN_DATA, FLOAT_DATA,
        LIST_DATA, MAP_DATA, DICT_DATA);
  }


  private void assertIsInstance(SoyType type, SoyValue... values) {
    for (SoyValue value : values) {
      assertTrue("Expected value of type " + value.getClass().getName() +
          " to be an instance of Soy type " + type,
          type.isInstance(value));
    }
  }


  private void assertIsNotInstance(SoyType type, SoyValue... values) {
    for (SoyValue value : values) {
      assertFalse("Expected value of type " + value.getClass().getName() +
          " to NOT be an instance of Soy type " + type,
          type.isInstance(value));
    }
  }
}
