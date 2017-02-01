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

package com.google.template.soy.passes;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.ExprUnionEquivalence;
import java.util.List;

/** Condition for a particular control flow branch. */
abstract class Condition {
  abstract Condition copy();

  abstract boolean isDefaultCond();

  static Condition getEmptyCondition() {
    return EmptyCondition.INSTANCE;
  }

  static Condition createIfCondition() {
    return new IfCondition();
  }

  static Condition createIfCondition(ExprUnion expr) {
    return new IfCondition(expr);
  }

  static Condition createSwitchCondition(ExprUnion switchExpr, List<ExprUnion> caseExprs) {
    return new SwitchCondition(switchExpr, caseExprs);
  }

  /** A placeholder for an empty condition. */
  private static final class EmptyCondition extends Condition {
    private static final EmptyCondition INSTANCE = new EmptyCondition();

    private EmptyCondition() {}

    @Override
    EmptyCondition copy() {
      return INSTANCE;
    }

    @Override
    boolean isDefaultCond() {
      return true;
    }
  }

  /** A condition for an {@code IfCondNode} or an {@code IfElseNode}. */
  private static final class IfCondition extends Condition {
    /**
     * An optional {@code ExprUnion} for {@code IfCondNode} or {@code IfElseNode}. This should only
     * be absent if it is for an {@code IfElseNode}.
     */
    private final Optional<ExprUnion> expr;

    IfCondition() {
      this.expr = Optional.absent();
    }

    IfCondition(ExprUnion expr) {
      this.expr = Optional.of(expr);
    }

    /** Copy constructor. */
    IfCondition(IfCondition condition) {
      if (condition.expr.isPresent()) {
        this.expr = Optional.of(condition.expr.get().copy(new CopyState()));
      } else {
        this.expr = Optional.absent();
      }
    }

    @Override
    boolean isDefaultCond() {
      return !expr.isPresent();
    }

    @Override
    Condition copy() {
      return new IfCondition(this);
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object instanceof IfCondition) {
        IfCondition cond = (IfCondition) object;
        return ExprUnionEquivalence.get().equivalent(expr.orNull(), cond.expr.orNull());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return ExprUnionEquivalence.get().hash(expr.orNull());
    }
  }

  /**
   * A {@code Condition} for children of {@code SwitchNode}. It is an union of an expression of
   * {@code SwitchNode} and a list of expressions for the current child.
   *
   * <p>TODO(user): make this class comparable so that we can sort the switch conditions.
   */
  private static final class SwitchCondition extends Condition {

    /** A expression for the parent {@code SwitchNode}. */
    private final ExprUnion switchExpr;

    /**
     * A list of {@code ExprUnion} for a {@code SwitchCaseNode} or a {@code SwitchDefaultNode}. An
     * empty list of caseExprs implies that this condition is for a SwitchDefaultNode.
     */
    private final ImmutableList<ExprUnion> caseExprs;

    SwitchCondition(ExprUnion switchExpr, List<ExprUnion> caseExprs) {
      this.switchExpr = switchExpr;
      this.caseExprs = ImmutableList.copyOf(caseExprs);
    }

    @Override
    boolean isDefaultCond() {
      return caseExprs.isEmpty();
    }

    @Override
    Condition copy() {
      return new SwitchCondition(switchExpr, caseExprs);
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object instanceof SwitchCondition) {
        SwitchCondition cond = (SwitchCondition) object;
        return ExprUnionEquivalence.get().equivalent(switchExpr, cond.switchExpr)
            && ExprUnionEquivalence.get().pairwise().equivalent(caseExprs, cond.caseExprs);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return 31 * ExprUnionEquivalence.get().hash(switchExpr)
          + ExprUnionEquivalence.get().pairwise().hash(caseExprs);
    }
  }
}
