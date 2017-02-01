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

package com.google.template.soy.soytree;

import com.google.common.base.Equivalence;
import com.google.template.soy.exprtree.ExprEquivalence;

/** An equivalence relation for ExprUnion. This handles both Soy v1 and v2 expressions. */
public final class ExprUnionEquivalence extends Equivalence<ExprUnion> {
  private static final ExprUnionEquivalence INSTANCE = new ExprUnionEquivalence();

  public static ExprUnionEquivalence get() {
    return INSTANCE;
  }

  private ExprUnionEquivalence() {}

  @Override
  protected boolean doEquivalent(ExprUnion a, ExprUnion b) {
    if (a.getExpr() == null && b.getExpr() == null) {
      // For V1 expressions, compare the text contents.
      return a.getExprText().equals(b.getExprText());
    } else if (a.getExpr() != null && b.getExpr() != null) {
      // For V2 expressions, delegate to ExprEquivalence for comparison.
      return ExprEquivalence.get().equivalent(a.getExpr(), b.getExpr());
    } else {
      // Return false if two inputs have different versions.
      return false;
    }
  }

  @Override
  protected int doHash(ExprUnion t) {
    if (t.getExpr() == null) {
      return t.getExprText().hashCode();
    }
    return 31 * t.getExprText().hashCode() + ExprEquivalence.get().hash(t.getExpr());
  }
}
