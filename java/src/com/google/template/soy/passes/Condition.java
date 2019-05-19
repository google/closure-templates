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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import java.util.List;
import java.util.Optional;

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

  static Condition createIfCondition(ExprNode expr) {
    return new IfCondition(expr);
  }

  static Condition createSwitchCondition(ExprNode switchExpr, List<ExprRootNode> caseExprs) {
    // cast ImmutableList<ExprRootNode> to List<ExprNode>
    @SuppressWarnings("unchecked")
    List<ExprNode> list = (List<ExprNode>) ((List<?>) caseExprs);
    return new SwitchCondition(switchExpr, list);
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
    public String toString() {
      return "EMPTY_CONDITION";
    }

    @Override
    boolean isDefaultCond() {
      return true;
    }
  }

  /** A condition for an {@code IfCondNode} or an {@code IfElseNode}. */
  private static final class IfCondition extends Condition {
    /**
     * An optional {@code ExprNode} for {@code IfCondNode} or {@code IfElseNode}. This should only
     * be absent if it is for an {@code IfElseNode}.
     */
    private final Optional<ExprNode> expr;

    IfCondition() {
      this.expr = Optional.empty();
    }

    IfCondition(ExprNode expr) {
      this.expr = Optional.of(expr);
    }

    /** Copy constructor. */
    IfCondition(IfCondition condition) {
      if (condition.expr.isPresent()) {
        this.expr = Optional.of(condition.expr.get().copy(new CopyState()));
      } else {
        this.expr = Optional.empty();
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
    public String toString() {
      return "IF_CONDITION: " + this.expr;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object instanceof IfCondition) {
        IfCondition cond = (IfCondition) object;
        return ExprEquivalence.get().equivalent(expr.orElse(null), cond.expr.orElse(null));
      }
      return false;
    }

    @Override
    public int hashCode() {
      return ExprEquivalence.get().hash(expr.orElse(null));
    }
  }

  /**
   * A {@code Condition} for children of {@code SwitchNode}. It is a union of an expression of
   * {@code SwitchNode} and a list of expressions for the current child.
   *
   * <p>TODO(user): make this class comparable so that we can sort the switch conditions.
   */
  private static final class SwitchCondition extends Condition {

    /** A expression for the parent {@code SwitchNode}. */
    private final ExprNode switchExpr;

    /**
     * A list of {@code ExprNode} for a {@code SwitchCaseNode} or a {@code SwitchDefaultNode}. An
     * empty list of caseExprs implies that this condition is for a SwitchDefaultNode.
     */
    private final ImmutableList<ExprNode> caseExprs;

    SwitchCondition(ExprNode switchExpr, List<ExprNode> caseExprs) {
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
    public String toString() {
      return "SWITCH_CONDITION: " + this.switchExpr + ", CASE: " + this.caseExprs;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (object instanceof SwitchCondition) {
        SwitchCondition cond = (SwitchCondition) object;
        return ExprEquivalence.get().equivalent(switchExpr, cond.switchExpr)
            && ExprEquivalence.get().pairwise().equivalent(caseExprs, cond.caseExprs);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return 31 * ExprEquivalence.get().hash(switchExpr)
          + ExprEquivalence.get().pairwise().hash(caseExprs);
    }
  }
}
