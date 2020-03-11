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

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.jssrc.dsl.Expression.id;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.CodeChunkUtils;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.jssrc.dsl.JsDoc;
import com.google.template.soy.jssrc.dsl.VariableDeclaration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;

/**
 * A JavaScript implementation of the CodeBuilder class.
 *
 * <p>Usage example that demonstrates most of the methods:
 * <pre>
 *   JsCodeBuilder jcb = new JsCodeBuilder(CodeStyle.STRINGBUILDER);
 *   jcb.appendLine("story.title = function(opt_data) {");
 *   jcb.increaseIndent();
 *   jcb.pushOutputVar("output");
 *   jcb.initOutputVarIfNecessary();
 *   jcb.pushOutputVar("temp");
 *   jcb.addToOutputVar(Lists.newArrayList(
 *       new JsExpr("'Snow White and the '", Integer.MAX_VALUE),
 *       new JsExpr("opt_data.numDwarfs", Integer.MAX_VALUE));
 *   jcb.popOutputVar();
 *   jcb.addToOutputVar(Lists.newArrayList(
 *       new JsExpr("temp", Integer.MAX_VALUE),
 *       new JsExpr("' Dwarfs'", Integer.MAX_VALUE));
 *   jcb.appendLineStart("return ").appendOutputVarName().appendLineEnd(".toString();");
 *   jcb.popOutputVar();
 *   jcb.decreaseIndent();
 *   String THE_END = "the end";
 *   jcb.appendLine("}  // ", THE_END);
 * </pre>
 *
 * <p>The above example builds the following JS code:
 * <pre>
 * story.title = function(opt_data) {
 *   var output = new soy.StringBuilder();
 *   var temp = new soy.StringBuilder('Snow White and the ', opt_data.numDwarfs);
 *   output.append(temp, ' Dwarfs');
 *   return output.toString();
 * }  // the end
 * </pre>
 *
 */
public class JsCodeBuilder {

  private static final class OutputVar {
    final Expression name;
    final boolean initialized;

    OutputVar(Expression name, boolean initialized) {
      this.name = name;
      this.initialized = initialized;
    }
  }

  /** The size of a single indent level. */
  private static final int INDENT_SIZE = 2;

  /** A buffer to accumulate the generated code. */
  private final StringBuilder code;
  /** The current stack of output variables. */
  private final Deque<OutputVar> outputVars;

  /** The current indent (some even number of spaces). */
  private String indent;

  // the set of symbols to require, indexed by symbol name to detect conflicting imports
  private final Map<String, GoogRequire> googRequires = new TreeMap<>();

  /**
   * The current output variable.
   *
   * <p>TODO(b/32224284): this is always an {@link Expression#id}. Consider exposing a subclass of
   * CodeChunk so we can enforce this invariant at compile time.
   */
  @Nullable protected Expression currOutputVar;

  /** Whether the current output variable is initialized. */
  private boolean currOutputVarIsInited;

  protected JsCodeBuilder() {
    code = new StringBuilder();
    indent = "";
    outputVars = new ArrayDeque<>();
    currOutputVar = null;
    currOutputVarIsInited = false;
  }

  protected JsCodeBuilder(JsCodeBuilder parent) {
    code = new StringBuilder();
    indent = parent.indent;
    outputVars = parent.outputVars;
    currOutputVar = parent.currOutputVar;
    currOutputVarIsInited = parent.currOutputVarIsInited;
  }

  Iterable<GoogRequire> googRequires() {
    return googRequires.values();
  }

  public void initOutputVarIfNecessary() {

    if (currOutputVarIsInited) {
      // Nothing to do since it's already initialized.
      return;
    }

    // let output = '';
    appendLine(
        "let ",
        // Don't tell the chunk about the current indent level.
        // We're in the middle of a line!
        currOutputVar.assertExpr().getText(),
        " = '';");
    setOutputVarInited();
  }

  /** Appends the given code chunk to the current output variable. */
  public JsCodeBuilder addChunkToOutputVar(Expression chunk) {
    return addChunksToOutputVar(ImmutableList.of(chunk));
  }

  /**
   * Appends one or more lines representing the concatenation of the values of the given code chunks
   * saved to the current output variable.
   */
  public JsCodeBuilder addChunksToOutputVar(List<? extends Expression> codeChunks) {
    if (currOutputVarIsInited) {
      Expression rhs = CodeChunkUtils.concatChunks(codeChunks);
      rhs.collectRequires(this::addGoogRequire);
      appendLine(currOutputVar.plusEquals(rhs).getCode());
    } else {
      Expression rhs = CodeChunkUtils.concatChunksForceString(codeChunks);
      rhs.collectRequires(this::addGoogRequire);
      append(
          VariableDeclaration.builder(currOutputVar.singleExprOrName().getText())
              .setMutable()
              .setRhs(rhs)
              .build());
      setOutputVarInited();
    }
    return this;
  }

  /** Increases the current indent. */
  public final JsCodeBuilder increaseIndent() {
    return changeIndentHelper(1);
  }

  /** Increases the current indent twice. */
  public final JsCodeBuilder increaseIndentTwice() {
    return changeIndentHelper(2);
  }

  /** Decreases the current indent. */
  public final JsCodeBuilder decreaseIndent() {
    return changeIndentHelper(-1);
  }

  /** Decreases the current indent twice. */
  public final JsCodeBuilder decreaseIndentTwice() {
    return changeIndentHelper(-2);
  }

  /**
   * Helper for the various indent methods.
   * @param chg The number of indent levels to change.
   */
  private JsCodeBuilder changeIndentHelper(int chg) {
    int newIndentDepth = indent.length() + chg * INDENT_SIZE;
    checkState(newIndentDepth >= 0);
    indent = Strings.repeat(" ", newIndentDepth);
    return this;
  }

  void setIndent(int indentCt) {
    this.indent = Strings.repeat(" ", indentCt);
  }

  /**
   * Pushes on a new current output variable.
   * @param outputVarName The new output variable name.
   */
  public JsCodeBuilder pushOutputVar(String outputVarName) {
    currOutputVar = id(outputVarName);
    outputVars.push(new OutputVar(currOutputVar, false));
    currOutputVarIsInited = false;
    return this;
  }

  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  public JsCodeBuilder popOutputVar() {
    outputVars.pop();
    OutputVar top = outputVars.peek();  // null if outputVars is now empty
    if (top != null) {
      currOutputVar = top.name;
      currOutputVarIsInited = top.initialized;
    } else {
      currOutputVar = null;
      currOutputVarIsInited = false;
    }
    return this;
  }

  /**
   * Tells this CodeBuilder that the current output variable has already been initialized. This
   * causes {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization
   * code even on the first use of the variable.
   */
  public JsCodeBuilder setOutputVarInited() {
    outputVars.pop();
    outputVars.push(new OutputVar(currOutputVar, /* initialized= */ true));
    currOutputVarIsInited = true;
    return this;
  }

  /**
   * Serializes the given {@link CodeChunk} into the code builder, respecting the code builder's
   * current indentation level.
   */
  public JsCodeBuilder append(CodeChunk codeChunk) {
    codeChunk.collectRequires(this::addGoogRequire);
    return append(codeChunk.getStatementsForInsertingIntoForeignCodeAtIndent(indent.length()));
  }

  /**
   * Serializes the given {@link JsDoc} into the code builder, respecting the code builder's current
   * indentation level.
   */
  public JsCodeBuilder append(JsDoc jsDoc) {
    jsDoc.collectRequires(this::addGoogRequire);
    return appendLine(jsDoc.toString());
  }

  /**
   * Appends one or more strings to the generated code.
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public JsCodeBuilder append(String... codeFragments) {
    for (String codeFragment : codeFragments) {
      code.append(codeFragment);
    }
    return this;
  }

  /**
   * Appends the current indent, then the given strings, then a newline.
   * @param codeFragments The code string(s) to append.
   * @return This CodeBuilder (for stringing together operations).
   */
  public JsCodeBuilder appendLine(String... codeFragments) {
    code.append(indent);
    append(codeFragments);
    code.append("\n");
    return this;
  }

  /**
   * Adds a {@code goog.require}
   *
   * @param require The namespace being required
   */
  public void addGoogRequire(GoogRequire require) {
    GoogRequire oldRequire = googRequires.put(require.symbol(), require);
    if (oldRequire != null) {
      googRequires.put(require.symbol(), require.merge(oldRequire));
    }
  }

  /** Should only be used by {@link GenJsCodeVisitor#visitSoyFileNode}. */
  void appendGoogRequiresTo(StringBuilder sb) {
    for (GoogRequire require : googRequires.values()) {
      // TODO(lukes): we need some namespace management here... though really we need namespace
      // management with all declarations... The problem is that a require could introduce a name
      // alias that conflicts with a symbol defined elsewhere in the file.
      require.writeTo(sb);
    }
  }

  /**
   * @return The generated code.
   */
  public String getCode() {
    return code.toString();
  }

  /** Appends the code accumulated in this builder to the given {@link StringBuilder}. */
  void appendCodeTo(StringBuilder sb) {
    sb.append(code);
  }
}
