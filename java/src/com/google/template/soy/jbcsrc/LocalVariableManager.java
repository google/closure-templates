/*
 * Copyright 2019 Google Inc.
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

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.template.soy.jbcsrc.restricted.CodeBuilder;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.LocalVariable;
import com.google.template.soy.jbcsrc.restricted.Statement;
import org.objectweb.asm.Type;

/** A class that can manage local variable lifetimes for a method. */
@CheckReturnValue
interface LocalVariableManager {
  /**
   * Creates a new scope for local variables.
   *
   * <p>Scopes should not be conserved. Assigning a scope to every local is reasonable.
   */
  Scope enterScope();
  /**
   * Looks up a user defined variable with the given name. The variable must have been created in a
   * currently active scope.
   */
  Expression getVariable(String name);

  /** Write a local variable table entry for every registered variable. */
  void generateTableEntries(CodeBuilder ga);

  interface Scope {

    /**
     * Adds a variable into the local scope with a generated name.
     *
     * <p>It is the responsibility of the caller to ensure that {@link LocalVariable#start} is
     * visited prior to the first use of this variable.
     */
    LocalVariable createTemporary(String proposedName, Type type);

    /**
     * Adds a variable into the local scope with the given name.
     *
     * <p>It is the responsibility of the caller to ensure that {@link LocalVariable#start} is
     * visited prior to the first use of this variable.
     *
     * <p>These variablles can be looked up with {@link LocalVariableManager#getNamedLocal}.
     */
    LocalVariable createNamedLocal(String name, Type type);

    /**
     * Exits the scope. After this point it is incorrect to reference the local variables. The
     * returned Statement must be generated after the locals go out of scope.
     */
    Statement exitScope();
  }
}
