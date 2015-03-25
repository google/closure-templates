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

import com.google.common.base.Optional;
import com.google.template.soy.base.SourceLocation;

import org.objectweb.asm.Label;
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

  // Optional because there will be many situations where having a source location is not possible
  // e.g. NULL_STATEMENT.  In general we should attempt to associate source locations whenever we
  // have one.  
  // TODO(lukes): when exprnodes get support for SourceLocation, move this optional sourcelocation
  // support up into BytecodeProcessor.
  private final Optional<SourceLocation> location;
  private final Label start;
  private final Label end;

  Statement() {
    this(Optional.<SourceLocation>absent());
  }

  // when UNKNOWN source locations go away, so can this use of isKnown
  @SuppressWarnings("deprecation")
  Statement(SourceLocation location) {
    this(location.isKnown() ? Optional.of(location) : Optional.<SourceLocation>absent());
  }

  private Statement(Optional<SourceLocation> location) {
    this.location = location;
    this.start = new Label();
    this.end = new Label();
  }

  /** The label at the begining of the statement. */
  public final Label start() {
    return start;
  }

  /** The label at the end of the statement. */
  public final Label end() {
    return end;
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

  /** Generate code to implement the statement. */
  @Override final void gen(GeneratorAdapter adapter) {
    adapter.mark(start);
    doGen(adapter);
    adapter.mark(end);
    if (location.isPresent()) {
      // These add entries to the line number tables that are associated with the current method.
      // The line number table is just a mapping of of bytecode offset (aka 'pc') to line number,
      // http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.12
      // It is used by the JVM to add source data to stack traces and by debuggers to highlight
      // source files.
      adapter.visitLineNumber(location.get().getLineNumber(), start);
      adapter.visitLineNumber(location.get().getEndLine(), end);
    }
  }

  abstract void doGen(GeneratorAdapter adapter);

  @Override public String toString() {
    return "Statement:\n" + trace();
  }
}
