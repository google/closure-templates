/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.jbcsrc.shared;

import static java.lang.Math.max;
import static java.lang.invoke.MethodType.methodType;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * An invoke dynamic factory for lazy closures.
 *
 * <p>Allows us to more flexibly change our strategy for how lazy closures are implemented since
 * many do not require laziness.
 *
 * <p>TODO(b/289390227): instead of an unconditional subclass, generate code to optimistically
 * evaluate and then fallback to constructing the class. Do class generation in a second bootstrap
 * step to defer this expensive work till it is needed. For now, generate a subclass on the fly,
 * this will be in the same package so we can directly access the impl method,
 */
public final class DetachableProviderFactory {

  private static final String RUNTIME_PACKAGE = "com/google/template/soy/jbcsrc/runtime";
  private static final MethodType BASE_CLASS_CTOR_TYPE = methodType(void.class);
  private static final MethodType EVALUATE_TYPE = methodType(Object.class);
  private static final MethodType DO_RENDER_TYPE =
      methodType(StackFrame.class, StackFrame.class, LoggingAdvisingAppendable.class);
  // Due to build cycles we don't have hard `.class` references we can use.
  private static final Type DETACHABLE_VALUE_PROVIDER_BASE_CLASS =
      Type.getType("L" + RUNTIME_PACKAGE + "/DetachableSoyValueProvider;");
  private static final Type DETACHABLE_VALUE_PROVIDER_PROVIDER_BASE_CLASS =
      Type.getType("L" + RUNTIME_PACKAGE + "/DetachableSoyValueProviderProvider;");
  private static final Type DETACHABLE_CONTENT_PROVIDER_BASE_CLASS =
      Type.getType("L" + RUNTIME_PACKAGE + "/DetachableContentProvider;");

  /**
   * Generates a subclass of DetachableSoyValueProvider binding implMethod to the abstract
   * `evaluate` method
   */
  public static CallSite bootstrapDetachableSoyValueProvider(
      MethodHandles.Lookup lookup, String name, MethodType type) {
    MethodHandle generatedClassCtor =
        new Metafactory(
                lookup,
                DETACHABLE_VALUE_PROVIDER_BASE_CLASS,
                "evaluate",
                EVALUATE_TYPE,
                name,
                type.changeReturnType(Object.class))
            .generateClass();
    // We need to up-cast the result to SoyValueProvider to match the type of the call site.
    return new ConstantCallSite(generatedClassCtor.asType(type));
  }

  /**
   * Generates a subclass of DetachableSoyValueProvider binding implMethod to the abstract
   * `evaluate`method
   */
  public static CallSite bootstrapDetachableSoyValueProviderProvider(
      MethodHandles.Lookup lookup, String name, MethodType type) {
    MethodHandle generatedClassCtor =
        new Metafactory(
                lookup,
                DETACHABLE_VALUE_PROVIDER_PROVIDER_BASE_CLASS,
                "evaluate",
                EVALUATE_TYPE,
                name,
                type.changeReturnType(Object.class))
            .generateClass();
    return new ConstantCallSite(generatedClassCtor.asType(type));
  }

  /**
   * Generates a subclass of DetachableContentProvider binding implMethod to the abstract
   * `doRender`method
   */
  public static CallSite bootstrapDetachableContentProvider(
      MethodHandles.Lookup lookup, String name, MethodType type) {
    MethodHandle generatedClassCtor =
        new Metafactory(
                lookup,
                DETACHABLE_CONTENT_PROVIDER_BASE_CLASS,
                "doRender",
                DO_RENDER_TYPE,
                name,
                type.changeReturnType(RenderResult.class))
            .generateClass();
    return new ConstantCallSite(generatedClassCtor.asType(type));
  }

  private static final class Metafactory {
    static final ClassValue<ConcurrentHashMap<String, Class<?>>> cache =
        new ClassValue<ConcurrentHashMap<String, Class<?>>>() {
          @Override
          protected ConcurrentHashMap<String, Class<?>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
          }
        };
    final MethodHandles.Lookup lookup;
    final Type baseClass;
    final String ownerInternalName;
    final String generatedClassInternalName;
    final String baseClassMethodName;
    final MethodType baseMethodType;
    final String implMethodName;
    final MethodType implMethodType;
    final List<Type> argTypes;
    final List<String> argNames;

    Metafactory(
        MethodHandles.Lookup lookup,
        Type baseClass,
        String methodToImplement,
        MethodType baseMethodType,
        String implMethodName,
        MethodType implMethodType) {
      this.lookup = lookup;
      this.baseClass = baseClass;
      this.ownerInternalName = Type.getInternalName(lookup.lookupClass());
      // We can use the 'same name' as the method we are adapting.  This is because methods and
      // classes are in different namespaces.  The dollar sign makes it look like an innerclass,
      // but it technically isn't since we are not generating the inner class metadata.
      this.generatedClassInternalName = ownerInternalName + "$" + implMethodName;
      this.baseClassMethodName = methodToImplement;
      this.baseMethodType = baseMethodType;
      this.implMethodName = implMethodName;
      this.implMethodType = implMethodType;
      int parameterCount = implMethodType.parameterCount();
      if (parameterCount > 0) {
        this.argTypes = new ArrayList<>(implMethodType.parameterCount());
        this.argNames = new ArrayList<>(implMethodType.parameterCount());
        for (int i = 0; i < parameterCount; i++) {
          argTypes.add(Type.getType(implMethodType.parameterType(i)));
          argNames.add("arg$" + (i + 1));
        }
      } else {
        this.argTypes = ImmutableList.of();
        this.argNames = ImmutableList.of();
      }
    }

    MethodHandle generateClass() {
      ClassWriter cw = new ClassWriter(/* flags= */ 0);
      cw.visit(
          Opcodes.V11,
          Opcodes.ACC_SUPER + Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC,
          generatedClassInternalName,
          /* signature= */ null, // we don't use a generic type signature
          /* superName= */ baseClass.getInternalName(),
          /* interfaces= */ null);
      // Define a field for every element,f_N in order
      for (int i = 0; i < this.argTypes.size(); i++) {
        FieldVisitor fv =
            cw.visitField(
                Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL,
                this.argNames.get(i),
                this.argTypes.get(i).getDescriptor(),
                /* signature= */ null,
                /* value= */ null);
        fv.visitEnd();
      }
      generateConstructor(cw);
      generateOverrideMethod(cw);
      cw.visitEnd();

      var classData = cw.toByteArray();
      // Use a cache and computeIfAbsent to ensure we only generate a single class per
      // implMethodName.
      // The JDK guarantees that only one callsite is installed from a bootstrap method but it does
      // allow multiple invocations to race.  So we use concurrent map to ensure we only generate
      // the class once per call.
      Class<?> cls =
          cache
              .get(lookup.lookupClass())
              .computeIfAbsent(
                  implMethodName,
                  ignored -> {
                    try {
                      // TODO(lukes): when jdk21 is available, make this class hidden and a nestmate
                      // of the ownerClass via `defineHiddenClass` then the lazy methods and the
                      // implementation class can be made private.  This is how jdk17+ define
                      // lambdas, so we can assume it is well supported and fast.
                      return lookup.defineClass(classData);
                    } catch (IllegalAccessException iae) {
                      // defineClass throws IAE if the class doesn't have at least package access,
                      // and
                      // ours always does, so this is impossible.
                      throw new AssertionError(iae);
                    }
                  });
      try {
        return lookup.findConstructor(cls, implMethodType.changeReturnType(void.class));
      } catch (ReflectiveOperationException nsme) {
        // findConstructor throws NSME if the class doesn't have a constructor that
        // matches the signature, and IAE if the constructor is not visible.  This is impossible
        // because we are generating the constructor ourselves.
        throw new LinkageError(nsme.getMessage(), nsme);
      }
    }

    /**
     * Create a constructor that accepts the all values and assigns them to fields. <code><pre>
     * Foo(arg1, arg2, ...) {
     *   super();
     *   this.arg$1 = arg1;
     *   this.arg$2 = arg2;
     *   ...
     * }
     * </pre></code>
     */
    void generateConstructor(ClassWriter cw) {
      MethodType ctorMethodType = implMethodType.changeReturnType(void.class);
      MethodVisitor constructor =
          cw.visitMethod(
              /* package access */ 0,
              "<init>",
              ctorMethodType.toMethodDescriptorString(),
              /* signature= */ null,
              /* exceptions= */ null);
      constructor.visitCode();
      // super()
      constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
      constructor.visitMethodInsn(
          Opcodes.INVOKESPECIAL,
          baseClass.getInternalName(),
          "<init>",
          BASE_CLASS_CTOR_TYPE.toMethodDescriptorString(),
          /* isInterface= */ false);
      // assign fields
      int argPosition = 1; // next arg starts at 1, 0 is this
      for (int i = 0; i < argTypes.size(); i++) {
        Type argType = argTypes.get(i);
        // this.arg$N = arg;
        constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
        constructor.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), argPosition);
        argPosition += argType.getSize();
        constructor.visitFieldInsn(
            Opcodes.PUTFIELD, generatedClassInternalName, argNames.get(i), argType.getDescriptor());
      }
      constructor.visitInsn(Opcodes.RETURN);
      constructor.visitMaxs(
          // our max stack is either 1, 2, or 3 depending on if any of our parameters is a
          // double/long
          /* maxStack= */ 1 + argTypes.stream().mapToInt(Type::getSize).max().orElse(0),
          // our max locals is just the size of our parameters + 1 for `this`
          /* maxLocals= */ 1 + argTypes.stream().mapToInt(Type::getSize).sum());
      constructor.visitEnd();
    }

    /**
     * creates the override method that delegates to the `implMethod` by loading all field values.
     * <code><pre>
     * {@literal @}Override protected T method() {
     *   return owner.implMethod(this.f_1, this.f_2,...);
     * }
     * </pre></code>
     */
    void generateOverrideMethod(ClassWriter cw) {
      MethodVisitor overrideMethod =
          cw.visitMethod(
              Opcodes.ACC_PUBLIC,
              baseClassMethodName,
              baseMethodType.toMethodDescriptorString(),
              /* signature= */ null,
              /* exceptions= */ null);
      // load the fields and call the method
      List<Type> methodArgTypes = new ArrayList<>(argTypes);
      for (int i = 0; i < argTypes.size(); i++) {
        var argType = this.argTypes.get(i);
        // load the arg$N field onto the stack
        overrideMethod.visitVarInsn(Opcodes.ALOAD, 0); // this
        overrideMethod.visitFieldInsn(
            Opcodes.GETFIELD, generatedClassInternalName, argNames.get(i), argType.getDescriptor());
      }
      int paramIndex = 1; // 0 is for `this`
      for (Class<?> paramClass : baseMethodType.parameterList()) {
        Type paramType = Type.getType(paramClass);
        methodArgTypes.add(paramType);
        overrideMethod.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), paramIndex);
        paramIndex += paramType.getSize();
      }
      Type returnType = Type.getType(baseMethodType.returnType());
      overrideMethod.visitMethodInsn(
          // all impl methods are static
          Opcodes.INVOKESTATIC,
          ownerInternalName,
          implMethodName,
          Type.getMethodDescriptor(returnType, methodArgTypes.toArray(new Type[0])),
          /* isInterface= */ false);
      overrideMethod.visitInsn(Opcodes.ARETURN);
      overrideMethod.visitMaxs(
          // our max stack is the size of all the method parameters for our implMethod or the size
          // of our return type if it is larger
          /* maxStack= */ max(
              returnType.getSize(), methodArgTypes.stream().mapToInt(Type::getSize).sum()),
          // our max locals is just the size of our parameters + 1 for `this`
          /* maxLocals= */ 1
              + (Type.getArgumentsAndReturnSizes(baseMethodType.toMethodDescriptorString()) >> 2));
      overrideMethod.visitEnd();
    }
  }

  private DetachableProviderFactory() {}
}
