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

import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.internal.JsRuntime.createHtmlOutputBufferFunction;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.FormatOptions;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Keeps track of the current output variable and generates code for declaring and assigning it. */
public final class OutputVarHandler {

  enum Style {
    // String concatenation.
    APPENDING,

    // Uses an array buffer and deferred calls via NodeBuilder.
    LAZY
  };

  /**
   * An output variable name that is pushed onto the stack only for evaluation of content param
   * nodes which don't need to emit output var statements. Only used for the Style to control
   * generation of lazy calls. Will throw an exception if user tries to emit output var statements.
   */
  private static final Expression EVAL_ONLY = id("__for_eval_only_never_used_");

  private static final class OutputVar {
    // TODO(b/32224284): this is always an {@link Expression#id}. Consider exposing a subclass of
    // CodeChunk so we can enforce this invariant at compile time.
    final Expression name;
    final boolean initialized;
    final Style style;

    OutputVar(Expression name, boolean initialized, Style style) {
      this.name = name;
      this.initialized = initialized;
      this.style = style;
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

  public static OutputVarHandler.Style outputStyleForBlock(RenderUnitNode node) {
    // Only html blocks might need the output buffer.
    if (node.getContentKind() != SanitizedContentKind.HTML
        && node.getContentKind() != SanitizedContentKind.HTML_ELEMENT) {
      return OutputVarHandler.Style.APPENDING;
    }
    // Use the buffer if there is any html call that we'll need to lazy execute.
    for (CallBasicNode call : SoyTreeUtils.getAllNodesOfType(node, CallBasicNode.class)) {
      if (call.isLazy()) {
        return OutputVarHandler.Style.LAZY;
      }
    }
    // Use the buffer if there are any html prints, since they could have html calls inside.
    for (PrintNode print : SoyTreeUtils.getAllNodesOfType(node, PrintNode.class)) {
      if (print.isHtml()) {
        return OutputVarHandler.Style.LAZY;
      }
    }

    return OutputVarHandler.Style.APPENDING;
  }

  public Style currentOutputVarStyle() {
    OutputVar currentVar = currentOutputVar();
    return currentVar != null ? currentVar.style : Style.APPENDING;
  }

  public Optional<Statement> initOutputVarIfNecessary() {
    assertNotEvalOnly();
    if (!outputVars.isEmpty() && currentOutputVar().initialized) {
      // Nothing to do since it's already initialized.
      return Optional.empty();
    }
    return Optional.of(
        currentOutputVar().style == Style.APPENDING
            ? initOutputVarAppending(
                currentOutputVar().name.assertExpr().getText(), Expressions.LITERAL_EMPTY_STRING)
            : initOutputVarLazy(
                currentOutputVar().name.assertExpr().getText(), ImmutableList.of()));
  }

  private VariableDeclaration initOutputVarAppending(String name, Expression rhs) {
    assertNotEvalOnly();
    setOutputVarInited();
    return VariableDeclaration.builder(name).setMutable().setRhs(rhs).build();
  }

  private VariableDeclaration initOutputVarLazy(String name, ImmutableList<Expression> rhs) {
    assertNotEvalOnly();
    setOutputVarInited();
    return VariableDeclaration.builder(name)
        .setRhs(createHtmlOutputBufferFunction().call(rhs))
        .build();
  }

  /** Appends the given code chunk to the current output variable. */
  public Statement addChunkToOutputVar(Expression chunk) {
    assertNotEvalOnly();
    return addChunksToOutputVar(ImmutableList.of(chunk));
  }

  /**
   * Appends one or more lines representing the concatenation of the values of the given code chunks
   * saved to the current output variable.
   */
  public Statement addChunksToOutputVar(List<? extends Expression> codeChunks) {
    assertNotEvalOnly();
    return currentOutputVar().style == Style.APPENDING
        ? addChunksToOutputVarAppending(codeChunks)
        : addChunksToOutputVarLazy(codeChunks);
  }

  private Statement addChunksToOutputVarAppending(List<? extends Expression> codeChunks) {
    assertNotEvalOnly();
    if (currentOutputVar().initialized) {
      Expression rhs = Expressions.concat(codeChunks);
      return currentOutputVar().name.plusEquals(rhs).asStatement();
    } else {
      Expression rhs = Expressions.concatForceString(codeChunks);
      return initOutputVarAppending(
          currentOutputVar().name.singleExprOrName(FormatOptions.JSSRC).getText(), rhs);
    }
  }

  private Statement addChunksToOutputVarLazy(List<? extends Expression> codeChunks) {
    assertNotEvalOnly();
    Expression rhs = Expressions.concat(codeChunks);
    if (currentOutputVar().initialized) {
      return currentOutputVar().name.dotAccess("append").call(rhs).asStatement();
    } else {
      return initOutputVarLazy(
          currentOutputVar().name.singleExprOrName(FormatOptions.JSSRC).getText(),
          ImmutableList.of(rhs));
    }
  }

  /**
   * Pushes on a new current output variable using string concatenation.
   *
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    pushOutputVar(outputVarName, Style.APPENDING);
  }

  public void pushOutputVar(String outputVarName, Style style) {
    outputVars.push(new OutputVar(id(outputVarName), false, style));
  }

  /**
   * Pushes a new output var with specified style. When in this state no output var statements can
   * be emitted, the output var can only be used to determine the correct style.
   */
  public void pushOutputVarForEvalOnly(Style style) {
    outputVars.push(new OutputVar(EVAL_ONLY, false, style));
  }

  private void assertNotEvalOnly() {
    if (currentOutputVar().name == EVAL_ONLY) {
      throw new AssertionError("Internal Error: OutputVar used after pushOutputVarForEvalOnly()");
    }
  }

  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  @CanIgnoreReturnValue
  public Expression popOutputVar() {
    OutputVar outputVar = outputVars.pop();
    return outputVar.style == Style.APPENDING
        ? outputVar.name
        : outputVar.name.dotAccess("render").call();
  }

  /**
   * Tells this handler that the current output variable has already been initialized. This causes
   * {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization code even
   * on the first use of the variable.
   */
  public void setOutputVarInited() {
    OutputVar outputVar = outputVars.pop();
    outputVars.push(new OutputVar(outputVar.name, /* initialized= */ true, outputVar.style));
  }
}
