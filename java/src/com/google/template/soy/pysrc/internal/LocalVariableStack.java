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

import com.google.common.base.Preconditions;
import com.google.template.soy.pysrc.restricted.PyExpr;
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

  private final Deque<Map<String, PyExpr>> localVarExprs = new ArrayDeque<>();

  /**
   * Adds a new reference frame to the stack. This should be used when entering a new scope, such as
   * a function or loop.
   */
  void pushFrame() {
    localVarExprs.push(new HashMap<>());
  }

  /** Removes a reference frame from the stack, typically used when leaving some scope. */
  void popFrame() {
    localVarExprs.pop();
  }

  /**
   * Adds a variable to the current reference frame.
   *
   * @param name The name of the variable as used by calling expressions.
   * @param varExpression The underlying expression used to access the variable.
   * @return A reference to this object.
   */
  LocalVariableStack addVariable(String name, PyExpr varExpression) {
    Preconditions.checkState(!localVarExprs.isEmpty());
    localVarExprs.peek().put(name, varExpression);
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
  PyExpr getVariableExpression(String variableName) {
    for (Map<String, PyExpr> frame : localVarExprs) {
      PyExpr translation = frame.get(variableName);
      if (translation != null) {
        return translation;
      }
    }
    return null;
  }
}
