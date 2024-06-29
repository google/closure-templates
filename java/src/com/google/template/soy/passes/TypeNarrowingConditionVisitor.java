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

import static com.google.template.soy.exprtree.ExprNodes.isNonFalsyLiteral;
import static com.google.template.soy.exprtree.ExprNodes.isNonNullishLiteral;
import static com.google.template.soy.exprtree.ExprNodes.isNullishLiteral;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.AbstractExprNodeVisitor;
import com.google.template.soy.exprtree.AbstractOperatorNode;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullSafeAccessNode;
import com.google.template.soy.exprtree.OperatorNodes.AmpAmpOpNode;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.BarBarOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.InstanceOfOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TripleNotEqualOpNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.types.AnyType;
import com.google.template.soy.types.NeverType;
import com.google.template.soy.types.NullType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.UndefinedType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
final class TypeNarrowingConditionVisitor {

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

  public void ifTruthy(ExprNode node) {
    // In places where the expression is implicitly cast to a boolean, treat
    // a reference to a variable as a comparison of that variable with null.
    // So for example an expression like {if $var} should be treated as
    // {if $var != null} but something like {if $var > 0} should not be changed.
    new TruthyVisitor().exec(node);
  }

  private void ifNullish(ExprNode node, NullishMode mode, boolean neq) {
    new NonNullishVisitor(neq, mode, true).exec(node);
  }

  public void ifNonNullish(ExprNode node) {
    new NonNullishVisitor(false, NullishMode.NULLISH, false).exec(node);
  }

  private TypeNarrowingConditionVisitor createTypeNarrowingConditionVisitor() {
    return new TypeNarrowingConditionVisitor(exprEquivalence, typeRegistry);
  }

  enum NullishMode {
    NULLISH,
    NULL,
    UNDEFINED;

    public static NullishMode forNode(ExprNode node) {
      switch (node.getKind()) {
        case NULL_NODE:
          return NULL;
        case UNDEFINED_NODE:
          return UNDEFINED;
        default:
          throw new IllegalArgumentException(node.getKind().toString());
      }
    }
  }

  private final class NonNullishVisitor extends AbstractExprNodeVisitor<Void> {

    private final boolean neq; // if operator is != or !==
    private final boolean nullish; // if comparing to null/undefined
    private final NullishMode mode;

    public NonNullishVisitor(boolean neq, NullishMode mode, boolean nullish) {
      this.neq = neq;
      this.mode = mode;
      this.nullish = nullish;
    }

    private SoyType tryRemoveNullish(SoyType type) {
      switch (mode) {
        case NULLISH:
          return SoyTypes.isNullOrUndefined(type)
              ? NeverType.getInstance()
              : SoyTypes.removeNullish(type);
        case NULL:
          return type.equals(NullType.getInstance())
              ? NeverType.getInstance()
              : SoyTypes.removeNull(type);
        case UNDEFINED:
          return type.equals(UndefinedType.getInstance())
              ? NeverType.getInstance()
              : SoyTypes.removeUndefined(type);
      }
      throw new AssertionError();
    }

    private SoyType tryKeepNullish(SoyType type) {
      switch (mode) {
        case NULLISH:
          return SoyTypes.isNullish(type)
              ? SoyTypes.tryKeepNullish(type)
              : SoyTypes.NULL_OR_UNDEFINED;
        case NULL:
          return NullType.getInstance();
        case UNDEFINED:
          return UndefinedType.getInstance();
      }
      throw new AssertionError();
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      if (isNullishNonTransform(node)) {
        visit(node.getParam(0));
        return;
      }
      visitExprNode(node);
    }

    @Override
    protected void visitExprNode(ExprNode node) {
      ExprEquivalence.Wrapper wrapped = exprEquivalence.wrap(node);
      if (nullish) {
        if (neq) {
          // x != null -> positive: not null, negative: null
          negativeTypeConstraints.put(wrapped, tryKeepNullish(node.getType()));
          positiveTypeConstraints.put(wrapped, tryRemoveNullish(node.getType()));
        } else {
          // x == null -> positive: null, negative: not null
          positiveTypeConstraints.put(wrapped, tryKeepNullish(node.getType()));
          negativeTypeConstraints.put(wrapped, tryRemoveNullish(node.getType()));
        }
      } else {
        if (neq) {
          // x != 'a' -> -
        } else {
          // x == 'a' -> positive: not null
          positiveTypeConstraints.put(wrapped, tryRemoveNullish(node.getType()));
        }
      }
    }

    @Override
    protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
      boolean nonNullConstant = mode == NullishMode.NULL || !nullish;
      Map<ExprEquivalence.Wrapper, SoyType> chainConstraints;
      if (neq) {
        if (nonNullConstant) {
          // a?.b !== undefined: negative (for now)
          // a?.b != 'a': negative
          chainConstraints = negativeTypeConstraints;
        } else {
          // a?.b != null: positive
          // a?.b !== null: positive (for now)
          chainConstraints = positiveTypeConstraints;
        }
      } else {
        if (nonNullConstant) {
          // a?.b == 'a': positive
          // a?.b === undefined: positive (for now)
          chainConstraints = positiveTypeConstraints;
        } else {
          // a?.b == null: negative
          // a?.b === null: negative (for now)
          chainConstraints = negativeTypeConstraints;
        }
      }

      ImmutableList<ExprNode> chain = node.asNullSafeBaseList();
      // Narrow [a, a.b] to non-nullish
      for (int i = 0; i < chain.size() - 1; i++) {
        ExprNode nullSafeBase = chain.get(i);
        chainConstraints.put(
            exprEquivalence.wrap(nullSafeBase), SoyTypes.tryRemoveNullish(nullSafeBase.getType()));
      }
      if (!chain.isEmpty()) {
        // Narrow a.b.c to non null/undefined/nullish
        visitExprNode(Iterables.getLast(chain));
      }

      // a?.b?.c was already narrowed by TruthyVisitor
      visitExprNode(node);
    }
  }

  private final class TruthyVisitor extends AbstractExprNodeVisitor<Void> {

    @Override
    protected void visitExprNode(ExprNode node) {
      ExprEquivalence.Wrapper wrapped = exprEquivalence.wrap(node);
      positiveTypeConstraints.put(wrapped, SoyTypes.tryRemoveNullish(node.getType()));
      // TODO(lukes): The 'negative' type constraint here is not optimal.  What we really know is
      // that the value of the expression is 'falsy' we could use that to inform later checks but
      // for now we just assume it has its normal type.
      negativeTypeConstraints.put(wrapped, node.getType());
    }

    @Override
    protected void visitExprRootNode(ExprRootNode node) {
      visit(node.getRoot());
      super.visitExprRootNode(node);
    }

    @Override
    protected void visitFunctionNode(FunctionNode node) {
      if (isTruthyNonTransform(node)) {
        visit(node.getParam(0));
        return;
      }
      if (node.getFunctionName().equals("hasContent")
          || node.getFunctionName().equals("isTruthyNonEmpty")) {
        TypeNarrowingConditionVisitor childVisitor = createTypeNarrowingConditionVisitor();
        childVisitor.ifTruthy(node.getChild(0));
        positiveTypeConstraints.putAll(childVisitor.positiveTypeConstraints);
      }
      visitExprNode(node);
    }

    @Override
    protected void visitAmpAmpOpNode(AmpAmpOpNode node) {
      processAnd(node);
    }

    @Override
    protected void visitAndOpNode(AndOpNode node) {
      processAnd(node);
    }

    private void processAnd(AbstractOperatorNode node) {
      Preconditions.checkArgument(node.numChildren() == 2);
      // Create two separate visitors to analyze each side of the expression.
      TypeNarrowingConditionVisitor leftVisitor = createTypeNarrowingConditionVisitor();
      TypeNarrowingConditionVisitor rightVisitor = createTypeNarrowingConditionVisitor();
      leftVisitor.ifTruthy(node.getChild(0));
      rightVisitor.ifTruthy(node.getChild(1));

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
    protected void visitBarBarOpNode(BarBarOpNode node) {
      processOr(node);
    }

    @Override
    protected void visitOrOpNode(OrOpNode node) {
      processOr(node);
    }

    private void processOr(AbstractOperatorNode node) {
      Preconditions.checkArgument(node.numChildren() == 2);
      // Create two separate visitors to analyze each side of the expression.
      TypeNarrowingConditionVisitor leftVisitor = createTypeNarrowingConditionVisitor();
      TypeNarrowingConditionVisitor rightVisitor = createTypeNarrowingConditionVisitor();
      leftVisitor.ifTruthy(node.getChild(0));
      rightVisitor.ifTruthy(node.getChild(1));

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
    protected void visitInstanceOfOpNode(InstanceOfOpNode node) {
      ExprNode expr = node.getChild(0);
      SoyType exprType = expr.getType();
      SoyType instanceType = node.getChild(1).getType();

      SoyType positiveType = instanceOfIntersection(exprType, instanceType);
      if (!positiveType.equals(exprType)) {
        positiveTypeConstraints.put(exprEquivalence.wrap(expr), positiveType);
      }
      SoyType negativeType = instanceOfRemainder(exprType, instanceType);
      if (!negativeType.equals(exprType)) {
        negativeTypeConstraints.put(exprEquivalence.wrap(expr), negativeType);
      }
    }

    @Override
    protected void visitNotOpNode(NotOpNode node) {
      // For a logical not node, compute the positive and negative constraints of the
      // operand and then simply swap them.
      TypeNarrowingConditionVisitor childVisitor = createTypeNarrowingConditionVisitor();
      childVisitor.ifTruthy(node.getChild(0));
      positiveTypeConstraints.putAll(childVisitor.negativeTypeConstraints);
      negativeTypeConstraints.putAll(childVisitor.positiveTypeConstraints);
    }

    @Override
    protected void visitEqualOpNode(EqualOpNode node) {
      if (isNullishLiteral(node.getChild(1))) {
        ifNullish(node.getChild(0), NullishMode.NULLISH, false);
      } else if (isNonNullishLiteral(node.getChild(1))) {
        ifNonNullish(node.getChild(0));
      }

      if (isNullishLiteral(node.getChild(0))) {
        ifNullish(node.getChild(1), NullishMode.NULLISH, false);
      } else if (isNonNullishLiteral(node.getChild(0))) {
        ifNonNullish(node.getChild(1));
      }
    }

    @Override
    protected void visitNotEqualOpNode(NotEqualOpNode node) {
      if (isNullishLiteral(node.getChild(1))) {
        ifNullish(node.getChild(0), NullishMode.NULLISH, true);
      }
      if (isNullishLiteral(node.getChild(0))) {
        ifNullish(node.getChild(1), NullishMode.NULLISH, true);
      }
    }

    @Override
    protected void visitTripleEqualOpNode(TripleEqualOpNode node) {
      if (isNullishLiteral(node.getChild(1))) {
        ifNullish(node.getChild(0), NullishMode.forNode(node.getChild(1)), false);
      } else if (isNonNullishLiteral(node.getChild(1))) {
        ifNonNullish(node.getChild(0));
      }

      if (isNullishLiteral(node.getChild(0))) {
        ifNullish(node.getChild(1), NullishMode.forNode(node.getChild(0)), false);
      } else if (isNonNullishLiteral(node.getChild(0))) {
        ifNonNullish(node.getChild(1));
      }
    }

    @Override
    protected void visitTripleNotEqualOpNode(TripleNotEqualOpNode node) {
      if (isNullishLiteral(node.getChild(1))) {
        ifNullish(node.getChild(0), NullishMode.forNode(node.getChild(1)), true);
      } else if (isNullishLiteral(node.getChild(0))) {
        ifNullish(node.getChild(1), NullishMode.forNode(node.getChild(0)), true);
      }
    }

    @Override
    protected void visitNullSafeAccessNode(NullSafeAccessNode node) {
      // Narrow [a, a.b, a.b.c]
      for (ExprNode nullSafeBase : node.asNullSafeBaseList()) {
        positiveTypeConstraints.put(
            exprEquivalence.wrap(nullSafeBase), SoyTypes.tryRemoveNullish(nullSafeBase.getType()));
      }
      // Narrow a?.b?.c
      super.visitNullSafeAccessNode(node);
    }

    @Override
    protected void visitLessThanOpNode(LessThanOpNode node) {
      ifNonNullish(node.getChild(0));
      ifNonNullish(node.getChild(1));
    }

    @Override
    protected void visitGreaterThanOpNode(GreaterThanOpNode node) {
      ifNonNullish(node.getChild(0));
      ifNonNullish(node.getChild(1));
    }

    @Override
    protected void visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
      // TODO(b/291132644): this behavior is coupled to Number(null) -> 0.
      if (isNonFalsyLiteral(node.getChild(1))) {
        ifNonNullish(node.getChild(0));
      } else if (isNonFalsyLiteral(node.getChild(0))) {
        ifNonNullish(node.getChild(1));
      }
    }

    @Override
    protected void visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
      // TODO(b/291132644): this behavior is coupled to Number(null) -> 0.
      if (isNonFalsyLiteral(node.getChild(1))) {
        ifNonNullish(node.getChild(0));
      } else if (isNonFalsyLiteral(node.getChild(0))) {
        ifNonNullish(node.getChild(1));
      }
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
  private static void computeConstraintUnionInto(
      Map<ExprEquivalence.Wrapper, SoyType> left,
      Map<ExprEquivalence.Wrapper, SoyType> right,
      Map<ExprEquivalence.Wrapper, SoyType> into) {
    Sets.union(left.keySet(), right.keySet())
        .forEach(
            key -> {
              SoyType lhsType = left.get(key);
              SoyType rhsType = right.get(key);
              SoyType stricter;
              if (lhsType == null) {
                stricter = rhsType;
              } else if (rhsType == null) {
                stricter = lhsType;
              } else {
                stricter =
                    SoyTypes.computeStricterType(lhsType, rhsType).orElse(NeverType.getInstance());
              }
              into.put(key, stricter);
            });
  }

  /**
   * Compute a map which combines the constraints from both the left and right side of an
   * expression. The result should be a set of constraints which satisfy <strong>either</strong>
   * sides.
   *
   * @param left Constraints from the left side.
   * @param right Constraints from the right side.
   */
  private void computeConstraintIntersectionInto(
      Map<ExprEquivalence.Wrapper, SoyType> left,
      Map<ExprEquivalence.Wrapper, SoyType> right,
      Map<ExprEquivalence.Wrapper, SoyType> into) {
    // A variable must be present in both the left and right sides in order to be
    // included in the output.
    Sets.intersection(left.keySet(), right.keySet())
        .forEach(
            key -> {
              SoyType originalType = key.get().getType();
              SoyType lhsType = left.get(key);
              SoyType rhsType = right.get(key);
              SoyType lct = SoyTypes.computeLowestCommonType(typeRegistry, lhsType, rhsType);
              // Don't add |null or |undefined to a type due to an OR condition.
              if (!lct.isNullOrUndefined()) {
                if (!SoyTypes.isUndefinable(originalType)) {
                  lct = SoyTypes.tryRemoveUndefined(lct);
                }
                if (!SoyTypes.isNullable(originalType)) {
                  lct = SoyTypes.tryRemoveNull(lct);
                }
              }
              // The intersection of two constraints is a *looser* constraint.
              // Thus "((a instanceof any) OR (a instanceof bool)) == (a instanceof any)"
              into.put(key, lct);
            });
  }

  /**
   * Returns true if the function is a single-argument function that returns a value that has the
   * same nullishness as the argument value.
   */
  private static boolean isNullishNonTransform(FunctionNode node) {
    if (node.isResolved()) {
      Object soyFunction = node.getSoyFunction();
      if (soyFunction == BuiltinFunction.UNDEFINED_TO_NULL
          || soyFunction == BuiltinFunction.UNDEFINED_TO_NULL_SSR
          || soyFunction == BuiltinFunction.CHECK_NOT_NULL) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the function is a single-argument function that returns a value that has the
   * same truthiness as the argument value.
   */
  private static boolean isTruthyNonTransform(FunctionNode node) {
    if (isNullishNonTransform(node)) {
      return true;
    }
    if (node.hasStaticName() && "Boolean".equals(node.getFunctionName())) {
      return true;
    }
    return false;
  }

  /** Returns `exprType` narrowed by a positive match on instanceof `instanceOfOperand`. */
  @VisibleForTesting
  static SoyType instanceOfIntersection(SoyType exprType, SoyType instanceOfOperand) {
    if (exprType.equals(UnknownType.getInstance())) {
      // (unknown instanceof x) => x
      return instanceOfOperand;
    }
    List<SoyType> matches =
        SoyTypes.expandUnions(exprType).stream()
            .map(
                member -> {
                  // (string instanceof string) => string
                  // (superclass instanceof subclass) => subclass
                  if (instanceOfOperand.isAssignableFromStrict(member)) {
                    return member;
                  }
                  // (subclass instanceof superclass) => subclass
                  if (member.isAssignableFromStrict(instanceOfOperand)) {
                    return instanceOfOperand;
                  }
                  return null;
                })
            .filter(Objects::nonNull)
            .collect(toList());
    return matches.isEmpty() ? NeverType.getInstance() : UnionType.of(matches);
  }

  /** Returns `exprType` narrowed by a negative match on instanceof `instanceOfOperand`. */
  @VisibleForTesting
  static SoyType instanceOfRemainder(SoyType exprType, SoyType instanceOfOperand) {
    // !(unknown instanceof x) => unknown
    // !(any instanceof x) => any
    if (exprType.equals(AnyType.getInstance()) || exprType.equals(UnknownType.getInstance())) {
      return exprType;
    }
    // !(superclass instanceof subclass) => superclass
    // !(subclass instanceof superclass) => never
    // !(a|b instanceof a) => b
    List<SoyType> remaining =
        SoyTypes.expandUnions(exprType).stream()
            .filter(t -> !instanceOfOperand.isAssignableFromStrict(t))
            .collect(toList());
    return remaining.isEmpty() ? NeverType.getInstance() : UnionType.of(remaining);
  }
}
