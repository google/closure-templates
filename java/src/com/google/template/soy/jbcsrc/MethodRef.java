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

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.Expression.areAllConstant;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyList;
import com.google.template.soy.data.SoyMap;
import com.google.template.soy.data.SoyRecord;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.data.internal.DictImpl;
import com.google.template.soy.data.internal.ListImpl;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.Expression.SimpleExpression;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.CompiledTemplate;
import com.google.template.soy.jbcsrc.api.RenderContext;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.runtime.Runtime;
import com.google.template.soy.shared.internal.SharedRuntime;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import java.io.PrintStream;
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
  static final MethodRef RUNTIME_UNEXPECTED_STATE_ERROR = 
      create(Runtime.class, "unexpectedStateError", int.class);
  static final MethodRef RUNTIME_COERCE_DOUBLE_TO_BOOLEAN = 
      create(Runtime.class, "coerceToBoolean", double.class);
  static final MethodRef RUNTIME_COERCE_TO_STRING = 
      create(Runtime.class, "coerceToString", SoyValue.class);
  static final MethodRef IMMUTABLE_LIST_OF = create(ImmutableList.class, "of");
  static final MethodRef IMMUTABLE_MAP_OF = create(ImmutableMap.class, "of");
  static final MethodRef RENDER_RESULT_DONE = create(RenderResult.class, "done");
  static final MethodRef RENDER_RESULT_IS_DONE = create(RenderResult.class, "isDone");
  static final MethodRef RENDER_RESULT_LIMITED = create(RenderResult.class, "limited");
  static final MethodRef RUNTIME_CHECK_REQUIRED_PARAM = 
      create(Runtime.class, "checkRequiredParam", SoyRecord.class, String.class);
  static final MethodRef RUNTIME_LOGGER = create(Runtime.class, "logger");
  static final MethodRef LONG_TO_STRING = create(Long.class, "toString", long.class);
  static final MethodRef INTEGER_TO_STRING = create(Integer.class, "toString", int.class);
  static final MethodRef DOUBLE_TO_STRING = create(Double.class, "toString", double.class);
  static final MethodRef DOUBLE_VALUE = create(Number.class, "doubleValue");
  static final MethodRef LONG_VALUE = create(Number.class, "longValue");
  static final MethodRef BOOLEAN_VALUE = create(Boolean.class, "booleanValue");

  // Instance methods
  static final MethodRef ARRAY_LIST_ADD = create(ArrayList.class, "add", Object.class);
  static final MethodRef EQUALS = create(Object.class, "equals", Object.class);
  static final MethodRef STRING_VALUE_OF = create(String.class, "valueOf", Object.class);
  static final MethodRef PRINT_STREAM_PRINTLN = create(PrintStream.class, "println");
  static final MethodRef LINKED_HASH_MAP_PUT = 
      create(LinkedHashMap.class, "put", Object.class, Object.class);
  static final MethodRef SOY_VALUE_RENDER = create(SoyValue.class, "render", Appendable.class);
  static final MethodRef SOY_VALUE_COERCE_TO_BOOLEAN = create(SoyValue.class, "coerceToBoolean");
  static final MethodRef SOY_VALUE_LONG_VALUE = create(SoyValue.class, "longValue");
  static final MethodRef SOY_VALUE_FLOAT_VALUE = create(SoyValue.class, "floatValue");
  static final MethodRef SOY_VALUE_STRING_VALUE = create(SoyValue.class, "stringValue");
  static final MethodRef SOY_VALUE_PROVIDER_RESOLVE = 
      create(Runtime.class, "resolveSoyValueProvider", SoyValueProvider.class);
  static final MethodRef SOY_VALUE_PROVIDER_RENDER_AND_RESOLVE = 
      create(SoyValueProvider.class, "renderAndResolve", AdvisingAppendable.class, boolean.class);
  static final MethodRef SOY_VALUE_PROVIDER_STATUS = create(SoyValueProvider.class, "status");
  static final MethodRef SOY_RECORD_HAS_FIELD = create(SoyRecord.class, "hasField", String.class);
  static final MethodRef SOY_RECORD_GET_FIELD_PROVIDER = 
      create(SoyRecord.class, "getFieldProvider", String.class);
  static final MethodRef INTEGER_DATA_GET_VALUE = create(IntegerData.class, "getValue");
  static final MethodRef INTEGER_DATA_INTEGER_VALUE = create(IntegerData.class, "integerValue");
  static final MethodRef STRING_CONCAT = create(String.class, "concat", String.class);
  static final MethodRef STRING_IS_EMPTY = create(String.class, "isEmpty");
  static final MethodRef ADVISING_APPENDABLE_APPEND = 
      create(AdvisingAppendable.class, "append", CharSequence.class);
  static final MethodRef ADVISING_APPENDABLE_APPEND_CHAR = 
      create(AdvisingAppendable.class, "append", char.class);
  static final MethodRef ADVISING_APPENDABLE_SOFT_LIMITED = 
      create(AdvisingAppendable.class, "softLimitReached");
  static final MethodRef RENDER_CONTEXT_RENAME_CSS_SELECTOR = 
      create(RenderContext.class, "renameCssSelector", String.class);
  static final MethodRef RENDER_CONTEXT_RENAME_XID = 
      create(RenderContext.class, "renameXid", String.class);
  static final MethodRef RENDER_CONTEXT_GET_FUNCTION = 
      create(RenderContext.class, "getFunction", String.class);
  static final MethodRef RENDER_CONTEXT_GET_PRINT_DIRECTIVE = 
      create(RenderContext.class, "getPrintDirective", String.class);
  static final MethodRef SOY_LIST_AS_JAVA_LIST = create(SoyList.class, "asJavaList");
  static final MethodRef LIST_SIZE = create(List.class, "size");
  static final MethodRef LIST_GET = create(List.class, "get", int.class);
  static final MethodRef RUNTIME_GET_LIST_ITEM = 
      create(Runtime.class, "getSoyListItem", List.class, long.class);
  static final MethodRef RUNTIME_GET_MAP_ITEM = 
      create(Runtime.class, "getSoyMapItem", SoyMap.class, SoyValue.class);
  static final MethodRef INTS_CHECKED_CAST = create(Ints.class, "checkedCast", long.class);
  static final MethodRef RUNTIME_CALL_SOY_FUNCTION = 
      create(Runtime.class, "callSoyFunction", SoyJavaFunction.class, List.class);
  static final MethodRef RUNTIME_APPLY_PRINT_DIRECTIVE = 
      create(Runtime.class, "applyPrintDirective", 
          SoyJavaPrintDirective.class, SoyValue.class, List.class);
  static final MethodRef RUNTIME_APPLY_ESCAPERS =
      create(Runtime.class, "applyEscapers", CompiledTemplate.class, List.class, ContentKind.class);
  static final MethodRef PARAM_STORE_SET_FIELD = 
      create(ParamStore.class, "setField", String.class, SoyValueProvider.class);
  static final MethodRef OBJECT_TO_STRING = create(Object.class, "toString");

  static MethodRef create(Class<?> clazz, String methodName, Class<?>... params) {
    java.lang.reflect.Method m;
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
        org.objectweb.asm.commons.Method.getMethod(m),
        Type.getType(m.getReturnType()),
        argTypes);
  }

  static MethodRef createInstanceMethod(TypeInfo owner, Method method) {
    return new AutoValue_MethodRef(
        Opcodes.INVOKEVIRTUAL, 
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder()
            .add(owner.type())
            .add(method.getArgumentTypes())
            .build());
  }

  /**
   * The opcode to use to invoke the method.  Will be one of {@link Opcodes#INVOKEINTERFACE},
   * {@link Opcodes#INVOKESTATIC} or {@link Opcodes#INVOKEVIRTUAL}.
   */
  abstract int opcode();
  
  /** The 'internal name' of the type that owns the method. */
  abstract TypeInfo owner();
  
  abstract org.objectweb.asm.commons.Method method();
  abstract Type returnType();
  abstract ImmutableList<Type> argTypes();
  
  // TODO(lukes): consider different names.  'invocation'? invoke() makes it sounds like we are 
  // actually calling the method rather than generating an expression that will output code that
  // will invoke the method.
  Statement invokeVoid(final Expression ...args) {
    return invokeVoid(Arrays.asList(args));
  }

  Statement invokeVoid(final Iterable<? extends Expression> args) {
    checkState(Type.VOID_TYPE.equals(returnType()), "Method return type is not void.");
    Expression.checkTypes(argTypes(), args);
    return new Statement() {
      @Override void doGen(CodeBuilder adapter) {
        doInvoke(adapter, args);
      }
    };
  }
  
  Expression invoke(final Expression ...args) {
    return invoke(Arrays.asList(args));
  }

  Expression invoke(final Iterable<? extends Expression> args) {
    // void methods violate the expression contract of pushing a result onto the runtime stack.
    checkState(!Type.VOID_TYPE.equals(returnType()), 
        "Cannot produce an expression from a void method.");
    Expression.checkTypes(argTypes(), args);
    boolean isConstant = areAllConstant(args);
    // TODO(lukes): this assumes all methods are idempotent... not really true.  how should we
    // distinguish? we could annotate each methodref?
    return new SimpleExpression(returnType(), isConstant) {
      @Override void doGen(CodeBuilder mv) {
        doInvoke(mv, args);
      }
    };
  }

  /**
   * Writes an invoke instruction for this method to the given adapter.  Useful when the expression
   * is not useful for representing operations.  For example, explicit dup operations are awkward
   * in the Expression api. 
   */
  void invokeUnchecked(CodeBuilder mv) {
    mv.visitMethodInsn(
        opcode(),
        owner().internalName(),
        method().getName(),
        method().getDescriptor(),
        // This is for whether the methods owner is an interface.  This is mostly to handle java8
        // default methods on interfaces.  We don't care about those currently, but ASM requires 
        // this.
        opcode() == Opcodes.INVOKEINTERFACE);
  }

  private void doInvoke(CodeBuilder mv, Iterable<? extends Expression> args) {
    for (Expression arg : args) {
      arg.gen(mv);
    }
    invokeUnchecked(mv);
  }
}
