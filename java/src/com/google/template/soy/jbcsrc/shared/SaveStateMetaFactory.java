/*
 * Copyright 2021 Google Inc.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.lang.Math.max;
import static java.lang.invoke.MethodType.methodType;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.jbcsrc.api.RenderResult;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Bootstrap methods for handling our save/restore state logic.
 *
 * <p>The benefit of {@code invokedynamic} in this case is 3 fold:
 *
 * <ul>
 *   <li>Most save/restore states are never triggered, so we can leverage the laziness supplied by
 *       the bootstrap linkage to reduce the amount of statically generated code.
 *   <li>We can efficiently pack our stack frames into fields and avoid overheads related to boxing
 *       and lots of stack operations in RenderContext.
 *   <li>The actual logic for save/retore is reduced since much of the management of fields and
 *       assignments can be relegated to this class.
 * </ul>
 */
public final class SaveStateMetaFactory {

  /** A map to ensure we only attempt to define a class for each FrameKey once. */
  private static final ConcurrentMap<FrameKey, Class<? extends StackFrame>> frameCache =
      new ConcurrentHashMap<>();

  private static final Type STACK_FRAME_TYPE = Type.getType(StackFrame.class);

  private static final String GENERATED_CLASS_NAME_INTERNAL_PREFIX =
      STACK_FRAME_TYPE.getInternalName();
  private static final MethodType STACK_FRAME_LINKED_CTOR_TYPE =
      MethodType.methodType(void.class, StackFrame.class, int.class);
  private static final MethodType STACK_FRAME_ROOT_CTOR_TYPE =
      MethodType.methodType(void.class, RenderResult.class, int.class);

  @AutoValue
  abstract static class FrameKey {
    static FrameKey create(ImmutableList<Class<?>> fieldTypes) {
      return new AutoValue_SaveStateMetaFactory_FrameKey(fieldTypes);
    }

    abstract ImmutableList<Class<?>> fieldTypes();

    // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2
    private static String charFromClass(Class<?> cls) {
      if (cls == int.class) {
        return "I";
      }
      if (cls == boolean.class) {
        return "Z";
      }
      if (cls == byte.class) {
        return "B";
      }
      if (cls == short.class) {
        return "S";
      }
      if (cls == char.class) {
        return "C";
      }
      if (cls == float.class) {
        return "F";
      }
      if (cls == double.class) {
        return "D";
      }
      if (cls == long.class) {
        return "J";
      }
      if (cls == Object.class) {
        return "A";
      }
      throw new AssertionError("unexpected class: " + cls);
    }

    @Memoized
    String symbol() {
      return fieldTypes().stream().map(FrameKey::charFromClass).collect(joining());
    }
  }

  private static Class<?> simplifyType(Class<?> paramType) {
    if (paramType.isPrimitive()) {
      return paramType;
    }
    return Object.class;
  }

  private static FrameKey frameKeyFromSaveMethodType(MethodType type) {
    // We generate a small class that is a subclass of StackFrame
    ImmutableList<Class<?>> fieldTypes =
        type.parameterList().stream()
            // Skip StackFrame/RenderResult and the state number, the first two parameters
            .skip(2)
            // map to just primitive types and objects.
            // This is important because the class is defined in this classloader and so shouldn't
            // reference types from child loaders.  restoreState will take care of relevant cast
            // operations.
            .map(SaveStateMetaFactory::simplifyType)
            .collect(toImmutableList());
    return FrameKey.create(fieldTypes);
  }

  /**
   * A JVM bootstrap method for saving incremental rendering state
   *
   * <p>This generates code equivalent to {@code renderResult.withFrame(new
   * StackFrameXXX(stateNumber, ....))} where {@code StackFrameXXX} is a dynamically generated
   * {@link StackFrame} instance, {@code stateNumber} is a parameter to the bootstrap method and
   * {@code ...} is all the values to be saved.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused. Hardcoded to 'save'
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (StackFrame,int, ....)->StackFrame where the parameters after render
   *     context are all the values to be saves
   */
  public static CallSite bootstrapSaveState(
      MethodHandles.Lookup lookup, String name, MethodType type) {
    // We generate a small class that is a subclass of StackFrame
    FrameKey frameKey = frameKeyFromSaveMethodType(type);
    // Generate a StackFrame subclass based on the set of fields it will hold.
    Class<? extends StackFrame> frameClass = getStackFrameClass(frameKey);

    MethodHandle ctorHandle;
    try {
      ctorHandle =
          lookup.findConstructor(
              frameClass,
              methodType(void.class, type.parameterType(0), int.class)
                  .appendParameterTypes(frameKey.fieldTypes()));
    } catch (ReflectiveOperationException nsme) {
      throw new LinkageError(nsme.getMessage(), nsme);
    }
    // our target signature is something like (I,RenderContext, SoyValueProvider, long)void, but the
    // stackFrameConstructionHandle here is (RenderContext, Object, long)void.
    // Our callsite signature needs to match exactly, asType() will adjust.
    return new ConstantCallSite(ctorHandle.asType(type));
  }

  private static Class<? extends StackFrame> getStackFrameClass(FrameKey key) {
    // Use a concurrent hashmap to ensure every class is only defined once.
    // In theory, we could use the 'system dictionary' that is maintained by the classloader as our
    // cache.  To test if the class exists we could call `Class.forName()` and then if that fails
    // with a ClassNotFoundException we could generate the class.  The problem is, of course, race
    // conditions.  There is no guarantee that this method will only be called by a single thread
    // and if it is called by multiple threads we might define the same class twice.  The
    // specification (and manual testing) reveals that when this happens a LinkageError will be
    // thrown.  We could theoretically catch this to detect such races, but this would imply a few
    // things:
    // 1. the normal case would involve throwing and catching an exception
    // 2. the racy case would require catching an ambiguous exception (LinkageError can be thrown
    // for multiple reasons)
    //
    // So given these oddities, maintaining our own cache to solve the concurrency problems seems
    // easier, even though it will technically consume more memory.
    return frameCache.computeIfAbsent(key, SaveStateMetaFactory::generateStackFrameClass);
  }

  /**
   * Generates a class to store objects according to the frame key.
   *
   * <p>For example, for a FrameKey for Object,long,int we would generate <code><pre>
   * class StackFrameAJI extends StackFrame {
   *   public final Object f_0;
   *   public final long f_1;
   *   public final int f_2;
   *
   *  StackFrameAJI(int stateNumber, Object f_0, long f_1, int f_2) {
   *    super(stateNumber);
   *    this.f_0 = f_0;
   *    this.f_1 = f_1;
   *    this.f_2 = f_2;
   *  }
   * }
   * </pre></code>
   */
  private static Class<? extends StackFrame> generateStackFrameClass(FrameKey key) {
    if (key.fieldTypes().isEmpty()) {
      return StackFrame.class;
    }
    ClassWriter cw = new ClassWriter(/* flags= */ 0);
    String className = GENERATED_CLASS_NAME_INTERNAL_PREFIX + key.symbol();
    cw.visit(
        Opcodes.V11,
        Opcodes.ACC_SUPER + Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC,
        className,
        /* signature= */ null, // we don't use a generic type signature
        /* superName= */ STACK_FRAME_TYPE.getInternalName(),
        /* interfaces= */ null);
    int counter = 0;
    // Define a field for every element,f_N in order
    for (Class<?> fieldType : key.fieldTypes()) {
      Type asmType = Type.getType(fieldType);
      FieldVisitor fv =
          cw.visitField(
              Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
              "f_" + counter,
              asmType.getDescriptor(),
              /* signature= */ null,
              /* value= */ null);
      fv.visitEnd();
      counter++;
    }
    // Create 2 constructors, one for linking to a child frame and one for creating a root frame.
    Type generatedType = Type.getObjectType(className);
    generateConstructor(cw, key, generatedType, STACK_FRAME_LINKED_CTOR_TYPE);
    generateConstructor(cw, key, generatedType, STACK_FRAME_ROOT_CTOR_TYPE);
    cw.visitEnd();
    try {
      return MethodHandles.lookup().defineClass(cw.toByteArray()).asSubclass(StackFrame.class);
    } catch (IllegalAccessException iae) {
      throw new AssertionError(iae);
    }
  }

  private static void generateConstructor(
      ClassWriter cw, FrameKey key, Type generatedType, MethodType baseClassCtor) {
    MethodType ctorMethodType = baseClassCtor.appendParameterTypes(key.fieldTypes());
    MethodVisitor constructor =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            ctorMethodType.toMethodDescriptorString(),
            /* signature= */ null,
            /* exceptions= */ null);
    constructor.visitCode();
    // super(stateNumber)
    constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
    int argPosition = 1;
    for (Class<?> baseClassArg : baseClassCtor.parameterList()) {
      var type = Type.getType(baseClassArg);
      constructor.visitVarInsn(type.getOpcode(Opcodes.ILOAD), argPosition);
      argPosition += type.getSize();
    }
    int superStackDepth = argPosition;
    constructor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        STACK_FRAME_TYPE.getInternalName(),
        "<init>",
        baseClassCtor.toMethodDescriptorString(),
        /* isInterface= */ false);
    // assign fields
    int largestArgSize = 0;
    for (int i = baseClassCtor.parameterCount(); i < ctorMethodType.parameterCount(); i++) {
      Class<?> argClass = ctorMethodType.parameterType(i);
      Type argType = Type.getType(argClass);
      // this.f_N = arg;
      constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
      constructor.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), argPosition);
      argPosition += argType.getSize();
      largestArgSize = max(largestArgSize, argType.getSize());
      constructor.visitFieldInsn(
          Opcodes.PUTFIELD,
          generatedType.getInternalName(),
          "f_" + (i - baseClassCtor.parameterCount()),
          argType.getDescriptor());
    }
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(
        /* maxStack= */ max(
            /* for the super(child, stateNumber) call */ superStackDepth,
            /* for the largest field store operation */
            1 + largestArgSize),
        /* maxLocals= */ argPosition);
    constructor.visitEnd();
  }

  /**
   * A JVM bootstrap method for restoring a field from a save StackFrame subtype. Because our stack
   * frames are dynamically generated we cannot generate static references to them. So instead we
   * use {@code invokedynamic} to access fields inside the stack frame objects.
   *
   * <p>This returns a {@code CallSite} that is equivalent to {@code (Target) ((StackFrameXXX)
   * frame).f_N} where {@code Target} is the return type of the method we are implementing, {@code
   * StackFrameXXX} is the name of the dynamically generated class of the frame (as defined by the
   * {@code frameType} method type) and {@code N} is the {@code slot}. {@code template(...)}
   * references.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused.
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (StackFrame)->T
   * @param frameType The unique identifier within the template for this save/restore point.
   * @param slot The field within the stack frame object that we will be restoring our value from.
   */
  public static CallSite bootstrapRestoreState(
      MethodHandles.Lookup lookup, String name, MethodType type, MethodType frameType, int slot) {

    FrameKey key = frameKeyFromSaveMethodType(frameType);
    Class<? extends StackFrame> implClass = getStackFrameClass(key);
    Field slotField;
    try {
      slotField = implClass.getField("f_" + slot);
    } catch (NoSuchFieldException nsfe) {
      throw new AssertionError(nsfe);
    }
    MethodHandle fieldGetter;
    try {
      fieldGetter = lookup.unreflectGetter(slotField);
    } catch (IllegalAccessException iae) {
      throw new AssertionError(iae);
    }
    // This asType() call is necessary to downcast objects.  the type may be SoyValueProvider, but
    // the frame field is plain Object.  asType() will insert a cast operator.
    return new ConstantCallSite(fieldGetter.asType(type));
  }

  private SaveStateMetaFactory() {}
}
