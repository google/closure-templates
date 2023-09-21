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
package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_PROVIDER_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.SOY_VALUE_TYPE;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.constant;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.isDefinitelyAssignableFrom;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;

/**
 * A Branch represents an if-statement on a condition as well as basic transformations on it.
 *
 * <p>This is useful for modeling boolean values and the conditions they control. Compared to the
 * `Expression`/`Statement` API this is different because it represents 'half' of a statement or an
 * expression. This half can be filled out with the {@link #asBoolean} method to get an
 * `Expression`. The major benefit of this model is simply that `asBoolean` can be stripped away by
 * a subequent call to {@link #ifTrue}.
 */
public final class Branch {
  /** Adapts a branch to a boolean expression. */
  private static class BranchToBoolean extends Expression {
    private final Branch branch;

    BranchToBoolean(Features features, Branch branch, SourceLocation location) {
      super(Type.BOOLEAN_TYPE, features, location);
      this.branch = branch;
      checkState(!(branch.brancher instanceof BooleanBrancher) || branch.isNegated);
    }

    @Override
    protected void doGen(CodeBuilder adapter) {
      if (branch.brancher == NEVER) {
        if (branch.isNegated) {
          adapter.pushBoolean(true);
        } else {
          adapter.pushBoolean(false);
        }
        return;
      }
      if (branch.brancher instanceof ComposedBrancher) {
        var evaluated =
            ((ComposedBrancher) branch.brancher)
                .maybeDirectlyEvaluateAsBoolean(adapter, branch.isNegated);
        if (evaluated) {
          return;
        }
      }
      Label ifTrue = new Label();
      Label end = new Label();
      branch.branchTo(adapter, ifTrue);
      adapter.pushBoolean(false);
      adapter.goTo(end);
      adapter.mark(ifTrue);
      adapter.pushBoolean(true);
      adapter.mark(end);
    }

    @Override
    public BranchToBoolean withSourceLocation(SourceLocation location) {
      checkNotNull(location);
      if (location.equals(this.location)) {
        return this;
      }
      return new BranchToBoolean(features(), branch, location);
    }

    @Override
    public BranchToBoolean asCheap() {
      if (isCheap()) {
        return this;
      }
      return new BranchToBoolean(features().plus(Feature.CHEAP), branch, location);
    }

    @Override
    public BranchToBoolean asNonJavaNullable() {
      if (isNonJavaNullable()) {
        return this;
      }
      return new BranchToBoolean(features().plus(Feature.NON_JAVA_NULLABLE), branch, location);
    }

    @Override
    public BranchToBoolean asJavaNullable() {
      if (!isNonJavaNullable()) {
        return this;
      }
      return new BranchToBoolean(features().minus(Feature.NON_JAVA_NULLABLE), branch, location);
    }

    @Override
    public BranchToBoolean labelStart(Label label) {
      return new BranchToBoolean(features(), branch.labelStart(label), location);
    }

    @Override
    public BranchToBoolean labelEnd(Label label) {
      throw new UnsupportedOperationException();
    }
  }

  /** A Brancer based on a boolean expression. */
  private static final class BooleanBrancher implements Brancher {
    final Expression booleanExpression;

    BooleanBrancher(Expression booleanExpression) {
      this.booleanExpression = booleanExpression;
    }

    @Override
    public void gen(CodeBuilder adapter, Label ifTrue, boolean negated) {
      booleanExpression.gen(adapter);
      adapter.ifZCmp(negated ? Opcodes.IFEQ : Opcodes.IFNE, ifTrue);
    }
  }

  private interface Brancher {
    void gen(CodeBuilder adapter, Label ifTrue, boolean negated);
  }

  private final Expression.Features features;
  private final boolean isNegated;
  private final Brancher brancher;
  private final Supplier<String> debugPrinter;
  private final Optional<Label> startLabel;

  private Branch(Expression.Features features, Brancher brancher, Supplier<String> debugPrinter) {
    this(false, features, brancher, debugPrinter, Optional.empty());
  }

  private Branch(
      boolean isNegated,
      Expression.Features features,
      Brancher brancher,
      Supplier<String> debugPrinter,
      Optional<Label> startLlabel) {
    this.isNegated = isNegated;
    this.features = features;
    this.brancher = brancher;
    this.debugPrinter = debugPrinter;
    this.startLabel = startLlabel;
  }

  /**
   * Generates code for the branch.
   *
   * @param adapter where to generate the code
   * @param ifTrue the label to jump to when the branch isn't taken.
   */
  public void branchTo(CodeBuilder adapter, Label ifTrue) {
    if (startLabel.isPresent()) {
      adapter.mark(startLabel.get());
    }
    brancher.gen(adapter, ifTrue, isNegated);
  }

  /** Labels the beginning of the branch */
  public Branch labelStart(Label label) {
    checkState(startLabel.isEmpty());
    return new Branch(isNegated, features, brancher, debugPrinter, Optional.of(label));
  }

  /** Negates the condition. */
  public Branch negate() {
    return new Branch(!isNegated, features, brancher, debugPrinter, startLabel);
  }

  /** Adapts this branch to a boolean expression. */
  public Expression asBoolean() {
    if (brancher instanceof BooleanBrancher && !isNegated) {
      return ((BooleanBrancher) brancher).booleanExpression;
    }
    return new BranchToBoolean(features, this, SourceLocation.UNKNOWN);
  }

  /**
   * Composes several branches into a branch that is only taken if all the branches would be taken.
   */
  public static Branch and(Branch... options) {
    return makeComposedBranch(ImmutableList.copyOf(options), CompositionMode.AND);
  }

  public static Branch or(Branch... options) {
    return makeComposedBranch(ImmutableList.copyOf(options), CompositionMode.OR);
  }

  public static Branch or(List<Branch> options) {
    return makeComposedBranch(options, CompositionMode.OR);
  }

  private static Branch makeComposedBranch(List<Branch> branches, CompositionMode mode) {
    if (branches.isEmpty()) {
      throw new IllegalArgumentException();
    }
    if (branches.size() == 1) {
      return branches.get(0);
    }
    Features features = branches.get(0).features;
    for (int i = 1; i < branches.size(); i++) {
      features = features.intersect(branches.get(i).features);
    }
    return new Branch(
        features,
        new ComposedBrancher(ImmutableList.copyOf(branches), mode),
        () -> mode + "(" + branches + ")");
  }

  /**
   * Uses this branch to choose one of two options.
   *
   * <p>The options must be type compatible with `resultType`.
   */
  public Expression ternary(Type resultType, Expression ifTrue, Expression ifFalse) {
    checkArgument(
        BytecodeUtils.isPossiblyAssignableFrom(resultType, ifTrue.resultType()),
        "expected %s to be assignable to %s",
        ifTrue.resultType(),
        resultType);
    checkArgument(
        BytecodeUtils.isPossiblyAssignableFrom(resultType, ifFalse.resultType()),
        "expected %s to be assignable to %s",
        ifFalse.resultType(),
        resultType);
    if (isNegated) {
      return negate().ternary(resultType, ifFalse, ifTrue);
    }
    Features features = Features.of();
    if (features.has(Feature.CHEAP) && ifTrue.isCheap() && ifFalse.isCheap()) {
      features = features.plus(Feature.CHEAP);
    }
    if (ifTrue.isNonJavaNullable() && ifFalse.isNonJavaNullable()) {
      features = features.plus(Feature.NON_JAVA_NULLABLE);
    }
    if (ifTrue.isNonSoyNullish() && ifFalse.isNonSoyNullish()) {
      features = features.plus(Feature.NON_SOY_NULLISH);
    }
    if (resultType.equals(Type.BOOLEAN_TYPE)) {
      Branch ifTrueBranch = Branch.ifTrue(ifTrue);
      Branch ifFalseBranch = Branch.ifTrue(ifFalse);
      Branch ternary = this;
      return new Branch(
              features,
              (CodeBuilder adapter, Label ifTrueLabel, boolean negate) -> {
                Label ifTernaryTrue = new Label();
                Label end = new Label();
                ternary.branchTo(adapter, ifTernaryTrue);
                (negate ? ifFalseBranch.negate() : ifFalseBranch).branchTo(adapter, ifTrueLabel);
                adapter.goTo(end);
                adapter.mark(ifTernaryTrue);
                (negate ? ifTrueBranch.negate() : ifTrueBranch).branchTo(adapter, ifTrueLabel);
                adapter.mark(end);
              },
              () -> "ternary(" + this + ", " + ifTrueBranch + ", " + ifFalseBranch + ")")
          .asBoolean();
    }

    return new Expression(resultType, features) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label ifTrueLabel = new Label();
        Label end = new Label();
        Branch.this.branchTo(adapter, ifTrueLabel);
        ifFalse.gen(adapter);
        adapter.goTo(end);
        adapter.mark(ifTrueLabel);
        ifTrue.gen(adapter);
        adapter.mark(end);
      }
    };
  }

  /** Returns a branch that is taken if the expression evaluates to true. */
  public static Branch ifTrue(Expression expression) {
    if (expression instanceof SoyExpression) {
      // Need to unwrap so our later tests work reliably
      return ifTrue(((SoyExpression) expression).delegate);
    }
    checkState(expression.resultType().equals(Type.BOOLEAN_TYPE));
    if (expression instanceof BranchToBoolean) {
      return ((BranchToBoolean) expression).branch;
    }
    return new Branch(
        expression.features(),
        new BooleanBrancher(expression),
        () -> "ifTrue (" + expression + ")");
  }

  public static Branch ifNonSoyNullish(Expression expression) {
    if (isDefinitelyAssignableFrom(SOY_VALUE_PROVIDER_TYPE, expression.resultType())) {
      if (expression.isNonSoyNullish()) {
        return always();
      }
      if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, expression.resultType())) {
        return new Branch(
                expression.features(),
                new BooleanBrancher(MethodRef.SOY_VALUE_IS_NULLISH.invoke(expression)),
                () -> "ifSoyNullish{" + expression + "}")
            .negate();
      } else {
        return new Branch(
            expression.features(),
            new BooleanBrancher(MethodRef.IS_SOY_NON_NULLISH.invoke(expression)),
            () -> "ifSoyNullish{" + expression + "}");
      }
    } else {
      if (expression.isNonJavaNullable()) {
        return always();
      }

      return ifNonJavaNull(expression);
    }
  }

  public static Branch ifNonSoyNull(Expression expression) {
    if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, expression.resultType())) {
      if (expression.isNonSoyNullish()) {
        return always();
      }
      return new Branch(
              expression.features(),
              new BooleanBrancher(MethodRef.SOY_VALUE_IS_NULL.invoke(expression)),
              () -> "ifSoyNull{" + expression + "}")
          .negate();
    } else {
      if (expression.isNonJavaNullable()) {
        return always();
      }
      return ifNonJavaNull(expression);
    }
  }

  public static Branch ifNonSoyUndefined(Expression expression) {
    if (isDefinitelyAssignableFrom(SOY_VALUE_TYPE, expression.resultType())) {
      if (expression.isNonSoyNullish()) {
        return always();
      }
      return new Branch(
              expression.features(),
              new BooleanBrancher(MethodRef.SOY_VALUE_IS_UNDEFINED.invoke(expression)),
              () -> "ifSoyUndefined{" + expression + "}")
          .negate();
    } else {
      return always();
    }
  }

  public static Branch ifNonJavaNull(Expression expression) {
    checkState(!BytecodeUtils.isPrimitive(expression.resultType()));
    return new Branch(
        expression.features(),
        (CodeBuilder adapter, Label ifTrue, boolean negate) -> {
          expression.gen(adapter);
          if (negate) {
            adapter.ifNull(ifTrue);
          } else {
            adapter.ifNonNull(ifTrue);
          }
        },
        () -> "ifNonNull{" + expression + "}");
  }

  private static final Brancher NEVER =
      (CodeBuilder adapter, Label ifTrue, boolean negate) -> {
        if (negate) {
          adapter.goTo(ifTrue);
        }
      };

  private enum CompositionMode {
    AND,
    OR;
  };

  private static final class ComposedBrancher implements Brancher {
    final ImmutableList<Branch> branches;
    final CompositionMode mode;

    ComposedBrancher(ImmutableList<Branch> branches, CompositionMode mode) {
      this.branches = branches;
      this.mode = mode;
    }

    @Override
    public void gen(CodeBuilder adapter, Label ifTrue, boolean negate) {
      CompositionMode mode = this.mode;
      ImmutableList<Branch> branches = this.branches;
      if (negate) {
        // apply De'Morgans law and push the inversion down.
        mode = mode == CompositionMode.AND ? CompositionMode.OR : CompositionMode.AND;
        branches = branches.stream().map(Branch::negate).collect(toImmutableList());
      }
      if (mode == CompositionMode.AND) {
        Label ifFalse = new Label();
        for (int i = 0; i < branches.size() - 1; i++) {
          branches.get(i).negate().branchTo(adapter, ifFalse);
        }
        Iterables.getLast(branches).branchTo(adapter, ifTrue);
        adapter.mark(ifFalse);
      } else {
        for (Branch branch : branches) {
          branch.branchTo(adapter, ifTrue);
        }
      }
    }

    /** Customize the boolean coercion to avoid an extra set of tests */
    boolean maybeDirectlyEvaluateAsBoolean(CodeBuilder adapter, boolean negate) {
      CompositionMode mode;

      ImmutableList<Branch> branches;
      if (negate) {
        // apply DeMorgan's law and push the inversion down.
        mode = this.mode == CompositionMode.AND ? CompositionMode.OR : CompositionMode.AND;
        branches = this.branches.stream().map(Branch::negate).collect(toImmutableList());
      } else {
        mode = this.mode;
        branches = this.branches;
      }
      Branch finalBranch = branches.get(branches.size() - 1);
      if (!(finalBranch.brancher instanceof BooleanBrancher) || finalBranch.isNegated) {
        return false;
      }
      Expression finalBoolean = ((BooleanBrancher) finalBranch.brancher).booleanExpression;

      if (mode == CompositionMode.AND) {
        Label ifFalse = new Label();
        // if any are false jump to ifFalse
        for (int i = 0; i < branches.size() - 1; i++) {
          branches.get(i).negate().branchTo(adapter, ifFalse);
        }
        // If we get to the final branch, its boolean value is the answer.
        finalBoolean.gen(adapter);
        Label end = new Label();
        adapter.goTo(end);
        adapter.mark(ifFalse);
        adapter.pushBoolean(false);
        adapter.mark(end);
      } else {
        Label ifTrue = new Label();
        // if any are true jump to ifTrue
        for (int i = 0; i < branches.size() - 1; i++) {
          branches.get(i).branchTo(adapter, ifTrue);
        }
        Label end = new Label();
        // If we get to the final branch, its boolean value is the answer.
        finalBoolean.gen(adapter);
        adapter.goTo(end);
        adapter.mark(ifTrue);
        adapter.pushBoolean(true);
        adapter.mark(end);
      }
      return true;
    }
  }

  public static Branch never() {
    return new Branch(Features.of(Feature.CHEAP), NEVER, () -> "never");
  }

  public static Branch always() {
    return never().negate();
  }

  public static Branch ifNotZero(Expression expression) {
    checkState(expression.resultType().equals(Type.LONG_TYPE));
    return Branch.compare(Opcodes.IFNE, expression, constant(0L));
  }

  public static Branch ifEqual(Expression left, Expression right) {
    return Branch.compare(Opcodes.IFEQ, left, right);
  }

  public static Branch compare(int comparisonOpcode, Expression left, Expression right) {
    checkState(left.resultType().equals(right.resultType()));
    checkState(BytecodeUtils.isPrimitive(left.resultType()));
    checkIntComparisonOpcode(left.resultType(), comparisonOpcode);

    return new Branch(
        left.features().intersect(right.features()),
        (CodeBuilder adapter, Label ifTrue, boolean negate) -> {
          left.gen(adapter);
          right.gen(adapter);
          int cmp = negate ? negateComparisonOpcode(comparisonOpcode) : comparisonOpcode;
          if (isOrderingOpcode(cmp)
              && negate
              && (left.resultType().getSort() == Type.FLOAT
                  || left.resultType().getSort() == Type.DOUBLE)) {
            // A special cases is needed here to deal with the spectre of NaN.  We cannot simply
            // flip the comparison opcode since that will change nan semantics
            // Consider `x < 2 ? 'a' :'b'`
            // If x is NaN, then this should yield 'b'
            // if we negate the condition, logically, `!(x<2) ? 'a', 'b' we should now get 'a' when
            // x is NaN
            // but if we rewrite it to be `x>=2 ? 'a' : 'b' then the answer is 'b'.
            // So, simply put, we cannot 'distribute' the negation over the opcode and get a correct
            // answer.
            boolean isFloat = left.resultType().getSort() == Type.FLOAT;
            boolean isGreaterThanIsh = cmp == Opcodes.IFGE || cmp == Opcodes.IFGT;
            if (isFloat) {
              adapter.visitInsn(isGreaterThanIsh ? Opcodes.FCMPG : Opcodes.FCMPL);
            } else {
              adapter.visitInsn(isGreaterThanIsh ? Opcodes.DCMPG : Opcodes.DCMPL);
            }
            adapter.visitJumpInsn(cmp, ifTrue);
          } else {
            adapter.ifCmp(right.resultType(), cmp, ifTrue);
          }
        },
        () -> "compare" + Printer.OPCODES[comparisonOpcode] + "(" + left + ", " + right + ")");
  }

  private static boolean isOrderingOpcode(int opcode) {
    switch (opcode) {
      case Opcodes.IFEQ:
      case Opcodes.IFNE:
        return false;
      case Opcodes.IFGT:
      case Opcodes.IFGE:
      case Opcodes.IFLT:
      case Opcodes.IFLE:
        return true;
      default: // fall out
    }
    throw new IllegalArgumentException("Unsupported opcode: " + Printer.OPCODES[opcode]);
  }

  private static int negateComparisonOpcode(int opcode) {
    switch (opcode) {
      case Opcodes.IFEQ:
        return Opcodes.IFNE;
      case Opcodes.IFNE:
        return Opcodes.IFEQ;
      case Opcodes.IFGT:
        return Opcodes.IFLE;
      case Opcodes.IFGE:
        return Opcodes.IFLT;
      case Opcodes.IFLT:
        return Opcodes.IFGE;
      case Opcodes.IFLE:
        return Opcodes.IFGT;

      default:
        throw new IllegalArgumentException(
            "Unsupported opcode for comparison operation: " + Printer.OPCODES[opcode]);
    }
  }

  private static void checkIntComparisonOpcode(Type comparisonType, int opcode) {
    switch (opcode) {
      case Opcodes.IFEQ:
      case Opcodes.IFNE:
        return;
      case Opcodes.IFGT:
      case Opcodes.IFGE:
      case Opcodes.IFLT:
      case Opcodes.IFLE:
        if (comparisonType.getSort() == Type.ARRAY || comparisonType.getSort() == Type.OBJECT) {
          throw new IllegalArgumentException(
              "Type: " + comparisonType + " cannot be compared via " + Printer.OPCODES[opcode]);
        }
        return;
      default:
        throw new IllegalArgumentException(
            "Unsupported opcode for comparison operation: " + Printer.OPCODES[opcode]);
    }
  }

  @Override
  public String toString() {
    return "Branch{negate: " + isNegated + "," + debugPrinter.get() + "}";
  }
}
