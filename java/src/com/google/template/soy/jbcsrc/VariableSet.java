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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Sets;
import com.google.template.soy.jbcsrc.VariableSet.VarKey.Kind;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A variable in this set is a SoyValue that must be saved/restored.  This means each variable has:
 * 
 * <ul>
 *     <li>A {@link FieldRef} that can be used to define the field.
 *     <li>A {@link Statement} that can be used to save the field.
 *     <li>A {@link Statement} that can be used to restore the field.
 *     <li>A {@link LocalVariable} that can be used to read the value.
 * </ul>
 */
final class VariableSet {
  abstract class Scope {
    private Scope() {}

    /**
     * Creates a new 'synthetic' variable.  A synthetic variable is a variable that is
     * introduced by the compiler rather than a user defined name.
     *
     * @param proposedName A proposed name for the variable, the name may be modified to ensure
     *     uniqueness
     * @param start The earliest location at which the variable is defined
     * @param end The latest location at which the variable is defined
     */
    abstract Variable createSynthetic(String proposedName, SoyExpression initializer, Label start,
        Label end);
    /**
     * Creates a new 'synthetic' variable.  A synthetic variable is a variable that is
     * introduced by the compiler rather than a user defined name.
     *
     * @param name The name of the variable, the name is assumed to be unique (enforced by the 
     *     ResolveNamesVisitor).
     * @param initializer The expression that can be used to initialize the variable
     * @param start The earliest location at which the variable is defined
     * @param end The latest location at which the variable is defined
     */
    abstract Variable create(String name, SoyExpression initializer, Label start, Label end);

    /**
     * Returns a statement that should be used when exiting the scope.  This is responsible for
     * appropriately clearing fields and visiting end labels.
     */
    abstract Statement exitScope();
  }

  /**
   * A sufficiently unique identifier.
   * 
   * <p>This key will uniquely identify a currently 'active' variable, but may not be unique over
   * all possible variables.
   */
  @AutoValue abstract static class VarKey {
    enum Kind {
      /** 
       * Includes @param, @inject, {let..}, and loop vars.
       * 
       * <p>Uniqueness of local variable names is enforced by the ResolveNamesVisitor pass, we
       * just need uniqueness for the field names 
       */
      USER_DEFINED,
      /**
       * There are certain operations in which a value must be used multiple times and may have
       * expensive initialization. For example, the collection being looped over in a
       * {@code foreach} loop.  For these we generate 'synthetic' variables to efficiently reference
       * the expression.
       */
      SYNTHETIC;
    }
    static VarKey create(Kind synthetic, String proposedName) {
      return new AutoValue_VariableSet_VarKey(synthetic, proposedName);
    }

    abstract Kind kind();
    abstract String name();
  }

  /**
   * A variable that may need to be saved/restored.
   */
  final class Variable {
    private FieldRef fieldRef;  // lazily allocated on a save/restore operation
    private final Statement initializer; 
    private final LocalVariable local;
    private final SoyExpression expression;

    private Variable(Statement initializer, LocalVariable local, SoyExpression expression) {
      this.initializer = initializer;
      this.local = local;
      this.expression = expression;
    }

    Statement initializer() {
      return initializer;
    }
    
    SoyExpression expr() {
      return expression;
    }

    private Statement save() {
      return getField().putInstanceField(thisVar, local);
    }

    private Statement restore() {
      Expression fieldValue = getField().accessor(thisVar);
      return local.store(fieldValue);
    }

    private FieldRef getField() {
      if (fieldRef == null) {
        fieldRef = FieldRef.createField(owner, local.variableName(), local.resultType());
      }
      return fieldRef;
    }

    private void maybeDefineField(ClassVisitor writer) {
      if (fieldRef != null) {
        fieldRef.defineField(writer);
      }
    }

    LocalVariable local() {
      return local;
    }
  }

  private final List<Variable> allVariables = new ArrayList<>();
  private final Deque<Map<VarKey, Variable>> frames = new ArrayDeque<>();
  private final UniqueNameGenerator fieldNames;
  private final BitSet availableSlots = new BitSet();
  private final TypeInfo owner;
  private final LocalVariable thisVar;

  /**
   * @param owner The type that is the owner of the method being generated
   * @param thisVar An expression returning the current 'this' reference
   * @param method The method being generated
   * @param fieldNames The field name set for the current class.
   */
  VariableSet(UniqueNameGenerator fieldNames, TypeInfo owner, LocalVariable thisVar,
      Method method) {
    this.fieldNames = fieldNames;
    this.owner = owner;
    this.thisVar = thisVar;
    availableSlots.set(0);   // for 'this'
    int from = 1;
    for (Type type : method.getArgumentTypes()) {
      int to = from + type.getSize();
      availableSlots.set(from, to);
      from = to;
    }
  }

  /**
   * Enters a new scope.  Variables may only be defined within a scope.
   */
  Scope enterScope() {
    final Map<VarKey, Variable> currentFrame = new LinkedHashMap<>();
    frames.push(currentFrame);
    return new Scope() {
      @Override Variable createSynthetic(
          String proposedName, SoyExpression initExpr, Label start, Label end) {
        VarKey key = VarKey.create(Kind.SYNTHETIC, proposedName);
        // synthetics are prefixed by $ by convention
        String name = fieldNames.generateName("$" + proposedName);
        return doCreate(name, start, end, initExpr, key);
      }

      @Override Variable create(String name, SoyExpression initExpr, Label start, Label end) {
        VarKey key = VarKey.create(Kind.USER_DEFINED, name);
        name = fieldNames.generateName(name);
        return doCreate(name, start, end, initExpr, key);
      }

      @Override Statement exitScope() {
        frames.pop();
        // Use identity semantics to make sure we visit each label at most once.  visiting a label
        // more than once tends to corrupt internal asm state.
        final Set<Label> endLabels = Sets.newSetFromMap(new IdentityHashMap<Label, Boolean>());
        for (Variable var : currentFrame.values()) {
          endLabels.add(var.local.end());
          availableSlots.clear(var.local.index(),
              var.local.index() + var.local.resultType().getSize());
        }
        return new Statement() {
          // TODO(lukes): we could generate null writes for when object typed fields go out of 
          // scope.  This would potentially allow intermediate results to be collected sooner.
          @Override void doGen(GeneratorAdapter adapter) {
            for (Label label : endLabels) {
              adapter.visitLabel(label);
            }
          }
        };
      }

      private Variable doCreate(
          String name, Label start, Label end, SoyExpression initExpr, VarKey key) {
        int index = reserveSlotFor(initExpr.resultType());
        LocalVariable local = 
            LocalVariable.createLocal(name, index, initExpr.resultType(), start, end);
        Variable var = new Variable(
            local.store(initExpr, start), 
            local, 
            initExpr.withSource(local));
        currentFrame.put(key, var);
        allVariables.add(var);
        return var;
      }
    };
  }

  /** Write a local variable table entry for every registered variable. */
  void generateTableEntries(GeneratorAdapter ga) {
    for (Variable var : allVariables) {
      var.local.tableEntry(ga);
    }
  }

  /** Defines all the fields necessary for the registered variables. */
  void defineFields(ClassVisitor writer) {
    for (Variable var : allVariables) {
      var.maybeDefineField(writer);
    }
  }

  /**
   * Looks up a user defined variable with the given name.  The variable must have been created
   * in a currently active scope.
   */
  Variable getVariable(String name) {
    VarKey varKey = VarKey.create(Kind.USER_DEFINED, name);
    for (Map<VarKey, Variable> f : frames) {
      Variable variable = f.get(varKey);
      if (variable != null) {
        return variable;
      }
    }
    throw new IllegalArgumentException("No variable named: '" + name + "' is bound");
  }
  
  /** Statements for saving and restoring local variables in class fields. */
  @AutoValue abstract static class SaveRestoreState {
    abstract Statement save();
    abstract Statement restore();
  }

  /** Returns a {@link SaveRestoreState} for the current state of the variable set. */
  SaveRestoreState saveRestoreState() {
    List<Statement> saves = new ArrayList<>();
    List<Statement> restores = new ArrayList<>();
    for (Map<VarKey, Variable> frame : frames) {
      for (Variable var : frame.values()) {
        saves.add(var.save());
        restores.add(var.restore());
      }
    }
    return new AutoValue_VariableSet_SaveRestoreState(
        Statement.concat(saves), Statement.concat(restores));
  }

  private int reserveSlotFor(Type type) {
    int size = type.getSize();
    checkArgument(size != 0);
    int start = 0;
    while (true) {
      int nextClear = availableSlots.nextClearBit(start);
      if (size == 2 && availableSlots.get(nextClear + 1)) {
        start = nextClear + 1;
      }
      availableSlots.set(nextClear, nextClear + size);
      return nextClear;
    }
  }
}

