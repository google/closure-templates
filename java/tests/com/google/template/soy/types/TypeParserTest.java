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
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.parse.ParseException;
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
    assertParseError("foo", "Unknown type");
    assertParseError("any any");
    assertParseError("any<string>", "parameters not allowed");
    assertParseError("list<");
    assertParseError("list<>");
    assertParseError("list<string");
    assertParseError("list", "Expected 1");
    assertParseError("list<string, string>", "Expected 1");
    assertParseError("map", "Expected 2");
    assertParseError("map<string>", "Expected 2");
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private void assertTypeEquals(SoyType expected, String typeInput) {
    try {
      assertEquals(expected, parseType(typeInput));
    } catch (Exception e) {
      fail("Error parsing '" + typeInput + "': " + e.getMessage());
      e.printStackTrace();
    }
  }


  private void assertParseError(String typeInput) {
    try {
      parseType(typeInput);
      fail("Input string '" + typeInput + "' should have failed to parse.");
    } catch (Exception e) {
      // Success
    }
  }


  private void assertParseError(String typeInput, String msg) {
    try {
      parseType(typeInput);
      fail("Input string '" + typeInput + "' should have failed to parse.");
    } catch (ParseException e) {
      assertTrue(e.getMessage().contains(msg));
    }
  }


  private SoyType parseType(String input) throws ParseException {
    return (new TypeParser(input, typeRegistry)).parseTypeDeclaration();
  }
}
