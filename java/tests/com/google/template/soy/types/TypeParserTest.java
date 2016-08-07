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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.parse.TypeParser;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import junit.framework.TestCase;

/**
 * Unit tests for the type parser.
 */
public class TypeParserTest extends TestCase {


  private static final SoyType FOO_BAR_TYPE = new SoyType() {
    @Override public Kind getKind() {
      return null;
    }

    @Override public boolean isAssignableFrom(SoyType srcType) {
      return false;
    }

    @Override public boolean isInstance(SoyValue value) {
      return false;
    }
  };


  private static final SoyTypeProvider TYPE_PROVIDER = new SoyTypeProvider() {
    @Override public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
      if (typeName.equals("foo.bar")) {
        return FOO_BAR_TYPE;
      }
      return null;
    }
  };


  private SoyTypeRegistry typeRegistry;


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    typeRegistry = new SoyTypeRegistry(ImmutableSet.of(TYPE_PROVIDER));
  }


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


  public void testParseUnionTypes() {
    assertTypeEquals(
        UnionType.of(IntType.getInstance(), BoolType.getInstance()),
        "int|bool");
    assertTypeEquals(
        UnionType.of(IntType.getInstance(), BoolType.getInstance(), StringType.getInstance()),
        "int|bool|string");
    assertTypeEquals(
        UnionType.of(IntType.getInstance(), BoolType.getInstance()),
        " int | bool ");
  }


  public void testParseRecordTypes() {
    assertTypeEquals(
        RecordType.of(ImmutableMap.<String, SoyType>builder()
            .put("a", IntType.getInstance())
            .build()),
        "[a:int]");
    assertTypeEquals(
        RecordType.of(ImmutableMap.<String, SoyType>builder()
            .put("a", IntType.getInstance())
            .put("b", FloatType.getInstance())
            .build()),
        "[a:int, b:float]");
    assertTypeEquals(
        RecordType.of(ImmutableMap.<String, SoyType>builder()
            .put("a", IntType.getInstance())
            .put("b", ListType.of(StringType.getInstance()))
            .build()),
        "[a:int, b:list<string>]");
  }


  public void testParameterizedTypes() {
    assertTypeEquals(ListType.of(StringType.getInstance()), "list<string>");
    assertTypeEquals(ListType.of(StringType.getInstance()), "list < string > ");
    assertTypeEquals(MapType.of(IntType.getInstance(), BoolType.getInstance()), "map<int, bool>");
  }


  public void testParseErrors() {
    // Look on my errors, ye Mighty, and despair
    // In particular our messages when we reach an unexpected end of string are poor.
    assertParseError("foo", "Unknown type 'foo'.");
    assertParseError("any any", "parse error at 'any': expected eof, |, or .");
    assertParseError("any<string>", "parse error at '<': expected eof, |, or .");
    assertParseError("list<", "parse error at '': expected [, ?, list, map, or identifier");
    assertParseError("list<>", "parse error at '>': expected [, ?, list, map, or identifier");
    assertParseError("list<string", "parse error at '': expected >, |, or .");
    assertParseError("list", "parse error at '': expected <");
    assertParseError("list<string, string>", "parse error at ',': expected >, |, or .");
    assertParseError("map", "parse error at '': expected <");
    assertParseError("map<string>", "parse error at '>': expected ',', |, or .");
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private void assertTypeEquals(SoyType expected, String typeInput) {
    assertThat(parseType(typeInput)).isEqualTo(expected);
  }


  private void assertParseError(String typeInput, String msg) {
    try {
      parseType(typeInput);
      fail("Input string '" + typeInput + "' should have failed to parse.");
    } catch (SoySyntaxException e) {
      assertThat(e.getMessage()).isEqualTo(msg);
    }
  }

  private SoyType parseType(String input) throws SoySyntaxException {
    return (new TypeParser(input, SourceLocation.UNKNOWN, typeRegistry)).parseTypeDeclaration();
  }
}
