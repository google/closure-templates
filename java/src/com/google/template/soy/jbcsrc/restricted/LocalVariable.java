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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A local variable representation.
 *
 * <p>This does nothing to enforce required constraints, e.g.:
 *
 * <ul>
 *   <li>This does not ensure that {@link #start()} and {@link #end()} are valid and exist in the
 *       method.
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
    return new LocalVariable("this", owner.type(), 0, start, end, Feature.NON_NULLABLE);
  }

  public static LocalVariable createLocal(
      String name, int index, Type type, Label start, Label end) {
    checkArgument(!name.equals("this"));
    return new LocalVariable(name, type, index, start, end);
  }

  private final String variableName;
  private final int index;
  private final Label start;
  private final Label end;

  private LocalVariable(
      String variableName, Type type, int index, Label start, Label end, Feature... features) {
    super(type, Feature.CHEAP /* locals are always cheap */, features);
    this.variableName = checkNotNull(variableName);
    this.index = index;
    this.start = checkNotNull(start);
    this.end = checkNotNull(end);
  }

  /** The name of the variable, ends up in debugging tables. */
  public String variableName() {
    return variableName;
  }

  public int index() {
    return index;
  }

  /** A label defining the earliest point at which this variable is defined. */
  public Label start() {
    return start;
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
  public LocalVariable asNonNullable() {
    if (isNonNullable()) {
      return this;
    }
    return new LocalVariable(variableName, resultType(), index, start, end, Feature.NON_NULLABLE);
  }

  /**
   * Write a local variable table entry for this variable. This informs debuggers about variable
   * names, types and lifetime.
   */
  public void tableEntry(CodeBuilder mv) {
    mv.visitLocalVariable(
        variableName(),
        resultType().getDescriptor(),
        null, // no generic signature
        start(),
        end(),
        index());
  }

  @Override
  protected void doGen(CodeBuilder mv) {
    mv.visitVarInsn(resultType().getOpcode(Opcodes.ILOAD), index());
  }

  /**
   * Return a {@link Statement} that stores the value of the given expression into this variable.
   */
  public Statement store(final Expression expr) {
    return store(expr, Optional.empty());
  }

  /**
   * Return a {@link Statement} that stores the value of the given expression into this variable.
   *
   * @param expr The expression to store
   * @param firstVarInstruction A label to use to mark the store instruction
   */
  public Statement store(final Expression expr, Label firstVarInstruction) {
    return store(expr, Optional.of(firstVarInstruction));
  }

  /** Writes the value at the top of the stack to the local variable. */
  private Statement store(final Expression expr, final Optional<Label> firstVarInstruction) {
    expr.checkAssignableTo(resultType());
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expr.gen(adapter);
        if (firstVarInstruction.isPresent()) {
          adapter.mark(firstVarInstruction.get());
        }
        adapter.visitVarInsn(resultType().getOpcode(Opcodes.ISTORE), index());
      }
    };
  }

  @Override
  protected void extraToStringProperties(MoreObjects.ToStringHelper helper) {
    helper.add("name", variableName);
  }
}
