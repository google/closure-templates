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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.jssrc.dsl.Expressions.id;
import static com.google.template.soy.jssrc.internal.JsRuntime.createHtmlOutputBufferFunction;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.dsl.Statement;
import com.google.template.soy.jssrc.dsl.Statements;
import com.google.template.soy.jssrc.dsl.StringLiteral;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Keeps track of the current output variable and generates code for declaring and assigning it. */
public class OutputVarHandler {

  public static final OutputVarHandler DISALLOWED =
      new OutputVarHandler() {
        @Override
        public Optional<Statement> initOutputVarIfNecessary() {
          return Optional.empty();
        }

        @Override
        public Statement addPartsToOutputVar(ImmutableList<Part> parts) {
          throw new UnsupportedOperationException("Output not allowed");
        }
      };

  enum InitializedState {
    // not in scope
    UNDECLARED,

    // declared, but with no initializer, possibly in an outer scope. If it was declared in an outer
    // scope redeclaring it will create a new variable, not refer to one in the outer scope, so we
    // need to use assignment to initialize it.
    DECLARED,

    // All done, ready to add content.
    INITIALIZED
  }

  /** A style that is tied to a specific output var. */
  enum Style {
    // String concatenation.
    APPENDING,

    // Uses an array buffer and deferred calls and print directives via NodeBuilder.
    LAZY
  };

  private static final class OutputVar {
    // TODO(b/32224284): this is always an {@link Expression#id}. Consider exposing a subclass of
    // CodeChunk so we can enforce this invariant at compile time.
    final Expression name;
    final InitializedState initialized;
    final Style style;

    OutputVar(Expression name, InitializedState initialized, Style style) {
      this.name = name;
      this.initialized = initialized;
      this.style = style;
    }
  }

  /**
   * An output variable name that is pushed onto the stack when evaluating expressions that don't
   * need to emit output var statements -- we still need to know the current output style to know if
   * we should use NodeBuilders, or defer print directives, &c. Will throw an exception if user
   * tries to emit output var statements.
   */
  private static final Expression EVAL_ONLY = id("__for_eval_only_never_used_");

  /**
   * A StyleBranchState is a global setting that enables or disables lazy output var style. When
   * generating an if/else with lazy vs appending output styles, set it before evaluating each of
   * the branches.
   */
  enum StyleBranchState {
    ALLOW,
    DISALLOW,
  };

  private StyleBranchState currentStyleBranchState;

  /** The current stack of output variables. */
  private final Deque<OutputVar> outputVars;

  OutputVarHandler() {
    outputVars = new ArrayDeque<>();
    currentStyleBranchState = null;
  }

  private OutputVar currentOutputVar() {
    return outputVars.peek();
  }

  public boolean shouldBranch(RenderUnitNode node) {
    return currentStyleBranchState == null && outputStyleForBlock(node) == Style.LAZY;
  }

  public void enterBranch(StyleBranchState state) {
    if (currentStyleBranchState != null) {
      throw new AssertionError("Internal Error: enterBranch with branch already set.");
    }

    this.currentStyleBranchState = state;
  }

  public void exitBranch() {
    if (currentStyleBranchState == null) {
      throw new AssertionError("Internal Error: exitBranch with no branch set.");
    }

    this.currentStyleBranchState = null;
  }

  public OutputVarHandler.Style outputStyleForBlock(RenderUnitNode node) {
    if (currentStyleBranchState == StyleBranchState.DISALLOW) {
      return Style.APPENDING;
    }

    // Only html blocks might need the output buffer.
    if (node.getContentKind() != SanitizedContentKind.HTML
        && node.getContentKind() != SanitizedContentKind.HTML_ELEMENT) {
      return OutputVarHandler.Style.APPENDING;
    }
    // Use the buffer if there is any html call. Even if it's eager, it might have a nested lazy
    // call.
    for (CallBasicNode call : SoyTreeUtils.getAllNodesOfType(node, CallBasicNode.class)) {
      if (call.isHtml()) {
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
    if (outputVars.isEmpty()) {
      throw new AssertionError("Tried to init an output var that hasn't been created.");
    }
    if (currentOutputVar().initialized == InitializedState.INITIALIZED) {
      // Nothing to do since it's already initialized.
      return Optional.empty();
    } else {
      return Optional.of(
          currentOutputVar().style == Style.APPENDING
              ? initOutputVarAppending(Expressions.LITERAL_EMPTY_STRING)
              : initOutputVarLazy());
    }
  }

  private Statement initOutputVarAppending(Expression rhs) {
    assertNotEvalOnly();
    Statement output =
        currentOutputVar().initialized == InitializedState.DECLARED
            ? Statements.assign(currentOutputVar().name, rhs)
            : VariableDeclaration.builder(currentOutputVar().name.assertExpr().getText())
                .setMutable()
                .setRhs(rhs)
                .build();
    setOutputVarInited();
    return output;
  }

  private Statement initOutputVarLazy() {
    assertNotEvalOnly();
    Expression rhs = createHtmlOutputBufferFunction().call();
    Statement output =
        currentOutputVar().initialized == InitializedState.DECLARED
            ? Statements.assign(currentOutputVar().name, rhs)
            : VariableDeclaration.builder(currentOutputVar().name.assertExpr().getText())
                .setRhs(rhs)
                .build();
    setOutputVarInited();
    return output;
  }

  /**
   * A Part is a partition of Expressions that can be concatenated and added in a single add() call
   * when in LAZY mode. When in APPENDING mode, all of the expressions are just concatenated
   * together, ignoring the Part boundaries.
   */
  @AutoValue
  abstract static class Part {
    enum Kind {
      STRING,
      DYNAMIC,
    }

    static Part create(Kind kind, ImmutableList<Expression> exprs) {
      return new AutoValue_OutputVarHandler_Part(kind, exprs);
    }

    abstract Kind kind();

    @Nullable
    abstract ImmutableList<Expression> exprs();

    Expression dynamicExpr() {
      if (kind() != Kind.DYNAMIC) {
        throw new AssertionError("dynamicExpr() called on non-dynamic Part.");
      }
      if (exprs().size() != 1) {
        throw new AssertionError("Part with Kind.DYNAMIC does not have exactly 1 Expression.");
      }
      return exprs().get(0);
    }
  }

  public static Part createStringPart(List<Expression> exprs) {
    return Part.create(Part.Kind.STRING, ImmutableList.copyOf(exprs));
  }

  public static Part createDynamicPart(Expression expr) {
    return Part.create(Part.Kind.DYNAMIC, ImmutableList.of(expr));
  }

  /** Appends the given code chunk to the current output variable. */
  public Statement addChunkToOutputVar(Expression chunk) {
    return addChunksToOutputVar(ImmutableList.of(chunk));
  }

  public Statement addChunksToOutputVar(List<Expression> codeChunks) {
    if (codeChunks.size() == 1 && !(codeChunks.get(0) instanceof StringLiteral)) {
      return addPartsToOutputVar(ImmutableList.of(createDynamicPart(codeChunks.get(0))));
    }
    return addPartsToOutputVar(ImmutableList.of(createStringPart(codeChunks)));
  }

  /**
   * Appends one or more lines representing the concatenation of the values of the given code chunks
   * saved to the current output variable.
   */
  public Statement addPartsToOutputVar(ImmutableList<Part> parts) {
    return currentOutputVar().style == Style.APPENDING
        ? addPartsToOutputVarAppending(parts)
        : addPartsToOutputVarLazy(parts);
  }

  private Statement addPartsToOutputVarAppending(ImmutableList<Part> parts) {
    assertNotEvalOnly();
    ImmutableList<Expression> codeChunks =
        parts.stream().flatMap((p) -> p.exprs().stream()).collect(toImmutableList());
    if (currentOutputVar().initialized == InitializedState.INITIALIZED) {
      Expression rhs = Expressions.concat(codeChunks);
      return currentOutputVar().name.plusEquals(rhs).asStatement();
    } else {
      Expression rhs = Expressions.concatForceString(codeChunks);
      return initOutputVarAppending(rhs);
    }
  }

  private Statement addPartsToOutputVarLazy(ImmutableList<Part> parts) {
    assertNotEvalOnly();
    Expression base =
        currentOutputVar().initialized == InitializedState.INITIALIZED
            ? currentOutputVar().name
            : createHtmlOutputBufferFunction().call();
    Expression chainedCalls = addChainedAddCalls(base, parts);
    Statement stmt;
    if (currentOutputVar().initialized == InitializedState.INITIALIZED) {
      stmt = chainedCalls.asStatement();
    } else {
      if (currentOutputVar().initialized == InitializedState.DECLARED) {
        stmt = Statements.assign(currentOutputVar().name, chainedCalls);
      } else {
        stmt =
            VariableDeclaration.builder(currentOutputVar().name.assertExpr().getText())
                .setRhs(chainedCalls)
                .build();
      }
      setOutputVarInited();
    }
    return stmt;
  }

  private static Expression addChainedAddCalls(Expression base, List<Part> parts) {
    Expression output = base;
    for (Part part : parts) {
      if (part.kind() == Part.Kind.STRING) {
        output = output.dotAccess("addString").call(Expressions.concat(part.exprs()));
      } else {
        output = output.dotAccess("addDynamic").call(part.dynamicExpr());
      }
    }
    return output;
  }

  /**
   * Pushes on a new current output variable using string concatenation.
   *
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    pushOutputVar(outputVarName, Style.APPENDING);
  }

  /** Pushes a new output var with specified output style. */
  public void pushOutputVar(String outputVarName, Style style) {
    outputVars.push(new OutputVar(id(outputVarName), InitializedState.UNDECLARED, style));
  }

  /**
   * Pushes a new output var with specified style. The output var can only be used to determine the
   * style for evaluating expressions. It will throw an error if the output var it self is emitted.
   */
  public void pushOutputVarForEvalOnly(Style style) {
    outputVars.push(new OutputVar(EVAL_ONLY, InitializedState.UNDECLARED, style));
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
    return outputVars.pop().name;
  }

  /**
   * Tells this handler that the current output variable has already been initialized. This causes
   * {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization code even
   * on the first use of the variable.
   */
  public void setOutputVarInited() {
    OutputVar outputVar = outputVars.pop();
    outputVars.push(new OutputVar(outputVar.name, InitializedState.INITIALIZED, outputVar.style));
  }

  /** Tells this handler that the current output variable has already been declared. */
  public void setOutputVarDeclared() {
    OutputVar outputVar = outputVars.pop();
    outputVars.push(new OutputVar(outputVar.name, InitializedState.DECLARED, outputVar.style));
  }

  public static Expression createHtmlArrayBufferExpr(List<Part> parts) {
    Expression expr = createHtmlOutputBufferFunction().call();
    return addChainedAddCalls(expr, parts);
  }
}
