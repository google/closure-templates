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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Comparator.comparing;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.MethodRefs;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.Statement;
import com.google.template.soy.jbcsrc.shared.SaveStateMetaFactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

/**
 * Manages logical template variables and their scopes as well as calculating how to generate
 * save/restore logic for template detaches.
 */
final class TemplateVariableManager implements LocalVariableManager {
  enum SaveStrategy {
    /** Means that the value of the variable should be recalculated rather than saved. */
    DERIVED,
    /** Means that the value of the variable should be saved . */
    STORE
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

    abstract AbstractVariable get(String name);
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
      SYNTHETIC
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

  abstract static class AbstractVariable {
    abstract Expression accessor();
  }

  private static final class TrivialVariable extends AbstractVariable {
    final Expression accessor;

    TrivialVariable(Expression accessor) {
      this.accessor = accessor;
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
   *   <li>A {@link Expression} that can be used to save the field.
   *   <li>A {@link Expression} that can be used to restore the field.
   *   <li>A {@link LocalVariable} that can be used to read the value.
   * </ul>
   */
  static final class Variable extends AbstractVariable {
    private final Expression initExpression;
    private final Expression accessor;
    private final LocalVariable local;
    private final SaveStrategy strategy;
    private final Statement initializer;

    private Variable(Expression initExpression, LocalVariable local, SaveStrategy strategy) {
      this.initExpression = initExpression;
      if (initExpression.isNonJavaNullable()) {
        local = local.asNonJavaNullable();
      }
      if (initExpression.isNonSoyNullish()) {
        local = local.asNonSoyNullish();
      }
      this.local = local;
      this.initializer = local.initialize(initExpression);
      this.strategy = strategy;
      if (initExpression instanceof SoyExpression) {
        accessor =
            SoyExpression.forRuntimeType(((SoyExpression) initExpression).soyRuntimeType(), local);
      } else {
        accessor = local;
      }
    }

    Statement initializer() {
      return initializer;
    }

    @Override
    Expression accessor() {
      return accessor;
    }

    LocalVariable local() {
      return local;
    }
  }

  private ScopeImpl activeScope;
  private final SimpleLocalVariableManager delegate;
  private final ImmutableMap<String, LocalVariable> methodParameters;

  TemplateVariableManager(
      Type owner,
      Type[] methodArguments,
      ImmutableList<String> parameterNames,
      Label methodBegin,
      Label methodEnd,
      boolean isStatic) {
    this.delegate =
        new SimpleLocalVariableManager(
            owner,
            methodArguments,
            parameterNames,
            methodBegin,
            methodEnd,
            /* isStatic= */ isStatic);
    activeScope = new ScopeImpl();
    // seed our map with all the method parameters from our delegate.
    methodParameters = delegate.allActiveVariables();
    methodParameters.forEach(
        (key, value) ->
            activeScope.variablesByKey.put(VarKey.create(key), new TrivialVariable(value)));
  }

  public void updateParameterTypes(Type[] parameterTypes, List<String> parameterNames) {
    delegate.updateParameterTypes(parameterTypes, parameterNames);
  }

  /** Enters a new scope. Variables may only be defined within a scope. */
  @Override
  public Scope enterScope() {
    return new ScopeImpl();
  }

  private class ScopeImpl extends Scope {
    final ScopeImpl parent;

    final LocalVariableManager.Scope delegateScope = delegate.enterScope();

    final List<VarKey> activeVariables = new ArrayList<>();
    final Map<VarKey, AbstractVariable> variablesByKey = new LinkedHashMap<>();

    ScopeImpl() {
      this.parent = TemplateVariableManager.this.activeScope;
      TemplateVariableManager.this.activeScope = this;
    }

    @Override
    void createTrivial(String name, Expression expression) {
      putVariable(VarKey.create(name), new TrivialVariable(expression));
    }

    @Override
    Variable createSynthetic(SyntheticVarName varName, Expression initExpr, SaveStrategy strategy) {
      return doCreate(
          // synthetics are prefixed by $ by convention
          "$" + varName.name(), initExpr, VarKey.create(varName), strategy);
    }

    @Override
    Variable create(String name, Expression initExpr, SaveStrategy strategy) {
      return doCreate(name, initExpr, VarKey.create(name), strategy);
    }

    @Override
    AbstractVariable get(String name) {
      return getVariable(VarKey.create(name));
    }

    @Override
    public LocalVariable createTemporary(String proposedName, Type type) {
      return delegateScope.createTemporary(proposedName, type);
    }

    @Override
    public LocalVariable createNamedLocal(String name, Type type) {
      LocalVariable var = delegateScope.createNamedLocal(name, type);
      putVariable(VarKey.create(name), new TrivialVariable(var));
      return var;
    }

    @Override
    public Label exitScopeMarker() {
      for (VarKey key : activeVariables) {
        AbstractVariable var = variablesByKey.remove(key);
        if (var == null) {
          throw new IllegalStateException("no variable active for key: " + key);
        }
      }
      TemplateVariableManager.this.activeScope = parent;
      return delegateScope.exitScopeMarker();
    }

    Variable doCreate(String proposedName, Expression initExpr, VarKey key, SaveStrategy strategy) {
      Variable var =
          new Variable(
              initExpr,
              delegateScope.createTemporary(proposedName, initExpr.resultType()),
              strategy);
      putVariable(key, var);
      return var;
    }

    void putVariable(VarKey key, AbstractVariable var) {
      AbstractVariable old = variablesByKey.put(key, var);
      if (old != null) {
        throw new IllegalStateException("multiple variables active for key: " + key);
      }
      activeVariables.add(key);
    }

    AbstractVariable getVariable(VarKey key) {
      var variable = variablesByKey.get(key);
      if (variable == null) {
        IllegalStateException e = null;
        if (parent != null) {
          try {
            return parent.getVariable(key);
          } catch (IllegalStateException ex) {
            e = ex;
          }
        }
        throw new IllegalStateException(
            "no variable: '" + key + "' is bound. " + variablesByKey.keySet() + " are in scope", e);
      }
      return variable;
    }

    /** Returns all variables currently in scope in creation order. */
    Stream<AbstractVariable> allVariables() {
      var direct = variablesByKey.values().stream();
      if (parent != null) {
        // Put parents first to preserve ordering
        return Streams.concat(parent.allVariables(), direct);
      }
      return direct;
    }
  }

  @Override
  public void generateTableEntries(CodeBuilder ga) {
    delegate.generateTableEntries(ga);
  }

  /**
   * Looks up a user defined variable with the given name. The variable must have been created in a
   * currently active scope.
   */
  @Override
  public Expression getVariable(String name) {
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
    return activeScope.getVariable(varKey).accessor();
  }

  /**
   * Looks up a user defined variable with the given name. The variable must have been created in a
   * currently active scope.
   */
  public LocalVariable getMethodParameter(String name) {
    return checkNotNull(
        methodParameters.get(name),
        "No such parameter: %s, expected one of %s",
        name,
        methodParameters.keySet());
  }

  /** Statements for saving and restoring local variables in class fields. */
  @AutoValue
  abstract static class SaveRestoreState {
    static SaveRestoreState create(
        Statement saveStackFrame,
        Statement saveRenderResult,
        Optional<Function<Expression, Statement>> restore) {
      return new AutoValue_TemplateVariableManager_SaveRestoreState(
          saveStackFrame, saveRenderResult, restore);
    }

    /**
     * This must be execute in a context where the StackFrame that is about to be returned is on top
     * of the stack.
     */
    abstract Statement saveStackFrame();

    /**
     * This must be execute in a context where the RenderREsult that is about to be returned is on
     * top of the stack.
     */
    abstract Statement saveRenderResult();

    abstract Optional<Function<Expression, Statement>> restore();
  }

  void assertSaveRestoreStateIsEmpty() {
    checkState(activeScope.allVariables().noneMatch(v -> v instanceof Variable));
  }

  private static final Handle BOOTSTRAP_SAVE_HANDLE =
      MethodRef.createPure(
              SaveStateMetaFactory.class,
              "bootstrapSaveState",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class)
          .asHandle();
  private static final Handle BOOTSTRAP_RESTORE_HANDLE =
      MethodRef.createPure(
              SaveStateMetaFactory.class,
              "bootstrapRestoreState",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              MethodType.class,
              int.class)
          .asHandle();

  private static Type simplifyType(Type type) {
    switch (type.getSort()) {
      case Type.OBJECT:
      case Type.ARRAY:
        return BytecodeUtils.OBJECT.type();
      case Type.BOOLEAN:
      case Type.CHAR:
      case Type.BYTE:
      case Type.SHORT:
      case Type.INT:
      case Type.FLOAT:
      case Type.LONG:
      case Type.DOUBLE:
        return type;
      default:
        throw new AssertionError("unsupported type: " + type);
    }
  }

  /** Returns a {@link SaveRestoreState} for the current state of the variable set. */
  SaveRestoreState saveRestoreState(int stateNumber) {
    // The map is in insertion order.  This is important since it means derived variables will work.
    // we save in reverse order and then reverse again to restore so derived variables work.  The
    // save and restore logic need to be in opposite orders because we are pushing and popping onto
    // a stack.
    // The map is in insertion order.  This is important since it means derived variables will work.
    // So our restore logic needs to be executed in the same order in order to restore derived
    // variable directly.
    ImmutableList<Variable> restoresInOrder =
        activeScope
            .allVariables()
            .filter(v -> !(v instanceof TrivialVariable))
            .map(v -> (Variable) v)
            .collect(toImmutableList());

    // Save order is not necessarily important, but we are saving into a synthetically created
    // StackFrame class generated by our bootstrap method.  To reduce the number of classes
    // generated and avoid boxing primitives we store a simplified set of field types. See
    // SaveStateMetaFactory.simplifyType.
    // So imagine we have 3 locals in scope with the following types (CompiledTemplate, long,
    // LoggingAdvisingAppendable), this will simplify to (Object, long, Object).
    // Now imagine another saveRestoreState with the followwing 3 locals (SoyValueProvider,
    // LoggingAdvisingAppendable, long), this will simplify to (Object, Object, long).
    // Ideally these two states would share a stack frame class, this would allow us to generate
    // fewer classes and should shorten bootstrap time.

    // in order to do this, we need to canonicalize the order of the save operations.  The order
    // itself doesn't really matter, just that all save restore sites perform the same operation.
    ImmutableList<Variable> storesToPerform =
        restoresInOrder.stream()
            .filter(v -> v.strategy == SaveStrategy.STORE)
            // sort based on the 'sort' of the local, getSort() returns a unique integer for every
            // primitive type and object/array.
            .sorted(comparing(v -> v.accessor().resultType().getSort()))
            .collect(toImmutableList());
    List<Type> methodTypeParams = new ArrayList<>();
    methodTypeParams.add(BytecodeUtils.STACK_FRAME_TYPE);
    methodTypeParams.add(Type.INT_TYPE);
    for (Variable variable : storesToPerform) {
      // Type simplification isn't strictly necessary, but it does reduce the number of MethodType
      // constants we create which can save on constant pool size.
      methodTypeParams.add(simplifyType(variable.accessor().resultType()));
    }

    Type saveStateMethodTypeStackFrame =
        Type.getMethodType(BytecodeUtils.STACK_FRAME_TYPE, methodTypeParams.toArray(new Type[0]));
    methodTypeParams.set(0, BytecodeUtils.RENDER_RESULT_TYPE);
    Type saveStateMethodTypeRenderResult =
        Type.getMethodType(BytecodeUtils.STACK_FRAME_TYPE, methodTypeParams.toArray(new Type[0]));
    Function<Type, Statement> saveState =
        (Type saveType) ->
            new Statement() {
              @Override
              protected void doGen(CodeBuilder cb) {
                // Because this is a constant, we could pass it to the visitInvokeDynamicInsn as a
                // constant bootstrap argument.  There is no real benefit though since all we do is
                // arrange to pass it to a constructor so we can just as easily do that here.
                // Bootstrap
                // arguments are mostly valuable when we can leverage them at linkage time.
                cb.pushInt(stateNumber);
                // load all variables onto the stack
                for (Variable var : storesToPerform) {
                  var.accessor().gen(cb);
                }
                // special case the base class ctors when there are no variables to save.
                if (storesToPerform.isEmpty()) {
                  var argTypes = saveType.getArgumentTypes();
                  if (argTypes[0].equals(BytecodeUtils.STACK_FRAME_TYPE)) {
                    MethodRefs.STACK_FRAME_CREATE_NON_LEAF.invokeUnchecked(cb);
                  } else {
                    MethodRefs.STACK_FRAME_CREATE_LEAF.invokeUnchecked(cb);
                  }
                } else {
                  cb.visitInvokeDynamicInsn(
                      "save", saveType.getDescriptor(), BOOTSTRAP_SAVE_HANDLE);
                }
              }
            };
    // Restore instructions
    // A side effect of save logic is that StackFrame type is created
    // So we can predict its name and generate direct references to it.
    // for each store, we use invokedynamic to retrieve the value from our generated StackFrame
    // To do this we need to know the index of the variable in the 'storesToPerform' list
    ImmutableMap<Variable, Integer> storeToSlotIndex =
        IntStream.range(0, storesToPerform.size())
            .boxed()
            .collect(toImmutableMap(storesToPerform::get, index -> index));
    ImmutableList<Variable> variablesToRestoreFromStorage =
        restoresInOrder.stream()
            .filter(v -> v.strategy == SaveStrategy.STORE)
            .collect(toImmutableList());
    Optional<Function<Expression, Statement>> restoreFromFrame =
        variablesToRestoreFromStorage.isEmpty()
            ? Optional.empty()
            : Optional.of(
                (stackFrameVar) ->
                    new Statement() {
                      @Override
                      protected void doGen(CodeBuilder cb) {
                        stackFrameVar.gen(cb);
                        for (int i = 0; i < variablesToRestoreFromStorage.size(); i++) {
                          if (i < variablesToRestoreFromStorage.size() - 1) {
                            // duplicate the reference to the stack frame at the top of the stack
                            // for all but the last restore operation
                            cb.dup();
                          }
                          Variable variableToRestore = variablesToRestoreFromStorage.get(i);
                          Type varType = variableToRestore.accessor().resultType();
                          cb.visitInvokeDynamicInsn(
                              "restoreLocal",
                              Type.getMethodType(varType, BytecodeUtils.STACK_FRAME_TYPE)
                                  .getDescriptor(),
                              BOOTSTRAP_RESTORE_HANDLE,
                              // Either type would be fine here since we ignore the first two
                              // parameters.  In fact we could just pass the encoded FrameKey string
                              // which would be smaller.
                              saveStateMethodTypeStackFrame,
                              storeToSlotIndex.get(variableToRestore));
                          variableToRestore.local.storeUnchecked(cb);
                        }
                      }
                    });

    ImmutableList<Statement> restoreDerivedVariables =
        restoresInOrder.stream()
            .filter(var -> var.strategy == SaveStrategy.DERIVED)
            .map(v -> v.local.store(v.initExpression))
            .collect(toImmutableList());
    return SaveRestoreState.create(
        saveState.apply(saveStateMethodTypeStackFrame),
        saveState.apply(saveStateMethodTypeRenderResult),
        restoreFromFrame.isEmpty() && restoreDerivedVariables.isEmpty()
            ? Optional.empty()
            : Optional.of(
                (Expression variable) ->
                    Statement.concat(
                        restoreFromFrame.isPresent()
                            ? restoreFromFrame.get().apply(variable)
                            : Statement.NULL_STATEMENT,
                        Statement.concat(restoreDerivedVariables))));
  }
}
