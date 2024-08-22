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

import static java.lang.invoke.MethodType.methodType;

import com.google.errorprone.annotations.Keep;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.internal.ParamStore;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A factory for creating a {@link CompiledTemplate} subclass for a given template.
 *
 * <p>This is used to create a subclass of {@link CompiledTemplate} that is specialized for a
 * particular template. This allows us to avoid a method lookup on every render call.
 */
public final class CompiledTemplateMetaFactory {
  public static final String JAVA_LANG_OBJECT = "java/lang/Object";

  private static final MethodType RENDER_TYPE =
      methodType(
          StackFrame.class,
          StackFrame.class,
          ParamStore.class,
          LoggingAdvisingAppendable.class,
          RenderContext.class);
  private static final MethodType FACTORY_TYPE = methodType(CompiledTemplate.class);

  private static final String COMPILED_TEMPLATE =
      Type.getType(CompiledTemplate.class).getInternalName();
  private static final String CONSTRUCTOR_DESCRIPTOR =
      methodType(void.class).toMethodDescriptorString();
  private static final Type CONTENT_KIND_TYPE = Type.getType(SanitizedContent.ContentKind.class);
  private static final Type LOGGING_ADVISING_APPENDABLE_TYPE =
      Type.getType(LoggingAdvisingAppendable.class);
  private static final MethodType SET_KIND_AND_DIRECTIONALITY_TYPE =
      methodType(LoggingAdvisingAppendable.class, SanitizedContent.ContentKind.class);

  /** A map to ensure we only attempt to define a class for each template once. */
  private static final ClassValue<ConcurrentMap<String, Class<? extends CompiledTemplate>>>
      classCache =
          new ClassValue<ConcurrentMap<String, Class<? extends CompiledTemplate>>>() {
            @Override
            protected ConcurrentMap<String, Class<? extends CompiledTemplate>> computeValue(
                Class<?> ownerClass) {
              return new ConcurrentHashMap<>();
            }
          };

  @Keep
  public static Object createCompiledTemplate(
      MethodHandles.Lookup lookup, String name, Class<?> type) throws Throwable {
    MethodHandle selfMethod = lookup.findStatic(lookup.lookupClass(), name, FACTORY_TYPE);

    var kind =
        lookup
            .revealDirect(selfMethod)
            .reflectAs(Method.class, lookup)
            .getAnnotation(TemplateMetadata.class)
            .contentKind();
    Type ownerType = Type.getType(lookup.lookupClass());
    String generatedClassInternalName = ownerType.getInternalName() + "$" + name;
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(
        Opcodes.V11,
        Opcodes.ACC_SUPER + Opcodes.ACC_FINAL + Opcodes.ACC_SYNTHETIC,
        generatedClassInternalName,
        /* signature= */ null,
        /* superName= */ COMPILED_TEMPLATE,
        /* interfaces= */ null);

    {
      MethodVisitor constructor =
          cw.visitMethod(
              Opcodes.ACC_PUBLIC,
              "<init>",
              CONSTRUCTOR_DESCRIPTOR,
              /* signature= */ null,
              /* exceptions= */ null);
      constructor.visitCode();
      constructor.visitVarInsn(Opcodes.ALOAD, 0); // this
      constructor.visitMethodInsn(
          Opcodes.INVOKESPECIAL,
          COMPILED_TEMPLATE,
          "<init>",
          CONSTRUCTOR_DESCRIPTOR,
          /* isInterface= */ false);
      constructor.visitInsn(Opcodes.RETURN);
      constructor.visitMaxs(-1, -1);
      constructor.visitEnd();
    }
    {
      MethodVisitor renderMethod =
          cw.visitMethod(
              Opcodes.ACC_PUBLIC,
              "render",
              RENDER_TYPE.toMethodDescriptorString(),
              /* signature= */ null,
              /* exceptions= */ null);
      renderMethod.visitCode();

      renderMethod.visitVarInsn(Opcodes.ALOAD, 1); // stackFrame
      renderMethod.visitVarInsn(Opcodes.ALOAD, 2); // paramStore
      renderMethod.visitVarInsn(Opcodes.ALOAD, 3); // appendable.setKindAndDirectionality(<kind>)
      renderMethod.visitFieldInsn(
          Opcodes.GETSTATIC,
          CONTENT_KIND_TYPE.getInternalName(),
          kind.name(),
          CONTENT_KIND_TYPE.getDescriptor());
      renderMethod.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL,
          LOGGING_ADVISING_APPENDABLE_TYPE.getInternalName(),
          "setKindAndDirectionality",
          SET_KIND_AND_DIRECTIONALITY_TYPE.toMethodDescriptorString(),
          /* isInterface= */ false);
      renderMethod.visitVarInsn(Opcodes.ALOAD, 4); // renderContext
      renderMethod.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          ownerType.getInternalName(),
          name,
          RENDER_TYPE.toMethodDescriptorString(),
          /* isInterface= */ false);
      renderMethod.visitInsn(Opcodes.ARETURN);
      renderMethod.visitMaxs(0, 0);
      renderMethod.visitEnd();
    }
    cw.visitEnd();

    var classData = cw.toByteArray();
    var clazz =
        classCache
            .get(lookup.lookupClass())
            .computeIfAbsent(
                name,
                n -> {
                  try {
                    return lookup.defineClass(classData).asSubclass(CompiledTemplate.class);
                  } catch (IllegalAccessException e) {
                    throw new LinkageError("Failed to define class", e);
                  }
                });
    return (CompiledTemplate)
        lookup
            .findConstructor(clazz, methodType(void.class))
            .asType(methodType(CompiledTemplate.class))
            .invokeExact();
  }

  private CompiledTemplateMetaFactory() {}
}
