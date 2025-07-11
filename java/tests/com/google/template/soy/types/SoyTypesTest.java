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
import static com.google.template.soy.types.SoyTypes.NULL_OR_UNDEFINED;
import static com.google.template.soy.types.SoyTypes.unionWithNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for soy types. */
@RunWith(JUnit4.class)
public class SoyTypesTest {

  private static final SoyType INT_OR_FLOAT =
      UnionType.of(IntType.getInstance(), FloatType.getInstance());
  private static final SoyType INT_OR_NUMBER =
      UnionType.of(IntType.getInstance(), NumberType.getInstance());
  private static final SoyType INT_OR_STRING =
      UnionType.of(IntType.getInstance(), StringType.getInstance());

  private static final BoolType BOOL_TYPE = BoolType.getInstance();
  private static final IntType INT_TYPE = IntType.getInstance();
  private static final FloatType FLOAT_TYPE = FloatType.getInstance();
  private static final NumberType NUMBER_TYPE = NumberType.getInstance();
  private static final StringType STRING_TYPE = StringType.getInstance();
  private static final HtmlType HTML_TYPE = HtmlType.getInstance();
  private static final NullType NULL_TYPE = NullType.getInstance();
  private static final AnyType ANY_TYPE = AnyType.getInstance();
  private static final UnknownType UNKNOWN_TYPE = UnknownType.getInstance();
  private static final UriType URI_TYPE = UriType.getInstance();
  private static final UndefinedType UNDEFINED_TYPE = UndefinedType.getInstance();

  private static final String NS = "unusedNamespace";

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
  public void testLiteralTypes() {
    assertThatSoyType("'x'").isAssignableFromStrict("'x'");
    assertThatSoyType("'x'").isNotAssignableFromStrict("'y'");

    assertThatSoyType("string").isAssignableFromStrict("'abc'");
    assertThatSoyType("'abc'").isNotAssignableFromStrict("string");
  }

  @Test
  public void testPrimitiveTypeEquality() {
    assertThatSoyType("any").isEqualTo("any");
    assertThatSoyType("any").isNotEqualTo("int");
    assertThatSoyType("int").isNotEqualTo("any");
    assertThatSoyType("?").isEqualTo("?");
  }

  @Test
  public void testNumber() {
    SoyTypeRegistry typeRegistry = SoyTypeRegistryBuilder.create();
    assertThat(parseType("number", typeRegistry)).isEqualTo(NumberType.getInstance());

    assertThatSoyType("number").isAssignableFromStrict("int");
    assertThatSoyType("number").isAssignableFromStrict("float");
    assertThatSoyType("number").isAssignableFromStrict("int|float");
    assertThatSoyType("number").isAssignableFromStrict("int|float|number");

    assertThatSoyType("int").isAssignableFromStrict("number");
    assertThatSoyType("float").isAssignableFromStrict("number");
    assertThatSoyType("int|float").isAssignableFromStrict("number");
    assertThatSoyType("int|float|number").isAssignableFromStrict("number");
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
    SoyType utype = UnionType.of(INT_TYPE, UnionType.of(INT_TYPE, NULL_TYPE));
    assertThat(utype.toString()).isEqualTo("int|null");
    assertThatSoyType("int|null").isAssignableFromStrict("int");
    assertThatSoyType("int|null").isAssignableFromStrict("null");
    assertThatSoyType("int|null").isNotAssignableFromStrict("float");
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
    SoyTypeRegistry registry =
        new TestTypeRegistry()
            .addNamed("StringAlias", "string")
            .addNamed("IntAlias", "int")
            .addNamed("UnionOfAliases", "StringAlias|IntAlias")
            .addNamed("AnotherUnion", "UnionOfAliases|bool")
            .addNamed("BoolOrNumber", "bool|int|float");

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
    SoyTypeRegistry registry =
        new TestTypeRegistry()
            .addNamed("StringAlias", "string")
            .addNamed("Rec1", "[a: string, b: float|int, c: bool, d: string|bool]");

    assertThatSoyType("Rec1['a']", registry).isAssignableFromStrict("string");
    assertThatSoyType("string", registry).isAssignableFromStrict("Rec1['a']");

    assertThatSoyType("Rec1['b']", registry).isAssignableFromStrict("float|int");
    assertThatSoyType("Rec1['c']", registry).isAssignableFromStrict("bool");
    assertThatSoyType("Rec1['d']", registry).isAssignableFromStrict("string");
    assertThatSoyType("Rec1['d']", registry).isAssignableFromStrict("bool");

    assertThatSoyType("Rec1['d']", registry).isAssignableFromStrict("Rec1['a']");
    assertThatSoyType("Rec1['a']", registry).isNotAssignableFromStrict("Rec1['d']");
  }

  @Test
  public void testPickAndOmit() {
    SoyTypeRegistry registry =
        new TestTypeRegistry()
            .addNamed("PropList", "'a' | 'c'")
            .addNamed("Rec1", "[a: string, b: float|int, c: bool, d: string|bool]");

    assertThatSoyType("Pick<Rec1, 'a'>", registry).isEffectivelyEqualTo("[a: string]");
    assertThatSoyType("Pick<Rec1, 'a' | 'b'>", registry)
        .isEffectivelyEqualTo("[a: string, b: float|int]");
    assertThatSoyType("Pick<Rec1, PropList>", registry)
        .isEffectivelyEqualTo("[a: string, c: bool]");

    assertThatSoyType("Omit<Rec1, 'a'>", registry)
        .isEffectivelyEqualTo("[b: float|int, c: bool, d: string|bool]");
    assertThatSoyType("Omit<Rec1, 'a' | 'b'>", registry)
        .isEffectivelyEqualTo("[c: bool, d: string|bool]");
    assertThatSoyType("Omit<Rec1, PropList>", registry)
        .isEffectivelyEqualTo("[b: float|int, d: string|bool]");

    // should be effectively never
    assertThatSoyType("Omit<Rec1, string>", registry).isNotAssignableFromStrict("[]");
  }

  @Test
  public void testNestedUnions() {
    SoyTypeRegistry registry =
        new TestTypeRegistry()
            .addNamed("U1", "string | number")
            .addNamed("U2", "U1 | bool")
            .addNamed("U3", "U2 | null")
            .addNamed("U4", "U3 | undefined")
            .addNamed("U5", "U4 | html");

    SoyType nestedUnion = registry.getType("U5");
    assertThat(SoyTypes.isNullable(nestedUnion)).isTrue();
    assertThat(SoyTypes.isUndefinable(nestedUnion)).isTrue();
    assertThat(SoyTypes.isNullish(nestedUnion)).isTrue();
    assertThat(SoyTypes.isNullOrUndefined(nestedUnion)).isFalse();

    SoyType nonNull = SoyTypes.excludeNullish(nestedUnion);
    assertThatSoyType(nonNull).isEffectivelyEqualTo("string | number | bool | html");
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
                  NamedType.create("Rec1", NS, parseType("[a: string]", baseRegistry)),
                  "Rec2",
                  NamedType.create("Rec2", NS, parseType("[b: string]", baseRegistry)),
                  "Rec3",
                  NamedType.create(
                      "Rec3", NS, parseType("[a: string, b: string, c: string]", baseRegistry)));

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
  public void testFunctionTypeAssignment() {
    assertThatSoyType("() => string").isAssignableFromStrict("() => string");
    assertThatSoyType("(p: int) => string").isAssignableFromStrict("() => string");
    assertThatSoyType("() => string").isNotAssignableFromStrict("(p: int) => string");

    assertThatSoyType("() => string|bool").isAssignableFromStrict("() => bool");
    assertThatSoyType("() => bool").isNotAssignableFromStrict("() => string|bool");
    assertThatSoyType("(p: bool) => string").isAssignableFromStrict("(p: string|bool) => string");
    assertThatSoyType("(p: string|bool) => string")
        .isNotAssignableFromStrict("(p: bool) => string");

    // param name doesn't matter
    assertThatSoyType("(p: int) => string").isAssignableFromStrict("(v: int) => string");
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
    assertThat(SoyTypes.bothAssignableFrom(INT_TYPE, INT_TYPE, IntType.getInstance())).isTrue();
    assertThat(SoyTypes.bothAssignableFrom(INT_TYPE, INT_TYPE, NumberType.getInstance())).isTrue();
    assertThat(SoyTypes.bothAssignableFrom(INT_TYPE, FLOAT_TYPE, NumberType.getInstance()))
        .isTrue();
    assertThat(SoyTypes.bothAssignableFrom(INT_TYPE, FLOAT_TYPE, NumberType.getInstance()))
        .isTrue();
    assertThat(SoyTypes.bothAssignableFrom(INT_OR_FLOAT, FLOAT_TYPE, NumberType.getInstance()))
        .isTrue();
    assertThat(SoyTypes.bothAssignableFrom(INT_OR_FLOAT, FLOAT_TYPE, NumberType.getInstance()))
        .isTrue();

    assertThat(SoyTypes.bothAssignableFrom(INT_TYPE, FLOAT_TYPE, IntType.getInstance())).isFalse();
    assertThat(SoyTypes.bothAssignableFrom(INT_OR_FLOAT, INT_TYPE, IntType.getInstance()))
        .isFalse();
    assertThat(SoyTypes.bothAssignableFrom(STRING_TYPE, INT_OR_FLOAT, NumberType.getInstance()))
        .isFalse();
  }

  @Test
  public void testComputeStricterType() {
    assertThat(SoyTypes.computeStricterType(INT_TYPE, ANY_TYPE)).hasValue(INT_TYPE);
    assertThat(SoyTypes.computeStricterType(INT_OR_FLOAT, INT_TYPE)).hasValue(INT_TYPE);
    assertThat(SoyTypes.computeStricterType(unionWithNull(STRING_TYPE), STRING_TYPE))
        .hasValue(STRING_TYPE);
    assertThat(SoyTypes.computeStricterType(INT_TYPE, STRING_TYPE)).isEmpty();
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
        .isEqualTo(INT_OR_FLOAT);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, FLOAT_TYPE, INT_TYPE))
        .isEqualTo(INT_OR_FLOAT);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, UNDEFINED_TYPE))
        .isEqualTo(UnionType.of(INT_TYPE, UNDEFINED_TYPE));
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, NUMBER_TYPE, INT_TYPE))
        .isEqualTo(INT_OR_NUMBER);
    assertThat(SoyTypes.computeLowestCommonType(typeRegistry, INT_TYPE, STRING_TYPE))
        .isEqualTo(INT_OR_STRING);
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
                UnionType.of(BOOL_TYPE, INT_TYPE, STRING_TYPE), INT_OR_FLOAT))
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
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(FLOAT_TYPE, INT_OR_FLOAT))
        .hasValue(FLOAT_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_TYPE, INT_OR_FLOAT))
        .hasValue(INT_OR_FLOAT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_OR_FLOAT, INT_OR_FLOAT))
        .hasValue(INT_OR_FLOAT);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, INT_TYPE))
        .hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(NUMBER_TYPE, FLOAT_TYPE))
        .hasValue(NUMBER_TYPE);
    assertThat(SoyTypes.computeLowestCommonTypeArithmetic(INT_OR_NUMBER, INT_TYPE))
        .hasValue(INT_OR_NUMBER);
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
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_TYPE, plusOp))
        .isEqualTo(INT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_OR_FLOAT, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_OR_FLOAT, plusOp))
        .isEqualTo(INT_OR_FLOAT);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_OR_FLOAT, INT_OR_FLOAT, plusOp))
        .isEqualTo(INT_OR_FLOAT);

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
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, INT_OR_FLOAT, plusOp))
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
        .isEqualTo(FLOAT_TYPE);
    assertThat(
            SoyTypes.getSoyTypeForBinaryOperator(
                UnionType.of(NULL_TYPE, STRING_TYPE), UnionType.of(NULL_TYPE, INT_TYPE), plusOp))
        .isEqualTo(STRING_TYPE);
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
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_OR_FLOAT, plusOp))
        .isEqualTo(FLOAT_TYPE);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_OR_FLOAT, plusOp))
        .isEqualTo(INT_OR_FLOAT);
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_OR_FLOAT, INT_OR_FLOAT, plusOp))
        .isEqualTo(INT_OR_FLOAT);

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
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, INT_OR_FLOAT, plusOp)).isNull();
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
        .isEqualTo(FLOAT_TYPE);
  }

  @Test
  public void testGetSoyTypeForBinaryOperatorEqualOp() {
    SoyTypes.SoyTypeBinaryOperator equalOp = new SoyTypes.SoyTypeEqualComparisonOp();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, FLOAT_TYPE, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(FLOAT_TYPE, INT_OR_FLOAT, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_TYPE, INT_OR_FLOAT, equalOp)).isNotNull();
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(INT_OR_FLOAT, INT_OR_FLOAT, equalOp))
        .isNotNull();

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
    assertThat(SoyTypes.getSoyTypeForBinaryOperator(STRING_TYPE, INT_OR_FLOAT, equalOp))
        .isNotNull();
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
    assertThat(SoyTypes.isKindOrUnionOfKind(IntType.getInstance(), Kind.BOOL)).isFalse();
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

  @Test
  public void testUndefinedToNull() {
    assertThat(SoyTypes.undefinedToNull(UNDEFINED_TYPE)).isEqualTo(NULL_TYPE);
    assertThat(SoyTypes.undefinedToNull(UnionType.of(STRING_TYPE, UNDEFINED_TYPE)))
        .isEqualTo(UnionType.of(STRING_TYPE, NULL_TYPE));
    assertThat(SoyTypes.undefinedToNull(ANY_TYPE)).isEqualTo(ANY_TYPE);
    assertThat(SoyTypes.undefinedToNull(NULL_OR_UNDEFINED)).isEqualTo(NULL_TYPE);
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
        .isNotAssignableFromLoose("[foo: string, bar: float|int]");
  }

  @Test
  public void testNullability() {
    assertThat(SoyTypes.excludeNull(NULL_TYPE)).isEqualTo(NeverType.getInstance());
    assertThat(SoyTypes.excludeNull(UnionType.of(STRING_TYPE, NULL_TYPE))).isEqualTo(STRING_TYPE);

    assertThat(SoyTypes.tryExcludeNullish(NULL_TYPE)).isEqualTo(NULL_TYPE);
    assertThat(SoyTypes.tryExcludeNullish(UnionType.of(STRING_TYPE, NULL_TYPE)))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.tryExcludeNullish(UnionType.of(STRING_TYPE, UNDEFINED_TYPE)))
        .isEqualTo(STRING_TYPE);
    assertThat(SoyTypes.tryExcludeNullish(UnionType.of(STRING_TYPE, NULL_TYPE, UNDEFINED_TYPE)))
        .isEqualTo(STRING_TYPE);

    assertThat(SoyTypes.excludeUndefined(UnionType.of(UNDEFINED_TYPE, NUMBER_TYPE)))
        .isEqualTo(NUMBER_TYPE);
  }

  static SoyTypeSubject assertThatSoyType(String typeString, SoyTypeRegistry registry) {
    return Truth.<SoyTypeSubject, String>assertAbout(
            (meta, subject) -> new SoyTypeSubject(meta, subject, registry))
        .that(typeString);
  }

  static SoyTypeSubject assertThatSoyType(SoyType type) {
    return Truth.<SoyTypeSubject, SoyType>assertAbout(
            (meta, subject) -> new SoyTypeSubject(meta, subject, SoyTypeRegistryBuilder.create()))
        .that(type);
  }

  static SoyTypeSubject assertThatSoyType(String typeString) {
    return assertThatSoyType(typeString, SoyTypeRegistryBuilder.create());
  }

  static final class SoyTypeSubject extends Subject {
    private final String actual;
    private final SoyType actualType;
    private final SoyTypeRegistry registry;

    SoyTypeSubject(FailureMetadata metadata, String actual, SoyTypeRegistry registry) {
      super(metadata, actual);
      this.actual = actual;
      this.actualType = null;
      this.registry = registry;
    }

    SoyTypeSubject(FailureMetadata metadata, SoyType actual, SoyTypeRegistry registry) {
      super(metadata, actual);
      this.actual = null;
      this.actualType = actual;
      this.registry = registry;
    }

    private SoyType getActualType() {
      return actualType != null ? actualType : parseType(actual);
    }

    void isAssignableFromLoose(String other) {
      isAssignableFromLoose(parseType(other));
    }

    void isAssignableFromLoose(SoyType rightType) {
      SoyType leftType = getActualType();
      if (!leftType.isAssignableFromLoose(rightType)) {
        failWithActual("expected to be assignable from", formatType(rightType));
      }
    }

    void isNotAssignableFromLoose(String other) {
      SoyType leftType = getActualType();
      SoyType rightType = parseType(other);
      if (leftType.isAssignableFromLoose(rightType)) {
        failWithActual("expected not to be assignable from", formatType(rightType));
      }
    }

    void isAssignableFromStrict(String other) {
      isAssignableFromStrict(parseType(other));
    }

    void isAssignableFromStrict(SoyType rightType) {
      SoyType leftType = getActualType();
      if (!leftType.isAssignableFromStrict(rightType)) {
        failWithActual("expected to be strictly assignable from", formatType(rightType));
      }
    }

    void isNotAssignableFromStrict(String other) {
      SoyType leftType = getActualType();
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
      SoyType leftType = getActualType();
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

    void isEffectivelyEqualTo(String other) {
      SoyType leftType = getActualType().getEffectiveType();
      SoyType rightType = parseType(other).getEffectiveType();
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
      SoyType leftType = getActualType();
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

  private static class TestTypeRegistry extends DelegatingSoyTypeRegistry {
    private final Map<String, SoyType> namedTypes = new HashMap<>();

    public TestTypeRegistry() {
      super(SoyTypeRegistryBuilder.create());
    }

    @CanIgnoreReturnValue
    TestTypeRegistry addNamed(String name, String typeString) {
      return addType(name, NamedType.create(name, NS, parseType(typeString, this)));
    }

    @CanIgnoreReturnValue
    TestTypeRegistry addType(String name, SoyType type) {
      namedTypes.put(name, type);
      return this;
    }

    @Override
    public SoyType getType(String typeName) {
      if (namedTypes.containsKey(typeName)) {
        return namedTypes.get(typeName);
      }
      return super.getType(typeName);
    }
  }
}
