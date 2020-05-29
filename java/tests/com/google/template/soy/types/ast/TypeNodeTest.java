/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.ast;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soyparse.SoyFileParser;
import com.google.template.soy.types.ast.RecordTypeNode.Property;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeNodeTest {
  private static final SourceLocation SOURCE_LOCATION = SourceLocation.UNKNOWN;

  private static final NamedTypeNode TYPE_ABC = NamedTypeNode.create(SOURCE_LOCATION, "abc");
  private static final NamedTypeNode TYPE_DEF = NamedTypeNode.create(SOURCE_LOCATION, "def");
  private static final NamedTypeNode TYPE_GHI = NamedTypeNode.create(SOURCE_LOCATION, "ghi");
  private static final NamedTypeNode TYPE_JKL = NamedTypeNode.create(SOURCE_LOCATION, "jkl");

  @Test
  public void testNamedTypeToString() throws Exception {
    assertThat(TYPE_ABC.toString()).isEqualTo("abc");
  }

  @Test
  public void testGenericTypeToString() throws Exception {
    assertThat(
            GenericTypeNode.create(
                    SOURCE_LOCATION, Identifier.create("foo", SOURCE_LOCATION), ImmutableList.of())
                .toString())
        .isEqualTo("foo<>");
    assertThat(
            GenericTypeNode.create(
                    SOURCE_LOCATION,
                    Identifier.create("list", SOURCE_LOCATION),
                    ImmutableList.of(TYPE_ABC))
                .toString())
        .isEqualTo("list<abc>");
    assertThat(
            GenericTypeNode.create(
                    SOURCE_LOCATION,
                    Identifier.create("map", SOURCE_LOCATION),
                    ImmutableList.of(TYPE_ABC, TYPE_DEF))
                .toString())
        .isEqualTo("map<abc, def>");
  }

  @Test
  public void testRecordTypeToString() throws Exception {
    assertThat(RecordTypeNode.create(SOURCE_LOCATION, ImmutableList.of()).toString())
        .isEqualTo("[]");
    assertThat(
            RecordTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(Property.create(SOURCE_LOCATION, "x", TYPE_ABC)))
                .toString())
        .isEqualTo("[x: abc]");

    assertThat(
            RecordTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        Property.create(SOURCE_LOCATION, "x", TYPE_ABC),
                        Property.create(SOURCE_LOCATION, "y", TYPE_DEF)))
                .toString())
        .isEqualTo("[x: abc, y: def]");

    assertThat(
            RecordTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        Property.create(SOURCE_LOCATION, "x", TYPE_ABC),
                        Property.create(SOURCE_LOCATION, "y", TYPE_DEF),
                        Property.create(SOURCE_LOCATION, "z", TYPE_GHI),
                        Property.create(SOURCE_LOCATION, "w", TYPE_JKL)))
                .toString())
        .isEqualTo("[\n  x: abc,\n  y: def,\n  z: ghi,\n  w: jkl\n]");
  }

  @Test
  public void testTemplateTypeToString() throws Exception {
    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(),
                    NamedTypeNode.create(SOURCE_LOCATION, "html"))
                .toString())
        .isEqualTo("() => html");

    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "x", TYPE_ABC)),
                    NamedTypeNode.create(SOURCE_LOCATION, "attributes"))
                .toString())
        .isEqualTo("(x: abc) => attributes");

    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "x", TYPE_ABC),
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "y", TYPE_DEF)),
                    NamedTypeNode.create(SOURCE_LOCATION, "css"))
                .toString())
        .isEqualTo("(x: abc, y: def) => css");

    assertThat(
            TemplateTypeNode.create(
                    SOURCE_LOCATION,
                    ImmutableList.of(
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "x", TYPE_ABC),
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "y", TYPE_DEF),
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "z", TYPE_GHI),
                        TemplateTypeNode.Parameter.create(SOURCE_LOCATION, "w", TYPE_JKL)),
                    NamedTypeNode.create(SOURCE_LOCATION, "uri"))
                .toString())
        .isEqualTo("(\n  x: abc,\n  y: def,\n  z: ghi,\n  w: jkl\n) => uri");
  }

  @Test
  public void testUnionTypeToString() throws Exception {
    assertThat(UnionTypeNode.create(ImmutableList.of(TYPE_ABC, TYPE_DEF)).toString())
        .isEqualTo("abc|def");
  }

  @Test
  public void testRoundTripThroughParser() {
    assertRoundTrip("int");
    assertRoundTrip("?");
    assertRoundTrip("list<int>");
    assertRoundTrip("list<list<list<list<int>>>>");
    assertRoundTrip("map<int, any>");
    assertRoundTrip("[foo: string, bar: int, quux: [foo: string, bar: int, quux: list<any>]]");
    assertRoundTrip("() => html");
    assertRoundTrip("(baz: int, tpl: (foo: string, bar: int) => attributes) => html");
    assertRoundTrip("(count: int) => html | (count: int) => attributes");
  }

  private void assertRoundTrip(String typeString) {
    TypeNode original = parse(typeString);
    TypeNode reparsed = parse(original.toString());
    // we can't assert on the equality of the type nodes because the source locations may be
    // different due to whitespace.
    assertThat(original.toString()).isEqualTo(reparsed.toString());
    assertEquals(original, reparsed);

    // Also assert equality after copying
    assertEquals(original, reparsed.copy());
    assertEquals(original.copy(), reparsed.copy());
    assertEquals(original.copy(), reparsed);
  }

  private static void assertEquals(
      ImmutableList<TypeNode> leftArgs, ImmutableList<TypeNode> rightArgs) {
    assertThat(leftArgs).hasSize(rightArgs.size());
    for (int i = 0; i < leftArgs.size(); i++) {
      assertEquals(leftArgs.get(i), rightArgs.get(i));
    }
  }

  static void assertEquals(TypeNode left, final TypeNode right) {
    left.accept(
        new TypeNodeVisitor<Void>() {

          @Override
          public Void visit(NamedTypeNode node) {
            assertThat(((NamedTypeNode) right).name().identifier())
                .isEqualTo(node.name().identifier());
            return null;
          }

          @Override
          public Void visit(GenericTypeNode node) {
            assertThat(((GenericTypeNode) right).name()).isEqualTo(node.name());
            assertEquals(node.arguments(), ((GenericTypeNode) right).arguments());
            return null;
          }

          @Override
          public Void visit(UnionTypeNode node) {
            assertEquals(node.candidates(), ((UnionTypeNode) right).candidates());
            return null;
          }

          @Override
          public Void visit(RecordTypeNode node) {
            assertThat(node.properties()).hasSize(((RecordTypeNode) right).properties().size());
            for (int i = 0; i < node.properties().size(); i++) {
              Property leftProp = node.properties().get(i);
              Property rightProp = ((RecordTypeNode) right).properties().get(i);
              assertThat(leftProp.name()).isEqualTo(rightProp.name());
              assertEquals(leftProp.type(), rightProp.type());
            }
            return null;
          }

          @Override
          public Void visit(TemplateTypeNode node) {
            assertThat(node.parameters()).hasSize(((TemplateTypeNode) right).parameters().size());
            ImmutableMap<String, TypeNode> leftArgumentMap =
                node.parameters().stream()
                    .collect(
                        toImmutableMap(
                            TemplateTypeNode.Parameter::name, TemplateTypeNode.Parameter::type));
            ImmutableMap<String, TypeNode> rightArgumentMap =
                Streams.stream(((TemplateTypeNode) right).parameters())
                    .collect(
                        toImmutableMap(
                            TemplateTypeNode.Parameter::name, TemplateTypeNode.Parameter::type));
            assertThat(leftArgumentMap.keySet()).isEqualTo(rightArgumentMap.keySet());
            for (String key : leftArgumentMap.keySet()) {
              assertEquals(leftArgumentMap.get(key), rightArgumentMap.get(key));
            }
            assertEquals(node.returnType(), ((TemplateTypeNode) right).returnType());
            return null;
          }
        });
  }

  private TypeNode parse(String typeString) {
    TypeNode typeNode =
        SoyFileParser.parseType(typeString, "fake-file.soy", ErrorReporter.exploding());
    // sanity, make sure copies work
    assertThat(typeNode).isEqualTo(typeNode.copy());
    return typeNode;
  }
}
