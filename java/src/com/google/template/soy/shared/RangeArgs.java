/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.shared;

import com.google.auto.value.AutoValue;
import com.google.template.soy.basicfunctions.RangeFunction;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.soytree.ForNode;
import java.util.List;
import java.util.Optional;

/**
 * A class that represents the arguments of a {@code range(...)} expression in a {@code {for ...}}
 * loop statement.
 */
@AutoValue
public abstract class RangeArgs {
  private static RangeArgs create(List<ExprNode> args) {
    switch (args.size()) {
      case 1:
        return new AutoValue_RangeArgs(Optional.empty(), args.get(0), Optional.empty());
      case 2:
        return new AutoValue_RangeArgs(Optional.of(args.get(0)), args.get(1), Optional.empty());
      case 3:
        return new AutoValue_RangeArgs(
            Optional.of(args.get(0)), args.get(1), Optional.of(args.get(2)));
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns a optional {@link RangeArgs} object if the for loop expression is a {@code range(...)}
   * expression.
   */
  public static Optional<RangeArgs> createFromNode(ForNode node) {
    return createFromExpr(node.getExpr());
  }

  /**
   * Returns a optional {@link RangeArgs} object if the expression is a {@code range(...)}
   * expression.
   */
  public static Optional<RangeArgs> createFromExpr(ExprNode node) {
    if (node instanceof ExprRootNode) {
      node = ((ExprRootNode) node).getRoot();
    }
    if (node instanceof FunctionNode) {
      FunctionNode fn = (FunctionNode) node;
      if (fn.getSoyFunction() instanceof RangeFunction) {
        return Optional.of(create(fn.getParams()));
      }
    }
    return Optional.empty();
  }

  /** The expression for the iteration start point. Default is {@code 0}. */
  public abstract Optional<ExprNode> start();

  /** The expression for the iteration end point. This is interpreted as an exclusive limit. */
  public abstract ExprNode limit();

  /** The expression for the iteration increment. Default is {@code 1}. */
  public abstract Optional<ExprNode> increment();
}
