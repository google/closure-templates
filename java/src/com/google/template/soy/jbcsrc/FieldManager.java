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

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.jbcsrc.internal.JbcSrcNameGenerators;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.restricted.TypeInfo;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Manages registering fields for a given class. */
final class FieldManager {
  private final UniqueNameGenerator fieldNames = JbcSrcNameGenerators.forFieldNames();
  private final TypeInfo owner;
  private final List<FieldRef> fields = new ArrayList<>();
  private final List<StaticFieldVariable> staticFields = new ArrayList<>();
  private boolean definedFields = false;

  FieldManager(TypeInfo owner) {
    this.owner = owner;
  }

  FieldRef addGeneratedFinalField(String suggestedName, Type type) {
    String name = fieldNames.generateName(suggestedName);
    return doAddField(name, type, Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL);
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

  FieldRef addStaticField(String proposedName, Expression initializer) {
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

  @AutoValue
  abstract static class StaticFieldVariable {
    abstract FieldRef field();

    abstract Expression initializer();
  }
}
