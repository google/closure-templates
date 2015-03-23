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

import com.google.template.soy.jbcsrc.SoyExpression.BoolExpression;
import com.google.template.soy.jbcsrc.SoyExpression.FloatExpression;
import com.google.template.soy.jbcsrc.SoyExpression.IntExpression;
import com.google.template.soy.jbcsrc.SoyExpression.StringExpression;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * A set of utilities for generating simple expressions in bytecode
 */
final class BytecodeUtils {
  static final Method NULLARY_INIT = Method.getMethod("void <init>()");
  static final Method CLASS_INIT = Method.getMethod("void <clinit>()");

  private BytecodeUtils() {}

  /** Returns an {@link Expression }that can load the given 'int' constant. */
  static Expression constant(final int value) {
    return new Expression() {
      @Override public void gen(GeneratorAdapter mv) {
        mv.push(value);
      }

      @Override public Type resultType() {
        return Type.INT_TYPE;
      }

      @Override boolean isConstant() {
        return true;
      }
    };
  }
  
  /** Returns an {@link Expression} that can load the given 'char' constant. */
  static Expression constant(final char value) {
    return new Expression() {
      @Override public void gen(GeneratorAdapter mv) {
        mv.push(value);
      }

      @Override public Type resultType() {
        return Type.CHAR_TYPE;
      }

      @Override boolean isConstant() {
        return true;
      }
    };
  }

  /** Returns an {@link Expression} that can load the given long constant. */
  static IntExpression constant(final long value) {
    return new IntExpression() {
      @Override public void gen(GeneratorAdapter mv) {
        mv.push(value);
      }

      @Override boolean isConstant() {
        return true;
      }
    };
  }
  /** Returns an {@link Expression} that can load the given double constant. */
  static FloatExpression constant(final double value) {
    return new FloatExpression() {
      @Override public void gen(GeneratorAdapter mv) {
        mv.push(value);
      }

      @Override boolean isConstant() {
        return true;
      }
    };
  }

  /** Returns an {@link Expression} that can load the given String constant. */
  static StringExpression constant(final String value) {
    checkNotNull(value);
    return new StringExpression() {
      @Override public void gen(GeneratorAdapter mv) {
        mv.push(value);
      }

      @Override boolean isConstant() {
        return true;
      }
    };
  }

  /**
   * Returns an expression that calls an appropriate dup opcode for the given type.
   */
  static Expression dupExpr(final Type type) {
    switch (type.getSize()) {
      case 1:
        return new Expression() {
          @Override void gen(GeneratorAdapter mv) {
            mv.dup();
          }

          @Override Type resultType() {
            return type;
          }
        };
      case 2:
        return new Expression() {
          @Override void gen(GeneratorAdapter mv) {
            mv.dup2();
          }

          @Override Type resultType() {
            return type;
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
    GeneratorAdapter mg = new GeneratorAdapter(Opcodes.ACC_PUBLIC, NULLARY_INIT, null, null, cv);
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

  /**
   * Compares the two {@code double} valued expressions using the provided comparison operation.
   */
  static BoolExpression compare(final int comparisonOpcode, final Expression left, 
      final Expression right) {
    checkIntComparisonOpcode(comparisonOpcode);
    checkArgument(left.resultType().equals(right.resultType()), 
        "left and right must have matching types, found %s and %s", left.resultType(), 
        right.resultType());
    return new BoolExpression() {
      @Override public void gen(GeneratorAdapter mv) {
        left.gen(mv);
        right.gen(mv);
        Label ifTrue = mv.newLabel();
        Label end = mv.newLabel();
        mv.ifCmp(left.resultType(), comparisonOpcode, ifTrue);
        mv.push(false);
        mv.goTo(end);
        mv.mark(ifTrue);
        mv.push(true);
        mv.mark(end);
      }

      @Override boolean isConstant() {
        return left.isConstant() && right.isConstant();
      }
    };
  }

  private static void checkIntComparisonOpcode(int opcode) {
    switch (opcode) {
      case Opcodes.IFEQ:
      case Opcodes.IFNE:
      case Opcodes.IFGT:
      case Opcodes.IFGE:
      case Opcodes.IFLT:
      case Opcodes.IFLE:
        return;
    }
    throw new IllegalArgumentException("Unsupported opcode for comparison operation: " + opcode);
  }

  /**
   * Returns an expression that evaluates to the logical negation of the given boolean valued 
   * expression.
   */
  static BoolExpression logicalNot(final Expression baseExpr) {
    baseExpr.checkType(Type.BOOLEAN_TYPE);
    checkArgument(baseExpr.resultType().equals(Type.BOOLEAN_TYPE), "not a boolean expression");
    return new BoolExpression() {
      @Override void gen(GeneratorAdapter mv) {
        baseExpr.gen(mv);
        // Surprisingly, java bytecode uses a branch (instead of 'xor 1' or something) to implement
        // this. This is most likely useful for allowing true to be represented by any non-zero
        // number.
        Label ifTrue = mv.newLabel();
        Label end = mv.newLabel();
        mv.ifZCmp(Opcodes.IFNE, ifTrue);  // if not 0 goto ifTrue
        mv.push(true);
        mv.goTo(end);
        mv.mark(ifTrue);
        mv.push(false);
        mv.mark(end);
      }

      @Override boolean isConstant() {
        return baseExpr.isConstant();
      }
    };
  }
}
