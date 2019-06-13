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

import com.google.auto.value.AutoValue;
import com.google.template.soy.jbcsrc.TemplateVariableManager.VarKey.Kind;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * Manages logical template variables and their scopes as well as calculating how to generate
 * save/restore logic for template detaches.
 */
final class TemplateVariableManager implements LocalVariableManager {
  enum SaveStrategy {
    /** Means that the value of the variable should be recalculated rather than saved to a field. */
    DERIVED,
    /** Means that the value of the variable should be saved to a field. */
    STORE;
  }

  abstract static class Scope implements LocalVariableManager.Scope {
    private Scope() {}

    /**
     * Creates a 'trivial' variable.
     *
     * <p>This simply registers an expression within the scope so it can be looked up via {@link
     * #getVariable}, but it does not use a local variable or generate save/restore logic, the
     * expression will simply be evaluated on every reference. So it is only reasonable for
     * 'trivial' expressions that just read fields or refer to other local variables.
     */
    abstract void createTrivial(String name, Expression expression);

    /**
     * Creates a new 'synthetic' variable. A synthetic variable is a variable that is introduced by
     * the compiler rather than having a user defined name.
     *
     * @param name A proposed name for the variable, the actual variable name may be modified to
     *     ensure uniqueness
     * @param initializer The expression that can be used to derive the initial value. Note, this
     *     expression must be able to be saved to gen() more than once if {@code strategy} is {@code
     *     DERIVED}.
     * @param strategy Set this to {@code DERIVED} if the value of the variable is trivially
     *     derivable from other variables already defined.
     */
    abstract Variable createSynthetic(
        SyntheticVarName name, Expression initializer, SaveStrategy strategy);

    /**
     * Creates a new user-defined variable.
     *
     * @param name The name of the variable, the name is assumed to be unique (enforced by the
     *     ResolveNamesPass).
     * @param initializer The expression that can be used to initialize the variable
     * @param strategy Set this to {@code DERIVED} if the value of the variable is trivially
     *     derivable from other variables already defined.
     */
    abstract Variable create(String name, Expression initializer, SaveStrategy strategy);
  }

  /**
   * A sufficiently unique identifier.
   *
   * <p>This key will uniquely identify a currently 'active' variable, but may not be unique over
   * all possible variables.
   */
  @AutoValue
  abstract static class VarKey {
    enum Kind {
      /**
       * Includes @param, @inject, {let..}, and loop vars.
       *
       * <p>Uniqueness of local variable names is enforced by the ResolveNamesPass pass, we just
       * need uniqueness for the field names
       */
      USER_DEFINED,

      /**
       * There are certain operations in which a value must be used multiple times and may have
       * expensive initialization. For example, the collection being looped over in a {@code
       * foreach} loop. For these we generate 'synthetic' variables to efficiently reference the
       * expression.
       */
      SYNTHETIC;
    }

    static VarKey create(String proposedName) {
      return new AutoValue_TemplateVariableManager_VarKey(Kind.USER_DEFINED, proposedName);
    }

    static VarKey create(SyntheticVarName proposedName) {
      return new AutoValue_TemplateVariableManager_VarKey(Kind.SYNTHETIC, proposedName);
    }

    abstract Kind kind();

    abstract Object name();
  }

  private abstract static class AbstractVariable {
    abstract Statement save();

    abstract Statement restore();

    abstract Expression accessor();
  }

  private static final class TrivialVariable extends AbstractVariable {
    final Expression accessor;

    TrivialVariable(Expression accessor) {
      this.accessor = accessor;
    }

    @Override
    Statement save() {
      return Statement.NULL_STATEMENT;
    }

    @Override
    Statement restore() {
      return Statement.NULL_STATEMENT;
    }

    @Override
    Expression accessor() {
      return accessor;
    }
  }
  /**
   * A variable that needs to be saved/restored.
   *
   * <p>Each variable has:
   *
   * <ul>
   *   <li>A {@link FieldRef} that can be used to define the field.
   *   <li>A {@link Statement} that can be used to save the field.
   *   <li>A {@link Statement} that can be used to restore the field.
   *   <li>A {@link LocalVariable} that can be used to read the value.
   * </ul>
   */
  abstract static class Variable extends AbstractVariable {
    protected final Expression initExpression;
    protected final LocalVariable local;
    private final Statement initializer;

    private Variable(Expression initExpression, LocalVariable local) {
      this.initExpression = initExpression;
      this.local = local;
      this.initializer = local.store(initExpression, local.start());
    }

    final Statement initializer() {
      return initializer;
    }

    @Override
    final Expression accessor() {
      return local();
    }

    final LocalVariable local() {
      return local;
    }
  }

  private final class FieldSavedVariable extends Variable {
    final String originalProposedName;
    FieldRef field;

    private FieldSavedVariable(
        String originalProposedName, Expression initExpression, LocalVariable local) {
      super(initExpression, local);
      this.originalProposedName = originalProposedName;
    }

    FieldRef getField() {
      if (field == null) {
        field = fields.addGeneratedField(originalProposedName, local.resultType());
      }
      return field;
    }

    @Override
    Statement save() {
      return getField().putInstanceField(thisVar, local);
    }

    @Override
    Statement restore() {
      Expression fieldValue = getField().accessor(thisVar);
      return local.store(fieldValue);
    }
  }

  private static final class DerivedVariable extends Variable {
    private DerivedVariable(Expression initExpression, LocalVariable local) {
      super(initExpression, local);
    }

    @Override
    Statement save() {
      return Statement.NULL_STATEMENT;
    }

    @Override
    Statement restore() {
      return local.store(initExpression);
    }
  }

  private final FieldManager fields;
  private final SimpleLocalVariableManager delegate;
  private final Map<VarKey, AbstractVariable> variablesByKey = new LinkedHashMap<>();
  private final LocalVariable thisVar;
  private int numberOfActiveTemporaries;

  /**
   * @param fields The field manager for the current class.
   * @param thisVar An expression returning the current 'this' reference
   * @param method The method being generated
   */
  TemplateVariableManager(FieldManager fields, LocalVariable thisVar, Method method) {
    this.fields = fields;
    this.thisVar = thisVar;
    this.delegate = new SimpleLocalVariableManager(method, /*isStatic=*/ false);
  }

  /** Enters a new scope. Variables may only be defined within a scope. */
  @Override
  public Scope enterScope() {
    final LocalVariableManager.Scope delegateScope = delegate.enterScope();
    return new Scope() {
      final List<VarKey> activeVariables = new ArrayList<>();
      int activeTemporariesInThisScope;

      @Override
      void createTrivial(String name, Expression expression) {
        putVariable(VarKey.create(name), new TrivialVariable(expression));
      }

      @Override
      Variable createSynthetic(
          SyntheticVarName varName, Expression initExpr, SaveStrategy strategy) {
        return doCreate(
            // synthetics are prefixed by $ by convention
            "$" + varName.name(), initExpr, VarKey.create(varName), strategy);
      }

      @Override
      Variable create(String name, Expression initExpr, SaveStrategy strategy) {
        return doCreate(name, initExpr, VarKey.create(name), strategy);
      }

      @Override
      public LocalVariable createLocal(String name, Type type) {
        numberOfActiveTemporaries++;
        activeTemporariesInThisScope++;
        return delegateScope.createLocal(name, type);
      }

      @Override
      public Statement exitScope() {
        numberOfActiveTemporaries -= activeTemporariesInThisScope;
        for (VarKey key : activeVariables) {
          AbstractVariable var = variablesByKey.remove(key);
          if (var == null) {
            throw new IllegalStateException("no variable active for key: " + key);
          }
        }
        return delegateScope.exitScope();
      }

      private Variable doCreate(
          String proposedName, Expression initExpr, VarKey key, SaveStrategy strategy) {
        Variable var;
        switch (strategy) {
          case DERIVED:
            var =
                new DerivedVariable(
                    initExpr, delegateScope.createLocal(proposedName, initExpr.resultType()));
            break;
          case STORE:
            var =
                new FieldSavedVariable(
                    proposedName,
                    initExpr,
                    delegateScope.createLocal(proposedName, initExpr.resultType()));
            break;
          default:
            throw new AssertionError();
        }
        putVariable(key, var);
        return var;
      }

      private void putVariable(VarKey key, AbstractVariable var) {
        AbstractVariable old = variablesByKey.put(key, var);
        if (old != null) {
          throw new IllegalStateException("multiple variables active for key: " + key);
        }
        activeVariables.add(key);
      }
    };
  }

  @Override
  public void generateTableEntries(CodeBuilder ga) {
    delegate.generateTableEntries(ga);
  }

  /**
   * Looks up a user defined variable with the given name. The variable must have been created in a
   * currently active scope.
   */
  Expression getVariable(String name) {
    return getVariable(VarKey.create(name));
  }

  /**
   * Looks up a synthetic variable with the given name. The variable must have been created in a
   * currently active scope.
   */
  Expression getVariable(SyntheticVarName name) {
    return getVariable(VarKey.create(name));
  }

  private Expression getVariable(VarKey varKey) {
    AbstractVariable var = variablesByKey.get(varKey);
    if (var != null) {
      return var.accessor();
    }
    throw new IllegalArgumentException("No variable: '" + varKey + "' is bound");
  }

  /** Statements for saving and restoring local variables in class fields. */
  @AutoValue
  abstract static class SaveRestoreState {
    abstract Statement save();

    abstract Statement restore();
  }

  /** Returns a {@link SaveRestoreState} for the current state of the variable set. */
  SaveRestoreState saveRestoreState() {
    if (numberOfActiveTemporaries > 0) {
      throw new IllegalStateException(
          "Can't generate save/restore state when there are active non-saved temporary variables: "
              + numberOfActiveTemporaries);
    }
    List<Statement> saves = new ArrayList<>();
    List<Statement> restores = new ArrayList<>();
    // The map is in insertion order.  This is important since it means derived variables will work
    for (AbstractVariable var : variablesByKey.values()) {
      saves.add(var.save());
      restores.add(var.restore());
    }
    return new AutoValue_TemplateVariableManager_SaveRestoreState(
        Statement.concat(saves), Statement.concat(restores));
  }
}
