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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.template.soy.base.SourceLocation;

import org.objectweb.asm.commons.GeneratorAdapter;

import java.util.Arrays;

/**
 * A statement models a section of code.  Unlike {@linkplain Expression expressions}, statements
 * may have side effects.  For example, the Soy code {@code {'foo'}} could be implemented as a 
 * statement that writes to an output stream.
 *
 * <p>The generated code should satisfy the invariant that the runtime stack and local variables
 * are are unchanged by the generated code (i.e. the frame map at the start of the statement is
 * identical to the frame map at the end of the statement).
 */
abstract class Statement extends BytecodeProducer {
  static final Statement NULL_STATEMENT = new Statement() {
    @Override void doGen(GeneratorAdapter adapter) {}
  };

  /** Returns a statement that concatenates all the provided statements. */
  static Statement concat(Statement ...statements) {
    return concat(Arrays.asList(statements));
  }

  /** Returns a statement that concatenates all the provided statements. */
  static Statement concat(final Iterable<? extends Statement> statements) {
    checkNotNull(statements);
    return new Statement() {
      @Override void doGen(GeneratorAdapter adapter) {
        for (Statement statement : statements) {
          statement.gen(adapter);
        }
      }
    };
  }

  Statement() {
    super();
  }

  Statement(SourceLocation location) {
    super(location);
  }

  /**
   * Returns a new {@link Statement} with the source location attached.
   */
  public final Statement withSourceLocation(SourceLocation location) {
    checkNotNull(location);
    return new Statement(location) {
      @Override void doGen(GeneratorAdapter adapter) {
        Statement.this.doGen(adapter);
      }
    };
  }

  @Override public String toString() {
    return "Statement:\n" + trace();
  }
}
