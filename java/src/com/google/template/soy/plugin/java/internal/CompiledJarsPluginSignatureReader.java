/*
 * Copyright 2019 Google Inc.
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

package com.google.template.soy.plugin.java.internal;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import com.google.template.soy.plugin.java.MethodChecker;
import com.google.template.soy.plugin.java.ReadMethodData;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Reads signatures for plugins from jar files, optionally falling back to reflection (on the
 * classpath of the compiler) if the jar files can't be read.
 */
public class CompiledJarsPluginSignatureReader implements PluginSignatureReader, MethodChecker {
  private static final Logger logger =
      Logger.getLogger(CompiledJarsPluginSignatureReader.class.getName());

  private final ImmutableList<File> pluginRuntimeJars;

  /** a map of class name -> method signatures in that class. */
  private final ConcurrentMap<String, ClassSignatures> readMethodsPerClass;

  private final boolean allowReflection;

  public CompiledJarsPluginSignatureReader(List<File> pluginRuntimeJars, boolean allowReflection) {
    this.pluginRuntimeJars = ImmutableList.copyOf(pluginRuntimeJars);
    this.readMethodsPerClass = new ConcurrentHashMap<>();
    this.allowReflection = allowReflection;
  }

  @Override
  @Nullable
  public ReadMethodData findMethod(MethodSignature methodSignature) {
    String className = methodSignature.fullyQualifiedClassName();
    // Get the cached methods per class if we have them, compute them if we don't.
    ClassSignatures readMethods =
        readMethodsPerClass.computeIfAbsent(className, k -> index(k, null));
    // Get all the possible methods for the partial signature.
    MethodSignatures methodsForSig =
        readMethods.forPartial(PartialSignature.create(methodSignature));
    // If we have an exact matching method for that return type, return it.
    if (methodsForSig.hasReturnType(methodSignature.returnType().getName())) {
      return methodsForSig.forReturnType(methodSignature.returnType().getName());
    }
    // If a matching sig exists w/o a matching return type, return an arbitrary method so we can
    // display a decent error message.
    if (!methodsForSig.isEmpty()) {
      return methodsForSig.allSignatures().iterator().next();
    }
    // Otherwise, nothing matches at all: return null.
    return null;
  }

  @Override
  public Response findMethod(
      String className, String methodName, String returnType, List<String> arguments) {
    return new AbstractMethodChecker() {
      @Override
      protected ClassSignatures getSignatures(String className) {
        return readMethodsPerClass.computeIfAbsent(className, k -> index(k, s -> {}));
      }
    }.findMethod(className, methodName, returnType, arguments);
  }

  public static boolean hasMatchingMethod(
      ClassSignatures signature, String methodName, String returnType, List<String> arguments) {
    // Get all the possible methods for the partial signature.
    MethodSignatures methodsForSig =
        signature.forPartial(PartialSignature.create(methodName, ImmutableList.copyOf(arguments)));
    // If we have an exact matching method for that return type, return it.
    if (methodsForSig.hasReturnType(returnType)) {
      return true;
    }
    // If a matching sig exists w/o a matching return type, return an arbitrary method so we can
    // display a decent error message.
    if (!methodsForSig.isEmpty()) {
      return false;
    }
    // Otherwise, nothing matches at all: return null.
    return false;
  }

  /**
   * Tries to index the available public methods in the class from reading jars. If reading jars
   * fails, falls back to using reflection.
   */
  private ClassSignatures index(String runtimeClassName, Consumer<String> errorReporter) {
    String ownerName = TypeInfo.create(runtimeClassName, /* doesn't matter */ false).internalName();
    for (File f : pluginRuntimeJars) {
      try (ZipFile jar = new ZipFile(f)) {
        ZipEntry entry = jar.getEntry(ownerName + ".class");
        if (entry == null) {
          // If the class didn't exist in this jar, try the next one.
          continue;
        }
        try (InputStream in = jar.getInputStream(entry)) {
          ClassReader reader = new org.objectweb.asm.ClassReader(in);
          Visitor visitor = new Visitor();
          reader.accept(
              visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
          return visitor.signatures.build();
        }
      } catch (IOException | UnsupportedOperationException e) {
        if (errorReporter != null) {
          errorReporter.accept("Unable to read class: " + runtimeClassName);
        } else {
          logger.log(Level.WARNING, e, () -> "Unable to read class: " + runtimeClassName);
        }
      }
    }
    if (allowReflection) {
      return indexReflectively(runtimeClassName);
    }
    return ClassSignatures.EMPTY;
  }

  /** Uses reflection to index the available public methods in a class. */
  public static ClassSignatures indexReflectively(String runtimeClassName) {
    try {
      Class<?> clazz = Class.forName(runtimeClassName);
      boolean classIsPublic = Modifier.isPublic(clazz.getModifiers());
      Method[] declaredMethods = clazz.getDeclaredMethods();
      ClassSignatures.Builder signatures = new ClassSignatures.Builder();
      for (Method m : declaredMethods) {
        signatures.add(
            PartialSignature.create(m),
            ReadMethodData.create(
                classIsPublic && Modifier.isPublic(m.getModifiers()),
                !Modifier.isStatic(m.getModifiers()),
                clazz.isInterface(),
                m.getReturnType().getName()));
      }
      return signatures.build();
    } catch (ClassNotFoundException | SecurityException e) {
      return ClassSignatures.EMPTY;
    }
  }

  /** Visits the methods in a class, storing the public methods that plugins can use. */
  private static class Visitor extends ClassVisitor {
    final ClassSignatures.Builder signatures = new ClassSignatures.Builder();
    boolean classIsInterface;
    boolean classIsPublic;

    Visitor() {
      super(Opcodes.ASM7);
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      classIsInterface = Modifier.isInterface(access);
      classIsPublic = Modifier.isPublic(access);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      Type methodType = Type.getMethodType(descriptor);
      signatures.add(
          PartialSignature.create(name, methodType),
          ReadMethodData.create(
              classIsPublic && Modifier.isPublic(access),
              !Modifier.isStatic(access),
              classIsInterface,
              methodType.getReturnType().getClassName()));
      return null;
    }
  }

  /**
   * Stores all methods per class, indexed by the partial signature of the method (just the method
   * name + parameters). This is necessary because classes can contain more than one method per
   * name+parameter, and we'd like to display useful error messages to the user if the return types
   * differ from what was expected. See {@link Class#getMethods} for an explanation of how a class
   * can have more than one method (TL;DR: "while the Java language forbids a class to declare
   * multiple methods with the same signature but different return types, the Java virtual machine
   * does not.")
   */
  public static class ClassSignatures {
    public static final ClassSignatures EMPTY = new ClassSignatures.Builder().build();

    final ImmutableMap<PartialSignature, MethodSignatures> methods;

    ClassSignatures(Builder builder) {
      this.methods =
          builder.methodsBuilder.entrySet().stream()
              .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().build()));
    }

    /** Returns all matching signatures for the partial signature. */
    public MethodSignatures forPartial(PartialSignature partial) {
      return methods.getOrDefault(partial, MethodSignatures.EMPTY);
    }

    public ImmutableSet<PartialSignature> allPartials() {
      return methods.keySet();
    }

    static class Builder {
      final Map<PartialSignature, MethodSignatures.Builder> methodsBuilder = new LinkedHashMap<>();

      Builder add(PartialSignature partialSig, ReadMethodData data) {
        methodsBuilder.computeIfAbsent(partialSig, k -> new MethodSignatures.Builder()).add(data);
        return this;
      }

      ClassSignatures build() {
        return new ClassSignatures(this);
      }
    }
  }

  /** Indexes method signature data based on the return type of the method. */
  static class MethodSignatures {
    static final MethodSignatures EMPTY = new MethodSignatures.Builder().build();

    final ImmutableMap<String, ReadMethodData> signaturesPerReturnType;

    MethodSignatures(Builder builder) {
      this.signaturesPerReturnType = builder.signaturesBuilder.build();
    }

    Iterable<ReadMethodData> allSignatures() {
      return signaturesPerReturnType.values();
    }

    boolean isEmpty() {
      return signaturesPerReturnType.isEmpty();
    }

    ReadMethodData forReturnType(String type) {
      return signaturesPerReturnType.get(type);
    }

    boolean hasReturnType(String type) {
      return signaturesPerReturnType.containsKey(type);
    }

    ImmutableSet<String> returnTypes() {
      return signaturesPerReturnType.keySet();
    }

    static class Builder {
      final ImmutableMap.Builder<String, ReadMethodData> signaturesBuilder = ImmutableMap.builder();

      void add(ReadMethodData data) {
        signaturesBuilder.put(data.returnType(), data);
      }

      MethodSignatures build() {
        return new MethodSignatures(this);
      }
    }
  }

  /**
   * A partial method signature -- just the method name & arguments. This is so that we construct
   * useful error messages if the return type differs from what we expect.
   */
  @AutoValue
  abstract static class PartialSignature {
    abstract String methodName();

    abstract ImmutableList<String> arguments();

    @Override
    public final String toString() {
      return methodName() + "(" + String.join(", ", arguments()) + ")";
    }

    static PartialSignature create(Method method) {
      return new AutoValue_CompiledJarsPluginSignatureReader_PartialSignature(
          method.getName(),
          Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(toImmutableList()));
    }

    static PartialSignature create(String methodName, ImmutableList<String> arguments) {
      return new AutoValue_CompiledJarsPluginSignatureReader_PartialSignature(
          methodName, arguments);
    }

    static PartialSignature create(MethodSignature methodSignature) {
      return new AutoValue_CompiledJarsPluginSignatureReader_PartialSignature(
          methodSignature.methodName(),
          methodSignature.arguments().stream().map(Class::getName).collect(toImmutableList()));
    }

    static PartialSignature create(String methodName, Type methodType) {
      return new AutoValue_CompiledJarsPluginSignatureReader_PartialSignature(
          methodName,
          Arrays.stream(methodType.getArgumentTypes())
              .map(Type::getClassName)
              .collect(toImmutableList()));
    }
  }
}
