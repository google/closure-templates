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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.data.internal.ParamStore;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.data.restricted.UndefinedData;
import com.google.template.soy.jbcsrc.restricted.Expression.Feature;
import com.google.template.soy.jbcsrc.restricted.Expression.Features;
import com.google.template.soy.jbcsrc.shared.StackFrame;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Representation of a field in a java class. */
@AutoValue
public abstract class FieldRef {
  private static final Handle ENUM_CONSTANT_HANDLE =
      MethodRef.createPure(
              ConstantBootstraps.class,
              "enumConstant",
              MethodHandles.Lookup.class,
              String.class,
              Class.class)
          .asHandle();
  private static final Handle GET_STATIC_FIELD_HANDLE =
      MethodRef.createPure(
              ConstantBootstraps.class,
              "getStaticFinal",
              MethodHandles.Lookup.class,
              String.class,
              Class.class,
              Class.class)
          .asHandle();

  public static final FieldRef NULL_DATA =
      staticFieldReference(NullData.class, "INSTANCE").asNonJavaNullable();
  public static final FieldRef UNDEFINED_DATA =
      staticFieldReference(UndefinedData.class, "INSTANCE").asNonJavaNullable();
  public static final FieldRef EMPTY_STRING_DATA =
      staticFieldReference(StringData.class, "EMPTY_STRING").asNonJavaNullable();
  public static final FieldRef EMPTY_PARAMS =
      staticFieldReference(ParamStore.class, "EMPTY_INSTANCE").asNonJavaNullable();
  public static final FieldRef BOOLEAN_DATA_FALSE =
      staticFieldReference(BooleanData.class, "FALSE").asNonJavaNullable();
  public static final FieldRef BOOLEAN_DATA_TRUE =
      staticFieldReference(BooleanData.class, "TRUE").asNonJavaNullable();

  public static final FieldRef STACK_FRAME_STATE_NUMBER =
      instanceFieldReference(StackFrame.class, "stateNumber");

  public static FieldRef create(
      TypeInfo owner, String name, Type type, int modifiers, boolean isNullable) {
    if ((Modifier.fieldModifiers() & modifiers) != modifiers) {
      throw new IllegalArgumentException(
          "invalid modifiers, expected: "
              + Modifier.toString(Modifier.fieldModifiers())
              + " ("
              + Modifier.fieldModifiers()
              + ")"
              + " got: "
              + Modifier.toString(modifiers)
              + " ("
              + modifiers
              + ")");
    }

    FieldRef ref = new AutoValue_FieldRef(owner, name, type);
    ref.accessFlags = modifiers;
    ref.isNullable = isNullable;
    return ref;
  }

  public static FieldRef create(TypeInfo owner, String name, Type fieldType, int modifiers) {
    return create(owner, name, fieldType, modifiers, !BytecodeUtils.isPrimitive(fieldType));
  }

  public static FieldRef instanceFieldReference(Class<?> owner, String name) {
    Class<?> fieldType;
    int modifiers = 0;
    try {
      Field declaredField = owner.getDeclaredField(name);
      // mask to remove jvm private bits
      modifiers = declaredField.getModifiers() & Modifier.fieldModifiers();
      if (Modifier.isStatic(modifiers)) {
        throw new IllegalStateException("Field: " + declaredField + " is static");
      }
      fieldType = declaredField.getType();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return create(
        TypeInfo.create(owner), name, Type.getType(fieldType), modifiers, !fieldType.isPrimitive());
  }

  public static FieldRef staticFieldReference(Class<?> owner, String name) {
    Field declaredField;
    try {
      declaredField = owner.getDeclaredField(name);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return staticFieldReference(declaredField);
  }

  public static FieldRef staticFieldReference(Field field) {
    if (!Modifier.isStatic(field.getModifiers())) {
      throw new IllegalStateException("Field: " + field + " is not static");
    }
    return create(
        TypeInfo.create(field.getDeclaringClass()),
        field.getName(),
        Type.getType(field.getType()),
        // mask to remove jvm private bits
        field.getModifiers() & Modifier.fieldModifiers(),
        /* isNullable= */ false);
  }

  public static <T extends Enum<T>> FieldRef enumReference(T enumInstance) {
    return staticFieldReference(enumInstance.getDeclaringClass(), enumInstance.name());
  }

  public static FieldRef createPublicStaticField(TypeInfo owner, String name, Type type) {
    return create(
        owner,
        name,
        type,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
        !BytecodeUtils.isPrimitive(type));
  }

  /** The type that owns this field. */
  public abstract TypeInfo owner();

  public abstract String name();

  public abstract Type type();

  /**
   * The field access flags. This is a bit set of things like {@link Opcodes#ACC_STATIC} and {@link
   * Opcodes#ACC_PRIVATE}.
   */
  private int accessFlags;

  private boolean isNullable;

  public final boolean isStatic() {
    return (accessFlags & Opcodes.ACC_STATIC) != 0;
  }

  public final boolean isFinal() {
    return (accessFlags & Opcodes.ACC_FINAL) != 0;
  }

  private static final int VISIBILITY_MASK =
      Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;

  @CanIgnoreReturnValue
  public final FieldRef setVisibility(int visibility) {
    checkArgument(visibility % VISIBILITY_MASK == visibility);
    accessFlags = (accessFlags & ~VISIBILITY_MASK) | visibility;
    return this;
  }

  /** Defines the given field as member of the class. */
  public void defineField(ClassVisitor cv) {
    cv.visitField(
        accessFlags,
        name(),
        type().getDescriptor(),
        null /* no generic signature */,
        null /* no initializer */);
  }

  @CanIgnoreReturnValue
  public FieldRef asNonJavaNullable() {
    isNullable = false;
    return this;
  }

  /** Returns an accessor that accesses this field on the given owner. */
  public Expression accessor(Expression owner) {
    checkState(!isStatic());
    checkArgument(
        owner.resultType().equals(this.owner().type()),
        "Unexpected type: %s expected %s",
        owner.resultType(),
        owner().type());

    Features features = Features.of();
    if (owner.isCheap()) {
      features = features.plus(Feature.CHEAP);
    }
    if (!isNullable) {
      features = features.plus(Feature.NON_JAVA_NULLABLE);
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
    if (!isNullable) {
      features = features.plus(Feature.NON_JAVA_NULLABLE);
    }
    if (isFinal()) {
      // static final fields can be loaded as constants
      ConstantDynamic constant;
      if (owner().classOptional().map(Class::isEnum).orElse(false)) {
        constant = new ConstantDynamic(name(), type().getDescriptor(), ENUM_CONSTANT_HANDLE);
      } else {
        constant =
            new ConstantDynamic(
                name(), type().getDescriptor(), GET_STATIC_FIELD_HANDLE, owner().type());
      }
      // enum and static final field refs are always trivial constants, a getstatic is just as good
      // as an ldc instruction and tends to be smaller.  It fits with the principle of generate code
      // that javac generates.
      return new Expression(
          type(),
          Expression.ConstantValue.dynamic(constant, type(), /* isTrivialConstant= */ true),
          features) {
        @Override
        protected void doGen(CodeBuilder mv) {
          accessStaticUnchecked(mv);
        }
      };
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
  public Statement putInstanceField(Expression instance, Expression value) {
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
  public Statement putStaticField(Expression value) {
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
}
