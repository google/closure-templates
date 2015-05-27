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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

import java.util.Arrays;

/**
 * An expression has a {@link #resultType()} and can {@link #gen generate} code to evaluate the
 * expression.
 * 
 * <p>Expressions should be side effect free and also should not <em>consume</em> stack items.
 */
abstract class Expression extends BytecodeProducer {

  /** Returns true if all referenced expressions are {@linkplain #isConstant() constant}. */
  static boolean areAllConstant(Iterable<? extends Expression> args) {
    for (Expression arg : args) {
      if (!arg.isConstant()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks that the given expressions are compatible with the given types.
   */
  static void checkTypes(ImmutableList<Type> types, Expression ...exprs) {
    checkTypes(types, Arrays.asList(exprs));
  }

  /**
   * Checks that the given expressions are compatible with the given types.
   */
  static void checkTypes(ImmutableList<Type> types, Iterable<? extends Expression> exprs) {
    int size = Iterables.size(exprs);
    checkArgument(size == types.size(), 
        "Supplied the wrong number of parameters. Expected %s, got %s",
        types.size(),
        size);
    int i = 0;
    for (Expression expr : exprs) {
      expr.checkAssignableTo(types.get(i), "Parameter %s", i);
      i++;
    }
  }

  /** 
   * Generate code to evaluate the expression.
   *   
   * <p>The generated code satisfies the invariant that the top of the runtime stack will contain a
   * value with this {@link #resultType()} immediately after evaluation of the code. 
   */
  @Override abstract void doGen(CodeBuilder adapter);
  
  /** The type of the expression. */
  abstract Type resultType();

  /** 
   * A constant expression is one that does not reference any variables. It may contain an 
   * arbitrarily large amount of logic.
   */
  boolean isConstant() {
    return false;
  }

  /**
   * Check that this expression is assignable to {@code expected}. 
   */
  final void checkAssignableTo(Type expected) {
    checkAssignableTo(expected, "");
  }

  /**
   * Check that this expression is assignable to {@code expected}. 
   */
  final void checkAssignableTo(Type expected, String fmt, Object ...args) {
    if (resultType().equals(expected)) {
      return;
    }
    if (expected.getSort() == resultType().getSort() && expected.getSort() == Type.OBJECT) {
      // for class types we really need to know type hierarchy information to test for 
      // whether actualType is assignable to expectedType.
      // This test is mostly optimistic so we just assume that they match. The verifier will tell
      // us ultimately if we screw up.
      // TODO(lukes): see if we can do something better here,  special case the SoyValue 
      // hierarchy?  We could use BytecodeUtils.classFromAsmType, but that might be overly expensive
      return;
    }
    String message = String.format(
        "Type mismatch. Expected %s, got %s.",
        expected,
        resultType());
    if (!fmt.isEmpty()) {
      message = String.format(fmt, args) + ". " + message;
    }
    throw new IllegalArgumentException(message);
  }

  /** 
   * Convert this expression to a statement, by executing it and throwing away the result.
   * 
   * <p>This is useful for invoking non-void methods when we don't care about the result.
   */
  Statement toStatement() {
    return new Statement() {
      @Override void doGen(CodeBuilder adapter) {
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
        }
      }
    };
  }
  
  Expression asConstant() {
    if (isConstant()) {
      return this;
    }
    return new ConstantExpression(this);
  }
  
  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  Expression cast(final Type target) {
    checkArgument(target.getSort() == Type.OBJECT, "cast targets must be reference types.");
    checkArgument(resultType().getSort() == Type.OBJECT, "you may only cast from reference types.");
    if (target.equals(resultType())) {
      return this;
    }
    return new CastExpression(this, target);
  }

  /**
   * Returns an expression that performs a checked cast from the current type to the target type.
   *
   * @throws IllegalArgumentException if either type is not a reference type.
   */
  Expression cast(Class<?> target) {
    return cast(Type.getType(target));
  }

  /**
   * A simple helper that calls through to {@link MethodRef#invoke(Expression...)}, but allows a
   * more natural fluent call style.
   */
  Expression invoke(MethodRef method, Expression ...args) {
    return method.invoke(ImmutableList.<Expression>builder().add(this).add(args).build());
  }

  /**
   * A simple helper that calls through to {@link MethodRef#invokeVoid(Expression...)}, but allows a
   * more natural fluent call style.
   */
  Statement invokeVoid(MethodRef method, Expression ...args) {
    return method.invokeVoid(ImmutableList.<Expression>builder().add(this).add(args).build());
  }

  /**
   * Returns a new expression identical to this one but with the given label applied at the start
   * of the expression.
   */
  Expression labelStart(final Label label) {
    return new SimpleExpression(resultType(), isConstant()) {
      @Override void doGen(CodeBuilder adapter) {
        adapter.mark(label);
        Expression.this.gen(adapter);
      }
    };
  }

  @Override public String toString() {
    return name() + "<" + resultType() + ">:\n" + trace();
  }

  /**
   * A simple name for the expression, used as part of {@link #toString()}.
   */
  private String name() {
    if (isConstant()) {
      return "ConstantExpression";
    }
    String simpleName = this.getClass().getSimpleName();
    return simpleName.isEmpty() ? "Expression" : simpleName;
  }

  private static final class CastExpression extends SimpleExpression {
    final Expression delegate;

    CastExpression(Expression delegate, Type resultType) {
      super(resultType, delegate.isConstant());
      this.delegate = delegate;
    }

    @Override void doGen(CodeBuilder adapter) {
      delegate.gen(adapter);
      adapter.checkCast(resultType());
    }
  }

  /**
   * A simple {@link Expression} for when the type and constant status are known at construction 
   * time.
   */
  abstract static class SimpleExpression extends Expression {
    private final Type resultType;
    private final boolean isConstant;

    SimpleExpression(Type resultType, boolean isConstant) {
      this.resultType = checkNotNull(resultType);
      this.isConstant = isConstant;
    }

    @Override final boolean isConstant() {
      return isConstant;
    }

    @Override final Type resultType() {
      return resultType;
    }
  }

  private static final class ConstantExpression extends Expression {
    private final Expression delegate;

    ConstantExpression(Expression expression) {
      this.delegate = expression;
    }

    @Override void doGen(CodeBuilder adapter) {
      delegate.gen(adapter);
    }

    @Override Type resultType() {
      return delegate.resultType();
    }

    @Override boolean isConstant() {
      return true;
    }
  }
}
