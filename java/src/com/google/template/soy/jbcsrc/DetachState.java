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
import java.util.Optional;
import javax.annotation.Nullable;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

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
          int state = addState(reattachPoint, saveRestoreState.restore());
          Statement saveState =
              getStateField().putInstanceField(thisExpr, BytecodeUtils.constant(state));
          return Statement.concat(
              saveRestoreState.save().orElse(Statement.NULL_STATEMENT), saveState);
        });
  }

  /**
   * Returns a Statement that will conditionally detach if the given {@link AdvisingAppendable} has
   * been {@link AdvisingAppendable#softLimitReached() output limited}.
   *
   * <p>This is only valid to call at the begining of templates. It does not allocate a save/restore
   * block since there should be nothing to save or restore.
   */
  Statement detachLimited(AppendableExpression appendable) {
    variables.assertSaveRestoreStateIsEmpty();
    if (!appendable.supportsSoftLimiting()) {
      return appendable.toStatement();
    }

    final Expression isSoftLimited = appendable.softLimitReached();
    final Statement returnLimited = returnExpression(MethodRef.RENDER_RESULT_LIMITED.invoke());
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Label continueLabel = new Label();
        isSoftLimited.gen(adapter);
        adapter.ifZCmp(Opcodes.IFEQ, continueLabel); // if !softLimited
        returnLimited.gen(adapter);
        adapter.mark(continueLabel);
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
   * @param render an Expression that can generate code to call a render method that returns a
   *     RenderResult
   */
  Statement detachForRender(final Expression render) {
    checkArgument(render.resultType().equals(RENDER_RESULT_TYPE));
    final Label reattachPoint = new Label();
    final SaveRestoreState saveRestoreState = variables.saveRestoreState();

    int state = addState(reattachPoint, saveRestoreState.restore());
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
        saveRestoreState.save().orElse(Statement.NULL_STATEMENT).gen(adapter);
        saveState.gen(adapter);
        adapter.returnValue();
        adapter.mark(end);
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
    // Generate a switch table.  Note, while it might be preferable to just 'goto state', Java
    // doesn't allow computable gotos (probably because it makes verification impossible).  So
    // instead we emulate that with a jump table.  And anyway we still need to execute 'restore'
    // logic to repopulate the local variable tables, so the 'case' statements are a natural
    // place for that logic to live.
    // add 1 so that state 0 is the initial state
    Label unexpectedState = new Label();
    Label end = new Label();
    List<Label> caseLabels = new ArrayList<>();
    List<Statement> casesToGen = new ArrayList<>();
    // handle case 0
    caseLabels.add(end);
    for (ReattachState reattachState : reattaches) {
      if (reattachState.restoreStatement().isPresent()) {
        Label caseLabel = new Label();
        Statement caseBody =
            Statement.concat(
                    reattachState.restoreStatement().get(),
                    new Statement() {
                      @Override
                      protected void doGen(CodeBuilder cb) {
                        cb.goTo(reattachState.reattachPoint());
                      }
                    })
                .labelStart(caseLabel);
        casesToGen.add(caseBody);
        caseLabels.add(caseLabel);
      } else {
        caseLabels.add(reattachState.reattachPoint());
      }
    }
    casesToGen.add(
        Statement.throwExpression(
                MethodRef.RUNTIME_UNEXPECTED_STATE_ERROR.invoke(getStateField().accessor(thisExpr)))
            .labelStart(unexpectedState));
    return Statement.concat(
            new Statement() {
              @Override
              protected void doGen(final CodeBuilder adapter) {
                readField.gen(adapter);
                adapter.visitTableSwitchInsn(
                    /* min=*/ 0,
                    /* max=*/ reattaches.size(),
                    /*dflt=*/ unexpectedState,
                    caseLabels.toArray(new Label[0]));
              }
            },
            Statement.concat(casesToGen))
        .labelEnd(end);
  }

  /** Add a new state item and return the state. */
  private int addState(Label reattachPoint, Optional<Statement> restore) {
    ReattachState create = ReattachState.create(reattachPoint, restore);
    reattaches.add(create);
    // the index of the ReattachState in the list + 1, 0 is reserved for 'initial state'.
    return reattaches.size();
  }

  @AutoValue
  abstract static class ReattachState {
    static ReattachState create(Label reattachPoint, Optional<Statement> restore) {
      return new AutoValue_DetachState_ReattachState(reattachPoint, restore);
    }

    /** The label where control should resume when continuing. */
    abstract Label reattachPoint();

    /** The statement that restores the state of local variables so we can resume execution. */
    abstract Optional<Statement> restoreStatement();
  }

  /** Returns the number of unique detach/reattach points. */
  int getNumberOfDetaches() {
    return reattaches.size();
  }
}
