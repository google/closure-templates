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

import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * An Expression involving a soy value.
 * 
 * <p>SoyExpressions can be {@link #box() boxed} into SoyValue subtypes and they also support some
 * implicit conversions.
 * 
 * <p>All soy expressions are convertable to {@code boolean} or {@link String} valued expressions,
 * but depending on the type they may also support additional unboxing conversions.
 */
abstract class SoyExpression extends Expression {
  static final SoyExpression NULL = new NullExpression();
  
  private final Class<?> clazz;
  private final Type type;

  SoyExpression(Class<?> clazz) {
    this.clazz = clazz;
    this.type = Type.getType(clazz);
  }

  @Override final Type type() {
    return type;
  }

  /** 
   * Returns the runtime type of this Soy expression. 
   * 
   * <p>For {@link #box() boxed} expressions, this is guaranteed to be a subtype of
   * {@link SoyValue}.
   */
  Class<?> clazz() {
    return clazz;
  }

  /**
   * Returns {@code true} if the current value is {@link #convert(Class)} convertable to a 
   * {@link SoyExpression} with the {@code target} type as its {@link #clazz}.
   */
  boolean convertibleTo(Class<?> target) {
    if (target.equals(long.class)) {
      return clazz().equals(long.class) || clazz().equals(IntegerData.class);
    }
    if (target.equals(double.class)) {
      return clazz().equals(double.class) || clazz().equals(FloatData.class);
    }
    if (target.equals(boolean.class) || target.equals(String.class)) {
      // everything is convertable to boolean or string
      return true;
    }
    return false;
  }
  
  /** Returns a SoyExpression that evaluates to a subtype of {@link SoyValue}. */
  abstract SoyExpression box();

  /**
   * Converts this to a {@link SoyExpression} with the given {@link #clazz()} iff it is 
   * {@link #convertibleTo(Class)} it.
   * 
   * <p>This will either be a type coercion or an unboxing operation (or return {@code this} if the
   * type already matches).
   */
  SoyExpression convert(Class<?> asType) {
    if (asType.equals(long.class)) {
      return MethodRef.SOY_VALUE_LONG_VALUE.invokeAsBoxedSoyExpression(this);
    }
    if (asType.equals(double.class)) {
      return MethodRef.SOY_VALUE_FLOAT_VALUE.invokeAsBoxedSoyExpression(this);
    }
    if (asType.equals(String.class)) {
      // string coercion is performed via the toString method apparently.
      return MethodRef.TO_STRING.invokeAsBoxedSoyExpression(this);
    }
    if (asType.equals(boolean.class)) {
      return MethodRef.SOY_VALUE_COERCE_TO_BOOLEAN.invokeAsBoxedSoyExpression(this);
    }
    throw new UnsupportedOperationException("Can't unbox " + clazz + " as " + asType);
  }

  /**
   * A {@link #box() Boxed} expression.
   */
  abstract static class BoxedExpression extends SoyExpression {
    BoxedExpression(Class<? extends SoyValue> clazz) {
      super(clazz);
    }

    @Override final Class<? extends SoyValue> clazz() {
      return super.clazz().asSubclass(SoyValue.class);
    }

    @Override final SoyExpression box() {
      return this;
    }
  }

  /**
   * Default subtype of {@link BoxedExpression} used by our core expression implementations.
   */
  private abstract static class DefaultBoxed extends BoxedExpression {
    private final SoyExpression unboxed;

    DefaultBoxed(Class<? extends SoyValue> clazz, SoyExpression unboxed) {
      super(clazz);
      this.unboxed = unboxed;
    }

    @Override boolean convertibleTo(Class<?> target) {
      return unboxed.convertibleTo(target);
    }

    @Override final SoyExpression convert(Class<?> asType) {
      return unboxed.convert(asType);
    }

    @Override final boolean isConstant() {
      return unboxed.isConstant();
    }
  }

  /**
   * An expression that results in a boolean value.
   */
  abstract static class BoolExpression extends SoyExpression {
    static final BoolExpression FALSE = new BoolExpression() {
      @Override void gen(GeneratorAdapter mv) {
        mv.push(false);
      }

      @Override SoyExpression box() {
        return new DefaultBoxed(BooleanData.class, FALSE) {
          @Override void gen(GeneratorAdapter mv) {
            FieldRef.BOOLEAN_DATA_FALSE.accessor().gen(mv);
          }
        };
      }
    };

    static final BoolExpression TRUE = new BoolExpression() {
      @Override void gen(GeneratorAdapter mv) {
        mv.push(true);
      }
      @Override SoyExpression box() {
        return new DefaultBoxed(BooleanData.class, TRUE) {
          @Override void gen(GeneratorAdapter mv) {
            FieldRef.BOOLEAN_DATA_TRUE.accessor().gen(mv);
          }
        };
      }
    };

    BoolExpression() {
      super(boolean.class);
    }

    @Override SoyExpression box() {
      return new DefaultBoxed(BooleanData.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          MethodRef.BOOLEAN_DATA_FOR_VALUE.invoke(BoolExpression.this).gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(boolean.class)) {
        return this;
      }
      return super.convert(asType);
    }
  }

  /**
   * An expression for a {@code long}
   */
  abstract static class IntExpression extends SoyExpression {
    IntExpression() {
      super(long.class);
    }

    @Override SoyExpression box() {
      return new DefaultBoxed(IntegerData.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          MethodRef.INTEGER_DATA_FOR_VALUE.invoke(IntExpression.this).gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(long.class)) {
        return this;
      }
      return super.convert(asType);
    }
  }

  /**
   * An expression for {@code null}
   */
  private static final class NullExpression extends SoyExpression {

    private NullExpression() {
      super(Object.class);
    }

    @Override void gen(GeneratorAdapter mv) {
      mv.visitInsn(Opcodes.ACONST_NULL);
    }
    
    @Override SoyExpression box() {
      return new DefaultBoxed(NullData.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          FieldRef.NULL_DATA_INSTANCE.accessor().gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(Object.class)) {
        return this;
      }
      if (asType.equals(boolean.class)) {
        return BoolExpression.FALSE;
      }
      return super.convert(asType);
    }

    @Override boolean isConstant() {
      return true;
    }
  }
  
  /**
   * An expression for a {@code double}.
   */
  abstract static class FloatExpression extends SoyExpression {
    FloatExpression() {
      super(double.class);
    }

    @Override SoyExpression box() {
      return new DefaultBoxed(FloatData.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          MethodRef.FLOAT_DATA_FOR_VALUE.invoke(FloatExpression.this).gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(double.class)) {
        return this;
      }
      return super.convert(asType);
    }
  }

  /**
   * An expression for an {@link String}.
   */
  abstract static class StringExpression extends SoyExpression {
    StringExpression() {
      super(String.class);
    }

    @Override SoyExpression box() {
      return new DefaultBoxed(StringData.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          MethodRef.STRING_DATA_FOR_VALUE.invoke(StringExpression.this).gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(String.class)) {
        return this;
      }
      return super.convert(asType);
    }
  }

  /**
   * An expression for a {@code LinkedHashMap}.
   */
  abstract static class MapExpression extends SoyExpression {
    MapExpression() {
      super(LinkedHashMap.class);
    }

    @Override SoyExpression box() {
      return new DefaultBoxed(DictImpl.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          MethodRef.DICT_IMPL_FOR_PROVIDER_MAP.invoke(MapExpression.this).gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(LinkedHashMap.class)) {
        return this;
      }
      return super.convert(asType);
    }
  }

  /**
   * An expression for an {@code ArrayList}.
   */
  abstract static class ListExpression extends SoyExpression {
    ListExpression() {
      super(ArrayList.class);
    }

    @Override SoyExpression box() {
      return new DefaultBoxed(ListImpl.class, this) {
        @Override void gen(GeneratorAdapter mv) {
          MethodRef.LIST_IMPL_FOR_PROVIDER_LIST.invoke(ListExpression.this).gen(mv);
        }
      };
    }

    @Override final SoyExpression convert(Class<?> asType) {
      if (asType.equals(ArrayList.class)) {
        return this;
      }
      return super.convert(asType);
    }
  }
}
