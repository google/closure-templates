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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.template.soy.jbcsrc.restricted.BytecodeUtils.THROWABLE_TYPE;

import com.google.template.soy.base.SourceLocation;
import java.io.IOException;
import java.util.Arrays;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * A statement models a section of code. Unlike {@linkplain Expression expressions}, statements may
 * have side effects. For example, the Soy code {@code {'foo'}} could be implemented as a statement
 * that writes to an output stream.
 *
 * <p>The generated code should satisfy the invariant that the runtime stack and local variables are
 * are unchanged by the generated code (i.e. the frame map at the start of the statement is
 * identical to the frame map at the end of the statement).
 */
public abstract class Statement extends BytecodeProducer {
  private static final Type[] IO_EXCEPTION_ARRAY = new Type[] {Type.getType(IOException.class)};

  public static final Statement NULL_STATEMENT =
      new Statement() {
        @Override
        protected void doGen(CodeBuilder adapter) {}
      };

  public static final Statement RETURN =
      new Statement() {
        @Override
        protected void doGen(CodeBuilder adapter) {
          adapter.returnValue();
        }
      };

  /**
   * Generates a statement that returns the value produced by the given expression.
   *
   * <p>This does not validate that the return type is appropriate. It is our callers responsibility
   * to do that.
   */
  public static Statement returnExpression(final Expression expression) {
    // TODO(lukes): it would be nice to do a checkType operation here to make sure that expression
    // is compatible with the return type of the method, but i don't know how to get that
    // information here (reasonably).  So it is the caller's responsibility.
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expression.gen(adapter);
        adapter.returnValue();
      }
    };
  }

  /**
   * Generates a statement that throws the throwable produced by the given expression.
   *
   * <p>This does not validate that the throwable is compatible with the methods throws clause.
   */
  public static Statement throwExpression(final Expression expression) {
    expression.checkAssignableTo(THROWABLE_TYPE);
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        expression.gen(adapter);
        adapter.throwException();
      }
    };
  }

  /** Returns a statement that concatenates all the provided statements. */
  public static Statement concat(Statement... statements) {
    return concat(Arrays.asList(statements));
  }

  /** Returns a statement that concatenates all the provided statements. */
  public static Statement concat(final Iterable<? extends Statement> statements) {
    checkNotNull(statements);
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        for (Statement statement : statements) {
          statement.gen(adapter);
        }
      }
    };
  }

  protected Statement() {
    super();
  }

  protected Statement(SourceLocation location) {
    super(location);
  }

  /**
   * Writes this statement to the {@link ClassVisitor} as a method.
   *
   * @param access The access modifiers of the method
   * @param method The method signature
   * @param visitor The class visitor to write it to
   */
  public final void writeMethod(int access, Method method, ClassVisitor visitor) {
    writeMethodTo(new CodeBuilder(access, method, null, visitor));
  }

  /**
   * Writes this statement to the {@link ClassVisitor} as a method.
   *
   * @param access The access modifiers of the method
   * @param method The method signature
   * @param visitor The class visitor to write it to
   */
  public final void writeIOExceptionMethod(int access, Method method, ClassVisitor visitor) {
    writeMethodTo(new CodeBuilder(access, method, IO_EXCEPTION_ARRAY, visitor));
  }

  /** Writes this statement as the complete method body to {@code ga}. */
  private final void writeMethodTo(CodeBuilder builder) {
    builder.visitCode();
    gen(builder);
    try {
      builder.endMethod();
    } catch (Throwable t) {
      // ASM fails in bizarre ways, attach a trace of the thing we tried to generate to the
      // exception.
      throw new RuntimeException("Failed to generate method:\n" + this, t);
    }
  }
  /**
   * Returns a new statement identical to this one but with the given label applied at the start of
   * the statement.
   */
  public final Statement labelStart(final Label label) {
    return new Statement() {
      @Override
      protected void doGen(CodeBuilder adapter) {
        adapter.mark(label);
        Statement.this.gen(adapter);
      }
    };
  }

  /** Returns a new {@link Statement} with the source location attached. */
  public final Statement withSourceLocation(SourceLocation location) {
    checkNotNull(location);
    if (location.equals(this.location)) {
      return this;
    }
    return new Statement(location) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Statement.this.gen(adapter);
      }
    };
  }

  /** Returns an Expression that evaluates this statement followed by the given expression. */
  final Expression then(final Expression expression) {
    return new Expression(expression.resultType(), expression.features()) {
      @Override
      protected void doGen(CodeBuilder adapter) {
        Statement.this.gen(adapter);
        expression.gen(adapter);
      }
    };
  }

  @Override
  public String toString() {
    return "Statement:\n" + trace();
  }
}
