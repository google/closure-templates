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

import static com.google.template.soy.jbcsrc.Expression.areAllConstant;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.SoyExpression.BoolExpression;
import com.google.template.soy.jbcsrc.SoyExpression.BoxedExpression;
import com.google.template.soy.jbcsrc.SoyExpression.FloatExpression;
import com.google.template.soy.jbcsrc.SoyExpression.IntExpression;
import com.google.template.soy.jbcsrc.SoyExpression.StringExpression;
import com.google.template.soy.jbcsrc.runtime.Runtime;
import com.google.template.soy.shared.internal.SharedRuntime;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A reference to a method that can be called at runtime.
 */
@AutoValue abstract class MethodRef {
  // Static methods
  static final MethodRef BOOLEAN_DATA_FOR_VALUE = 
      create(BooleanData.class, "forValue", boolean.class);
  static final MethodRef INTEGER_DATA_FOR_VALUE = create(IntegerData.class, "forValue", long.class);
  static final MethodRef STRING_DATA_FOR_VALUE = create(StringData.class, "forValue", String.class);
  static final MethodRef FLOAT_DATA_FOR_VALUE = create(FloatData.class, "forValue", double.class);
  static final MethodRef LIST_IMPL_FOR_PROVIDER_LIST = 
      create(ListImpl.class, "forProviderList", List.class);
  static final MethodRef DICT_IMPL_FOR_PROVIDER_MAP = 
      create(DictImpl.class, "forProviderMap", Map.class);
  static final MethodRef RUNTIME_DIVIDED_BY = 
      create(SharedRuntime.class, "dividedBy", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_TIMES = 
      create(SharedRuntime.class, "times", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_MINUS = 
      create(SharedRuntime.class, "minus", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_PLUS = 
      create(SharedRuntime.class, "plus", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_LESS_THAN =
      create(SharedRuntime.class, "lessThan", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_LESS_THAN_OR_EQUAL = 
      create(SharedRuntime.class, "lessThanOrEqual", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_EQUAL = 
      create(SharedRuntime.class, "equal", SoyValue.class, SoyValue.class);
  static final MethodRef RUNTIME_NEGATIVE = 
      create(SharedRuntime.class, "negative", SoyValue.class);
  static final MethodRef RUNTIME_STRING_EQUALS_AS_NUMBER = 
      create(Runtime.class, "stringEqualsAsNumber", String.class, double.class);
  static final MethodRef IMMUTABLE_LIST_OF = create(ImmutableList.class, "of");
  static final MethodRef IMMUTABLE_MAP_OF = create(ImmutableMap.class, "of");

  // Instance methods
  static final MethodRef ARRAY_LIST_ADD = create(ArrayList.class, "add", Object.class);
  static final MethodRef EQUALS = create(Object.class, "equals", Object.class);
  static final MethodRef TO_STRING = create(Object.class, "toString");
  static final MethodRef PRINT_STREAM_PRINTLN = create(PrintStream.class, "println");
  static final MethodRef LINKED_HASH_MAP_PUT = 
      create(LinkedHashMap.class, "put", Object.class, Object.class);
  static final MethodRef SOY_VALUE_RENDER = create(SoyValue.class, "render", Appendable.class);
  static final MethodRef SOY_VALUE_COERCE_TO_BOOLEAN = create(SoyValue.class, "coerceToBoolean");
  static final MethodRef SOY_VALUE_LONG_VALUE = create(SoyValue.class, "longValue");
  static final MethodRef SOY_VALUE_FLOAT_VALUE = create(SoyValue.class, "floatValue");
  static final MethodRef SOY_VALUE_STRING_VALUE = create(SoyValue.class, "stringValue");
  static final MethodRef SOY_VALUE_PROVIDER_RESOLVE = create(SoyValueProvider.class, "resolve");
  static final MethodRef SOY_RECORD_HAS_FIELD = create(SoyRecord.class, "hasField", String.class);
  static final MethodRef SOY_RECORD_GET_FIELD_PROVIDER = 
      create(SoyRecord.class, "getFieldProvider", String.class);
  static final MethodRef INTEGER_DATA_GET_VALUE = create(IntegerData.class, "getValue");
  static final MethodRef INTEGER_DATA_INTEGER_VALUE = create(IntegerData.class, "integerValue");
  static final MethodRef STRING_CONCAT = create(String.class, "concat", String.class);

  private static MethodRef create(Class<?> clazz, String methodName, Class<?>... params) {
    Method m;
    try {
      // Ensure that the method exists and is public.
      m = clazz.getMethod(methodName, params);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    TypeInfo ownerType = TypeInfo.create(clazz);
    boolean isStatic = Modifier.isStatic(m.getModifiers());
    ImmutableList<Type> argTypes;
    if (isStatic) {
      argTypes = ImmutableList.copyOf(Type.getArgumentTypes(m));
    } else {
      // for instance methods the first 'argument' is always an instance of the class.
      argTypes = ImmutableList.<Type>builder()
          .add(ownerType.type())
          .add(Type.getArgumentTypes(m))
          .build();
    }
    return new AutoValue_MethodRef(
        clazz.isInterface() 
            ? Opcodes.INVOKEINTERFACE 
            : isStatic 
                ? Opcodes.INVOKESTATIC 
                : Opcodes.INVOKEVIRTUAL, 
        ownerType, 
        methodName, 
        Type.getMethodDescriptor(m),
        m.getReturnType(),
        argTypes);
  }

  /**
   * The opcode to use to invoke the method.  Will be one of {@link Opcodes#INVOKEINTERFACE},
   * {@link Opcodes#INVOKESTATIC} or {@link Opcodes#INVOKEVIRTUAL}.
   */
  abstract int opcode();
  
  /** The 'internal name' of the type that owns the method. */
  abstract TypeInfo owner();
  
  abstract String methodName();
  abstract String methodDescriptor();
  abstract Class<?> returnType();
  abstract ImmutableList<Type> argTypes();
  
  private void invoke(GeneratorAdapter mv) {
    mv.visitMethodInsn(
        opcode(),
        owner().internalName(),
        methodName(),
        methodDescriptor(),
        // This is for whether the methods owner is an interface.  This is mostly to handle java8
        // default methods on interfaces.  We don't care about those currently, but ASM requires 
        // this.
        opcode() == Opcodes.INVOKEINTERFACE);
  }
  // TODO(lukes): consider different names.  'invocation'? invoke() makes it sounds like we are 
  // actually calling the method rather than generating an expression that will output code that
  // will invoke the method.

  Expression invoke(final Expression ...args) {
    Expression.checkTypes(argTypes(), args);
    final boolean isConstant = areAllConstant(Arrays.asList(args));
    if (SoyValue.class.isAssignableFrom(returnType())) {
      Class<? extends SoyValue> boxType = returnType().asSubclass(SoyValue.class);
      return new BoxedExpression(boxType) {
        @Override public void gen(GeneratorAdapter mv) {
          invoke(mv, args);
        }

        @Override boolean isConstant() {
          return isConstant;
        }
      };
    }
    if (double.class.equals(returnType())) {
      return new FloatExpression() {
        @Override public void gen(GeneratorAdapter mv) {
          invoke(mv, args);
        }

        @Override boolean isConstant() {
          return isConstant;
        }
      };
    }
    if (long.class.equals(returnType())) {
      return new IntExpression() {
        @Override public void gen(GeneratorAdapter mv) {
          invoke(mv, args);
        }

        @Override boolean isConstant() {
          return isConstant;
        }
      };
    }
    if (boolean.class.equals(returnType())) {
      return new BoolExpression() {
        @Override public void gen(GeneratorAdapter mv) {
          invoke(mv, args);
        }

        @Override boolean isConstant() {
          return isConstant;
        }
      };
    }
    if (String.class.equals(returnType())) {
      return new StringExpression() {
        @Override public void gen(GeneratorAdapter mv) {
          invoke(mv, args);
        }

        @Override boolean isConstant() {
          return isConstant;
        }
      };
    }
    // default
    return new Expression() {
      final Type type = Type.getType(returnType());

      @Override void gen(GeneratorAdapter mv) {
        invoke(mv, args);
      }

      @Override Type resultType() {
        return type;
      }

      @Override boolean isConstant() {
        return isConstant;
      }
    };
  }

  SoyExpression invokeAsBoxedSoyExpression(final SoyExpression ...args) {
    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].box();
    }
    return (SoyExpression) invoke(args);
  }

  private void invoke(GeneratorAdapter mv, Expression... args) {
    for (Expression arg : args) {
      arg.gen(mv);
    }
    invoke(mv);
  }
}
