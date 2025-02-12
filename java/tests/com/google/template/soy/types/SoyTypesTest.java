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
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertThat;
import static com.google.template.soy.types.SoyTypes.makeNullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.SanitizedType.ElementType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeConverter;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for soy types. */
@RunWith(JUnit4.class)
public class SoyTypesTest {
  private static final BoolType BOOL_TYPE = BoolType.getInstance();
  private static final NumberType NUMBER_TYPE = NumberType.getInstance();
  private static final NumberType FLOAT_TYPE = NumberType.getInstance();
  private static final StringType STRING_TYPE = StringType.getInstance();
  private static final HtmlType HTML_TYPE = HtmlType.getInstance();
  private static final NullType NULL_TYPE = NullType.getInstance();
  private static final AnyType ANY_TYPE = AnyType.getInstance();
  private static final UnknownType UNKNOWN_TYPE = UnknownType.getInstance();
  private static final UriType URI_TYPE = UriType.getInstance();
  private static final UndefinedType UNDEFINED_TYPE = UndefinedType.getInstance();

  @Test
  public void testAnyType() {
    assertThatSoyType("any").isAssignableFromStrict("null");
    assertThatSoyType("any").isAssignableFromStrict("undefined");
    assertThatSoyType("any").isAssignableFromStrict("any");
    assertThatSoyType("any").isAssignableFromStrict("?");
    assertThatSoyType("any").isAssignableFromStrict("string");
    assertThatSoyType("any").isAssignableFromStrict("int");
  }

  @Test
  public void testUnknownType() {
    assertThatSoyType("?").isAssignableFromStrict("null");
    assertThatSoyType("?").isAssignableFromStrict("undefined");
    assertThatSoyType("?").isAssignableFromStrict("any");
    assertThatSoyType("?").isAssignableFromStrict("?");
    assertThatSoyType("?").isAssignableFromStrict("string");
    assertThatSoyType("?").isAssignableFromStrict("int");
  }

  @Test
  public void testNeverType() {
    assertThatSoyType("never").isNotAssignableFromStrict("never");
    assertThatSoyType("any").isNotAssignableFromStrict("never");
    assertThatSoyType("?").isNotAssignableFromStrict("never");
    assertThatSoyType("null").isNotAssignableFromStrict("never");
    assertThatSoyType("never").isNotAssignableFromStrict("any");
    assertThatSoyType("never").isNotAssignableFromStrict("?");
    assertThatSoyType("never").isNotAssignableFromStrict("null");
  }

  @Test
  public void testNullType() {
    assertThatSoyType("null").isAssignableFromStrict("null");
    assertThatSoyType("null").isNotAssignableFromStrict("undefined");
    assertThatSoyType("null").isNotAssignableFromStrict("string");
    assertThatSoyType("null").isNotAssignableFromStrict("int");
    assertThatSoyType("null").isNotAssignableFromStrict("any");
    assertThatSoyType("null").isNotAssignableFromStrict("?");
    assertThatSoyType("null").isAssignableFromLoose("?");
  }

  @Test
  public void testUndefinedType() {
    assertThatSoyType("undefined").isAssignableFromStrict("undefined");
    assertThatSoyType("undefined").isNotAssignableFromStrict("null");
    assertThatSoyType("undefined").isNotAssignableFromStrict("string");
    assertThatSoyType("undefined").isNotAssignableFromStrict("int");
    assertThatSoyType("undefined").isNotAssignableFromStrict("any");
    assertThatSoyType("undefined").isNotAssignableFromStrict("?");
    assertThatSoyType("undefined").isAssignableFromLoose("?");
  }

  @Test
  public void testStringType() {
    assertThatSoyType("string").isAssignableFromStrict("string");
    assertThatSoyType("string").isNotAssignableFromStrict("int");
    assertThatSoyType("string").isNotAssignableFromStrict("null");
    assertThatSoyType("string").isNotAssignableFromStrict("any");
    assertThatSoyType("string").isNotAssignableFromStrict("?");
    assertThatSoyType("string").isAssignableFromLoose("string");
    assertThatSoyType("string").isNotAssignableFromStrict("int");
    assertThatSoyType("string").isNotAssignableFromStrict("null");
    assertThatSoyType("string").isNotAssignableFromStrict("any");
    assertThatSoyType("string").isNotAssignableFromStrict("?");
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
    assertThatSoyType("string").isNotAssignableFromStrict("html");
    assertThatSoyType("string").isNotAssignableFromStrict("css");
    assertThatSoyType("string").isNotAssignableFromStrict("uri");
    assertThatSoyType("string").isNotAssignableFromStrict("trusted_resource_uri");
    assertThatSoyType("string").isNotAssignableFromStrict("attributes");
    assertThatSoyType("string").isNotAssignableFromStrict("js");

    assertThatSoyType("html").isAssignableFromStrict("html");
    assertThatSoyType("html").isNotAssignableFromStrict("int");
    assertThatSoyType("html").isNotAssignableFromStrict("css");

    assertThatSoyType("css").isAssignableFromStrict("css");
    assertThatSoyType("css").isNotAssignableFromStrict("int");
    assertThatSoyType("css").isNotAssignableFromStrict("html");

    assertThatSoyType("uri").isAssignableFromStrict("uri");
    assertThatSoyType("uri").isNotAssignableFromStrict("int");
    assertThatSoyType("uri").isNotAssignableFromStrict("html");

    assertThatSoyType("trusted_resource_uri").isAssignableFromStrict("trusted_resource_uri");
    assertThatSoyType("trusted_resource_uri").isNotAssignableFromStrict("int");
    assertThatSoyType("trusted_resource_uri").isNotAssignableFromStrict("html");

    assertThatSoyType("attributes").isAssignableFromStrict("attributes");
    assertThatSoyType("attributes").isNotAssignableFromStrict("int");
    assertThatSoyType("attributes").isNotAssignableFromStrict("html");

    assertThatSoyType("js").isAssignableFromStrict("js");
    assertThatSoyType("js").isNotAssignableFromStrict("int");
    assertThatSoyType("js").isNotAssignableFromStrict("html");
  }

  @Test
  public void testUnionType() {
    // Test that it flattens properly
    SoyType utype = UnionType.of(NUMBER_TYPE, UnionType.of(NUMBER_TYPE, NULL_TYPE));
    assertThat(utype.toString()).isEqualTo("null|number");
    assertThatSoyType("int|null").isAssignableFromStrict("int");
    assertThatSoyType("int|null").isAssignableFromStrict("null");
    assertThatSoyType("int|null").isAssignableFromStrict("float");
    assertThatSoyType("int|null").isNotAssignableFromStrict("string");
    assertThatSoyType("int|null").isNotAssignableFromStrict("any");
    assertThatSoyType("int|null").isNotAssignableFromStrict("?");
    assertThatSoyType("int|null").isAssignableFromLoose("?");
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
    assertThatSoyType("list<int|string>").isAssignableFromStrict("list<int>|list<string>");
    assertThatSoyType("list<int>|list<string>").isNotAssignableFromStrict("list<int|string>");

    assertThatSoyType("list<[field: int|string]>")
        .isAssignableFromStrict("list<[field: string]>|list<[field: int]>");
    assertThatSoyType("list<[field: string]>|list<[field: int]>")
        .isNotAssignableFromStrict("list<[field: int|string]>");

    assertThatSoyType("map<string, int|string>")
        .isAssignableFromStrict("map<string, int>|map<string, string>");
    assertThatSoyType("map<string, int>|map<string, string>")
        .isNotAssignableFromStrict("map<string, int|string>");
  }

  // Test that list types are covariant over their element types.
  @Test
  public void testListCovariance() {
    // Legal to assign List<X> to List<X>
    assertThatSoyType("list<any>").isAssignableFromStrict("list<any>");
    assertThatSoyType("list<string>").isAssignableFromStrict("list<string>");
    assertThatSoyType("list<int>").isAssignableFromStrict("list<int>");

    // Legal to assign List<X> to List<Y> where Y <: X
    assertThatSoyType("list<any>").isAssignableFromStrict("list<string>");
    assertThatSoyType("list<any>").isAssignableFromStrict("list<int>");

    // Not legal to assign List<X> to List<Y> where !(Y <: X)
    assertThatSoyType("list<int>").isNotAssignableFromStrict("list<string>");
    assertThatSoyType("list<string>").isNotAssignableFromStrict("list<int>");
    assertThatSoyType("list<int>").isNotAssignableFromStrict("list<any>");
    assertThatSoyType("list<string>").isNotAssignableFromStrict("list<any>");
    assertThatSoyType("list<string>").isNotAssignableFromStrict("list<?>");
    assertThatSoyType("list<string>").isAssignableFromLoose("list<?>");
  }

  @Test
  public void testIterable() {
    assertThatSoyType("iterable<string>").isAssignableFromStrict("iterable<string>");
    assertThatSoyType("iterable<?>").isAssignableFromStrict("iterable<string>");
    assertThatSoyType("iterable<string>").isAssignableFromStrict("list<string>");
    assertThatSoyType("iterable<?>").isAssignableFromStrict("list<string>");
    assertThatSoyType("iterable<string>").isAssignableFromStrict("set<string>");
    assertThatSoyType("iterable<?>").isAssignableFromStrict("set<string>");
    assertThatSoyType("iterable<string>").isAssignableFromStrict("set<string>|list<string>");

    assertThatSoyType("list<string>").isNotAssignableFromStrict("iterable<string>");
    assertThatSoyType("set<string>").isNotAssignableFromStrict("iterable<string>");
  }

  @Test
  public void testListTypeEquality() {
    assertThatSoyType("list<any>").isEqualTo("list<any>");
    assertThatSoyType("list<any>").isNotEqualTo("list<string>");
  }

  // Test that map types are covariant over their key types.
  @Test
  public void testLegacyObjectMapKeyCovariance() {
    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFromStrict("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<string, any>")
        .isAssignableFromStrict("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<int, any>")
        .isAssignableFromStrict("legacy_object_map<int, any>");

    // Legal to assign Map<X, Z> to Map<Y, Z> where Y <: X
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFromStrict("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFromStrict("legacy_object_map<int, any>");

    // Not legal to assign Map<X, Z> to Map<Y, Z> where !(Y <: X)
    assertThatSoyType("legacy_object_map<int, any>")
        .isNotAssignableFromStrict("legacy_object_map<string, any>");
    assertThatSoyType("legacy_object_map<string, any>")
        .isNotAssignableFromStrict("legacy_object_map<int, any>");
    assertThatSoyType("legacy_object_map<int, any>")
        .isNotAssignableFromStrict("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<string, any>")
        .isNotAssignableFromStrict("legacy_object_map<any, any>");
  }

  // Test that map types are covariant over their value types.
  @Test
  public void testLegacyObjectMapValueCovariance() {
    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFromStrict("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<any, string>")
        .isAssignableFromStrict("legacy_object_map<any, string>");
    assertThatSoyType("legacy_object_map<any, int>")
        .isAssignableFromStrict("legacy_object_map<any, int>");

    // Legal to assign Map<X, Y> to Map<X, Z> where Z <: Y
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFromStrict("legacy_object_map<any, string>");
    assertThatSoyType("legacy_object_map<any, any>")
        .isAssignableFromStrict("legacy_object_map<any, int>");

    // Not legal to assign Map<X, Y> to Map<X, Z> where !(Z <: Y)
    assertThatSoyType("legacy_object_map<any, int>")
        .isNotAssignableFromStrict("legacy_object_map<any, string>");
    assertThatSoyType("legacy_object_map<any, string>")
        .isNotAssignableFromStrict("legacy_object_map<any, int>");
    assertThatSoyType("legacy_object_map<any, int>")
        .isNotAssignableFromStrict("legacy_object_map<any, any>");
    assertThatSoyType("legacy_object_map<any, string>")
        .isNotAssignableFromStrict("legacy_object_map<any, any>");
  }

  // Test that map types are covariant over their value types.
  @Test
  public void testMapValueCovariance() {
    // Legal to assign Map<X, Y> to Map<X, Y>
    assertThatSoyType("map<int,any>").isAssignableFromStrict("map<int,any>");
    assertThatSoyType("map<int,string>").isAssignableFromStrict("map<int,string>");
    assertThatSoyType("map<int,int>").isAssignableFromStrict("map<int,int>");

    // Legal to assign Map<X, Y> to Map<X, Z> where Z <: Y
    assertThatSoyType("map<int,any>").isAssignableFromStrict("map<int,string>");
    assertThatSoyType("map<int,any>").isAssignableFromStrict("map<int,int>");

    // Not legal to assign Map<X, Y> to Map<X, Z> where !(Z <: Y)
    assertThatSoyType("map<int,int>").isNotAssignableFromStrict("map<int,string>");
    assertThatSoyType("map<int,string>").isNotAssignableFromStrict("map<int,int>");
    assertThatSoyType("map<int,int>").isNotAssignableFromStrict("map<int,any>");
    assertThatSoyType("map<int,string>").isNotAssignableFromStrict("map<int,any>");
  }

  @Test
  public void testNamed() {
    SoyTypeRegistry baseRegistry = SoyTypeRegistryBuilder.create();
    SoyType stringAlias = NamedType.create("StringAlias", "-", StringType.getInstance());
    SoyType intAlias = NamedType.create("IntAlias", "-", NumberType.getInstance());
    SoyType unionOfAliases =
        NamedType.create("UnionOfAliases", "-", UnionType.of(stringAlias, intAlias));
    SoyType anotherUnion =
        NamedType.create("AnotherUnion", "-", UnionType.of(unionOfAliases, BoolType.getInstance()));

    SoyTypeRegistry registry =
        new DelegatingSoyTypeRegistry(baseRegistry) {
          private final ImmutableMap<String, SoyType> namedTypes =
              ImmutableMap.of(
                  "StringAlias",
                  stringAlias,
                  "IntAlias",
                  intAlias,
                  "UnionOfAliases",
                  unionOfAliases,
                  "AnotherUnion",
                  anotherUnion,
                  "BoolOrNumber",
                  NamedType.create("BoolOrNumber", "-", parseType("bool|int|float", baseRegistry)));

          @Override
          public SoyType getType(String typeName) {
            if (namedTypes.containsKey(typeName)) {
              return namedTypes.get(typeName);
            }
            return super.getType(typeName);
          }
        };

    assertThatSoyType("StringAlias", registry).isEqualTo("StringAlias");
    assertThatSoyType("StringAlias", registry).isNotEqualTo("IntAlias");

    assertThatSoyType("StringAlias", registry).isAssignableFromStrict("StringAlias");
    assertThatSoyType("StringAlias", registry).isAssignableFromStrict("string");
    assertThatSoyType("string", registry).isAssignableFromStrict("StringAlias");
    assertThatSoyType("IntAlias", registry).isAssignableFromStrict("IntAlias");
    assertThatSoyType("IntAlias", registry).isNotAssignableFromStrict("StringAlias");
    assertThatSoyType("StringAlias", registry).isNotAssignableFromStrict("IntAlias");

    assertThatSoyType("UnionOfAliases", registry).isAssignableFromStrict("UnionOfAliases");
    assertThatSoyType("UnionOfAliases", registry).isAssignableFromStrict("string|int");
    assertThatSoyType("string|int", registry).isAssignableFromStrict("UnionOfAliases");
    assertThatSoyType("UnionOfAliases", registry).isAssignableFromStrict("string");
    assertThatSoyType("UnionOfAliases", registry).isAssignableFromStrict("int");
    assertThatSoyType("UnionOfAliases", registry).isAssignableFromStrict("StringAlias");
    assertThatSoyType("UnionOfAliases", registry).isAssignableFromStrict("IntAlias");

    assertThatSoyType("AnotherUnion", registry).isAssignableFromStrict("AnotherUnion");
    assertThatSoyType("AnotherUnion", registry).isAssignableFromStrict("UnionOfAliases");
    assertThatSoyType("UnionOfAliases", registry).isNotAssignableFromStrict("AnotherUnion");
    assertThatSoyType("AnotherUnion", registry).isAssignableFromStrict("string|int|bool");
    assertThatSoyType("string|int|bool", registry).isAssignableFromStrict("AnotherUnion");

    assertThatSoyType("BoolOrNumber", registry).isAssignableFromStrict("BoolOrNumber");
    assertThatSoyType("BoolOrNumber", registry).isAssignableFromStrict("int");
    assertThatSoyType("BoolOrNumber", registry).isAssignableFromStrict("IntAlias");
    assertThatSoyType("int", registry).isNotAssignableFromStrict("BoolOrNumber");
    assertThatSoyType("IntAlias", registry).isNotAssignableFromStrict("BoolOrNumber");
  }

  @Test
  public void testIndexed() {
    SoyTypeRegistry baseRegistry = SoyTypeRegistryBuilder.create();
    SoyType stringAlias = NamedType.create("StringAlias", "-", StringType.getInstance());
    SoyType rec1 =
        NamedType.create(
            "Rec1",
            "-",
            parseType("[a: string, b: number, c: bool, d: string|bool]", baseRegistry));

    SoyTypeRegistry registry =
        new DelegatingSoyTypeRegistry(baseRegistry) {
          private final ImmutableMap<String, SoyType> namedTypes =
              ImmutableMap.of("StringAlias", stringAlias, "Rec1", rec1);

          @Override
          public SoyType getType(String typeName) {
            if (namedTypes.containsKey(typeName)) {
              return namedTypes.get(typeName);
            }
            return super.getType(typeName);
          }
        };

    assertThatSoyType("Rec1['a']", registry).isAssignableFromStrict("string");
    assertThatSoyType("string", registry).isAssignableFromStrict("Rec1['a']");

    assertThatSoyType("Rec1['b']", registry).isAssignableFromStrict("number");
    assertThatSoyType("Rec1['c']", registry).isAssignableFromStrict("bool");
    assertThatSoyType("Rec1['d']", registry).isAssignableFromStrict("string");
    assertThatSoyType("Rec1['d']", registry).isAssignableFromStrict("bool");

    assertThatSoyType("Rec1['d']", registry).isAssignableFromStrict("Rec1['a']");
    assertThatSoyType("Rec1['a']", registry).isNotAssignableFromStrict("Rec1['d']");
  }

  @Test
  public void testIntersection() {
    assertThatSoyType("[a: string] & [b: string]").isAssignableFromStrict("[a: string, b: string]");
    assertThatSoyType("[a: string] & [b: string]")
        .isAssignableFromStrict("[a: string, b: string, c: string]");
    assertThatSoyType("[a: string] & [b: string]").isNotAssignableFromStrict("[a: string]");
    assertThatSoyType("[a: string] & [b?: string]").isAssignableFromStrict("[a: string]");

    // incompatible fields, never assignable
    assertThatSoyType("[a: any]").isNotAssignableFromStrict("[a: string] & [a: bool]");

    // not record types => never
    assertThatSoyType("any").isNotAssignableFromStrict("string & bool");
  }

  @Test
  public void testNamedIntersection() {
    SoyTypeRegistry baseRegistry = SoyTypeRegistryBuilder.create();
    SoyTypeRegistry registry =
        new DelegatingSoyTypeRegistry(baseRegistry) {
          private final ImmutableMap<String, SoyType> namedTypes =
              ImmutableMap.of(
                  "Rec1",
                  NamedType.create("Rec1", "-", parseType("[a: string]", baseRegistry)),
                  "Rec2",
                  NamedType.create("Rec2", "-", parseType("[b: string]", baseRegistry)),
                  "Rec3",
                  NamedType.create(
                      "Rec3", "-", parseType("[a: string, b: string, c: string]", baseRegistry)));

          @Override
          public SoyType getType(String typeName) {
            if (namedTypes.containsKey(typeName)) {
              return namedTypes.get(typeName);
            }
            return super.getType(typeName);
          }
        };

    assertThatSoyType("Rec1", registry).isAssignableFromStrict("Rec1");
    assertThatSoyType("Rec1", registry).isNotAssignableFromStrict("Rec2");
    assertThatSoyType("Rec2", registry).isNotAssignableFromStrict("Rec1");
    assertThatSoyType("Rec1", registry).isAssignableFromStrict("Rec3");
    assertThatSoyType("Rec2", registry).isAssignableFromStrict("Rec3");

    assertThatSoyType("Rec1 & Rec2", registry).isAssignableFromStrict("Rec3");
    assertThatSoyType("Rec1 & Rec1", registry).isAssignableFromStrict("Rec1");
    assertThatSoyType("Rec1 & Rec1", registry).isAssignableFromStrict("Rec3");
    assertThatSoyType("Rec1 & Rec3", registry).isNotAssignableFromStrict("Rec2");
  }

  @Test
  public void testMapTypeAssignability() {
    assertThat(MapType.of(ANY_TYPE, ANY_TYPE).isAssignableFromStrict(UNKNOWN_TYPE)).isFalse();
    assertThat(UNKNOWN_TYPE.isAssignableFromStrict(MapType.of(ANY_TYPE, ANY_TYPE))).isFalse();
  }

  @Test
  public void testElementTypeAssignability() {
    assertThat(ElementType.getInstance("").isAssignableFromStrict(ElementType.getInstance("")))
        .isTrue();
    assertThat(ElementType.getInstance("").isAssignableFromStrict(ElementType.getInstance("div")))
        .isTrue();
    assertThat(ElementType.getInstance("div").isAssignableFromStrict(ElementType.getInstance("")))
        .isFalse();
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
    assertThatSoyType("[]").isAssignableFromStrict("[]");
    assertThatSoyType("[a?:int]").isAssignableFromStrict("[]");
    assertThatSoyType("[]").isAssignableFromStrict("[a:int, b:any]");
    assertThatSoyType("[a:int, b:any]").isNotAssignableFromStrict("[]");

    // Same
    assertThatSoyType("[a:int, b:any]").isAssignableFromStrict("[a:int, b:any]");

    // "b" is subtype
    assertThatSoyType("[a:int, b:any]").isAssignableFromStrict("[a:int, b:string]");

    // Additional field
    assertThatSoyType("[a:int, b:any]").isAssignableFromStrict("[a:int, b:string, c:string]");

    // Missing "b"
    assertThatSoyType("[a:int, b:any]").isNotAssignableFromStrict("[a:int, c:string]");

    // Field type mismatch on a
    assertThatSoyType("[a:int, b:any]").isNotAssignableFromStrict("[a:string, c:any]");

    // Optional
    assertThatSoyType("[a:int, b?:string|null]").isAssignableFromStrict("[a:int]");
    assertThatSoyType("[a:int, b?:string|null]").isAssignableFromStrict("[a:int, b?:string|null]");
    assertThatSoyType("[a:int, b?:string|null]").isAssignableFromStrict("[a:int, b:string]");
    assertThatSoyType("[a:int, b?:string|null]").isNotAssignableFromStrict("[a:int, b:int]");
    assertThatSoyType("[a:int]").isAssignableFromStrict("[a:int, b?:string|null]");
  }

  @Test
  public void testAllContentKindsCovered() {
    Set<SoyType> types = Sets.newIdentityHashSet();
    for (SanitizedContentKind kind : SanitizedContentKind.values()) {
      SoyType typeForContentKind = SanitizedType.getTypeForContentKind(kind);
      switch (kind) {
        case TEXT:
          assertThat(typeForContentKind).isEqualTo(STRING_TYPE);
          break;
        case HTML_ELEMENT:
          assertThat(typeForContentKind instanceof ElementType).isTrue();
          break;
        default:
          assertThat(((SanitizedType) typeForContentKind).getContentKind()).isEqualTo(kind);
      }
      // ensure there is a unique SoyType for every ContentKind
      assertThat(types.add(typeForContentKind)).isTrue();
    }
  }

  @Test
  public void testBothOfKind() {
    assertThat(SoyTypes.bothOfKind(NUMBER_TYPE, NUMBER_TYPE, NUMBER_TYPE.getKind())).isTrue();
    assertThat(SoyTypes.bothOfKind(NUMBER_TYPE, NUMBER_TYPE, SoyTypes.ARITHMETIC_PRIMITIVES))
        .isTrue();
    assertThat(
            SoyTypes.bothOfKind(
                NUMBER_TYPE,
                FLOAT_TYPE,
                ImmutableSet.of(NUMBER_TYPE.getKind(), FLOAT_TYPE.getKind())))
        .isTrue();
    assertThat(SoyTypes.bothOfKind(NUMBER_TYPE, FLOAT_TYPE, SoyTypes.ARITHMETIC_PRIMITIVES))
        .isTrue();
    assertThat(
            SoyTypes.bothOfKind(
                NumberType.getInstance(),
                FLOAT_TYPE,
                ImmutableSet.of(NUMBER_TYPE.getKind(), FLOAT_TYPE.getKind())))
        .isTrue();
    assertThat(
            SoyTypes.bothOfKind(
                NumberType.getInstance(), FLOAT_TYPE, SoyTypes.ARITHMETIC_PRIMITIVES))
        .isTrue();

    assertThat(SoyTypes.bothOfKind(NUMBER_TYPE, NUMBER_TYPE, ImmutableSet.of())).isFalse();
    assertThat(SoyTypes.bothOfKind(NUMBER_TYPE, FLOAT_TYPE, NUMBER_TYPE.getKind())).isTrue();
    assertThat(SoyTypes.bothOfKind(NumberType.getInstance(), NUMBER_TYPE, NUMBER_TYPE.getKind()))
        .isTrue();
    assertThat(
            SoyTypes.bothOfKind(
                STRING_TYPE, NumberType.getInstance(), SoyTypes.ARITHMETIC_PRIMITIVES))
        .isFalse();
  }

  @Test
  public void testEitherOfKind() {
    assertThat(SoyTypes.eitherOfKind(NUMBER_TYPE, NUMBER_TYPE, NUMBER_TYPE.getKind())).isTrue();
    assertThat(SoyTypes.eitherOfKind(STRING_TYPE, NUMBER_TYPE, NUMBER_TYPE.getKind())).isTrue();
    assertThat(SoyTypes.eitherOfKind(NUMBER_TYPE, STRING_TYPE, NUMBER_TYPE.getKind())).isTrue();
    assertThat(SoyTypes.eitherOfKind(NUMBER_TYPE, STRING_TYPE, SoyTypes.ARITHMETIC_PRIMITIVES))
        .isTrue();

    assertThat(SoyTypes.eitherOfKind(FLOAT_TYPE, NumberType.getInstance(), ImmutableSet.of()))
        .isFalse();
    assertThat(SoyTypes.eitherOfKind(FLOAT_TYPE, NumberType.getInstance(), NUMBER_TYPE.getKind()))
        .isTrue();
  }

  @Test
  public void testComputeStricterType() {
    assertThat(SoyTypes.computeStricterType(NUMBER_TYPE, ANY_TYPE)).hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeStricterType(NumberType.getInstance(), NUMBER_TYPE))
        .hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeStricterType(makeNullable(STRING_TYPE), STRING_TYPE))
        .hasValue(STRING_TYPE);
    assertThat(SoyTypes.computeStricterType(NUMBER_TYPE, STRING_TYPE)).isEmpty();
  }

  @Test
  public void testLowestCommonType() {
    SoyTypeRegistry typeRegistry = SoyTypeRegistryBuilder.create();

    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, NUMBER_TYPE, ANY_TYPE))
        .isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, NUMBER_TYPE, UNKNOWN_TYPE))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, UNKNOWN_TYPE, NUMBER_TYPE))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, ANY_TYPE, NUMBER_TYPE))
        .isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, STRING_TYPE, HTML_TYPE))
        .isEqualTo(UnionType.of(STRING_TYPE, HTML_TYPE));
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, NUMBER_TYPE, FLOAT_TYPE))
        .isEqualTo(NumberType.getInstance());
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, FLOAT_TYPE, NUMBER_TYPE))
        .isEqualTo(NumberType.getInstance());
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, NUMBER_TYPE, UNDEFINED_TYPE))
        .isEqualTo(UnionType.of(NUMBER_TYPE, UNDEFINED_TYPE));
  }

  @Test
  public void testLowestCommonTypeArithmetic() {
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, ANY_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(ANY_TYPE, NUMBER_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(STRING_TYPE, HTML_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(HTML_TYPE, STRING_TYPE)).isEmpty();
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(ListType.of(NUMBER_TYPE), NUMBER_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                LegacyObjectMapType.of(NUMBER_TYPE, STRING_TYPE), NUMBER_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                RecordType.of(ImmutableMap.of("a", NUMBER_TYPE, "b", FLOAT_TYPE)), NUMBER_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(LegacyObjectMapType.of(FLOAT_TYPE, STRING_TYPE), NUMBER_TYPE),
                FLOAT_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(BOOL_TYPE, NUMBER_TYPE, STRING_TYPE), NumberType.getInstance()))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(BOOL_TYPE, NUMBER_TYPE), NUMBER_TYPE))
        .isEmpty();
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                UnionType.of(STRING_TYPE, FLOAT_TYPE), NUMBER_TYPE))
        .isEmpty();

    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, FLOAT_TYPE))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, NUMBER_TYPE))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, UNKNOWN_TYPE))
        .hasValue(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(UNKNOWN_TYPE, FLOAT_TYPE))
        .hasValue(UNKNOWN_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, NUMBER_TYPE))
        .hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, FLOAT_TYPE))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, NumberType.getInstance()))
        .hasValue(NumberType.getInstance());
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, NumberType.getInstance()))
        .hasValue(NumberType.getInstance());
    assertThat(
            SoyTypes.computeLowestCommonTypeArithmetic(
                NumberType.getInstance(), NumberType.getInstance()))
        .hasValue(NumberType.getInstance());
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorPlusOp() {
    SoyTypes.SoyTypeBinaryOperator plusOp = new SoyTypes.SoyTypePlusOperator();
    /*
     * This is largely the same as computeLowestCommonTypeArithmetic(), but this method is slightly
     * better. There is one difference: float + number will return float type, which is expected.
     * Since number is basically {int|float}; float + int returns float, and float + float returns
     * float, so we always return float. OTOH, computeLowestCommonTypeArithmetic(float, number) will
     * return number.
     */
    // All number types
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(NUMBER_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NumberType.getInstance(), plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NumberType.getInstance(), plusOp))
        .isEqualTo(NumberType.getInstance());
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NumberType.getInstance(), NumberType.getInstance(), plusOp))
        .isEqualTo(NumberType.getInstance());

    // Unknown types are fine
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);

    // Any string types should be resolved to string
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, STRING_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, BOOL_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, NumberType.getInstance(), plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, STRING_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, URI_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(HTML_TYPE, STRING_TYPE, plusOp))
        .isEqualTo(STRING_TYPE);

    // Some types are definitely not allowed.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ListType.of(STRING_TYPE), FLOAT_TYPE, plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, LegacyObjectMapType.of(NUMBER_TYPE, STRING_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE,
                RecordType.of(ImmutableMap.of("a", NUMBER_TYPE, "b", FLOAT_TYPE)),
                plusOp))
        .isNull();

    // Union types should list all possible combinations
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(NUMBER_TYPE, STRING_TYPE), plusOp))
        .isEqualTo(UnionType.of(NUMBER_TYPE, STRING_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, FLOAT_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE),
                plusOp))
        .isEqualTo(UnionType.of(STRING_TYPE, FLOAT_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, NUMBER_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE),
                plusOp))
        .isEqualTo(UnionType.of(STRING_TYPE, NUMBER_TYPE));
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE),
                plusOp))
        .isEqualTo(STRING_TYPE);

    // If any of these combinations are incompatible, we should return null.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(BOOL_TYPE, FLOAT_TYPE, NUMBER_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE, ListType.of(NUMBER_TYPE)),
                plusOp))
        .isNull();

    // Nullable types should be fine. However, null type itself is not allowed.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NULL_TYPE, plusOp)).isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(NULL_TYPE, NUMBER_TYPE), plusOp))
        .isEqualTo(NUMBER_TYPE);
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, FLOAT_TYPE), UnionType.of(NULL_TYPE, NUMBER_TYPE), plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE), UnionType.of(NULL_TYPE, NUMBER_TYPE), plusOp))
        .isEqualTo(STRING_TYPE);
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorArithmeticOp() {
    SoyTypes.SoyTypeBinaryOperator plusOp = new SoyTypes.SoyTypeArithmeticOperator();
    // All number types
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NUMBER_TYPE, plusOp))
        .isEqualTo(NUMBER_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NumberType.getInstance(), plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NumberType.getInstance(), plusOp))
        .isEqualTo(NumberType.getInstance());
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NumberType.getInstance(), NumberType.getInstance(), plusOp))
        .isEqualTo(NumberType.getInstance());

    // Unknown types are fine
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, UNKNOWN_TYPE, plusOp))
        .isEqualTo(UNKNOWN_TYPE);

    // Any string types should be rejected
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, STRING_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, FLOAT_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, NumberType.getInstance(), plusOp))
        .isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, STRING_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, URI_TYPE, plusOp)).isNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(HTML_TYPE, STRING_TYPE, plusOp)).isNull();

    // Arbitrary types are banned
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ListType.of(STRING_TYPE), FLOAT_TYPE, plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, LegacyObjectMapType.of(NUMBER_TYPE, STRING_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE,
                RecordType.of(ImmutableMap.of("a", NUMBER_TYPE, "b", FLOAT_TYPE)),
                plusOp))
        .isNull();

    // If any of these combinations are incompatible, we should return null.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(BOOL_TYPE, FLOAT_TYPE, NUMBER_TYPE), plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, NUMBER_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE, ListType.of(NUMBER_TYPE)),
                plusOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE), UnionType.of(NULL_TYPE, NUMBER_TYPE), plusOp))
        .isNull();

    // Nullable types should be fine. However, null type itself is not allowed.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NULL_TYPE, plusOp)).isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(NULL_TYPE, NUMBER_TYPE), plusOp))
        .isEqualTo(NUMBER_TYPE);
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, FLOAT_TYPE), UnionType.of(NULL_TYPE, NUMBER_TYPE), plusOp))
        .isEqualTo(FLOAT_TYPE);
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorEqualOp() {
    SoyTypes.SoyTypeBinaryOperator equalOp = new SoyTypes.SoyTypeEqualComparisonOp();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NUMBER_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NUMBER_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, NumberType.getInstance(), equalOp))
        .isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NumberType.getInstance(), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NumberType.getInstance(), NumberType.getInstance(), equalOp))
        .isNotNull();

    // Unknown types are fine
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, UNKNOWN_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(UNKNOWN_TYPE, UNKNOWN_TYPE, equalOp))
        .isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ANY_TYPE, STRING_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ANY_TYPE, NULL_TYPE, equalOp)).isNotNull();

    // String-number comparisons are okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, STRING_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, NumberType.getInstance(), equalOp))
        .isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, URI_TYPE, equalOp)).isNotNull();

    // String-string comparisons are okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, STRING_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(HTML_TYPE, STRING_TYPE, equalOp)).isNotNull();

    // Aribtrary types are not okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(ListType.of(STRING_TYPE), FLOAT_TYPE, equalOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, LegacyObjectMapType.of(NUMBER_TYPE, STRING_TYPE), equalOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE,
                RecordType.of(ImmutableMap.of("a", NUMBER_TYPE, "b", FLOAT_TYPE)),
                equalOp))
        .isNull();

    // Union types might be okay if all combinations are okay.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(NUMBER_TYPE, STRING_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, FLOAT_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE),
                equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, NUMBER_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE),
                equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE),
                equalOp))
        .isNotNull();

    // If any of these combinations are incompatible, we should return null.
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                LegacyObjectMapType.of(NUMBER_TYPE, STRING_TYPE),
                UnionType.of(BOOL_TYPE, FLOAT_TYPE, NUMBER_TYPE),
                equalOp))
        .isNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(STRING_TYPE, HTML_TYPE),
                UnionType.of(NUMBER_TYPE, STRING_TYPE, ListType.of(NUMBER_TYPE)),
                equalOp))
        .isNull();

    // Nullable types and null type itself are okay.
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(NUMBER_TYPE, NULL_TYPE, equalOp)).isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                NUMBER_TYPE, UnionType.of(NULL_TYPE, NUMBER_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, FLOAT_TYPE), UnionType.of(NULL_TYPE, NUMBER_TYPE), equalOp))
        .isNotNull();
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE),
                UnionType.of(NULL_TYPE, NUMBER_TYPE),
                equalOp))
        .isNotNull();
  }

  @Test
  public void testIsKindOrUnionOfKind() {
    assertThat(
            SoyTypes.isKindOrUnionOfKind(
                MapType.of(AnyType.getInstance(), AnyType.getInstance()), Kind.MAP))
        .isTrue();
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
    assertThat(SoyTypes.isKindOrUnionOfKind(NumberType.getInstance(), Kind.BOOL)).isFalse();
  }

  @Test
  public void testContainsKinds_singleKind() {
    assertThat(
            SoyTypes.containsKinds(
                MapType.of(AnyType.getInstance(), AnyType.getInstance()),
                Sets.immutableEnumSet(Kind.MAP)))
        .isTrue();
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
                Sets.immutableEnumSet(Kind.NUMBER)))
        .isFalse();
  }

  @Test
  public void testContainsKinds_multipleKinds() {
    assertThat(
            SoyTypes.containsKinds(
                NumberType.getInstance(), Sets.immutableEnumSet(Kind.BOOL, Kind.STRING)))
        .isFalse();
  }

  @Test
  public void testContainsKinds_multipleNonMatchingKinds() {
    assertThat(
            SoyTypes.containsKinds(
                NumberType.getInstance(), Sets.immutableEnumSet(Kind.BOOL, Kind.NUMBER)))
        .isTrue();
  }

  // Simple cases testing loose assignability are above, the complex cases involve generics and
  // things like record types.
  @Test
  public void testLooseAssignability() {
    assertThatSoyType("list<string>").isAssignableFromLoose("list<?>");
    assertThatSoyType("list<string>").isNotAssignableFromLoose("list<int>");

    assertThatSoyType("map<string, string>").isAssignableFromLoose("map<string, ?>");
    assertThatSoyType("map<string, string>").isNotAssignableFromLoose("map<string, int>");

    assertThatSoyType("[foo: string, bar: int]").isAssignableFromLoose("[foo: ?, bar: ?]");

    assertThatSoyType("[foo: string, bar: int]").isAssignableFromLoose("[foo: ?, bar: ?, baz: ?]");
    assertThatSoyType("[foo: string, bar: int]")
        .isAssignableFromLoose("[foo: string, bar: number]");
  }

  @Test
  public void testNullability() {
    assertThat(SoyTypes.tryRemoveNull(NULL_TYPE)).isEqualTo(NULL_TYPE);
    assertThat(SoyTypes.tryRemoveNull(UnionType.of(STRING_TYPE, NULL_TYPE))).isEqualTo(STRING_TYPE);

    assertThat(SoyTypes.tryRemoveNullish(NULL_TYPE)).isEqualTo(NULL_TYPE);
    assertThat(SoyTypes.tryRemoveNullish(UnionType.of(STRING_TYPE, NULL_TYPE)))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.tryRemoveNullish(UnionType.of(STRING_TYPE, UNDEFINED_TYPE)))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.tryRemoveNullish(UnionType.of(STRING_TYPE, NULL_TYPE, UNDEFINED_TYPE)))
        .isEqualTo(STRING_TYPE);
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

    void isAssignableFromLoose(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (!leftType.isAssignableFromLoose(rightType)) {
        failWithActual("expected to be assignable from", formatType(rightType));
      }
    }

    void isNotAssignableFromLoose(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (leftType.isAssignableFromLoose(rightType)) {
        failWithActual("expected not to be assignable from", formatType(rightType));
      }
    }

    void isAssignableFromStrict(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (!leftType.isAssignableFromStrict(rightType)) {
        failWithActual("expected to be strictly assignable from", formatType(rightType));
      }
    }

    void isNotAssignableFromStrict(String other) {
      SoyType leftType = parseType(actual);
      SoyType rightType = parseType(other);
      if (leftType.isAssignableFromStrict(rightType)) {
        failWithActual("expected not to be strictly assignable from", formatType(rightType));
      }
    }

    Object formatType(SoyType type) {
      SoyType effective = type.getEffectiveType();
      if (effective != type) {
        return type + " (" + effective + ")";
      }
      return type;
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
      if (!leftType.isAssignableFromStrict(rightType)) {
        failWithoutActual(
            simpleFact(
                lenientFormat("types are equal, but %s is not assignable from %s", actual, other)));
      }
      if (!rightType.isAssignableFromStrict(leftType)) {
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
      if (leftType.isAssignableFromStrict(rightType)
          && rightType.isAssignableFromStrict(leftType)) {
        failWithoutActual(
            simpleFact(
                lenientFormat(
                    "types are not equal, but %s and %s are mutally assignable", actual, other)));
      }
    }

    private SoyType parseType(String input) {
      return SoyTypesTest.parseType(input, registry);
    }
  }

  private static SoyType parseType(String input, SoyTypeRegistry registry) {
    TypeNode typeNode =
        SoyFileParser.parseType(
            input, SourceFilePath.create("-", "-" + ""), ErrorReporter.exploding());
    return typeNode != null
        ? TypeNodeConverter.builder(ErrorReporter.exploding())
            .setTypeRegistry(registry)
            .build()
            .getOrCreateType(typeNode)
        : UnknownType.getInstance();
  }
}
