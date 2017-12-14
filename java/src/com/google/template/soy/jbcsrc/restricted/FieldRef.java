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
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.runtime.JbcSrcRuntime;
import java.lang.reflect.Modifier;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Representation of a field in a java class. */
@AutoValue
public abstract class FieldRef {
  public static final FieldRef BOOLEAN_DATA_FALSE =
      staticFieldReference(BooleanData.class, "FALSE");
  public static final FieldRef BOOLEAN_DATA_TRUE = staticFieldReference(BooleanData.class, "TRUE");
  public static final FieldRef NULL_PROVIDER =
      staticFieldReference(JbcSrcRuntime.class, "NULL_PROVIDER");
  public static final FieldRef EMPTY_DICT =
      staticFieldReference(SoyValueConverter.class, "EMPTY_DICT");
  public static final FieldRef EMPTY_MAP =
      staticFieldReference(SoyValueConverter.class, "EMPTY_MAP");

  public static FieldRef createFinalField(TypeInfo owner, String name, Class<?> type) {
    return createFinalField(owner, name, Type.getType(type));
  }

  public static FieldRef createFinalField(TypeInfo owner, String name, Type type) {
    return new AutoValue_FieldRef(
        owner,
        name,
        type,
        Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL,
        !BytecodeUtils.isPrimitive(type));
  }

  public static FieldRef instanceFieldReference(Class<?> owner, String name) {
    Class<?> fieldType;
    int modifiers = 0;
    try {
      java.lang.reflect.Field declaredField = owner.getDeclaredField(name);
      modifiers = declaredField.getModifiers();
      if (Modifier.isStatic(modifiers)) {
        throw new IllegalStateException("Field: " + declaredField + " is static");
      }
      fieldType = declaredField.getType();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new AutoValue_FieldRef(
        TypeInfo.create(owner), name, Type.getType(fieldType), modifiers, !fieldType.isPrimitive());
  }

  public static FieldRef staticFieldReference(Class<?> owner, String name) {
    java.lang.reflect.Field declaredField;
    try {
      declaredField = owner.getDeclaredField(name);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return staticFieldReference(declaredField);
  }

  public static FieldRef staticFieldReference(java.lang.reflect.Field field) {
    if (!Modifier.isStatic(field.getModifiers())) {
      throw new IllegalStateException("Field: " + field + " is not static");
    }
    return new AutoValue_FieldRef(
        TypeInfo.create(field.getDeclaringClass()),
        field.getName(),
        Type.getType(field.getType()),
        Opcodes.ACC_STATIC,
        /* isNullable= */ false);
  }

  public static <T extends Enum<T>> FieldRef enumReference(T enumInstance) {
    return staticFieldReference(enumInstance.getDeclaringClass(), enumInstance.name());
  }

  public static FieldRef createPublicStaticField(TypeInfo owner, String name, Type type) {
    return new AutoValue_FieldRef(
        owner,
        name,
        type,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
        !BytecodeUtils.isPrimitive(type));
  }

  public static FieldRef createField(TypeInfo owner, String name, Class<?> type) {
    return createField(owner, name, Type.getType(type));
  }

  public static FieldRef createField(TypeInfo owner, String name, Type type) {
    return new AutoValue_FieldRef(
        owner, name, type, Opcodes.ACC_PRIVATE, !BytecodeUtils.isPrimitive(type));
  }

  /** The type that owns this field. */
  public abstract TypeInfo owner();

  public abstract String name();

  public abstract Type type();

  /**
   * The field access flags. This is a bit set of things like {@link Opcodes#ACC_STATIC} and {@link
   * Opcodes#ACC_PRIVATE}.
   */
  abstract int accessFlags();

  abstract boolean isNullable();

  public final boolean isStatic() {
    return (accessFlags() & Opcodes.ACC_STATIC) != 0;
  }

  /** Defines the given field as member of the class. */
  public void defineField(ClassVisitor cv) {
    cv.visitField(
        accessFlags(),
        name(),
        type().getDescriptor(),
        null /* no generic signature */,
        null /* no initializer */);
  }

  public FieldRef asNonNull() {
    if (!isNullable() || BytecodeUtils.isPrimitive(type())) {
      return this;
    }
    return new AutoValue_FieldRef(owner(), name(), type(), accessFlags(), false);
  }

  /** Returns an accessor that accesses this field on the given owner. */
  public Expression accessor(final Expression owner) {
    checkState(!isStatic());
    checkArgument(owner.resultType().equals(this.owner().type()));
    Features features = Features.of();
    if (owner.isCheap()) {
      features = features.plus(Feature.CHEAP);
    }
    if (!isNullable()) {
      features = features.plus(Feature.NON_NULLABLE);
    }
    return new Expression(type(), features) {
      @Override
      protected void doGen(CodeBuilder mv) {
        owner.gen(mv);
        mv.getField(owner().type(), FieldRef.this.name(), resultType());
      }
    };
  }

  /** Returns an expression that accesses this static field. */
  public Expression accessor() {
    checkState(isStatic());
    Features features = Features.of(Feature.CHEAP);
    if (!isNullable()) {
      features = features.plus(Feature.NON_NULLABLE);
    }
    return new Expression(type(), features) {
      @Override
      protected void doGen(CodeBuilder mv) {
        accessStaticUnchecked(mv);
      }
    };
  }

  /** Accesses a static field. */
  void accessStaticUnchecked(CodeBuilder mv) {
    checkState(isStatic());
    mv.getStatic(owner().type(), FieldRef.this.name(), type());
  }

  /**
   * Returns a {@link Statement} that stores the {@code value} in this field on the given {@code
   * instance}.
   *
   * @throws IllegalStateException if this is a static field
   */
  public Statement putInstanceField(final Expression instance, final Expression value) {
    checkState(!isStatic(), "This field is static!");
    instance.checkAssignableTo(owner().type());
    value.checkAssignableTo(type());
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        instance.gen(adapter);
        value.gen(adapter);
        putUnchecked(adapter);
      }
    };
  }

  /**
   * Returns a {@link Statement} that stores the {@code value} in this field on the given {@code
   * instance}.
   *
   * @throws IllegalStateException if this is a static field
   */
  public Statement putStaticField(final Expression value) {
    checkState(isStatic(), "This field is not static!");
    value.checkAssignableTo(type());
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        value.gen(adapter);
        adapter.putStatic(owner().type(), name(), type());
      }
    };
  }

  /**
   * Adds code to place the top item of the stack into this field.
   *
   * @throws IllegalStateException if this is a static field
   */
  public void putUnchecked(CodeBuilder adapter) {
    checkState(!isStatic(), "This field is static!");
    adapter.putField(owner().type(), name(), type());
  }

  public static FieldRef create(
      TypeInfo owner, String name, Type type, int accessFlags, boolean isNullable) {
    return new AutoValue_FieldRef(owner, name, type, accessFlags, isNullable);
  }
}
