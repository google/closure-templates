/*
 * Copyright 2023 Google Inc.
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

import static com.google.template.soy.exprtree.ExprNodes.isNullishLiteral;
import static com.google.template.soy.types.SoyTypes.tryKeepNullish;
import static com.google.template.soy.types.SoyTypes.tryRemoveNull;
import static com.google.template.soy.types.SoyTypes.tryRemoveNullish;
import static com.google.template.soy.types.SoyTypes.tryRemoveUndefined;

import com.google.common.base.Preconditions;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NullCoalescingOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UndefinedType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visitor which analyzes a boolean expression and determines if any of the variables involved in
 * the expression can have their types narrowed depending on the outcome of the condition.
 *
 * <p>For example, if the condition is "$var != null", then we know that if the condition is true,
 * then $var cannot be of null type. We also know that if the condition is false, then $var must be
 * of the null type.
 *
 * <p>The result of the analysis is two "constraint maps", which map variables to types, indicating
 * that the variable satisfies the criteria of being of that type.
 *
 * <p>The "positiveConstraints" map is the set of constraints that will be satisfied if the
 * condition is true, the "negativeConstraints" is the set of constraints that will be satisfied if
 * the condition is false.
 *
 * <p>TODO(user) - support instanceof tests. Right now the only type tests supported are
 * comparisons with null. If we added an 'instanceof' operator to Soy, the combination of instanceof
 * + flow-based type analysis would effectively allow template authors to do typecasts, without
 * having to add a cast operator to the language.
 */
final class TypeNarrowingConditionVisitor extends AbstractExprNodeVisitor<Void> {

  private final ExprEquivalence exprEquivalence;
  private final SoyTypeRegistry typeRegistry;

  // Type constraints that are valid if the condition is true.
  Map<ExprEquivalence.Wrapper, SoyType> positiveTypeConstraints = new LinkedHashMap<>();

  // Type constraints that are valid if the condition is false.
  Map<ExprEquivalence.Wrapper, SoyType> negativeTypeConstraints = new LinkedHashMap<>();

  public TypeNarrowingConditionVisitor(
      ExprEquivalence exprEquivalence, SoyTypeRegistry typeRegistry) {
    this.exprEquivalence = exprEquivalence;
    this.typeRegistry = typeRegistry;
  }

  @Override
  public Void exec(ExprNode node) {
    visitAndImplicitlyCastToBoolean(node);
    return null;
  }

  @Override
  protected void visitExprRootNode(ExprRootNode node) {
    visitAndImplicitlyCastToBoolean(node.getRoot());
  }

  void visitAndImplicitlyCastToBoolean(ExprNode node) {
    // In places where the expression is implicitly cast to a boolean, treat
    // a reference to a variable as a comparison of that variable with null.
    // So for example an expression like {if $var} should be treated as
    // {if $var != null} but something like {if $var > 0} should not be changed.
    visit(node);
    ExprEquivalence.Wrapper wrapped = exprEquivalence.wrap(node);
    positiveTypeConstraints.put(wrapped, tryRemoveNullish(node.getType()));
    // TODO(lukes): The 'negative' type constraint here is not optimal.  What we really know is
    // that the value of the expression is 'falsy' we could use that to inform later checks but
    // for now we just assume it has its normal type.
    negativeTypeConstraints.put(wrapped, node.getType());
  }

  @Override
  protected void visitAndOpNode(AndOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    // Create two separate visitors to analyze each side of the expression.
    TypeNarrowingConditionVisitor leftVisitor =
        new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
    TypeNarrowingConditionVisitor rightVisitor =
        new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
    leftVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
    rightVisitor.visitAndImplicitlyCastToBoolean(node.getChild(1));

    // Both the left side and right side constraints will be valid if the condition is true.
    computeConstraintUnionInto(
        leftVisitor.positiveTypeConstraints,
        rightVisitor.positiveTypeConstraints,
        /* into= */ positiveTypeConstraints);
    // If the condition is false, then the overall constraint is the intersection of
    // the complements of the true constraints.
    computeConstraintIntersectionInto(
        leftVisitor.negativeTypeConstraints,
        rightVisitor.negativeTypeConstraints,
        /* into= */ negativeTypeConstraints);
  }

  @Override
  protected void visitOrOpNode(OrOpNode node) {
    Preconditions.checkArgument(node.numChildren() == 2);
    // Create two separate visitors to analyze each side of the expression.
    TypeNarrowingConditionVisitor leftVisitor =
        new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
    TypeNarrowingConditionVisitor rightVisitor =
        new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
    leftVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
    rightVisitor.visitAndImplicitlyCastToBoolean(node.getChild(1));

    // If the condition is true, then only constraints that appear on both sides of the
    // operator will be valid.
    computeConstraintIntersectionInto(
        leftVisitor.positiveTypeConstraints,
        rightVisitor.positiveTypeConstraints,
        /* into= */ positiveTypeConstraints);
    // If the condition is false, then both sides must be false, so the overall constraint
    // is the union of the complements of the constraints on each side.
    computeConstraintUnionInto(
        leftVisitor.negativeTypeConstraints,
        rightVisitor.negativeTypeConstraints,
        /* into= */ negativeTypeConstraints);
  }

  @Override
  protected void visitNotOpNode(NotOpNode node) {
    // For a logical not node, compute the positive and negative constraints of the
    // operand and then simply swap them.
    TypeNarrowingConditionVisitor childVisitor =
        new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
    childVisitor.visitAndImplicitlyCastToBoolean(node.getChild(0));
    positiveTypeConstraints.putAll(childVisitor.negativeTypeConstraints);
    negativeTypeConstraints.putAll(childVisitor.positiveTypeConstraints);
  }

  @Override
  protected void visitEqualOpNode(EqualOpNode node) {
    if (isNullishLiteral(node.getChild(1))) {
      addNullishEqualOpConstraint(node.getChild(0), false);
    } else if (isNullishLiteral(node.getChild(0))) {
      addNullishEqualOpConstraint(node.getChild(1), false);
    }
  }

  @Override
  protected void visitNotEqualOpNode(NotEqualOpNode node) {
    if (isNullishLiteral(node.getChild(1))) {
      addNullishEqualOpConstraint(node.getChild(0), true);
    } else if (isNullishLiteral(node.getChild(0))) {
      addNullishEqualOpConstraint(node.getChild(1), true);
    }
  }

  private void addNullishEqualOpConstraint(ExprNode node, boolean notEqual) {
    ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node);
    SoyType type = wrappedExpr.get().getType();
    (notEqual ? positiveTypeConstraints : negativeTypeConstraints)
        .put(wrappedExpr, tryRemoveNullish(type));
    (notEqual ? negativeTypeConstraints : positiveTypeConstraints)
        .put(
            wrappedExpr,
            SoyTypes.isNullish(type) ? tryKeepNullish(type) : SoyTypes.NULL_OR_UNDEFINED);
  }

  @Override
  protected void visitTripleEqualOpNode(TripleEqualOpNode node) {
    if (isNullishLiteral(node.getChild(1))) {
      addNullishTripleEqualOpConstraint(node.getChild(0), false, node.getChild(1).getKind());
    } else if (isNullishLiteral(node.getChild(0))) {
      addNullishTripleEqualOpConstraint(node.getChild(1), false, node.getChild(0).getKind());
    }
  }

  @Override
  protected void visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
    if (isNullishLiteral(node.getChild(1))) {
      addNullishTripleEqualOpConstraint(node.getChild(0), true, node.getChild(1).getKind());
    } else if (isNullishLiteral(node.getChild(0))) {
      addNullishTripleEqualOpConstraint(node.getChild(1), true, node.getChild(0).getKind());
    }
  }

  private void addNullishTripleEqualOpConstraint(
      ExprNode node, boolean notEqual, ExprNode.Kind compareKind) {
    ExprEquivalence.Wrapper wrappedExpr = exprEquivalence.wrap(node);
    SoyType type = wrappedExpr.get().getType();
    (notEqual ? positiveTypeConstraints : negativeTypeConstraints)
        .put(
            wrappedExpr,
            compareKind == ExprNode.Kind.NULL_NODE
                ? tryRemoveNull(type)
                : tryRemoveUndefined(type));
    (notEqual ? negativeTypeConstraints : positiveTypeConstraints)
        .put(
            wrappedExpr,
            compareKind == ExprNode.Kind.NULL_NODE
                ? NullType.getInstance()
                : UndefinedType.getInstance());
  }

  @Override
  protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
    for (ExprNode nullSafeBase : node.asNullSafeBaseList()) {
      positiveTypeConstraints.put(
          exprEquivalence.wrap(nullSafeBase), tryRemoveNullish(nullSafeBase.getType()));
    }
  }

  @Override
  protected void visitNullCoalescingOpNode(NullCoalescingOpNode node) {
    // Don't make any inferences (don't visit children).
    // Note: It would be possible to support this case by expanding it into
    // if-statements.
  }

  @Override
  protected void visitConditionalOpNode(ConditionalOpNode node) {
    // Don't make any inferences (don't visit children).
    // Note: It would be possible to support this case by expanding it into
    // if-statements.
  }

  @Override
  protected void visitFunctionNode(FunctionNode node) {}

  @Override
  protected void visitExprNode(ExprNode node) {
    if (node instanceof ParentExprNode) {
      visitChildren((ParentExprNode) node);
    }
  }

  /**
   * Compute a map which combines the constraints from both the left and right side of an
   * expression. The result should be a set of constraints which satisfy <strong>both</strong>
   * sides.
   *
   * @param left Constraints from the left side.
   * @param right Constraints from the right side.
   */
  private <T> void computeConstraintUnionInto(
      Map<T, SoyType> left, Map<T, SoyType> right, Map<T, SoyType> into) {
    if (left.isEmpty()) {
      return;
    }
    if (right.isEmpty()) {
      return;
    }
    into.putAll(left);
    for (Map.Entry<T, SoyType> entry : right.entrySet()) {
      // The union of two constraints is a *stricter* constraint.
      // Thus "((a instanceof any) AND (a instanceof bool)) == (a instanceof bool)"
      // For now, it's sufficient that the map contains an entry for the variable
      // (since we're only testing for nullability). Once we add support for more
      // complex type tests, we'll need to add code here that combines the two
      // constraints.
      into.putIfAbsent(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Compute a map which combines the constraints from both the left and right side of an
   * expression. The result should be a set of constraints which satisfy <strong>either</strong>
   * sides.
   *
   * @param left Constraints from the left side.
   * @param right Constraints from the right side.
   */
  private <T> void computeConstraintIntersectionInto(
      Map<T, SoyType> left, Map<T, SoyType> right, Map<T, SoyType> into) {
    if (left.isEmpty()) {
      return;
    }
    if (right.isEmpty()) {
      return;
    }
    for (Map.Entry<T, SoyType> entry : left.entrySet()) {
      // A variable must be present in both the left and right sides in order to be
      // included in the output.
      SoyType rightSideType = right.get(entry.getKey());
      if (rightSideType != null) {
        // The intersection of two constraints is a *looser* constraint.
        // Thus "((a instanceof any) OR (a instanceof bool)) == (a instanceof any)"
        into.put(
            entry.getKey(),
            SoyTypes.computeLowestCommonType(typeRegistry, entry.getValue(), rightSideType));
      }
    }
  }
}
