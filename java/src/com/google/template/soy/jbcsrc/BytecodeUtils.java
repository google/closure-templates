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
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.google.template.soy.jbcsrc.Expression.SimpleExpression;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.Printer;

import java.lang.reflect.Array;
import java.util.List;

/**
 * A set of utilities for generating simple expressions in bytecode
 */
final class BytecodeUtils {
  static final Method NULLARY_INIT = Method.getMethod("void <init>()");
  static final Method CLASS_INIT = Method.getMethod("void <clinit>()");
  private static final ImmutableMap<String, Class<?>> PRIMITIVES_MAP;

  static {
    ImmutableMap.Builder<String, Class<?>> builder = ImmutableMap.builder();
    for (Class<?> cl : Primitives.allPrimitiveTypes()) {
      builder.put(cl.getName(), cl);
    }
    PRIMITIVES_MAP = builder.build();
  }

  private BytecodeUtils() {}

  /**
   * Returns the runtime class represented by the given type.
   *
   * @throws IllegalArgumentException if the class cannot be found.  It is expected that this
   *     method will only be called for types that have a runtime on the compilers classpath.
   */
  static Class<?> classFromAsmType(Type type) {
    switch (type.getSort()) {
      case Type.ARRAY:
        Class<?> elementType = classFromAsmType(type.getElementType());
        // The easiest way to generically get an array class.
        Object array = Array.newInstance(elementType, 0);
        return array.getClass();
      case Type.OBJECT:
        try {
          return Class.forName(type.getClassName(), false, BytecodeUtils.class.getClassLoader());
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException("Could not load " + type, e);
        }
      case Type.METHOD:
        throw new IllegalArgumentException("Method types are not supported: " + type);
      default:
        // primitive, class.forname doesn't work on primitives
        return PRIMITIVES_MAP.get(type.getClassName());
    }
  }

  /** Returns an {@link Expression} that can load the given 'boolean' constant. */
  static Expression constant(final boolean value) {
    return new SimpleExpression(Type.BOOLEAN_TYPE, true) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushBoolean(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given 'int' constant. */
  static Expression constant(final int value) {
    return new SimpleExpression(Type.INT_TYPE, true) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }
  
  /** Returns an {@link Expression} that can load the given 'char' constant. */
  static Expression constant(final char value) {
    return new SimpleExpression(Type.CHAR_TYPE, true) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushInt(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given long constant. */
  static Expression constant(final long value) {
    return new SimpleExpression(Type.LONG_TYPE, true) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushLong(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given double constant. */
  static Expression constant(final double value) {
    return new SimpleExpression(Type.DOUBLE_TYPE, true) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushDouble(value);
      }
    };
  }

  /** Returns an {@link Expression} that can load the given String constant. */
  static Expression constant(final String value) {
    checkNotNull(value);
    return new SimpleExpression(Type.getType(String.class), true) {
      @Override void doGen(CodeBuilder mv) {
        mv.pushString(value);
      }
    };
  }
  
  /** Returns an {@link Expression} with the given type that always returns null. */
  static Expression constantNull(final Class<?> clazz) {
    Type type = Type.getType(clazz);
    checkArgument(type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY, 
        "%s is not a reference type", clazz);
    return new SimpleExpression(type, true) {
      @Override void doGen(CodeBuilder mv) {
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
    return new SimpleExpression(to, expr.isConstant()) {
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

  /**
   * Returns an expression that calls an appropriate dup opcode for the given type.
   */
  static Expression dupExpr(final Type type) {
    switch (type.getSize()) {
      case 1:
        return new SimpleExpression(type, false) {
          @Override void doGen(CodeBuilder mv) {
            mv.dup();
          }
        };
      case 2:
        return new SimpleExpression(type, false) {
          @Override void doGen(CodeBuilder mv) {
            mv.dup2();
          }
        };
      default:
        throw new AssertionError("cannot dup() " + type);
    }
  }

  /** Loads the default value for the type onto the stack. Useful for initializing fields. */
  static void loadDefault(MethodVisitor mv, Type type) {
    switch (type.getSort()) {
        case Type.BOOLEAN:
        case Type.CHAR:
        case Type.BYTE:
        case Type.SHORT:
        case Type.INT:
          mv.visitInsn(Opcodes.ICONST_0);
          break;
        case Type.FLOAT:
          mv.visitInsn(Opcodes.FCONST_0);
          break;
        case Type.LONG:
          mv.visitInsn(Opcodes.LCONST_0);
          break;
        case Type.DOUBLE:
          mv.visitInsn(Opcodes.DCONST_0);
          break;
        case Type.ARRAY:
        case Type.OBJECT:
          mv.visitInsn(Opcodes.ACONST_NULL);
          break;
        default:
          throw new AssertionError("unexpected sort for type: " + type);
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
    Label start = mg.mark();
    Label end = mg.newLabel();
    LocalVariable thisVar = LocalVariable.createThisVar(ownerType, start, end);
    thisVar.gen(mg);
    mg.invokeConstructor(Type.getType(Object.class), NULLARY_INIT);
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
   * Compares the two {@code double} valued expressions using the provided comparison operation.
   */
  static Expression compare(final int comparisonOpcode, final Expression left, 
      final Expression right) {
    checkArgument(left.resultType().equals(right.resultType()), 
        "left and right must have matching types, found %s and %s", left.resultType(), 
        right.resultType());
    checkIntComparisonOpcode(left.resultType(), comparisonOpcode);
    return new SimpleExpression(Type.BOOLEAN_TYPE, left.isConstant() && right.isConstant()) {
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
    return new SimpleExpression(Type.BOOLEAN_TYPE, baseExpr.isConstant()) {
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
      return doEqualsString(left.convert(String.class), right);
    }
    if (right.isKnownString()) {
      return doEqualsString(right.convert(String.class), left);
    }
    if (left.isKnownInt() && right.isKnownInt()) {
      return compare(Opcodes.IFEQ, left.convert(long.class), right.convert(long.class));
    }
    if (left.isKnownNumber() && right.isKnownNumber()) {
      return compare(Opcodes.IFEQ, left.convert(double.class), right.convert(double.class));
    }
    return MethodRef.RUNTIME_EQUAL.invoke(left.box(), right.box());
  }

  private static Expression doEqualsString(SoyExpression stringExpr, SoyExpression other) {
    if (other.isKnownString()) {
      SoyExpression strOther = other.convert(String.class);
      return MethodRef.EQUALS.invoke(stringExpr, strOther);
    }
    if (other.isKnownNumber()) {
      // in this case, we actually try to convert stringExpr to a number
      return MethodRef.RUNTIME_STRING_EQUALS_AS_NUMBER
          .invoke(stringExpr, other.convert(double.class));
    }
    // We don't know what other is, assume the worst and call out to our boxed implementation
    // TODO(lukes): in this case we know that the first param is a string, maybe we can specialize
    // the runtime to take advantage of this and avoid reboxing the string (and rechecking the type)
    return MethodRef.RUNTIME_EQUAL.invoke(stringExpr.box(), other.box());
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

    return new SimpleExpression(Type.BOOLEAN_TYPE, Expression.areAllConstant(expressions)) {
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
}
