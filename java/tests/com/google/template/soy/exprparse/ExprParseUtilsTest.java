/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.exprparse;

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.StringNode;

import junit.framework.TestCase;


/**
 * Unit tests for ExprParseUtils.
 *
 * @author Kai Huang
 */
public class ExprParseUtilsTest extends TestCase {


  public void testParseExprElseThrowSoySyntaxException() {

    ExprRootNode<?> expr =
        ExprParseUtils.parseExprElseThrowSoySyntaxException("'foo'", "bad expr");
    assertTrue(expr.getChild(0) instanceof StringNode);

    try {
      ExprParseUtils.parseExprElseThrowSoySyntaxException("'foo", "bad expr");
      fail();
    } catch (SoySyntaxException sse) {
      assertEquals("bad expr", sse.getMessage());
      assertTrue(sse.getCause() instanceof TokenMgrError);
    }

    try {
      ExprParseUtils.parseExprElseThrowSoySyntaxException("$ij", "bad expr");
      fail();
    } catch (SoySyntaxException sse) {
      assertEquals("bad expr", sse.getMessage());
      assertTrue(sse.getCause() instanceof ParseException);
    }
  }

}
