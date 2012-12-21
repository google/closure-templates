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

package com.google.template.soy.parsepasses;

import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.parsepasses.RewriteNullCoalescingOpVisitor.RewriteNullCoalescingOpInExprVisitor;

import junit.framework.TestCase;


/**
 * Unit tests for RewriteNullCoalescingOpVisitor.
 *
 * @author Kai Huang
 */
public class RewriteNullCoalescingOpVisitorTest extends TestCase {


  public void testRewriteExpr() throws Exception {

    assertRewrite("$boo ?: -1", "isNonnull($boo) ? $boo : -1");

    assertRewrite("$a ?: $b ?: $c", "isNonnull($a) ? $a : isNonnull($b) ? $b : $c");
    assertRewrite("$a ?: $b ? $c : $d", "isNonnull($a) ? $a : $b ? $c : $d");
    assertRewrite("$a ? $b ?: $c : $d", "$a ? (isNonnull($b) ? $b : $c) : $d");
    assertRewrite("$a ? $b : $c ?: $d", "$a ? $b : isNonnull($c) ? $c : $d");
    assertRewrite(
        "($a ?: $b) ?: $c", "isNonnull(isNonnull($a) ? $a : $b) ? (isNonnull($a) ? $a : $b) : $c");
    assertRewrite("$a ?: ($b ?: $c)", "isNonnull($a) ? $a : isNonnull($b) ? $b : $c");
    assertRewrite("($a ?: $b) ? $c : $d", "(isNonnull($a) ? $a : $b) ? $c : $d");
  }


  /**
   * Private helper to check the rewrite of one expression.
   */
  private void assertRewrite(String origSrc, String expectedRewrittenSrc) throws Exception {

    ExprNode expr = (new ExpressionParser(origSrc)).parseExpression();
    (new RewriteNullCoalescingOpInExprVisitor()).exec(expr);
    String rewrittenSrc = expr.toSourceString();
    assertEquals(expectedRewrittenSrc, rewrittenSrc);
  }

}
