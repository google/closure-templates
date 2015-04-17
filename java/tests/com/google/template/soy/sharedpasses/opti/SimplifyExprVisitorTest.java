/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.sharedpasses.opti;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.error.ExplodingErrorReporter;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.sharedpasses.SharedPassesModule;

import junit.framework.TestCase;

/**
 * Unit tests for SimplifyExprVisitor.
 *
 */
public class SimplifyExprVisitorTest extends TestCase {


  public void testSimplifyFullySimplifiableExpr() throws Exception {

    assertEquals("-210", simplifyExpr("-99+-111").toSourceString());
    assertEquals("'-99-111'", simplifyExpr("-99 + '-111'").toSourceString());
    assertEquals("''", simplifyExpr("false or 0 or 0.0 or ''").toSourceString());
    assertEquals("true", simplifyExpr("0 <= 0").toSourceString());
    assertEquals("true", simplifyExpr("'22' == 22").toSourceString());
    assertEquals("true", simplifyExpr("'22' == '' + 22").toSourceString());

    // With functions.
    // TODO SOON: Uncomment these tests when basic functions have been changed to SoyJavaFunction.
    //assertEquals("8", simplifyExpr("max(4, 8)").toSourceString());
    //assertEquals("3", simplifyExpr("floor(7/2)").toSourceString());
  }


  public void testSimplifyNotSimplifiableExpr() throws Exception {

    assertEquals("$boo", simplifyExpr("$boo").toSourceString());
    assertEquals("$boo % 3", simplifyExpr("$boo % 3").toSourceString());
    assertEquals("not $boo", simplifyExpr("not $boo").toSourceString());
    assertEquals("$boo + ''", simplifyExpr("$boo + ''").toSourceString());

    // With functions.
    assertEquals("max(4, $boo)", simplifyExpr("max(4, $boo)").toSourceString());
    assertEquals("floor($boo / 3)", simplifyExpr("floor($boo / 3)").toSourceString());
  }


  public void testSimplifyPartiallySimplifiableExpr() throws Exception {

    assertEquals("15 % $boo", simplifyExpr("3 * 5 % $boo").toSourceString());
    assertEquals("not $boo", simplifyExpr("not false and not $boo").toSourceString());
    assertEquals("'ab' + $boo", simplifyExpr("'a' + 'b' + $boo").toSourceString());

    // With functions.
    // TODO SOON: Uncomment these tests when basic functions have been changed to SoyJavaFunction.
    //assertEquals("max(8, $boo)", simplifyExpr("max(max(4, 8), $boo)").toSourceString());
    assertEquals("floor($boo / 3.0)", simplifyExpr("floor($boo / (1.0 + 2))").toSourceString());
  }


  public void testSimplifyListAndMapLiterals() throws Exception {

    assertEquals("['ab', -2]", simplifyExpr("['a' + 'b', 1 - 3]").toSourceString());
    assertEquals("['ab': -2]", simplifyExpr("['a' + 'b': 1 - 3]").toSourceString());
    assertEquals("[8, ['ab', -2]]", simplifyExpr("[8, ['a' + 'b', 1 - 3]]").toSourceString());
    assertEquals("['z': ['ab': -2]]", simplifyExpr("['z': ['a' + 'b': 1 - 3]]").toSourceString());

    // With functions.
    // Note: Currently, ListLiteralNode and MapLiteralNode are never considered to be constant,
    // even though in reality, they can be constant. So in the current implementation, this keys()
    // call cannot be simplified away.
    assertEquals("keys(['ab': -2])", simplifyExpr("keys(['a' + 'b': 1 - 3])").toSourceString());
  }


  public void testSimplifyBinaryLogicalOps() throws Exception {

    // 'and'
    assertEquals("true", simplifyExpr("true and true").toSourceString());
    assertEquals("false", simplifyExpr("true and false").toSourceString());
    assertEquals("false", simplifyExpr("false and true").toSourceString());
    assertEquals("false", simplifyExpr("false and false").toSourceString());
    assertEquals("$boo", simplifyExpr("true and $boo").toSourceString());
    assertEquals("$boo and true", // Can't simplify
        simplifyExpr("$boo and true").toSourceString());
    assertEquals("1", simplifyExpr("true and 1").toSourceString());
    assertEquals("true", simplifyExpr("1 and true").toSourceString());
    assertEquals("false", simplifyExpr("false and 1").toSourceString());
    assertEquals("false", simplifyExpr("1 and false").toSourceString());
    assertEquals("false", simplifyExpr("false and $boo").toSourceString());
    assertEquals("$boo and false", // Can't simplify
        simplifyExpr("$boo and false").toSourceString());

    // 'or'
    assertEquals("true", simplifyExpr("true or true").toSourceString());
    assertEquals("true", simplifyExpr("true or false").toSourceString());
    assertEquals("true", simplifyExpr("false or true").toSourceString());
    assertEquals("false", simplifyExpr("false or false").toSourceString());
    assertEquals("true", simplifyExpr("true or $boo").toSourceString());
    assertEquals("$boo or true", // Can't simplify
        simplifyExpr("$boo or true").toSourceString());
    assertEquals("$boo", simplifyExpr("false or $boo").toSourceString());
    assertEquals("$boo or false",
        simplifyExpr("$boo or false").toSourceString());
    assertEquals("1", simplifyExpr("false or 1").toSourceString());
    assertEquals("1", simplifyExpr("1 or false").toSourceString());
    assertEquals("true", simplifyExpr("true or 1").toSourceString());
    assertEquals("1", simplifyExpr("1 or true").toSourceString());
  }


  public void testSimplifyConditionalOp() throws Exception {

    assertEquals("111", simplifyExpr("true ? 111 : 222").toSourceString());
    assertEquals("222", simplifyExpr("false ? 111 : 222").toSourceString());
    assertEquals("111", simplifyExpr("true ? 111 : $boo").toSourceString());
    assertEquals("222", simplifyExpr("false ? $boo : 222").toSourceString());
    assertEquals("$boo or true ? $boo and false : true", // Can't simplify
        simplifyExpr("$boo or true ? $boo and false : true").toSourceString());
  }


  // -----------------------------------------------------------------------------------------------
  // Helpers.


  private static final Injector INJECTOR =
      Guice.createInjector(new SharedPassesModule(), new BasicFunctionsModule());


  private static ExprNode simplifyExpr(String expression) throws Exception {
    ExprRootNode exprRoot = new ExprRootNode(
        new ExpressionParser(expression, SourceLocation.UNKNOWN, ExplodingErrorReporter.get())
          .parseExpression());
    SimplifyExprVisitor simplifyExprVisitor = INJECTOR.getInstance(SimplifyExprVisitor.class);
    simplifyExprVisitor.exec(exprRoot);
    return exprRoot;
  }

}
