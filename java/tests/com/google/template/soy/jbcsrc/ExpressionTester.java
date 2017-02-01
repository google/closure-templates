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

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.template.soy.jbcsrc.shared.Names;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Test-Only utility for testing Expression instances.
 *
 * <p>Since {@link Expression expressions} are fully encapsulated we can represent them as simple
 * nullary interface methods. For each expression we will compile an appropriately typed
 * implementation of an invoker interface.
 */
public final class ExpressionTester {
  private interface Invoker {
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

  /** Returns a truth subject that can be used to assert on an {@link Expression}. */
  public static ExpressionSubject assertThatExpression(Expression resp) {
    return Truth.assertAbout(FACTORY).that(resp);
  }

  static final class ExpressionSubject extends Subject<ExpressionSubject, Expression> {
    private ClassData compiledClass;
    private Invoker invoker;

    private ExpressionSubject(FailureStrategy strategy, Expression subject) {
      super(strategy, subject);
    }

    ExpressionSubject evaluatesTo(int expected) {
      compile();
      if (((IntInvoker) invoker).invoke() != expected) {
        fail("evaluatesTo", expected);
      }
      return this;
    }

    /**
     * Asserts on the literal code of the expression, use sparingly since it may lead to overly
     * coupled tests.
     */
    ExpressionSubject hasCode(String... instructions) {
      compile();
      String formatted = Joiner.on('\n').join(instructions);
      if (!formatted.equals(actual().trace().trim())) {
        fail("hasCode", formatted);
      }
      return this;
    }

    /**
     * Asserts on the literal code of the expression, use sparingly since it may lead to overly
     * coupled tests.
     */
    ExpressionSubject doesNotContainCode(String... instructions) {
      compile();
      String formatted = Joiner.on('\n').join(instructions);
      String actual = actual().trace().trim();
      if (actual.contains(formatted)) {
        failWithBadResults("doesNotContainCode", formatted, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject evaluatesTo(boolean expected) {
      compile();
      boolean actual;
      try {
        actual = ((BooleanInvoker) invoker).invoke();
      } catch (Throwable t) {
        failWithBadResults("evalutes to", expected, "fails with", t);
        return this;
      }
      if (actual != expected) {
        failWithBadResults("evaluates to", expected, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject evaluatesTo(double expected) {
      compile();
      double actual;
      try {
        actual = ((DoubleInvoker) invoker).invoke();
      } catch (Throwable t) {
        failWithBadResults("evalutes to", expected, "fails with", t);
        return this;
      }
      if (actual != expected) {
        failWithBadResults("evaluates to", expected, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject evaluatesTo(long expected) {
      compile();
      long actual;
      try {
        actual = ((LongInvoker) invoker).invoke();
      } catch (Throwable t) {
        failWithBadResults("evalutes to", expected, "fails with", t);
        return this;
      }
      if (actual != expected) {
        failWithBadResults("evaluates to", expected, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject evaluatesTo(char expected) {
      compile();
      char actual;
      try {
        actual = ((CharInvoker) invoker).invoke();
      } catch (Throwable t) {
        failWithBadResults("evalutes to", expected, "fails with", t);
        return this;
      }
      if (actual != expected) {
        failWithBadResults("evaluates to", expected, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject evaluatesTo(Object expected) {
      compile();
      Object actual;
      try {
        actual = ((ObjectInvoker) invoker).invoke();
      } catch (Throwable t) {
        failWithBadResults("evalutes to", expected, "fails with", t);
        return this;
      }
      if (!Objects.equal(actual, expected)) {
        failWithBadResults("evaluates to", expected, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject evaluatesToInstanceOf(Class<?> expected) {
      compile();
      Object actual;
      try {
        actual = ((ObjectInvoker) invoker).invoke();
      } catch (Throwable t) {
        failWithBadResults("evalutes to instance of", expected, "fails with", t);
        return this;
      }
      if (!expected.isInstance(actual)) {
        failWithBadResults("evaluates to instance of", expected, "evaluates to", actual);
      }
      return this;
    }

    ExpressionSubject throwsException(Class<? extends Throwable> clazz) {
      return throwsException(clazz, null);
    }

    ExpressionSubject throwsException(Class<? extends Throwable> clazz, String message) {
      compile();
      try {
        invoker.voidInvoke();
      } catch (Throwable t) {
        if (!clazz.isInstance(t)) {
          failWithBadResults("throws an exception of type", clazz, "fails with", t);
        }
        if (message != null && !t.getMessage().equals(message)) {
          failWithBadResults("throws an exception with message", message, "fails with", t);
        }
        return this;
      }
      fail("throws an exception");
      return this; // dead code, but the compiler can't prove it
    }

    private void compile() {
      if (invoker == null) {
        try {
          Class<? extends Invoker> invokerClass = invokerForType(actual().resultType());
          this.compiledClass = createClass(invokerClass, actual());
          this.invoker = load(invokerClass, compiledClass);
        } catch (Throwable t) {
          throw new RuntimeException("Compilation of" + actualAsString() + " failed", t);
        }
      }
    }
  }

  private static final SubjectFactory<ExpressionSubject, Expression> FACTORY =
      new SubjectFactory<ExpressionSubject, Expression>() {
        @Override
        public ExpressionSubject getSubject(FailureStrategy fs, Expression that) {
          return new ExpressionSubject(fs, that);
        }
      };

  static <T> T createInvoker(Class<T> clazz, Expression expr) {
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

  static ClassData createClass(Class<? extends Invoker> targetInterface, Expression expr) {
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
              void doGen(CodeBuilder adapter) {
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
        ExpressionTester.class.getClassLoader(),
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
    }
    throw new AssertionError("unsupported type" + type);
  }
}
