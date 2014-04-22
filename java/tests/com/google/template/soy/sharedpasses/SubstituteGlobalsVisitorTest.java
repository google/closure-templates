/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.types.SoyEnumType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import junit.framework.TestCase;

import java.util.Map;

import javax.annotation.Nullable;


/**
 * Unit tests for SubstituteGlobalsVisitor.
 *
 */
public class SubstituteGlobalsVisitorTest extends TestCase {


  public void testSubstituteGlobals() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("BOO + 'aaa' + foo.GOO")).parseExpression();
    PlusOpNode plus0 = (PlusOpNode) expr.getChild(0);
    PlusOpNode plus1 = (PlusOpNode) plus0.getChild(0);

    assertEquals("BOO", ((GlobalNode) plus1.getChild(0)).getName());
    assertEquals("foo.GOO", ((GlobalNode) plus0.getChild(1)).getName());

    Map<String, PrimitiveData> globals =
        ImmutableMap.<String, PrimitiveData>of(
            "BOO", StringData.forValue("boo"), "foo.GOO", StringData.forValue("goo"),
            "foo.MOO", StringData.forValue("moo"));
    ((new SubstituteGlobalsVisitor(globals, null, false)).new SubstituteGlobalsInExprVisitor())
        .exec(expr);

    assertEquals("boo", ((StringNode) plus1.getChild(0)).getValue());
    assertEquals("goo", ((StringNode) plus0.getChild(1)).getValue());
  }


  public void testSubstituteGlobalsFromType() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("foo.BOO + foo.GOO")).parseExpression();
    PlusOpNode plus0 = (PlusOpNode) expr.getChild(0);

    assertEquals("foo.BOO", ((GlobalNode) plus0.getChild(0)).getName());
    assertEquals("foo.GOO", ((GlobalNode) plus0.getChild(1)).getName());

    // Fake enum type
    final SoyEnumType enumType = new SoyEnumType() {
      private Map<String, Integer> values = new ImmutableMap.Builder<String, Integer>()
          .put("BOO", 1).put("GOO", 2).build();

      @Override public Kind getKind() {
        return SoyType.Kind.ENUM;
      }

      @Override public boolean isAssignableFrom(SoyType srcType) {
        return true;
      }

      @Override public boolean isInstance(SoyValue value) {
        return false;
      }

      @Override public String getName() {
        return "foo";
      }

      @Override public String getNameForBackend(SoyBackendKind backend) {
        return "foo";
      }

      @Override @Nullable public Integer getValue(String memberName) {
        return values.get(memberName);
      }
    };

    // Fake type provider
    SoyTypeProvider enumTypeProvider = new SoyTypeProvider() {
      @Override
      public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
        if (typeName.equals("foo")) {
          return enumType;
        }
        return null;
      }
    };

    // Create a registry with the enum type
    SoyTypeRegistry typeRegistry = new SoyTypeRegistry(
        ImmutableSet.<SoyTypeProvider>of(enumTypeProvider));
    ((new SubstituteGlobalsVisitor(null, typeRegistry, false)).new SubstituteGlobalsInExprVisitor())
        .exec(expr);

    assertEquals(1, ((IntegerNode) plus0.getChild(0)).getValue());
    assertEquals(2, ((IntegerNode) plus0.getChild(1)).getValue());
  }


  public void testAssertNoUnboundGlobals() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("BOO + 'aaa' + foo.GOO")).parseExpression();

    Map<String, PrimitiveData> globals =
        ImmutableMap.<String, PrimitiveData>of(
            "BOO", StringData.forValue("boo"), "GOO", StringData.forValue("goo"),
            "foo.MOO", StringData.forValue("moo"));

    try {
      ((new SubstituteGlobalsVisitor(globals, null, true)).new SubstituteGlobalsInExprVisitor())
          .exec(expr);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains("Found unbound global 'foo.GOO'."));
    }
  }

}
