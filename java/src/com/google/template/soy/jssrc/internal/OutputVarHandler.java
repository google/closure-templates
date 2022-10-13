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

package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.dsl.Expression.id;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Keeps track of the current output variable and generates code for declaring and assigning it. */
public final class OutputVarHandler {

  private static final class OutputVar {
    // TODO(b/32224284): this is always an {@link Expression#id}. Consider exposing a subclass of
    // CodeChunk so we can enforce this invariant at compile time.
    final Expression name;
    final boolean initialized;

    OutputVar(Expression name, boolean initialized) {
      this.name = name;
      this.initialized = initialized;
    }
  }

  /** The current stack of output variables. */
  private final Deque<OutputVar> outputVars;

  public OutputVarHandler() {
    outputVars = new ArrayDeque<>();
  }

  private OutputVar currentOutputVar() {
    return outputVars.peek();
  }

  public Optional<Statement> initOutputVarIfNecessary() {
    if (!outputVars.isEmpty() && currentOutputVar().initialized) {
      // Nothing to do since it's already initialized.
      return Optional.empty();
    }
    return Optional.of(
        initOutputVar(
            currentOutputVar().name.assertExpr().getText(), Expression.LITERAL_EMPTY_STRING));
  }

  private VariableDeclaration initOutputVar(String name, Expression rhs) {
    setOutputVarInited();
    return VariableDeclaration.builder(name).setMutable().setRhs(rhs).build();
  }

  /** Appends the given code chunk to the current output variable. */
  public Statement addChunkToOutputVar(Expression chunk) {
    return addChunksToOutputVar(ImmutableList.of(chunk));
  }

  /**
   * Appends one or more lines representing the concatenation of the values of the given code chunks
   * saved to the current output variable.
   */
  public Statement addChunksToOutputVar(List<? extends Expression> codeChunks) {
    if (currentOutputVar().initialized) {
      Expression rhs = CodeChunkUtils.concatChunks(codeChunks);
      return currentOutputVar().name.plusEquals(rhs).asStatement();
    } else {
      Expression rhs = CodeChunkUtils.concatChunksForceString(codeChunks);
      return initOutputVar(currentOutputVar().name.singleExprOrName().getText(), rhs);
    }
  }

  /**
   * Pushes on a new current output variable.
   *
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    outputVars.push(new OutputVar(id(outputVarName), false));
  }

  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  @CanIgnoreReturnValue
  public Expression popOutputVar() {
    return outputVars.pop().name;
  }

  /**
   * Tells this handler that the current output variable has already been initialized. This causes
   * {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization code even
   * on the first use of the variable.
   */
  public void setOutputVarInited() {
    Expression outputVar = outputVars.pop().name;
    outputVars.push(new OutputVar(outputVar, /* initialized= */ true));
  }
}
