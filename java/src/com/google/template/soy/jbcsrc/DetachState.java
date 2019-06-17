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
import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jbcsrc.StandardNames.STATE_FIELD;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.RENDER_RESULT_TYPE;
import static com.google.template.soy.jbcsrc.restricted.Statement.returnExpression;

import com.google.auto.value.AutoValue;
import com.google.template.soy.jbcsrc.TemplateVariableManager.SaveRestoreState;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.FieldRef;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.TableSwitchGenerator;

/**
 * An object that manages generating the logic to save and restore execution state to enable
 * rendering to pause partway through a template.
 *
 * <p>First, definitions:
 *
 * <dl>
 *   <dt>Detach
 *   <dd>A 'detach' is the act of saving local state and returning control to our caller. Logically,
 *       we are saving a continuation.
 *   <dt>Detachable
 *   <dd>An operation that may conditionally detach.
 *   <dt>Reattach
 *   <dd>A 'reattach' is the act of restoring state and jumping back to the location just before the
 *       original 'detach'. We are calling back into our saved 'continuation'.
 *   <dt>Reattach Point
 *   <dd>The specific code location to which control should return.
 * </dl>
 *
 * <p>Each detachable method will look approximately like:
 *
 * <pre>{@code
 * int state;
 * Result detachable() {
 *   switch (state) {
 *     case 0: goto L0;  // no locals for state 0
 *     case 1:
 *       // restore all variables active at state 1
 *       goto L1;
 *     case 2:
 *       // restore locals active at state 2
 *       goto L2;
 *     ...
 *     default:
 *       throw new AssertionError();
 *   }
 *   L0:
 *   // start of the method
 *   ...
 * }
 * }</pre>
 *
 * <p>Then prior to each detachable point we will assign a label and generate code that looks like
 * this:
 *
 * <pre>{@code
 * LN:
 *   if (needs to detach) {
 *     save locals to fields
 *     state = N;
 *     return Result.detachCause(cause);
 *   }
 * }
 * }</pre>
 *
 * <p>This object is mutable and depends on the state of the {@link TemplateVariableManager} to
 * determine the current set of active variables. So it is important that uses of this object are
 * sequenced appropriately with operations that introduce (or remove) active variables.
 *
 * <p>Note, in the above examples, the caller is responsible for calculating when/why to detach but
 * this class is responsible for calculating the save/restore reattach logic.
 */
final class DetachState implements ExpressionDetacher.Factory {
  private final TemplateVariableManager variables;
  private final List<ReattachState> reattaches = new ArrayList<>();
  private final Expression thisExpr;
  private final FieldManager fields;
  // lazily allocated
  @Nullable private FieldRef stateField;

  DetachState(TemplateVariableManager variables, Expression thisExpr, FieldManager fields) {
    this.variables = variables;
    this.thisExpr = thisExpr;
    this.fields = fields;
  }

  private FieldRef getStateField() {
    if (stateField == null) {
      stateField = fields.addField(STATE_FIELD, Type.INT_TYPE);
    }
    return stateField;
  }

  /**
   * Returns a {@link ExpressionDetacher} that can be used to instrument an expression with detach
   * reattach logic.
   */
  @Override
  public ExpressionDetacher createExpressionDetacher(Label reattachPoint) {
    // Lazily allocate the save restore state since it isnt always needed.
    return new ExpressionDetacher.BasicDetacher(
        () -> {
          SaveRestoreState saveRestoreState = variables.saveRestoreState();
          Statement restore = saveRestoreState.restore();
          int state = addState(reattachPoint, restore);
          Statement saveState =
              getStateField().putInstanceField(thisExpr, BytecodeUtils.constant(state));
          return Statement.concat(saveRestoreState.save(), saveState);
        });
  }

  /**
   * Returns a Statement that will conditionally detach if the given {@link AdvisingAppendable} has
   * been {@link AdvisingAppendable#softLimitReached() output limited}.
   */
  Statement detachLimited(AppendableExpression appendable) {
    if (!appendable.supportsSoftLimiting()) {
      return appendable.toStatement();
    }
    final Label reattachPoint = new Label();
    final SaveRestoreState saveRestoreState = variables.saveRestoreState();

    Statement restore = saveRestoreState.restore();
    int state = addState(reattachPoint, restore);
    final Expression isSoftLimited = appendable.softLimitReached();
    final Statement returnLimited = returnExpression(MethodRef.RENDER_RESULT_LIMITED.invoke());
    final Statement saveState =
        getStateField().putInstanceField(thisExpr, BytecodeUtils.constant(state));
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        isSoftLimited.gen(adapter);
        adapter.ifZCmp(Opcodes.IFEQ, reattachPoint); // if !softLimited
        // ok we were limited, save state and return
        saveRestoreState.save().gen(adapter); // save locals
        saveState.gen(adapter); // save the state field
        returnLimited.gen(adapter);
        // Note, the reattach point for 'limited' is _after_ the check.  That means we do not
        // recheck the limit state.  So if a caller calls us back without freeing any buffer we
        // will print more before checking again.  This is fine, because our caller is breaking the
        // contract.
        adapter.mark(reattachPoint);
      }
    };
  }

  /**
   * Generate detach logic for render operations (like SoyValueProvider.renderAndResolve).
   *
   * <p>This is simple
   *
   * <pre>{@code
   * REATTACH_RENDER:
   * RenderResult initialResult = svp.renderAndResolve(appendable);
   * if (!initialResult.isDone()) {
   *   // save all fields
   *   state = REATTACH_RENDER;
   *   return initialResult;
   * }
   * }</pre>
   *
   * <p>NOTE: {@code call} statements should use {@link #detachForCall} which has an optimization
   * for the fact that calls are simpler
   *
   * @param render an Expression that can generate code to call a render method that returns a
   *     RenderResult
   */
  Statement detachForRender(final Expression render) {
    checkArgument(render.resultType().equals(RENDER_RESULT_TYPE));
    final Label reattachPoint = new Label();
    final SaveRestoreState saveRestoreState = variables.saveRestoreState();

    Statement restore = saveRestoreState.restore();
    int state = addState(reattachPoint, restore);
    final Statement saveState =
        getStateField().putInstanceField(thisExpr, BytecodeUtils.constant(state));
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(reattachPoint);
        // Legend: RR = RenderResult, Z = boolean
        render.gen(adapter); // Stack: RR
        adapter.dup(); // Stack: RR, RR
        MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(adapter); // Stack: RR, Z
        // if isDone goto Done
        Label end = new Label();
        adapter.ifZCmp(Opcodes.IFNE, end); // Stack: RR
        saveRestoreState.save().gen(adapter);
        saveState.gen(adapter);
        adapter.returnValue();
        adapter.mark(end);
        adapter.pop(); // Stack:
      }
    };
  }

  /**
   * Generate detach logic for calls.
   *
   * <p>Calls are a little different due to a desire to minimize the cost of detaches. We assume
   * that if a given call site detaches once, it is more likely to detach multiple times. So we
   * generate code that looks like:
   *
   * <pre>{@code
   * RenderResult initialResult = template.render(appendable, renderContext);
   * if (!initialResult.isDone()) {
   *   // save all fields
   *   state = REATTACH_RENDER;
   *   return initialResult;
   * } else {
   *   goto END;
   * }
   * REATTACH_RENDER:
   * // restore nothing!
   * RenderResult secondResult = template.render(appendable, renderContext);
   * if (!secondResult.isDone()) {
   *   // saveFields
   *   state = REATTACH_RENDER;
   *   return secondResult;
   * } else {
   *   // restore all fields
   *   goto END;
   * }
   * END:
   * }</pre>
   *
   * <p>With this technique we save re-running the save-restore logic for multiple detaches from the
   * same call site. This should be especially useful for top level templates.
   *
   * <p>A consequence of this technique is that the callRender expression cannot depend on any
   * variables that are controlled by the restore logic.
   *
   * @param callRender an Expression that can generate code to call the render method, should be
   *     safe to generate more than once. And should not rely on any state that is managed by the
   *     save restore logic.
   */
  Statement detachForCall(final Expression callRender) {
    checkArgument(callRender.resultType().equals(RENDER_RESULT_TYPE));
    final Label reattachRender = new Label();
    final SaveRestoreState saveRestoreState = variables.saveRestoreState();
    // We pass NULL statement for the restore logic since we handle that ourselves below
    int state = addState(reattachRender, Statement.NULL_STATEMENT);
    final Statement saveState =
        getStateField().putInstanceField(thisExpr, BytecodeUtils.constant(state));
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        // Legend: RR = RenderResult, Z = boolean
        callRender.gen(adapter); // Stack: RR
        adapter.dup(); // Stack: RR, RR
        MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(adapter); // Stack: RR, Z
        // if isDone goto Done
        Label end = new Label();
        adapter.ifZCmp(Opcodes.IFNE, end); // Stack: RR

        saveRestoreState.save().gen(adapter);
        saveState.gen(adapter);
        adapter.returnValue();

        adapter.mark(reattachRender);
        callRender.gen(adapter); // Stack: RR
        adapter.dup(); // Stack: RR, RR
        MethodRef.RENDER_RESULT_IS_DONE.invokeUnchecked(adapter); // Stack: RR, Z
        // if isDone goto restore
        Label restore = new Label();
        adapter.ifZCmp(Opcodes.IFNE, restore); // Stack: RR
        // no need to save or restore anything
        adapter.returnValue();
        adapter.mark(restore); // Stack: RR
        saveRestoreState.restore().gen(adapter);
        adapter.mark(end); // Stack: RR
        adapter.pop(); // Stack:
      }
    };
  }

  /**
   * Returns a statement that generates the reattach jump table.
   *
   * <p>Note: This statement should be the <em>first</em> statement in any detachable method.
   */
  Statement generateReattachTable() {
    if (stateField == null) {
      checkState(reattaches.isEmpty(), "expected no reattaches: %s", reattaches);
      return Statement.NULL_STATEMENT;
    }
    if (reattaches.isEmpty()) {
      throw new IllegalStateException(
          "inconsistent state, never generated a state but has reattach logic.");
    }
    final Expression readField = getStateField().accessor(thisExpr);
    final Statement defaultCase =
        Statement.throwExpression(MethodRef.RUNTIME_UNEXPECTED_STATE_ERROR.invoke(readField));
    return new Statement() {
      @Override
      protected void doGen(final CodeBuilder adapter) {
        // add 1 so that state 0 is the initial state
        int[] keys = new int[reattaches.size() + 1];
        for (int i = 0; i < keys.length; i++) {
          keys[i] = i;
        }
        readField.gen(adapter);
        // Generate a switch table.  Note, while it might be preferable to just 'goto state', Java
        // doesn't allow computable gotos (probably because it makes verification impossible).  So
        // instead we emulate that with a jump table.  And anyway we still need to execute 'restore'
        // logic to repopulate the local variable tables, so the 'case' statements are a natural
        // place for that logic to live.
        adapter.tableSwitch(
            keys,
            new TableSwitchGenerator() {
              @Override
              public void generateCase(int key, Label end) {
                if (key == 0) {
                  // State 0 is special, it means initial state, so we just jump to the very end
                  adapter.goTo(end);
                  return;
                }
                // subtract 1 for the index of the state
                ReattachState reattachState = reattaches.get(key - 1);
                // restore and jump!
                reattachState.restoreStatement().gen(adapter);
                adapter.goTo(reattachState.reattachPoint());
              }

              @Override
              public void generateDefault() {
                defaultCase.gen(adapter);
              }
            },
            // Use tableswitch instead of lookupswitch.  TableSwitch is appropriate because our case
            // labels are sequential integers in the range [0, N).  This means that switch is O(1)
            // and
            // there are no 'holes' meaning that it is compact in the bytecode.
            true);
      }
    };
  }

  /** Add a new state item and return the state. */
  private int addState(Label reattachPoint, Statement restore) {
    ReattachState create = ReattachState.create(reattachPoint, restore);
    reattaches.add(create);
    // the index of the ReattachState in the list + 1, 0 is reserved for 'initial state'.
    int state = reattaches.size();
    return state;
  }

  @AutoValue
  abstract static class ReattachState {
    static ReattachState create(Label reattachPoint, Statement restore) {
      return new AutoValue_DetachState_ReattachState(reattachPoint, restore);
    }

    /** The label where control should resume when continuing. */
    abstract Label reattachPoint();

    /** The statement that restores the state of local variables so we can resume execution. */
    abstract Statement restoreStatement();
  }

  /** Returns the number of unique detach/reattach points. */
  int getNumberOfDetaches() {
    return reattaches.size();
  }
}
