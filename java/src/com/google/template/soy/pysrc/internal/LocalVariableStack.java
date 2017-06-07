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

package com.google.template.soy.pysrc.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.internal.UniqueNameGenerator;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyListExpr;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.LogNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.LocalVarNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.defn.TemplateParam;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class tracks the mappings of local variable names (and foreach-loop special functions) to
 * their respective Python expressions. It enables scoped resolution of variables in places such as
 * inside function calls and loops.
 */
final class LocalVariableStack {
  private static final ImmutableSet<String> PYTHON_LITERALS =
      ImmutableSet.of("True", "False", "None");

  // http://docs.python.org/reference/lexical_analysis.html#keywords
  private static final ImmutableSet<String> PYTHON_KEYWORDS =
      ImmutableSet.of(
          "and",
          "as",
          "assert",
          "break",
          "class",
          "continue",
          "def",
          "del",
          "elif",
          "else",
          "except",
          "exec",
          "finally",
          "for",
          "from",
          "global",
          "if",
          "import",
          "in",
          "is",
          "lambda",
          "not",
          "or",
          "pass",
          "print",
          "raise",
          "return",
          "try",
          "while",
          "with",
          "yield");

  // https://docs.python.org/2/library/functions.html
  private static final ImmutableSet<String> PYTHON_GLOBALS =
      ImmutableSet.of(
          // builtin functions
          "abs",
          "divmod",
          "input",
          "open",
          "staticmethod",
          "all",
          "enumerate",
          "int",
          "ord",
          "str",
          "any",
          "eval",
          "isinstance",
          "pow",
          "sum",
          "basestring",
          "execfile",
          "issubclass",
          "print",
          "super",
          "bin",
          "file",
          "iter",
          "property",
          "tuple",
          "bool",
          "filter",
          "len",
          "range",
          "type",
          "bytearray",
          "float",
          "list",
          "raw_input",
          "unichr",
          "callable",
          "format",
          "locals",
          "reduce",
          "unicode",
          "chr",
          "frozenset",
          "long",
          "reload",
          "vars",
          "classmethod",
          "getattr",
          "map",
          "repr",
          "xrange",
          "cmp",
          "globals",
          "max",
          "reversed",
          "zip",
          "compile",
          "hasattr",
          "memoryview",
          "round",
          "__import__",
          "complex",
          "hash",
          "min",
          "set",
          "delattr",
          "help",
          "next",
          "setattr",
          "dict",
          "hex",
          "object",
          "slice",
          "dir",
          "id",
          "oct",
          "sorted",
          // builtin types
          "ArithmeticError",
          "AssertionError",
          "AttributeError",
          "BaseException",
          "BufferError",
          "BytesWarning",
          "DeprecationWarning",
          "EOFError",
          "Ellipsis",
          "EnvironmentError",
          "Exception",
          "False",
          "FloatingPointError",
          "FutureWarning",
          "GeneratorExit",
          "IOError",
          "ImportError",
          "ImportWarning",
          "IndentationError",
          "IndexError",
          "KeyError",
          "KeyboardInterrupt",
          "LookupError",
          "MemoryError",
          "NameError",
          "None",
          "NotImplemented",
          "NotImplementedError",
          "OSError",
          "OverflowError",
          "PendingDeprecationWarning",
          "ReferenceError",
          "RuntimeError",
          "RuntimeWarning",
          "StandardError",
          "StopIteration",
          "SyntaxError",
          "SyntaxWarning",
          "SystemError",
          "SystemExit",
          "TabError",
          "True",
          "TypeError",
          "UnboundLocalError",
          "UnicodeDecodeError",
          "UnicodeEncodeError",
          "UnicodeError",
          "UnicodeTranslateError",
          "UnicodeWarning",
          "UserWarning",
          "ValueError",
          "Warning",
          "ZeroDivisionError",
          "__debug__",
          "__doc__",
          "__name__",
          "__package__");

  /**
   * A VarKey is a logical reference to a local variable. This allows us to abstract the identity of
   * a local variable from the particular name that is selected for it.
   *
   * <p>This object should be treated as an opaque token.
   *
   * <p>TODO(lukes): extract and share with jssrc?
   */
  @AutoValue
  abstract static class VarKey {

    /** Creates the standard top level output variable. */
    static VarKey createOutputVar(TemplateNode declaringNode) {
      return createSyntheticVariable("output", declaringNode);
    }

    /** Creates a temporary output variable needed for the given node. */
    static VarKey createOutputVar(CallParamContentNode declaringNode) {
      return createSyntheticVariable("param_" + declaringNode.getKey().identifier(), declaringNode);
    }
    /** Creates a temporary output variable needed for the given node. */
    static VarKey createOutputVar(LogNode declaringNode) {
      return createSyntheticVariable("log", declaringNode);
    }

    /** Creates a key for a local variable. */
    static VarKey createLocalVar(LocalVarNode localVar) {
      return create(localVar.getVarName(), localVar, Type.USER_DEFINED);
    }

    /** Creates a key for referencing the {@code foreach} node list expression. */
    static VarKey createListVar(ForeachNonemptyNode loopNode) {
      return createSyntheticVariable(loopNode.getVarName() + "List", loopNode);
    }

    /** Creates a key for referencing current index of the foreach loop. */
    static VarKey createIndexVar(ForeachNonemptyNode loopNode) {
      return createSyntheticVariable(loopNode.getVarName() + "Index", loopNode);
    }

    /**
     * A synthetic variable is a variable that is needed by the code generation but does not
     * correspond to a user defined symbol.
     *
     * <p>Many synthetic variables that are referneced multiple times have dedicated constructors
     * below.
     */
    private static VarKey createSyntheticVariable(String desiredName, SoyNode declaringNode) {
      return create(desiredName, declaringNode, Type.SYNTHETIC);
    }

    private static VarKey create(String desiredName, SoyNode owner, Type type) {
      return new AutoValue_LocalVariableStack_VarKey(desiredName, checkNotNull(owner), null, type);
    }

    enum Type {
      USER_DEFINED,
      SYNTHETIC
    }

    abstract String desiredName();

    abstract SoyNode declaringNode();

    @Nullable
    abstract TemplateParam param();

    @Nullable
    abstract Type type();
  }

  // python 2.x has fairly limited support for identifiers.  3.x expanded it greatly but we can't
  // depend on all users using 3.x
  private static final CharMatcher DANGEROUS_CHARACTERS =
      CharMatcher.inRange('a', 'z')
          .or(CharMatcher.inRange('A', 'Z'))
          .or(CharMatcher.inRange('0', '9'))
          .or(CharMatcher.is('_'))
          .negate()
          .precomputed();

  private final Map<VarKey, String> keyToName = new HashMap<>();
  private final Deque<Map<VarKey, PyExpr>> localVarExprs = new ArrayDeque<>();
  private final UniqueNameGenerator nameGenerator;

  LocalVariableStack() {
    nameGenerator = new UniqueNameGenerator(DANGEROUS_CHARACTERS, "___");
    nameGenerator.reserve(PYTHON_LITERALS);
    nameGenerator.reserve(PYTHON_GLOBALS);
    nameGenerator.reserve(PYTHON_KEYWORDS);
  }
  /**
   * Adds a new reference frame to the stack. This should be used when entering a new scope, such as
   * a function or loop.
   */
  void pushFrame() {
    localVarExprs.push(new HashMap<VarKey, PyExpr>());
  }

  /** Removes a reference frame from the stack, typically used when leaving some scope. */
  void popFrame() {
    localVarExprs.pop();
  }

  /** Allocates a name for the given variable. */
  String createVariable(VarKey key) {
    String name = nameGenerator.generateName(key.desiredName());
    String oldName = keyToName.put(key, name);
    if (oldName != null) {
      throw new IllegalStateException(key + " already had a name allocated");
    }
    return name;
  }

  /**
   * Adds a variable to the current reference frame.
   *
   * @param name The name of the variable as used by calling expressions.
   * @param varExpression The underlying expression used to access the variable.
   * @return A reference to this object.
   */
  LocalVariableStack addVariable(VarKey name, PyExpr varExpression) {
    Preconditions.checkState(!localVarExprs.isEmpty());
    localVarExprs.peek().put(name, varExpression);
    return this;
  }

  /**
   * Adds a variable to the current reference frame.
   *
   * @param key The name of the variable as used by calling expressions.
   * @return A reference to this object.
   */
  LocalVariableStack addVariable(VarKey key) {
    Preconditions.checkState(!localVarExprs.isEmpty());
    String name = keyToName.get(key);
    if (name == null) {
      throw new IllegalArgumentException(key + " has not had a name allocated");
    }
    localVarExprs.peek().put(key, new PyExpr(name, Integer.MAX_VALUE));
    return this;
  }

  /**
   * Adds a variable to the current reference frame.
   *
   * @param key The name of the variable as used by calling expressions.
   * @return A reference to this object.
   */
  LocalVariableStack addListVariable(VarKey key) {
    Preconditions.checkState(!localVarExprs.isEmpty());
    String name = keyToName.get(key);
    if (name == null) {
      throw new IllegalArgumentException(key + " has not had a name allocated");
    }
    localVarExprs.peek().put(key, new PyListExpr(name, Integer.MAX_VALUE));
    return this;
  }

  /**
   * Retrieves the Python expression for a given variable name. The stack is traversed from top to
   * bottom, giving the tightest scope the highest priority.
   *
   * @param variableName The name of the variable.
   * @return The translated expression, or null if not found.
   */
  @Nullable
  PyExpr getVariableExpression(VarKey variableName) {
    for (Map<VarKey, PyExpr> frame : localVarExprs) {
      PyExpr translation = frame.get(variableName);
      if (translation != null) {
        return translation;
      }
    }
    return null;
  }
}
