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
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;

import junit.framework.TestCase;

import java.util.Map;


/**
 * Unit tests for SubstituteGlobalsVisitor.
 *
 * @author Kai Huang
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
    ((new SubstituteGlobalsVisitor(globals, false)).new SubstituteGlobalsInExprVisitor())
        .exec(expr);

    assertEquals("boo", ((StringNode) plus1.getChild(0)).getValue());
    assertEquals("goo", ((StringNode) plus0.getChild(1)).getValue());
  }


  public void testAssertNoUnboundGlobals() throws Exception {

    ExprRootNode<?> expr = (new ExpressionParser("BOO + 'aaa' + foo.GOO")).parseExpression();

    Map<String, PrimitiveData> globals =
        ImmutableMap.<String, PrimitiveData>of(
            "BOO", StringData.forValue("boo"), "GOO", StringData.forValue("goo"),
            "foo.MOO", StringData.forValue("moo"));

    try {
      ((new SubstituteGlobalsVisitor(globals, true)).new SubstituteGlobalsInExprVisitor())
          .exec(expr);
      fail();
    } catch (SoySyntaxException sse) {
      assertTrue(sse.getMessage().contains("Found unbound global 'foo.GOO'."));
    }
  }

}
