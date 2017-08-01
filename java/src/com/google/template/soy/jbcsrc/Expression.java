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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.ForOverride;
import com.google.template.soy.base.SourceLocation;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * An expression has a {@link #resultType()} and can {@link #gen generate} code to evaluate the
 * expression.
 *
 * <p>Expressions should:
 *
 * <ul>
 *   <li>have no side effects
 *   <li>be idempotent (you can compose them multiple times and get sensible results)
 *   <li>produce <em>exactly 1</em> <i>value</i> onto the runtime stack
 *   <li>not <em>consume</em> stack items
 * </ul>
 *
 * <p>These rules make it easier to compose and reason about the effect of composing expressions. In
 * particular it makes it easier to maintain the stack height and type invariants of the JVM.
 *
 * <p>Due to these constraints there are some natural consequences, a few examples include:
 *
 * <ul>
 *   <li>An expression should never branch to a label outside of the same expression. (Note: {@code
 *       return} and {@code throw} are special cases that are allowed)
 * </ul>
 */
abstract class Expression extends BytecodeProducer {
  /**
   * Expression features track additional metadata for expressions.
   *
   * <p>Features should be defined such that not setting a feature on an expression is a safe
   * default. That way if they get accidentally dropped in a transformation we simply generate less
   * efficient code, not incorrect code.
   */
  enum Feature {
    /** The expression is guaranteed to not return null. */
    NON_NULLABLE,
    /**
     * The expression is 'cheap'. As a rule of thumb, if it involves allocation, it is not cheap.
     *
     * <p>Cheapness is useful when deciding if it would be reasonable to evaluate an expression more
     * than once if the alternative is generating additional fields and save/restore code.
     */
    CHEAP;
    // TODO(lukes): an idempotent feature would be useful some expressions are not safe to gen more
    // than once.
  }

  /** An immutable wrapper of an EnumSet of {@link Feature}. */
  static final class Features {
    private static final Features EMPTY = new Features(EnumSet.noneOf(Feature.class));

    static Features of() {
      return EMPTY;
    }

    static Features of(Feature first, Feature... rest) {
      EnumSet<Feature> set = EnumSet.of(first);
      Collections.addAll(set, rest);
      return new Features(set);
    }

    private static Features forType(Type expressionType, Features features) {
      switch (expressionType.getSort()) {
        case Type.OBJECT:
        case Type.ARRAY:
          return features;
        case Type.BOOLEAN:
        case Type.BYTE:
        case Type.CHAR:
        case Type.DOUBLE:
        case Type.INT:
        case Type.SHORT:
        case Type.LONG:
        case Type.FLOAT:
          // primitives are never null
          return features.plus(Feature.NON_NULLABLE);
        case Type.VOID:
        case Type.METHOD:
          throw new IllegalArgumentException("Invalid type: " + expressionType);
        default:
          throw new AssertionError("unexpected type " + expressionType);
      }
    }

    private final EnumSet<Feature> set;

    private Features(EnumSet<Feature> set) {
      this.set = checkNotNull(set);
    }

    boolean has(Feature feature) {
      return set.contains(feature);
    }

    Features plus(Feature feature) {
      if (set.contains(feature)) {
        return this;
      }
      EnumSet<Feature> newSet = copyFeatures();
      newSet.add(feature);
      return new Features(newSet);
    }

    Features minus(Feature feature) {
      if (!set.contains(feature)) {
        return this;
      }
      EnumSet<Feature> newSet = copyFeatures();
      newSet.remove(feature);
      return new Features(newSet);
    }

    private EnumSet<Feature> copyFeatures() {
      // Can't use EnumSet.copyOf() because it throws on empty collections!
      EnumSet<Feature> newSet = EnumSet.noneOf(Feature.class);
      newSet.addAll(set);
      return newSet;
    }
  }

  /** Returns true if all referenced expressions are {@linkplain #isCheap() cheap}. */
  static boolean areAllCheap(Iterable<? extends Expression> args) {
    for (Expression arg : args) {
      if (!arg.isCheap()) {
        return false;
      }
    }
    return true;
  }

  /** Returns true if all referenced expressions are {@linkplain #isCheap() cheap}. */
  static boolean areAllCheap(Expression first, Expression... rest) {
    return areAllCheap(ImmutableList.<Expression>builder().add(first).add(rest).build());
  }

  /** Checks that the given expressions are compatible with the given types. */
  static void checkTypes(ImmutableList<Type> types, Expression... exprs) {
    if (Flags.DEBUG) {
      checkTypes(types, Arrays.asList(exprs));
    }
  }

  /** Checks that the given expressions are compatible with the given types. */
  static void checkTypes(ImmutableList<Type> types, Iterable<? extends Expression> exprs) {
    if (Flags.DEBUG) {
      int size = Iterables.size(exprs);
      checkArgument(
          size == types.size(),
          "Supplied the wrong number of parameters. Expected %s, got %s",
          types.size(),
          size);
      int i = 0;
      for (Expression expr : exprs) {
        expr.checkAssignableTo(types.get(i), "Parameter %s", i);
        i++;
      }
    }
  }

  private final Features features;
  private final Type resultType;

  Expression(Type resultType) {
    this(resultType, Features.of());
  }

  Expression(Type resultType, Feature first, Feature... rest) {
    this(resultType, Features.of(first, rest));
  }

  Expression(Type resultType, Features features) {
    this(resultType, features, SourceLocation.UNKNOWN);
  }

  Expression(Type resultType, Features features, SourceLocation location) {
    super(location);
    this.resultType = checkNotNull(resultType);
    this.features = Features.forType(resultType, features);
  }

  /**
   * Generate code to evaluate the expression.
   *
   * <p>The generated code satisfies the invariant that the top of the runtime stack will contain a
   * value with this {@link #resultType()} immediately after evaluation of the code.
   */
  @Override
  abstract void doGen(CodeBuilder adapter);

  /** Returns an identical {@link Expression} with the given source location. */
  Expression withSourceLocation(SourceLocation location) {
    checkNotNull(location);
    if (location.equals(this.location)) {
      return this;
    }
    return new Expression(resultType, features, location) {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
      }
    };
  }

  /** The type of the expression. */
  final Type resultType() {
    return resultType;
  }

  /** Whether or not this expression is {@link Feature#CHEAP cheap}. */
  boolean isCheap() {
    return features.has(Feature.CHEAP);
  }

  /** Whether or not this expression is {@link Feature#NON_NULLABLE non nullable}. */
  boolean isNonNullable() {
    return features.has(Feature.NON_NULLABLE);
  }

  /**
   * Returns all the feature bits. Typically, users will want to invoke one of the convenience
   * accessors {@link #isCheap()} or {@link #isNonNullable()}.
   */
  Features features() {
    return features;
  }

  /** Check that this expression is assignable to {@code expected}. */
  final void checkAssignableTo(Type expected) {
    checkAssignableTo(expected, "");
  }

  /** Check that this expression is assignable to {@code expected}. */
  final void checkAssignableTo(Type expected, String fmt, Object... args) {
    if (Flags.DEBUG && !BytecodeUtils.isPossiblyAssignableFrom(expected, resultType())) {
      String message =
          String.format(
              "Type mismatch. %s not assignable to %s.",
              resultType().getClassName(), expected.getClassName());
      if (!fmt.isEmpty()) {
        message = String.format(fmt, args) + ". " + message;
      }

      throw new IllegalArgumentException(message);
    }
  }

  /**
   * Convert this expression to a statement, by executing it and throwing away the result.
   *
   * <p>This is useful for invoking non-void methods when we don't care about the result.
   */
  Statement toStatement() {
    return new Statement() {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        switch (resultType().getSize()) {
          case 0:
            throw new AssertionError("void expressions are not allowed");
          case 1:
            adapter.pop();
            break;
          case 2:
            adapter.pop2();
            break;
          default:
            throw new AssertionError();
        }
      }
    };
  }

  /** Returns an equivalent expression where {@link #isCheap()} returns {@code true}. */
  Expression asCheap() {
    if (isCheap()) {
      return this;
    }
    return new Expression(resultType, features.plus(Feature.CHEAP)) {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
      }
    };
  }

  /** Returns an equivalent expression where {@link #isNonNullable()} returns {@code true}. */
  Expression asNonNullable() {
    if (isNonNullable()) {
      return this;
    }
    return new Expression(resultType, features.plus(Feature.NON_NULLABLE)) {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
      }
    };
  }

  Expression asNullable() {
    if (!isNonNullable()) {
      return this;
    }
    return new Expression(resultType, features.minus(Feature.NON_NULLABLE)) {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
      }
    };
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  Expression checkedCast(final Type target) {
    checkArgument(
        target.getSort() == Type.OBJECT,
        "cast targets must be reference types. (%s)",
        target.getClassName());
    checkArgument(
        resultType().getSort() == Type.OBJECT,
        "you may only cast from reference types. (%s)",
        resultType().getClassName());
    if (BytecodeUtils.isDefinitelyAssignableFrom(target, resultType())) {
      return this;
    }
    return new Expression(target, features()) {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        adapter.checkCast(resultType());
      }
    };
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  Expression checkedCast(Class<?> target) {
    return checkedCast(Type.getType(target));
  }

  /**
   * A simple helper that calls through to {@link MethodRef#invoke(Expression...)}, but allows a
   * more natural fluent call style.
   */
  Expression invoke(MethodRef method, Expression... args) {
    return method.invoke(ImmutableList.<Expression>builder().add(this).add(args).build());
  }

  /**
   * A simple helper that calls through to {@link MethodRef#invokeVoid(Expression...)}, but allows a
   * more natural fluent call style.
   */
  Statement invokeVoid(MethodRef method, Expression... args) {
    return method.invokeVoid(ImmutableList.<Expression>builder().add(this).add(args).build());
  }

  /**
   * Returns a new expression identical to this one but with the given label applied at the start of
   * the expression.
   */
  Expression labelStart(final Label label) {
    return new Expression(resultType(), features) {
      @Override
      void doGen(CodeBuilder adapter) {
        adapter.mark(label);
        Expression.this.gen(adapter);
      }
    };
  }

  /**
   * Returns a new expression identical to this one but with the given label applied at the end of
   * the expression.
   */
  Expression labelEnd(final Label label) {
    return new Expression(resultType(), features) {
      @Override
      void doGen(CodeBuilder adapter) {
        Expression.this.gen(adapter);
        adapter.mark(label);
      }
    };
  }

  /** Subclasses can override this to supply extra properties for the toString method. */
  @ForOverride
  protected void extraToStringProperties(MoreObjects.ToStringHelper helper) {}

  @Override
  public String toString() {
    String name = getClass().getSimpleName();
    if (name.isEmpty()) {
      // provide a default for anonymous subclasses
      name = "Expression";
    }
    MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(name).omitNullValues();
    helper.add("type", resultType());
    extraToStringProperties(helper);
    helper.add("cheap", features.has(Feature.CHEAP) ? "true" : null);
    helper.add(
        "non-null",
        features.has(Feature.NON_NULLABLE) && !BytecodeUtils.isPrimitive(resultType)
            ? "true"
            : null);
    return helper + ":\n" + trace();
  }
}
