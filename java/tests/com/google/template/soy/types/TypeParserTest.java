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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.SoyFileSetParserBuilder;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for the type parser. */
@RunWith(JUnit4.class)
public class TypeParserTest {

  private static final SoyType FOO_BAR_TYPE =
      new SoyType() {
        @Override
        public Kind getKind() {
          return null;
        }

        @Override
        public boolean isAssignableFrom(SoyType srcType) {
          return false;
        }

        @Override
        public boolean isInstance(SoyValue value) {
          return false;
        }
      };

  private static final SoyTypeProvider TYPE_PROVIDER =
      new SoyTypeProvider() {
        @Override
        public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
          if (typeName.equals("foo.bar")) {
            return FOO_BAR_TYPE;
          }
          return null;
        }
      };

  private SoyTypeRegistry typeRegistry;

  @Before
  public void setUp() throws Exception {
    typeRegistry = new SoyTypeRegistry(ImmutableSet.of(TYPE_PROVIDER));
  }

  @Test
  public void testParseTypeNames() {
    assertTypeEquals(AnyType.getInstance(), "any");
    assertTypeEquals(AnyType.getInstance(), " any ");
    assertTypeEquals(IntType.getInstance(), "int");
    assertTypeEquals(BoolType.getInstance(), "bool");
    assertTypeEquals(FOO_BAR_TYPE, "foo.bar");
    assertTypeEquals(FOO_BAR_TYPE, " foo.bar ");
    assertTypeEquals(FOO_BAR_TYPE, " foo . bar ");
    assertTypeEquals(UnknownType.getInstance(), "?");
  }

  @Test
  public void testParseUnionTypes() {
    assertTypeEquals(UnionType.of(IntType.getInstance(), BoolType.getInstance()), "int|bool");
    assertTypeEquals(
        UnionType.of(IntType.getInstance(), BoolType.getInstance(), StringType.getInstance()),
        "int|bool|string");
    assertTypeEquals(UnionType.of(IntType.getInstance(), BoolType.getInstance()), " int | bool ");
  }

  @Test
  public void testParseRecordTypes() {
    assertTypeEquals(RecordType.of(ImmutableMap.of("a", IntType.getInstance())), "[a:int]");
    assertTypeEquals(RecordType.of(ImmutableMap.of("a", IntType.getInstance())), "[a:int]");
    assertTypeEquals(
        RecordType.of(ImmutableMap.of("a", IntType.getInstance(), "b", FloatType.getInstance())),
        "[a:int, b:float]");
    assertTypeEquals(
        RecordType.of(
            ImmutableMap.of(
                "a", IntType.getInstance(), "b", ListType.of(StringType.getInstance()))),
        "[a:int, b:list<string>]");
  }

  @Test
  public void testParameterizedTypes() {
    assertTypeEquals(ListType.of(StringType.getInstance()), "list<string>");
    assertTypeEquals(ListType.of(StringType.getInstance()), "list < string > ");
    assertTypeEquals(MapType.of(IntType.getInstance(), BoolType.getInstance()), "map<int, bool>");
  }

  // -----------------------------------------------------------------------------------------------
  // Helpers.

  private void assertTypeEquals(SoyType expected, String typeInput) {
    assertThat(parseType(typeInput)).isEqualTo(expected);
  }

  private SoyType parseType(String input) {
    TemplateNode template =
        SoyFileSetParserBuilder.forTemplateContents("{@param p : " + input + "}\n{$p ? 't' : 'f'}")
            .typeRegistry(typeRegistry)
            .parse()
            .fileSet()
            .getChild(0)
            .getChild(0);
    return Iterables.getOnlyElement(template.getAllParams()).type();
  }
}
