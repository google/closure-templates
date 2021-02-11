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
import static java.util.stream.Collectors.joining;
import static org.objectweb.asm.Opcodes.V1_8;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import sun.misc.Unsafe;

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
  /** An abstraction for MethodHandles.Lookup.defineClass, since we may not always be using jdk9+ */
  private interface ClassDefiner {
    Class<?> doDefine(MethodHandles.Lookup lookup, String name, byte[] bytes)
        throws IllegalAccessException, InvocationTargetException;

    default Class<?> define(MethodHandles.Lookup lookup, String name, byte[] bytes) {
      try {
        return doDefine(lookup, name, bytes);
      } catch (InvocationTargetException iae) {
        Throwables.throwIfUnchecked(iae.getTargetException());
        throw new AssertionError(iae);
      } catch (IllegalAccessException iae) {
        throw new AssertionError("MethodHandles.Lookup.defineClass is inaccessible?", iae);
      }
    }
  }

  /** A map to ensure we only attempt to define a class for each FrameKey once. */
  private static final ConcurrentMap<FrameKey, Class<? extends StackFrame>> frameCache =
      new ConcurrentHashMap<>();

  private static final ClassDefiner definer;

  static {
    // Polyfills!  JDK9 has exactly the method we want on the MethodHandles class but jdk9 isn't
    // fully available yet.  So we use reflection to access it if available and otherwise we
    // fallback to unsafe.  The other option would be to create a new classloader for these types,
    // but that introduces weird issues in our bootstrap methods where cross classloader calls don't
    // trivially work.
    ClassDefiner impl;
    try {
      // try to find the defineClass method which is only defined in jdk9+
      Method defineClass = MethodHandles.Lookup.class.getMethod("defineClass", byte[].class);
      impl = (lookup, name, bytes) -> (Class<?>) defineClass.invoke(lookup, bytes);
    } catch (NoSuchMethodException nsme) {
      // must be pre jdk9 :( but in this case we can use Unsafe for similar purposes
      try {
        ProtectionDomain callerProtectionDomain =
            AccessController.doPrivileged(
                new PrivilegedAction<ProtectionDomain>() {
                  @Override
                  public ProtectionDomain run() {
                    return StackFrame.class.getProtectionDomain();
                  }
                });
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafeObject = (Unsafe) unsafeField.get(null);
        Method defineClass =
            Unsafe.class.getMethod(
                "defineClass",
                String.class,
                byte[].class,
                int.class,
                int.class,
                ClassLoader.class,
                ProtectionDomain.class);

        impl =
            (lookup, name, bytes) ->
                (Class<?>)
                    defineClass.invoke(
                        unsafeObject,
                        name,
                        bytes,
                        0,
                        bytes.length,
                        StackFrame.class.getClassLoader(),
                        callerProtectionDomain);
      } catch (ReflectiveOperationException e) {
        e.addSuppressed(nsme);
        throw new AssertionError(
            "failed to find both the MethodHandles.Lookup.defineClass method and the"
                + " Unsafe.defineClass method, what jdk version is this?",
            e);
      }
    }
    definer = impl;
  }

  private static final MethodType SAVE_STATE_TYPE =
      MethodType.methodType(void.class, StackFrame.class);
  private static final Type STACK_FRAME_TYPE = Type.getType(StackFrame.class);

  private static final String GENERATED_CLASS_NAME_PREFIX =
      StackFrame.class.getPackage().getName() + ".StackFrame";
  private static final String GENERATED_CLASS_NAME_INTERNAL_PREFIX =
      GENERATED_CLASS_NAME_PREFIX.replace('.', '/');
  private static final MethodType STACK_FRAME_CTOR_TYPE =
      MethodType.methodType(void.class, int.class);

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
            // Skip RenderContext, the first parameter
            .skip(1)
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
   * <p>This generates code equivalent to {@code renderContext.pushFrame(new
   * StackFrameXXX(user, ....))} where {@code StackFrameXXX} is a dynamically generated
   * {@link StackFrame} instance, {@code stateNumber} is a parameter to the bootstrap method and
   * {@code ...} is all the values to be saved.
   *
   * @param lookup An object that allows us to resolve classes/methods in the context of the
   *     callsite. Provided automatically by invokeDynamic JVM infrastructure
   * @param name The name of the invokeDynamic method being called. This is provided by
   *     invokeDynamic JVM infrastructure and currently unused. Hardcoded to 'save'
   * @param type The type of the method being called. This will be the method signature of the
   *     callsite we produce. Provided automatically by invokeDynamic JVM infrastructure. For this
   *     method it is always (RenderContext, ....)->void where the parameters after render context
   *     are all the values to be saves
   * @param stateNumber The state number constant for this save point. These are small incrementing
   *     integers that uniquely identify the point in the bytecode that control should return to.
   */
  public static CallSite bootstrapSaveState(
      MethodHandles.Lookup lookup, String name, MethodType type, int stateNumber) {
    MethodHandle renderContextSaveState;
    try {
      renderContextSaveState =
          lookup.findVirtual(RenderContext.class, "pushFrame", SAVE_STATE_TYPE);
    } catch (ReflectiveOperationException nsme) {
      throw new AssertionError(nsme);
    }
    // We generate a small class that is a subclass of StackFrame
    FrameKey frameKey = frameKeyFromSaveMethodType(type);
    // Generate a StackFrame subclass based on the set of fields it will hold.
    Class<? extends StackFrame> frameClass = getStackFrameClass(frameKey);
    MethodHandle ctorHandle;
    try {
      ctorHandle =
          lookup.findConstructor(
              frameClass, STACK_FRAME_CTOR_TYPE.appendParameterTypes(frameKey.fieldTypes()));
    } catch (ReflectiveOperationException nsme) {
      throw new AssertionError(nsme);
    }
    MethodHandle stackFrameConstructionHandle =
        MethodHandles.insertArguments(ctorHandle, 0, stateNumber);
    // The handle is currently returning a specific subtype of StackFrame, modify the return type
    // to be StackFrame exactly which is required by the collectArguments combiner below.  Various
    // methodHandle APIs require exact type matching even though Java semantics would not.
    stackFrameConstructionHandle =
        stackFrameConstructionHandle.asType(
            stackFrameConstructionHandle.type().changeReturnType(StackFrame.class));
    MethodHandle saveStateHandle =
        MethodHandles.collectArguments(renderContextSaveState, 1, stackFrameConstructionHandle);
    // our target signature is something like (RenderContext, SoyValueProvider, long)void, but the
    // stackFrameConstructionHandle here is (RenderContext, Object, long)void.
    // Our callsite signature needs to match exactly, asType() will adjust.
    return new ConstantCallSite(saveStateHandle.asType(type));
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
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES);
    String className = GENERATED_CLASS_NAME_INTERNAL_PREFIX + key.symbol();
    cw.visit(
        V1_8,
        Opcodes.ACC_SUPER + Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC,
        className,
        /* signature=*/ null, // we don't use a generic type signature
        /* extends= */ STACK_FRAME_TYPE.getInternalName(),
        /* interfaces=*/ null);
    int counter = 0;
    // Define a field for every element,f_N in order
    List<Type> argTypes = new ArrayList<>(key.fieldTypes().size());
    for (Class<?> fieldType : key.fieldTypes()) {
      Type asmType = Type.getType(fieldType);
      argTypes.add(asmType);
      FieldVisitor fv =
          cw.visitField(
              Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL,
              "f_" + counter,
              asmType.getDescriptor(),
              /*signature=*/ null,
              /*value=*/ null);
      fv.visitEnd();
      counter++;
    }
    // Create a constructor that accepts the state number and all values
    Type generatedType = Type.getObjectType(className);
    MethodType ctorMethodType =
        MethodType.methodType(void.class, key.fieldTypes()).insertParameterTypes(0, int.class);
    MethodVisitor constructor =
        cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            "<init>",
            ctorMethodType.toMethodDescriptorString(),
            /*signature=*/ null,
            /*exceptions=*/ null);
    constructor.visitCode();
    // super(stateNumber)
    constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
    constructor.visitVarInsn(Opcodes.ILOAD, 1); // stateNumber (first argument)
    constructor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        STACK_FRAME_TYPE.getInternalName(),
        "<init>",
        STACK_FRAME_CTOR_TYPE.toMethodDescriptorString(),
        /*isInterface=*/ false);
    // assign fields
    int argPosition = 2; // next arg starts at 2, 0 is this, 1 is stateNumber
    for (int i = 0; i < argTypes.size(); i++) {
      Type argType = argTypes.get(i);
      // this.f_N = arg;
      constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
      constructor.visitVarInsn(argType.getOpcode(Opcodes.ILOAD), argPosition);
      argPosition += argType.getSize();
      constructor.visitFieldInsn(
          Opcodes.PUTFIELD, generatedType.getInternalName(), "f_" + i, argType.getDescriptor());
    }
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(-1, -1); // neccessary for automatic stack frame calculation
    constructor.visitEnd();
    cw.visitEnd();
    return definer
        // Use our own lookup to define the class.
        // this essentially means that we leak StackFrame classes into our own classloader.
        // This
        // shouldn't be a concern, due to our interning map `frameCache` we should be generating
        // only a few dozen such classes, and they are shareable across all child classloaders.
        .define(MethodHandles.lookup(), className, cw.toByteArray())
        .asSubclass(StackFrame.class);
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
