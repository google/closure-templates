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

package com.google.template.soy.jbcsrc.restricted;

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.restricted.Expression.areAllCheap;
import static com.google.template.soy.jbcsrc.restricted.Expression.areAllConstant;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** A reference to a method that can be called at runtime. */
@AutoValue
public abstract class MethodRef {
  private static final Handle INVOKE_HANDLE =
      createPure(
              ConstantBootstraps.class,
              "invoke",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              MethodHandle.class,
              Object[].class)
          .asHandle();
  public static final Type[] NO_METHOD_ARGS = {};

  /**
   * Tracks whether a given method is pure.
   *
   * <p>Pureness implies that, given constant arguments, we can promote the method call to a
   * constant as well. Whether or not doing so is appropriate may depend on contextual information.
   */
  public enum MethodPureness {
    PURE,
    NON_PURE;
  }

  public static MethodRef createNonPure(Class<?> clazz, String methodName, Class<?>... params) {
    return create(getMethodUnchecked(clazz, methodName, params), MethodPureness.NON_PURE);
  }

  public static MethodRef createPure(Class<?> clazz, String methodName, Class<?>... params) {
    return create(getMethodUnchecked(clazz, methodName, params), MethodPureness.PURE);
  }

  private static java.lang.reflect.Method getMethodUnchecked(
      Class<?> clazz, String methodName, Class<?>... params) {
    try {
      // Ensure that the method exists and is public.
      return clazz.getMethod(methodName, params);
    } catch (Exception e) {
      throw new VerifyException(
          "Couldn't find the expected method among: " + Arrays.toString(clazz.getMethods()), e);
    }
  }

  public static MethodRef createNonPureConstructor(Class<?> clazz, Class<?>... params) {
    return create(getConstructorUnchecked(clazz, params), MethodPureness.NON_PURE);
  }

  public static MethodRef createPureConstructor(Class<?> clazz, Class<?>... params) {
    return create(getConstructorUnchecked(clazz, params), MethodPureness.PURE);
  }

  private static <T> Constructor<? extends T> getConstructorUnchecked(
      Class<T> clazz, Class<?>... params) {
    try {
      // Ensure that the method exists and is public.
      return clazz.getConstructor(params);
    } catch (Exception e) {
      throw new VerifyException(
          "Couldn't find the expected method among: " + Arrays.toString(clazz.getMethods()), e);
    }
  }

  public static MethodRef create(Executable method, MethodPureness pureness) {
    Class<?> clazz = method.getDeclaringClass();
    TypeInfo ownerType = TypeInfo.create(clazz);
    boolean isStatic = Modifier.isStatic(method.getModifiers());
    boolean isConstructor = method instanceof Constructor;
    var asmMethod =
        isConstructor
            ? Method.getMethod((Constructor) method)
            : Method.getMethod((java.lang.reflect.Method) method);
    ImmutableList<Type> argTypes;
    // Constructors implicitly have a 'this' parameter, but it isn't in their method signature
    // they are also kind-of static and kind-of not static.
    if (isStatic || isConstructor) {
      argTypes = ImmutableList.copyOf(asmMethod.getArgumentTypes());
    } else {
      // for instance methods the first 'argument' is always an instance of the class.
      argTypes =
          ImmutableList.<Type>builder()
              .add(ownerType.type())
              .add(asmMethod.getArgumentTypes())
              .build();
    }
    return new AutoValue_MethodRef(
        method instanceof Constructor
            ? Opcodes.INVOKESPECIAL
            : clazz.isInterface()
                ? Opcodes.INVOKEINTERFACE
                : isStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
        ownerType,
        method instanceof java.lang.reflect.Method
            ? Method.getMethod((java.lang.reflect.Method) method)
            : Method.getMethod((Constructor) method),
        isConstructor ? ownerType.type() : asmMethod.getReturnType(),
        argTypes,
        featuresForMethod(method),
        pureness);
  }

  public static MethodRef createInterfaceMethod(
      TypeInfo owner, Method method, MethodPureness pureness) {
    Preconditions.checkArgument(owner.isInterface());
    return new AutoValue_MethodRef(
        Opcodes.INVOKEINTERFACE,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of(),
        pureness);
  }

  public static MethodRef createInstanceMethod(
      TypeInfo owner, Method method, MethodPureness pureness) {
    Preconditions.checkArgument(!owner.isInterface());
    return new AutoValue_MethodRef(
        Opcodes.INVOKEVIRTUAL,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(owner.type()).add(method.getArgumentTypes()).build(),
        Features.of(),
        pureness);
  }

  public static MethodRef createStaticMethod(
      TypeInfo owner, Method method, MethodPureness pureness) {
    return new AutoValue_MethodRef(
        Opcodes.INVOKESTATIC,
        owner,
        method,
        method.getReturnType(),
        ImmutableList.<Type>builder().add(method.getArgumentTypes()).build(),
        Features.of(),
        pureness);
  }

  public static MethodRef createConstructorMethod(
      TypeInfo owner, MethodPureness pureness, Iterable<Type> argTypes) {
    checkState(!owner.isInterface(), "Constructors are not supported on interfaces.");
    var argTypesList = ImmutableList.copyOf(argTypes);
    var method = new Method("<init>", Type.VOID_TYPE, argTypesList.toArray(new Type[0]));
    return new AutoValue_MethodRef(
        Opcodes.INVOKESPECIAL,
        owner,
        method,
        owner.type(),
        argTypesList,
        Features.of(Feature.NON_JAVA_NULLABLE),
        pureness);
  }

  private static Features featuresForMethod(Executable method) {
    boolean nonnull = method instanceof Constructor || method.isAnnotationPresent(Nonnull.class);
    return nonnull ? Features.of(Feature.NON_JAVA_NULLABLE) : Features.of();
  }

  /**
   * The opcode to use to invoke the method. Will be one of {@link Opcodes#INVOKEINTERFACE}, {@link
   * Opcodes#INVOKESTATIC} or {@link Opcodes#INVOKEVIRTUAL}.
   */
  abstract int opcode();

  /** The 'internal name' of the type that owns the method. */
  public abstract TypeInfo owner();

  public abstract Method method();

  public abstract Type returnType();

  public abstract ImmutableList<Type> argTypes();

  public abstract Features features();

  public abstract MethodPureness pureness();

  public Handle asHandle() {
    int tag;
    switch (opcode()) {
      case Opcodes.INVOKESTATIC:
        tag = Opcodes.H_INVOKESTATIC;
        break;
      case Opcodes.INVOKEINTERFACE:
        tag = Opcodes.H_INVOKEINTERFACE;
        break;
      case Opcodes.INVOKEVIRTUAL:
        tag = Opcodes.H_INVOKEVIRTUAL;
        break;
      case Opcodes.INVOKESPECIAL:
        tag = Opcodes.H_INVOKESPECIAL;
        break;
      default:
        throw new AssertionError("unsupported opcode: " + opcode());
    }
    return new Handle(
        tag,
        owner().internalName(),
        method().getName(),
        method().getDescriptor(),
        owner().isInterface());
  }

  boolean isInterfaceMethod() {
    return owner().isInterface() && opcode() == Opcodes.INVOKEINTERFACE;
  }

  // TODO(lukes): consider different names.  'invocation'? invoke() makes it sounds like we are
  // actually calling the method rather than generating an expression that will output code that
  // will invoke the method.
  public Statement invokeVoid(Expression... args) {
    return invokeVoid(Arrays.asList(args));
  }

  public Statement invokeVoid(Iterable<? extends Expression> args) {
    checkState(Type.VOID_TYPE.equals(returnType()), "Method return type is not void.");
    Expression.checkTypes(argTypes(), args);
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        doInvoke(adapter, args);
      }
    };
  }

  public Expression invoke(Expression... args) {
    return invoke(Arrays.asList(args));
  }

  public Expression invoke(Iterable<? extends Expression> args) {
    // void methods violate the expression contract of pushing a result onto the runtime stack.
    checkState(
        !Type.VOID_TYPE.equals(returnType()), "Cannot produce an expression from a void method.");
    Expression.checkTypes(argTypes(), args);
    Features features = features();
    if (!areAllCheap(args)) {
      features = features.minus(Feature.CHEAP);
    }
    if (pureness() == MethodPureness.PURE && areAllConstant(args)) {
      var params = new ArrayList<>();
      params.add(asHandle());
      SourceLocation loc = SourceLocation.UNKNOWN;
      for (var arg : args) {
        params.add(arg.constantBytecodeValue());
        SourceLocation argLoc = arg.location();
        if (argLoc.isKnown()) {
          loc = loc.isKnown() ? loc.createSuperRangeWith(argLoc) : argLoc;
        }
      }
      return new Expression(
          returnType(),
          features,
          loc,
          Optional.of(
              Expression.ConstantValue.dynamic(
                  new ConstantDynamic(
                      method().getName(),
                      returnType().getDescriptor(),
                      INVOKE_HANDLE,
                      params.toArray(new Object[0])),
                  returnType(),
                  /* isTrivialConstant= */ false))) {
        @Override
        protected void doGen(CodeBuilder mv) {
          doInvoke(mv, args);
        }
      };
    }

    return new Expression(returnType(), features) {
      @Override
      protected void doGen(CodeBuilder mv) {
        doInvoke(mv, args);
      }
    };
  }

  public MethodRef asCheap() {
    return withFeature(Feature.CHEAP);
  }

  public MethodRef asNonJavaNullable() {
    return withFeature(Feature.NON_JAVA_NULLABLE);
  }

  private MethodRef withFeature(Feature feature) {
    if (features().has(feature)) {
      return this;
    }
    return new AutoValue_MethodRef(
        opcode(),
        owner(),
        method(),
        returnType(),
        argTypes(),
        features().plus(feature),
        pureness());
  }

  /**
   * Writes an invoke instruction for this method to the given adapter. Useful when the expression
   * is not useful for representing operations. For example, explicit dup operations are awkward in
   * the Expression api.
   */
  public void invokeUnchecked(CodeBuilder cb) {
    cb.visitMethodInsn(
        opcode(),
        owner().internalName(),
        method().getName(),
        method().getDescriptor(),
        // This is for whether the methods owner is an interface.  This is mostly to handle java8
        // default methods on interfaces.  We don't care about those currently, but ASM requires
        // this.
        owner().isInterface());
  }

  private void doInvoke(CodeBuilder mv, Iterable<? extends Expression> args) {
    if (method().getName().equals("<init>")) {
      mv.newInstance(returnType());
      // push a second reference onto the stack so there is still a reference to the new object
      // after invoking the constructor (constructors are void methods)
      mv.dup();
    }
    for (Expression arg : args) {
      arg.gen(mv);
    }
    invokeUnchecked(mv);
  }
}
