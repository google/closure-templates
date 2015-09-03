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

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.jbcsrc.Expression.Feature;
import com.google.template.soy.jbcsrc.Expression.Features;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplate;
import com.google.template.soy.jbcsrc.shared.RenderContext;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.Printer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A set of utilities for generating simple expressions in bytecode
 */
final class BytecodeUtils {
  static final TypeInfo OBJECT = TypeInfo.create(Object.class);
  static final Type STRING_TYPE = Type.getType(String.class);
  static final Type ARRAY_LIST_TYPE = Type.getType(ArrayList.class);
  static final Type ADVISING_APPENDABLE_TYPE = Type.getType(AdvisingAppendable.class);
  static final Type ADVISING_BUILDER_TYPE = Type.getType(AdvisingStringBuilder.class);
  static final Type RENDER_RESULT_TYPE = Type.getType(RenderResult.class);
  static final Type NULL_POINTER_EXCEPTION_TYPE = Type.getType(NullPointerException.class);
  static final Type RENDER_CONTEXT_TYPE = Type.getType(RenderContext.class);
  static final Type SOY_RECORD_TYPE = Type.getType(SoyRecord.class);
  static final Type LINKED_HASH_MAP_TYPE = Type.getType(LinkedHashMap.class);
  static final Type SOY_VALUE_TYPE = Type.getType(SoyValue.class);
  static final Type SOY_VALUE_PROVIDER_TYPE = Type.getType(SoyValueProvider.class);
  static final Type THROWABLE_TYPE = Type.getType(Throwable.class);
  static final Type SOY_LIST_TYPE = Type.getType(SoyList.class);
  static final Type CONTENT_KIND_TYPE = Type.getType(ContentKind.class);
  static final Type COMPILED_TEMPLATE_TYPE = Type.getType(CompiledTemplate.class);

  static final Method NULLARY_INIT = Method.getMethod("void <init>()");
  static final Method CLASS_INIT = Method.getMethod("void <clinit>()");

  private static final LoadingCache<Type, Optional<Class<?>>> objectTypeToClassCache = 
      CacheBuilder.newBuilder()
      .build(new CacheLoader<Type, Optional<Class<?>>>() {
        @Override public Optional<Class<?>> load(Type key) throws Exception {
          switch (key.getSort()) {
            case Type.ARRAY:
              Optional<Class<?>> elementType = 
                  objectTypeToClassCache.getUnchecked(key.getElementType());
              if (elementType.isPresent()) {
                // The easiest way to generically get an array class.
                return Optional.<Class<?>>of(Array.newInstance(elementType.get(), 0).getClass());
              }
              return Optional.absent();
            case Type.VOID:
              return Optional.<Class<?>>of(void.class);
            case Type.BOOLEAN:
              return Optional.<Class<?>>of(boolean.class);
            case Type.BYTE:
              return Optional.<Class<?>>of(byte.class);
            case Type.CHAR:
              return Optional.<Class<?>>of(char.class);
            case Type.DOUBLE:
              return Optional.<Class<?>>of(double.class);
            case Type.INT:
              return Optional.<Class<?>>of(int.class);
            case Type.SHORT:
              return Optional.<Class<?>>of(short.class);
            case Type.LONG:
              return Optional.<Class<?>>of(long.class);
            case Type.FLOAT:
              return Optional.<Class<?>>of(float.class);
            case Type.OBJECT:
              try {
                return Optional.<Class<?>>of(
                    Class.forName(key.getClassName(), false, BytecodeUtils.class.getClassLoader()));
              } catch (ClassNotFoundException e) {
                return Optional.absent();
              }
            default:
              throw new IllegalArgumentException("unsupported type: " + key);
          }
        }
      });

  private BytecodeUtils() {}

  /** 
   * Returns {@code true} if {@code left} is possibly assignable from {@code right}.
   */
  static boolean isPossiblyAssignableFrom(Type left, Type right) {
    return doIsAssignableFrom(left, right, true);
  }

  /** Returns {@code true} if {@code left} is definitely assignable from {@code right}. */
  static boolean isDefinitelyAssignableFrom(Type left, Type right) {
    return doIsAssignableFrom(left, right, false);
  }

  /**
   * Checks if {@code left} is assignable from {@code right}, however if we don't have information
   * about one of the types then this returns {@code failOpen}.
   */
  private static boolean doIsAssignableFrom(Type left, Type right, boolean failOpen) {
    if (left.equals(right)) {
      return true;
    }
    if (left.getSort() != right.getSort()) {
      return false;
    }
    if (left.getSort() != Type.OBJECT) {
      return false;  // all other sorts require exact equality (even arrays)
    }
    // for object types we really need to know type hierarchy information to test for whether 
    // right is assignable to left.
    Optional<Class<?>> leftClass = objectTypeToClassCache.getUnchecked(left);
    Optional<Class<?>> rightClass = objectTypeToClassCache.getUnchecked(right);
    if (!leftClass.isPresent() || rightClass.isPresent()) {
      // This means one of the types being compared is a generated object.  So we can't easily check
      // it.  Just delegate responsibility to the verifier.
      return failOpen;
    }
    return leftClass.get().isAssignableFrom(rightClass.get());
  }

  /**
   * Returns the runtime class represented by the given type.
   *
   * @throws IllegalArgumentException if the class cannot be found.  It is expected that this
   *     method will only be called for types that have a runtime on the compilers classpath.
   */
  static Class<?> classFromAsmType(Type type) {
    Optional<Class<?>> maybeClass = objectTypeToClassCache.getUnchecked(type);
    if (!maybeClass.isPresent()) {
      throw new IllegalArgumentException("Could not load: " + type);
    }
    return maybeClass.get();
  }

  private static final Expression FALSE =
      new Expression(Type.BOOLEAN_TYPE, Feature.CHEAP) {
        @Override
        void doGen(CodeBuilder mv) {
          mv.pushBoolean(false);
        }
      };

  private static final Expression TRUE =
      new Expression(Type.BOOLEAN_TYPE, Feature.CHEAP) {
        @Override
        void doGen(CodeBuilder mv) {
          mv.pushBoolean(true);
        }
      };

  /** Returns an {@link Expression} that can load the given 'boolean' constant. */
  static Expression constant(boolean value) {
    return value ? TRUE : FALSE;
  }

  /** Returns an {@link Expression} that can load the given 'int' constant. */
  static Expression constant(final int value) {
    return new Expression(Type.INT_TYPE, Feature.CHEAP) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }
  
  /** Returns an {@link Expression} that can load the given 'char' constant. */
  static Expression constant(final char value) {
    return new Expression(Type.CHAR_TYPE, Feature.CHEAP) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given long constant. */
  static Expression constant(final long value) {
    return new Expression(Type.LONG_TYPE, Feature.CHEAP) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushLong(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given double constant. */
  static Expression constant(final double value) {
    return new Expression(Type.DOUBLE_TYPE, Feature.CHEAP) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushDouble(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given String constant. */
  static Expression constant(final String value) {
    checkNotNull(value);
    return new Expression(STRING_TYPE, Feature.CHEAP, Feature.NON_NULLABLE) {
      @Override
      void doGen(CodeBuilder mv) {
        mv.pushString(value);
      }
    };
  }
  
  /** Returns an {@link Expression} with the given type that always returns null. */
  static Expression constantNull(Type type) {
    checkArgument(
        type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY,
        "%s is not a reference type",
        type);
    return new Expression(type, Feature.CHEAP) {
      @Override
      void doGen(CodeBuilder mv) {
        mv.visitInsn(Opcodes.ACONST_NULL);
      }
    };
  }

  /**
   * Returns an expression that does a numeric conversion cast from the given expression to the
   * given type.
   * 
   * @throws IllegalArgumentException if either the expression or the target type is not a numeric 
   *     primitive
   */
  static Expression numericConversion(final Expression expr, final Type to) {
    if (to.equals(expr.resultType())) {
      return expr;
    }
    if (!isNumericPrimitive(to) || !isNumericPrimitive(expr.resultType())) {
      throw new IllegalArgumentException("Cannot convert from " + expr.resultType() + " to " + to);
    }
    return new Expression(to, expr.features()) {
      @Override void doGen(CodeBuilder adapter) {
        expr.gen(adapter);
        adapter.cast(expr.resultType(), to);
      }
    };
  }

  private static boolean isNumericPrimitive(Type type) {
    int sort = type.getSort();
    switch (sort) {
      case Type.OBJECT:
      case Type.ARRAY:
      case Type.VOID:
      case Type.METHOD:
      case Type.BOOLEAN:
        return false;
      case Type.BYTE:
      case Type.CHAR:
      case Type.DOUBLE:
      case Type.INT:
      case Type.SHORT:
      case Type.LONG:
      case Type.FLOAT:
        return true;
      default:
        throw new AssertionError("unexpected type " + type);
    }
  }
  
  static boolean isPrimitive(Type type) {
    switch (type.getSort()) {
      case Type.OBJECT:
      case Type.ARRAY:
        return false;
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.CHAR:
      case Type.DOUBLE:
      case Type.INT:
      case Type.SHORT:
      case Type.LONG:
      case Type.FLOAT:
        return true;
      case Type.VOID:
      case Type.METHOD:
        throw new IllegalArgumentException("Invalid type: " + type);
      default:
        throw new AssertionError("unexpected type " + type);
    }
  }

  /**
   * Generates a default nullary public constructor for the given type on the {@link ClassVisitor}.
   * 
   * <p>For java classes this is normally generated by the compiler and looks like: <pre>{@code    
   *   public Foo() {
   *     super();
   *   }}</pre>
   */
  static void defineDefaultConstructor(ClassVisitor cv, TypeInfo ownerType) {
    CodeBuilder mg = new CodeBuilder(Opcodes.ACC_PUBLIC, NULLARY_INIT, null, cv);
    mg.visitCode();
    Label start = mg.mark();
    Label end = mg.newLabel();
    LocalVariable thisVar = LocalVariable.createThisVar(ownerType, start, end);
    thisVar.gen(mg);
    mg.invokeConstructor(OBJECT.type(), NULLARY_INIT);
    mg.returnValue();
    mg.mark(end);
    thisVar.tableEntry(mg);
    mg.endMethod();
  }

  // TODO(lukes): some of these branch operators are a little too branchy.  For example, the
  // expression a == b || a == c, could be implemented by
  // logicalOr(compare(Opcodes.IFEQ, a, b), compare(Opcodes.IFEQ, a, c)), but that is not optimal
  // instead we could allow compare to take an expression for what to do when the comparison fails
  // that way we could save a branch.  Maybe these operators are a failed abstraction?

  /**
   * Compares the two primitive valued expressions using the provided comparison operation.
   */
  static Expression compare(final int comparisonOpcode, final Expression left, 
      final Expression right) {
    checkArgument(left.resultType().equals(right.resultType()), 
        "left and right must have matching types, found %s and %s", left.resultType(), 
        right.resultType());
    checkIntComparisonOpcode(left.resultType(), comparisonOpcode);
    Features features = 
        Expression.areAllCheap(left, right) ? Features.of(Feature.CHEAP) : Features.of();
    return new Expression(Type.BOOLEAN_TYPE, features) {
      @Override void doGen(CodeBuilder mv) {
        left.gen(mv);
        right.gen(mv);
        Label ifTrue = mv.newLabel();
        Label end = mv.newLabel();
        mv.ifCmp(left.resultType(), comparisonOpcode, ifTrue);
        mv.pushBoolean(false);
        mv.goTo(end);
        mv.mark(ifTrue);
        mv.pushBoolean(true);
        mv.mark(end);
      }
    };
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
    }
    throw new IllegalArgumentException("Unsupported opcode for comparison operation: " + opcode);
  }

  /**
   * Returns an expression that evaluates to the logical negation of the given boolean valued 
   * expression.
   */
  static Expression logicalNot(final Expression baseExpr) {
    baseExpr.checkAssignableTo(Type.BOOLEAN_TYPE);
    checkArgument(baseExpr.resultType().equals(Type.BOOLEAN_TYPE), "not a boolean expression");
    return new Expression(Type.BOOLEAN_TYPE, baseExpr.features()) {
      @Override void doGen(CodeBuilder mv) {
        baseExpr.gen(mv);
        // Surprisingly, java bytecode uses a branch (instead of 'xor 1' or something) to implement
        // this. This is most likely useful for allowing true to be represented by any non-zero
        // number.
        Label ifTrue = mv.newLabel();
        Label end = mv.newLabel();
        mv.ifZCmp(Opcodes.IFNE, ifTrue);  // if not 0 goto ifTrue
        mv.pushBoolean(true);
        mv.goTo(end);
        mv.mark(ifTrue);
        mv.pushBoolean(false);
        mv.mark(end);
      }
    };
  }

  /**
   * Compares two {@link SoyExpression}s for equality using soy == semantics.
   */
  static Expression compareSoyEquals(final SoyExpression left, final SoyExpression right) {
    // We can special case when we know the types.
    // If either is a string, we run special logic so test for that first
    // otherwise we special case primitives and eventually fall back to our runtime.
    if (left.isKnownString()) {
      return doEqualsString(left.unboxAs(String.class), right);
    }
    if (right.isKnownString()) {
      return doEqualsString(right.unboxAs(String.class), left);
    }
    if (left.isKnownInt() && right.isKnownInt()) {
      return compare(Opcodes.IFEQ, left.unboxAs(long.class), right.unboxAs(long.class));
    }
    if (left.isKnownNumber() && right.isKnownNumber() 
        && (left.isKnownFloat() || right.isKnownFloat())) {
      return compare(Opcodes.IFEQ, left.coerceToDouble(), right.coerceToDouble());
    }
    return MethodRef.RUNTIME_EQUAL.invoke(left.box(), right.box());
  }

  /**
   * Compare a string valued expression to another expression using soy == semantics.
   * 
   * @param stringExpr An expression that is known to be an unboxed string
   * @param other An expression to compare it to.
   */
  private static Expression doEqualsString(SoyExpression stringExpr, SoyExpression other) {
    // This is compatible with SharedRuntime.compareString, which interestingly makes == break
    // transitivity.  See b/21461181
    if (other.isKnownStringOrSanitizedContent()) {
      return stringExpr.invoke(MethodRef.EQUALS, other.unboxAs(String.class));
    }
    if (other.isKnownNumber()) {
      // in this case, we actually try to convert stringExpr to a number
      return MethodRef.RUNTIME_STRING_EQUALS_AS_NUMBER.invoke(stringExpr, other.coerceToDouble());
    }
    // We don't know what other is, assume the worst and call out to our boxed implementation for
    // string comparisons.
    return MethodRef.RUNTIME_COMPARE_STRING.invoke(stringExpr, other.box());
  }

  /**
   * Returns an expression that evaluates to {@code left} if left is non null, and evaluates to
   * {@code right} otherwise. 
   */
  static Expression firstNonNull(final Expression left, final Expression right) {
    checkArgument(left.resultType().getSort() == Type.OBJECT);
    checkArgument(right.resultType().getSort() == Type.OBJECT);
    Features features = Features.of();
    if (Expression.areAllCheap(left, right)) {
      features = features.plus(Feature.CHEAP);
    }
    if (right.isNonNullable()) {
      features = features.plus(Feature.NON_NULLABLE);
    }
    return new Expression(left.resultType(), features) {
      @Override void doGen(CodeBuilder cb) {
        Label leftIsNonNull = new Label();
        left.gen(cb);                   // Stack: L
        cb.dup();                       // Stack: L, L
        cb.ifNonNull(leftIsNonNull);    // Stack: L
        // pop the extra copy of left
        cb.pop();                       // Stack:  
        right.gen(cb);                  // Stack: R
        cb.mark(leftIsNonNull);         // At this point the stack has an instance of L or R
      }
    };
  }
  
  /**
   * Returns an expression that evaluates equivalently to a java ternary expression: 
   * {@code condition ? left : right}
   */
  static Expression ternary(final Expression condition, 
      final Expression trueBranch, 
      final Expression falseBranch) {
    checkArgument(condition.resultType().equals(Type.BOOLEAN_TYPE));
    checkArgument(trueBranch.resultType().getSort() == falseBranch.resultType().getSort());
    Features features = Features.of();
    if (Expression.areAllCheap(condition, trueBranch, falseBranch)) {
      features = features.plus(Feature.CHEAP);
    }
    if (trueBranch.isNonNullable() && falseBranch.isNonNullable()) {
      features = features.plus(Feature.NON_NULLABLE);
    }
    return new Expression(trueBranch.resultType(), features) {
      @Override void doGen(CodeBuilder mv) {
        condition.gen(mv);
        Label ifFalse = new Label();
        Label end = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, ifFalse);  // if 0 goto ifFalse
        trueBranch.gen(mv);  // eval true branch
        mv.visitJumpInsn(Opcodes.GOTO, end);  // jump to the end
        mv.visitLabel(ifFalse);
        falseBranch.gen(mv);  // eval false branch
        mv.visitLabel(end);
      }
    };
  }

  /**
   * Implements the short circuiting logical or ({@code ||}) operator over the list of boolean
   * expressions.
   */
  static Expression logicalOr(Expression ...expressions) {
    return logicalOr(ImmutableList.copyOf(expressions));
  }

  /**
   * Implements the short circuiting logical or ({@code ||}) operator over the list of boolean
   * expressions.
   */
  static Expression logicalOr(List<? extends Expression> expressions) {
    return doShortCircuitingLogicalOperator(ImmutableList.copyOf(expressions), true);
  }

  /**
   * Implements the short circuiting logical and ({@code &&}) operator over the list of boolean
   * expressions.
   */
  static Expression logicalAnd(Expression ...expressions) {
    return logicalAnd(ImmutableList.copyOf(expressions));
  }

  /**
   * Implements the short circuiting logical and ({@code &&}) operator over the list of boolean
   * expressions.
   */
  static Expression logicalAnd(List<? extends Expression> expressions) {
    return doShortCircuitingLogicalOperator(ImmutableList.copyOf(expressions), false);
  }

  private static Expression doShortCircuitingLogicalOperator(
      final ImmutableList<? extends Expression> expressions, final boolean isOrOperator) {
    checkArgument(!expressions.isEmpty());
    for (Expression expr : expressions) {
      expr.checkAssignableTo(Type.BOOLEAN_TYPE);
    }
    if (expressions.size() == 1) {
      return expressions.get(0);
    }

    return new Expression(Type.BOOLEAN_TYPE, 
        Expression.areAllCheap(expressions) 
            ? Features.of(Feature.CHEAP)
            : Features.of()) {
      @Override void doGen(CodeBuilder adapter) {
        Label end = new Label();
        Label shortCircuit = new Label();
        for (int i = 0; i < expressions.size(); i++) {
          Expression expr = expressions.get(i);
          expr.gen(adapter);
          if (i == expressions.size() - 1) {
            // if we are the last one, just goto end. Whatever the result of the last expression is
            // determines the result of the whole expression (when all prior tests fail).
            adapter.goTo(end);
          } else {
            adapter.ifZCmp(isOrOperator ? Opcodes.IFNE : Opcodes.IFEQ, shortCircuit);
          }
        }
        adapter.mark(shortCircuit);
        adapter.pushBoolean(isOrOperator);  // default for || is true && is false
        adapter.mark(end);
      }
    };
  }

  /**
   * Returns an expression that returns a new {@link ArrayList} containing all the given items.
   */
  static Expression asList(Iterable<? extends Expression> items) {
    final ImmutableList<Expression> copy = ImmutableList.copyOf(items);
    if (copy.isEmpty()) {
      return MethodRef.IMMUTABLE_LIST_OF.invoke();
    }
    // Note, we cannot neccesarily use ImmutableList for anything besides the empty list because
    // we may need to put a null in it.
    final Expression construct = ConstructorRef.ARRAY_LIST_SIZE.construct(constant(copy.size()));
    return new Expression(ARRAY_LIST_TYPE, Feature.NON_NULLABLE) {
      @Override
      void doGen(CodeBuilder mv) {
        construct.gen(mv);
        for (Expression child : copy) {
          mv.dup();
          child.gen(mv);
          MethodRef.ARRAY_LIST_ADD.invokeUnchecked(mv);
          mv.pop(); // pop the bool result of arraylist.add
        }
      }
    };
  }

  /**
   * Returns an expression that returns a new {@link LinkedHashMap} containing all the given 
   * entries.
   */
  static Expression newLinkedHashMap(
      Iterable<? extends Expression> keys, 
      Iterable<? extends Expression> values) {
    final ImmutableList<Expression> keysCopy = ImmutableList.copyOf(keys);
    final ImmutableList<Expression> valuesCopy = ImmutableList.copyOf(values);
    checkArgument(keysCopy.size() == valuesCopy.size());
    for (int i = 0; i < keysCopy.size(); i++) {
      checkArgument(keysCopy.get(i).resultType().getSort() == Type.OBJECT);
      checkArgument(valuesCopy.get(i).resultType().getSort() == Type.OBJECT);
    }
    final Expression construct = ConstructorRef.LINKED_HASH_MAP_SIZE
        .construct(constant(hashMapCapacity(keysCopy.size())));
    return new Expression(LINKED_HASH_MAP_TYPE, Feature.NON_NULLABLE) {
      @Override
      void doGen(CodeBuilder mv) {
        construct.gen(mv);
        for (int i = 0; i < keysCopy.size(); i++) {
          Expression key = keysCopy.get(i);
          Expression value = valuesCopy.get(i);
          mv.dup();
          key.gen(mv);
          value.gen(mv);
          MethodRef.LINKED_HASH_MAP_PUT.invokeUnchecked(mv);
          mv.pop(); // pop the Object result of map.put
        }
      }
    };
  }
  
  private static int hashMapCapacity(int expectedSize) {
    if (expectedSize < 3) {
      return expectedSize + 1;
    }
    if (expectedSize < Ints.MAX_POWER_OF_TWO) {
      // This is the calculation used in JDK8 to resize when a putAll
      // happens; it seems to be the most conservative calculation we
      // can make.  0.75 is the default load factor.
      return (int) (expectedSize / 0.75F + 1.0F);
    }
    return Integer.MAX_VALUE; // any large value
  }
}
