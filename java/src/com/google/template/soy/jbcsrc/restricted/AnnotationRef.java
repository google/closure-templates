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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableMap;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;

/**
 * A helper for turning {@link Annotation} instances into asm visit operations.
 *
 * <p>This is safer than just using {@link AnnotationVisitor} directly since it makes it impossible
 * to typo field names, which are silently ignored, since it is assumed in the parser that the field
 * is from a different version of the annotation.
 */
public final class AnnotationRef<T extends Annotation> {
  public static <T extends Annotation> AnnotationRef<T> forType(Class<T> annType) {
    checkArgument(annType.isAnnotation());
    return new AnnotationRef<>(annType);
  }

  private final Class<T> annType;
  private final String typeDescriptor;
  private final boolean isRuntimeVisible;
  private final ImmutableMap<Method, FieldWriter> writers;

  private AnnotationRef(Class<T> annType) {
    this.annType = annType;
    Retention retention = annType.getAnnotation(Retention.class);
    this.isRuntimeVisible = retention != null && retention.value() == RetentionPolicy.RUNTIME;
    this.typeDescriptor = Type.getDescriptor(annType);
    // we need to ensure that our writers are in a consistent ordering.  Otherwise we will generate
    // bytecode non deterministically.  getDeclaredMethods() internally uses a hashMap for storing
    // objects so the order of methods returned from it is non deterministic
    ImmutableMap.Builder<Method, FieldWriter> writersBuilder = ImmutableMap.builder();
    Method[] methods = annType.getDeclaredMethods();
    Arrays.sort(methods, comparing(Method::toGenericString));
    for (Method method : methods) {
      if (method.getParameterTypes().length == 0 && !Modifier.isStatic(method.getModifiers())) {
        Class<?> returnType = method.getReturnType();
        if (returnType.isArray()) {
          if (returnType.getComponentType().isAnnotation()) {
            @SuppressWarnings("unchecked") // we just checked above
            AnnotationRef<?> forType =
                forType((Class<? extends Annotation>) returnType.getComponentType());
            writersBuilder.put(method, annotationArrayFieldWriter(method.getName(), forType));
          } else {
            // simple array type
            writersBuilder.put(method, simpleArrayFieldWriter(method.getName()));
          }
        } else if (returnType.isAnnotation()) {
          // N.B. this is recursive and will fail if we encounter recursive annotations
          // (StackOverflowError).  This could be resolved when we have a usecase, but the failure
          // will be obvious if it every pops up.
          @SuppressWarnings("unchecked") // we just checked above
          AnnotationRef<?> forType = forType((Class<? extends Annotation>) returnType);
          writersBuilder.put(method, annotationFieldWriter(method.getName(), forType));
        } else {
          // simple primitive
          writersBuilder.put(method, simpleFieldWriter(method.getName()));
        }
      }
    }
    this.writers = writersBuilder.build();
  }

  /** Writes the given annotation to the visitor. */
  public void write(T instance, ClassVisitor visitor) {
    doWrite(instance, visitor.visitAnnotation(typeDescriptor, isRuntimeVisible));
  }

  private void doWrite(T instance, AnnotationVisitor annVisitor) {
    for (Map.Entry<Method, FieldWriter> entry : writers.entrySet()) {
      entry.getValue().write(annVisitor, invokeExplosively(instance, entry.getKey()));
    }
    annVisitor.visitEnd();
  }

  // Invokes the given method on the instance, throwing runtime exceptions if any exception is
  // thrown
  private Object invokeExplosively(T instance, Method key) {
    Object invoke;
    try {
      invoke = key.invoke(instance);
    } catch (ReflectiveOperationException e) {
      // these are unexpected since annotation accessors should be public
      throw new RuntimeException(e);
    }
    return invoke;
  }

  private interface FieldWriter {
    void write(AnnotationVisitor visitor, Object value);
  }

  /** Writes an annotation valued field to the writer. */
  private static <T extends Annotation> FieldWriter annotationFieldWriter(
      final String name, final AnnotationRef<T> ref) {
    return new FieldWriter() {
      @Override
      public void write(AnnotationVisitor visitor, Object value) {
        ref.doWrite(ref.annType.cast(value), visitor.visitAnnotation(name, ref.typeDescriptor));
      }
    };
  }

  /**
   * Writes an primitive valued field to the writer.
   *
   * <p>See {@link AnnotationVisitor#visit(String, Object)} for the valid types.
   */
  private static FieldWriter simpleFieldWriter(final String name) {
    return new FieldWriter() {
      @Override
      public void write(AnnotationVisitor visitor, Object value) {
        visitor.visit(name, value);
      }
    };
  }

  /** Writes an simple array valued field to the annotation visitor. */
  private static FieldWriter simpleArrayFieldWriter(final String name) {
    return new FieldWriter() {
      @Override
      public void write(AnnotationVisitor visitor, Object value) {
        int len = Array.getLength(value);
        AnnotationVisitor arrayVisitor = visitor.visitArray(name);
        for (int i = 0; i < len; i++) {
          arrayVisitor.visit(null, Array.get(value, i));
        }
        arrayVisitor.visitEnd();
      }
    };
  }

  /** Writes an annotation array valued field to the annotation visitor. */
  private static <T extends Annotation> FieldWriter annotationArrayFieldWriter(
      final String name, final AnnotationRef<T> ref) {
    return new FieldWriter() {
      @Override
      public void write(AnnotationVisitor visitor, Object value) {
        int len = Array.getLength(value);
        AnnotationVisitor arrayVisitor = visitor.visitArray(name);
        for (int i = 0; i < len; i++) {
          ref.doWrite(
              ref.annType.cast(Array.get(value, i)),
              arrayVisitor.visitAnnotation(null, ref.typeDescriptor));
        }
        arrayVisitor.visitEnd();
      }
    };
  }
}
