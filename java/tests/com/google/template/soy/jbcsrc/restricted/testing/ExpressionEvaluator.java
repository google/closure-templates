/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.jbcsrc.restricted.testing;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.internal.ClassData;
import com.google.template.soy.jbcsrc.internal.MemoryClassLoader;
import com.google.template.soy.jbcsrc.internal.SoyClassWriter;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.jbcsrc.shared.Names;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.CheckClassAdapter;

/** Test-only utility for evaluating an arbitrary expression. */
public final class ExpressionEvaluator {
  interface Invoker {
    void voidInvoke();
  }
  // These need to be public so that our memory classloader can access them across protection
  // domains
  public interface IntInvoker extends Invoker {
    int invoke();
  }

  public interface CharInvoker extends Invoker {
    char invoke();
  }

  public interface BooleanInvoker extends Invoker {
    boolean invoke();
  }

  public interface FloatInvoker extends Invoker {
    float invoke();
  }

  public interface LongInvoker extends Invoker {
    long invoke();
  }

  public interface DoubleInvoker extends Invoker {
    double invoke();
  }

  public interface ObjectInvoker extends Invoker {
    Object invoke();
  }

  public static Object evaluate(Expression expr) throws ReflectiveOperationException {
    ExpressionEvaluator evaluator = new ExpressionEvaluator();
    evaluator.compile(expr);
    return evaluator.invoker.getClass().getMethod("invoke").invoke(evaluator.invoker);
  }

  private ClassData compiledClass;
  Invoker invoker;

  void compile(Expression expr) {
    if (invoker == null) {
      try {
        Class<? extends Invoker> invokerClass = invokerForType(expr.resultType());
        compiledClass = createClass(invokerClass, expr);
        invoker = load(invokerClass, compiledClass);
      } catch (Throwable t) {
        throw new RuntimeException("Compilation of" + expr + " failed", t);
      }
    }
  }

  public static <T> T createInvoker(Class<T> clazz, Expression expr) {
    Class<? extends Invoker> expected = invokerForType(expr.resultType());
    checkArgument(
        clazz.equals(expected),
        "%s isn't an appropriate invoker type for %s, expected %s",
        clazz,
        expr.resultType(),
        expected);
    ClassData data = createClass(clazz.asSubclass(Invoker.class), expr);
    return load(clazz, data);
  }

  private static <T> T load(Class<T> clazz, ClassData data) {
    MemoryClassLoader loader = new MemoryClassLoader(ImmutableList.of(data));
    Class<?> generatedClass;
    try {
      generatedClass = loader.loadClass(data.type().className());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
    try {
      return generatedClass.asSubclass(clazz).getConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public static ClassData createClass(Class<? extends Invoker> targetInterface, Expression expr) {
    java.lang.reflect.Method invokeMethod;
    try {
      invokeMethod = targetInterface.getMethod("invoke");
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
    Class<?> returnType = invokeMethod.getReturnType();
    if (!Type.getType(returnType).equals(expr.resultType())) {
      if (!returnType.equals(Object.class) || expr.resultType().getSort() != Type.OBJECT) {
        throw new IllegalArgumentException(
            targetInterface + " is not appropriate for this expression");
      }
    }
    TypeInfo generatedType =
        TypeInfo.create(Names.CLASS_PREFIX + targetInterface.getSimpleName() + "Impl");
    SoyClassWriter cw =
        SoyClassWriter.builder(generatedType)
            .setAccess(Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_PUBLIC)
            .implementing(TypeInfo.create(targetInterface))
            .build();
    BytecodeUtils.defineDefaultConstructor(cw, generatedType);
    Method invoke = Method.getMethod(invokeMethod);
    Statement.returnExpression(expr).writeMethod(Opcodes.ACC_PUBLIC, invoke, cw);

    Method voidInvoke;
    try {
      voidInvoke = Method.getMethod(Invoker.class.getMethod("voidInvoke"));
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e); // this method definitely exists
    }
    Statement.concat(
            LocalVariable.createThisVar(generatedType, new Label(), new Label())
                .invoke(MethodRef.create(invokeMethod))
                .toStatement(),
            new Statement() {
              @Override
              protected void doGen(CodeBuilder adapter) {
                adapter.visitInsn(Opcodes.RETURN);
              }
            })
        .writeMethod(Opcodes.ACC_PUBLIC, voidInvoke, cw);
    ClassData data = cw.toClassData();
    checkClassData(data);
    return data;
  }

  /**
   * Utility to run the {@link CheckClassAdapter} on the class and print it to a string. for
   * debugging.
   */
  private static void checkClassData(ClassData clazz) {
    StringWriter sw = new StringWriter();
    CheckClassAdapter.verify(
        new ClassReader(clazz.data()),
        ExpressionEvaluator.class.getClassLoader(),
        false,
        new PrintWriter(sw));
    String result = sw.toString();
    if (!result.isEmpty()) {
      throw new IllegalStateException(result);
    }
  }

  private static Class<? extends Invoker> invokerForType(Type type) {
    switch (type.getSort()) {
      case Type.INT:
        return IntInvoker.class;
      case Type.CHAR:
        return CharInvoker.class;
      case Type.BOOLEAN:
        return BooleanInvoker.class;
      case Type.FLOAT:
        return FloatInvoker.class;
      case Type.LONG:
        return LongInvoker.class;
      case Type.DOUBLE:
        return DoubleInvoker.class;
      case Type.ARRAY:
      case Type.OBJECT:
        return ObjectInvoker.class;
      default:
        throw new AssertionError("unsupported type" + type);
    }
  }
}
