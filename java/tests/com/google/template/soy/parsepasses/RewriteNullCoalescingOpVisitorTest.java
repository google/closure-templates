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

import static com.google.common.truth.Truth.assertThat;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.parsepasses.RewriteNullCoalescingOpVisitor.RewriteNullCoalescingOpInExprVisitor;
import com.google.template.soy.soyparse.ErrorReporter;
import com.google.template.soy.soyparse.ExplodingErrorReporter;

import junit.framework.TestCase;

/**
 * Unit tests for {@link RewriteNullCoalescingOpVisitor}.
 *
 */
public final class RewriteNullCoalescingOpVisitorTest extends TestCase {

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

  private static void assertRewrite(String origSrc, String expectedRewrittenSrc) throws Exception {
    ErrorReporter boom = ExplodingErrorReporter.get();
    ExprRootNode expr = new ExprRootNode(
        new ExpressionParser(origSrc, SourceLocation.UNKNOWN, boom).parseExpression());
    new RewriteNullCoalescingOpInExprVisitor(boom).exec(expr);
    String rewrittenSrc = expr.toSourceString();
    assertThat(rewrittenSrc).isEqualTo(expectedRewrittenSrc);
  }
}
