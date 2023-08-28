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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A local variable representation.
 *
 * <p>This does nothing to enforce required constraints, e.g.:
 *
 * <ul>
 *   <li>This does not ensure that {@link #start} and {@link #end} are valid and have been visited.
 *   <li>This does not ensure that the {@link #index} is otherwise unused and that only one variable
 *       is active at a time with the index.
 * </ul>
 *
 * <p>Note: This class does not attempt to make use of the convenience methods on generator adapter
 * such as {@link CodeBuilder#newLocal(Type)} or {@link CodeBuilder#loadArg(int)} that make it
 * easier to work with local variables (and calculating local variable indexes). Instead we push
 * this responsibility onto our caller. This is because CodeBuilder doesn't make it possible to
 * generate local variable debugging tables in this case (e.g. there is no way to map a method
 * parameter index to a local variable index).
 */
public final class LocalVariable extends Expression {
  // TODO(lukes): the fact that you need to specify the start and end labels during construction
  // ends up being awkward... Due to the fact that it is unclear who is responsible for actually
  // visiting the labels.  Maybe this object should be label agnostic and the labels should just be
  // parameters to tableEntry?

  public static LocalVariable createThisVar(TypeInfo owner, Label start, Label end) {
    return new LocalVariable(
        "this",
        owner.type(),
        new State(0, true),
        start,
        end,
        Features.of(Feature.NON_JAVA_NULLABLE));
  }

  public static LocalVariable createLocal(
      String name, int index, Type type, Label start, Label end) {
    return new LocalVariable(name, type, new State(index, false), start, end, Features.of());
  }

  private static final class State {
    int index;
    boolean indexHasBeenRead;

    State(int index, boolean indexHasBeenRead) {
      this.index = index;
      this.indexHasBeenRead = indexHasBeenRead;
    }

    void shiftIndex(int offset) {
      if (indexHasBeenRead) {
        throw new IllegalStateException("slot has been read");
      }
      this.index += offset;
    }

    void markRead() {
      this.indexHasBeenRead = true;
    }
  }

  private final String variableName;
  private final State state;
  private final Label start;
  private final Label end;

  private LocalVariable(
      String variableName, Type type, State state, Label start, Label end, Features features) {
    super(type, /* locals are always cheap */ features.plus(Feature.CHEAP));
    this.variableName = checkNotNull(variableName);
    this.state = state;
    this.start = checkNotNull(start);
    this.end = checkNotNull(end);
  }

  /** The name of the variable, ends up in debugging tables. */
  public String variableName() {
    return variableName;
  }

  public int index() {
    return state.index;
  }

  public void shiftIndex(int offset) {
    state.shiftIndex(offset);
  }

  /** A label defining the latest point at which this variable is defined. */
  public Label end() {
    return end;
  }

  @Override
  public LocalVariable asCheap() {
    return this;
  }

  @Override
  public LocalVariable asNonJavaNullable() {
    if (isNonJavaNullable()) {
      return this;
    }
    return new LocalVariable(
        variableName, resultType(), state, start, end, features().plus(Feature.NON_JAVA_NULLABLE));
  }

  @Override
  public LocalVariable asNonSoyNullish() {
    if (isNonSoyNullish()) {
      return this;
    }
    return new LocalVariable(
        variableName, resultType(), state, start, end, features().plus(Feature.NON_SOY_NULLISH));
  }

  /**
   * Write a local variable table entry for this variable. This informs debuggers about variable
   * names, types and lifetime.
   */
  public void tableEntry(CodeBuilder mv) {
    state.markRead();
    if (Flags.DEBUG) {
      // calling getOffSet will throw if the label has not been visited
      start.getOffset();
      end.getOffset();
    }
    mv.visitLocalVariable(
        variableName(),
        resultType().getDescriptor(),
        null, // no generic signature
        start,
        end,
        index());
  }

  @Override
  protected void doGen(CodeBuilder cb) {
    cb.visitVarInsn(resultType().getOpcode(Opcodes.ILOAD), index());
  }

  public void storeUnchecked(CodeBuilder cb) {
    state.markRead();
    cb.visitVarInsn(resultType().getOpcode(Opcodes.ISTORE), index());
  }

  public void loadUnchecked(CodeBuilder cb) {
    state.markRead();
    cb.visitVarInsn(resultType().getOpcode(Opcodes.ILOAD), index());
  }

  /**
   * Return a {@link Statement} that stores the value of the given expression into this variable.
   */
  public Statement store(Expression expr) {
    return doStore(expr, false);
  }

  /**
   * Return a {@link Statement} that stores the value of the given expression into this variable.
   *
   * @param expr The expression to store
   */
  public Statement initialize(Expression expr) {
    return doStore(expr, true);
  }

  /** Writes the value at the top of the stack to the local variable. */
  private Statement doStore(Expression expr, boolean initialization) {
    expr.checkAssignableTo(resultType());
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expr.gen(adapter);
        if (initialization) {
          adapter.visitLabel(start);
        }
        storeUnchecked(adapter);
      }
    };
  }

  @Override
  public String toString() {
    // Make sure reading tostring (e.g. as debuggers do) doesn't set the read bit.
    boolean indexHasBeenRead = state.indexHasBeenRead;
    try {
      return super.toString();
    } finally {
      if (!indexHasBeenRead) {
        state.indexHasBeenRead = false;
      }
    }
  }

  @Override
  protected void extraToStringProperties(MoreObjects.ToStringHelper helper) {
    helper.add("name", variableName);
  }
}
