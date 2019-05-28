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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.StandardNames.CURRENT_APPENDABLE_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.CURRENT_CALLEE_FIELD;
import static com.google.template.soy.jbcsrc.StandardNames.CURRENT_RENDEREE_FIELD;

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jbcsrc.internal.JbcSrcNameGenerators;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.ClassFieldManager;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Manages registering fields for a given class. */
final class FieldManager implements ClassFieldManager {
  private final UniqueNameGenerator fieldNames = JbcSrcNameGenerators.forFieldNames();
  private final TypeInfo owner;
  private final List<FieldRef> fields = new ArrayList<>();
  private final List<StaticFieldVariable> staticFields = new ArrayList<>();
  // Allocated lazily
  @Nullable private FieldRef currentCalleeField;
  // Allocated lazily
  @Nullable private FieldRef currentRendereeField;
  // Allocated lazily
  @Nullable private FieldRef currentAppendable;

  private boolean definedFields = false;

  FieldManager(TypeInfo owner) {
    this.owner = owner;
    // pre-claim these field names
    this.fieldNames.claimName(CURRENT_CALLEE_FIELD);
    this.fieldNames.claimName(CURRENT_RENDEREE_FIELD);
    this.fieldNames.claimName(CURRENT_APPENDABLE_FIELD);
  }

  FieldRef addGeneratedField(String suggestedName, Type type) {
    String name = fieldNames.generateName(suggestedName);
    return doAddField(name, type, Opcodes.ACC_PRIVATE);
  }

  FieldRef addGeneratedFinalField(String suggestedName, Type type) {
    String name = fieldNames.generateName(suggestedName);
    return doAddField(name, type, Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL);
  }

  FieldRef addField(String name, Type type) {
    fieldNames.claimName(name);
    return doAddField(name, type, Opcodes.ACC_PRIVATE);
  }

  FieldRef addFinalField(String name, Type type) {
    fieldNames.claimName(name);
    return doAddField(name, type, Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
  }

  private FieldRef doAddField(String name, Type type, int modifiers) {
    checkState(!definedFields);
    FieldRef field = FieldRef.create(owner, name, type, modifiers);
    fields.add(field);
    return field;
  }

  @Override
  public FieldRef addStaticField(String proposedName, Expression initializer) {
    return addStaticField(
        proposedName, initializer, Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE);
  }

  private FieldRef addStaticField(String proposedName, Expression initializer, int accessFlags) {
    String name = fieldNames.generateName(proposedName);
    FieldRef ref = doAddField(name, initializer.resultType(), accessFlags);
    if (initializer.isNonNullable()) {
      ref = ref.asNonNull();
    }
    staticFields.add(new AutoValue_FieldManager_StaticFieldVariable(ref, initializer));
    return ref;
  }

  FieldRef addPackagePrivateStaticField(String proposedName, Expression initializer) {
    return addStaticField(proposedName, initializer, Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
  }

  /** Defines all the fields necessary for the registered variables. */
  void defineFields(ClassVisitor writer) {
    checkState(!definedFields);
    definedFields = true;
    for (FieldRef field : fields) {
      field.defineField(writer);
    }
  }

  /**
   * Writes a static initializer ({@code <clinit>}) that initializes the static fields registered
   * with this class.
   */
  void defineStaticInitializer(ClassVisitor writer) {
    checkState(definedFields);
    if (staticFields.isEmpty()) {
      return;
    }
    List<Statement> statements = new ArrayList<>();
    for (StaticFieldVariable staticField : staticFields) {
      statements.add(staticField.field().putStaticField(staticField.initializer()));
    }
    statements.add(Statement.RETURN);
    Statement.concat(statements).writeMethod(Opcodes.ACC_STATIC, BytecodeUtils.CLASS_INIT, writer);
  }

  /**
   * Returns the field that holds the current callee template.
   *
   * <p>Unlike normal variables the VariableSet doesn't maintain responsibility for saving and
   * restoring the current callee to a local.
   */
  FieldRef getCurrentCalleeField() {
    FieldRef local = currentCalleeField;
    if (local == null) {
      local =
          currentCalleeField =
              doAddField(
                  CURRENT_CALLEE_FIELD, BytecodeUtils.COMPILED_TEMPLATE_TYPE, Opcodes.ACC_PRIVATE);
    }
    return local;
  }

  /**
   * Returns the field that holds the currently rendering SoyValueProvider.
   *
   * <p>Unlike normal variables the VariableSet doesn't maintain responsibility for saving and
   * restoring the current renderee to a local.
   */
  FieldRef getCurrentRenderee() {
    FieldRef local = currentRendereeField;
    if (local == null) {
      local =
          currentRendereeField =
              doAddField(
                  CURRENT_RENDEREE_FIELD,
                  BytecodeUtils.SOY_VALUE_PROVIDER_TYPE,
                  Opcodes.ACC_PRIVATE);
    }
    return local;
  }

  /**
   * Returns the field that holds the currently rendering LoggingAdvisingAppendable object that is
   * used for streaming renders.
   *
   * <p>Unlike normal variables the VariableSet doesn't maintain responsibility for saving and
   * restoring the current renderee to a local.
   *
   * <p>TODO(lukes): it would be better if the VariableSet would save/restore... the issue is
   * allowing multiple uses within a template to share the field.
   */
  FieldRef getCurrentAppendable() {
    FieldRef local = currentAppendable;
    if (local == null) {
      local =
          currentAppendable =
              doAddField(
                  CURRENT_APPENDABLE_FIELD,
                  BytecodeUtils.LOGGING_ADVISING_APPENDABLE_TYPE,
                  Opcodes.ACC_PRIVATE);
    }
    return local;
  }

  @AutoValue
  abstract static class StaticFieldVariable {
    abstract FieldRef field();

    abstract Expression initializer();
  }
}
