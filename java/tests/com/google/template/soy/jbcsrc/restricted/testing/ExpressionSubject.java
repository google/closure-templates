/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.jbcsrc.restricted.testing;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.BooleanInvoker;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.CharInvoker;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.DoubleInvoker;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.IntInvoker;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.LongInvoker;
import com.google.template.soy.jbcsrc.restricted.testing.ExpressionEvaluator.ObjectInvoker;

/**
 * Truth subject for {@link Expression}.
 *
 * <p>Since {@link Expression expressions} are fully encapsulated we can represent them as simple
 * nullary interface methods. For each expression we will compile an appropriately typed
 * implementation of an invoker interface.
 */
public final class ExpressionSubject extends Subject<ExpressionSubject, Expression> {

  /** Returns a truth subject that can be used to assert on an {@link Expression}. */
  public static ExpressionSubject assertThatExpression(Expression resp) {
    return Truth.assertAbout(ExpressionSubject::new).that(resp);
  }

  private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

  private ExpressionSubject(FailureMetadata failureMetadata, Expression subject) {
    super(failureMetadata, subject);
  }

  public ExpressionSubject evaluatesTo(int expected) {
    evaluator.compile(actual());
    if (((IntInvoker) evaluator.invoker).invoke() != expected) {
      fail("evaluatesTo", expected);
    }
    return this;
  }

  public ExpressionSubject evaluatesTo(boolean expected) {
    evaluator.compile(actual());
    boolean actual;
    try {
      actual = ((BooleanInvoker) evaluator.invoker).invoke();
    } catch (Throwable t) {
      failWithBadResults("evalutes to", expected, "fails with", t);
      return this;
    }
    if (actual != expected) {
      failWithBadResults("evaluates to", expected, "evaluates to", actual);
    }
    return this;
  }

  public ExpressionSubject evaluatesTo(double expected) {
    evaluator.compile(actual());
    double actual;
    try {
      actual = ((DoubleInvoker) evaluator.invoker).invoke();
    } catch (Throwable t) {
      failWithBadResults("evalutes to", expected, "fails with", t);
      return this;
    }
    if (actual != expected) {
      failWithBadResults("evaluates to", expected, "evaluates to", actual);
    }
    return this;
  }

  public ExpressionSubject evaluatesTo(long expected) {
    evaluator.compile(actual());
    long actual;
    try {
      actual = ((LongInvoker) evaluator.invoker).invoke();
    } catch (Throwable t) {
      failWithBadResults("evalutes to", expected, "fails with", t);
      return this;
    }
    if (actual != expected) {
      failWithBadResults("evaluates to", expected, "evaluates to", actual);
    }
    return this;
  }

  public ExpressionSubject evaluatesTo(char expected) {
    evaluator.compile(actual());
    char actual;
    try {
      actual = ((CharInvoker) evaluator.invoker).invoke();
    } catch (Throwable t) {
      failWithBadResults("evalutes to", expected, "fails with", t);
      return this;
    }
    if (actual != expected) {
      failWithBadResults("evaluates to", expected, "evaluates to", actual);
    }
    return this;
  }

  public ExpressionSubject evaluatesTo(Object expected) {
    evaluator.compile(actual());
    Object actual;
    try {
      actual = ((ObjectInvoker) evaluator.invoker).invoke();
    } catch (Throwable t) {
      failWithBadResults("evaluates to", expected, "fails with", t);
      return this;
    }
    if (!Objects.equal(actual, expected)) {
      failWithBadResults("evaluates to", expected, "evaluates to", actual);
    }
    return this;
  }

  /**
   * Asserts on the literal code of the expression, use sparingly since it may lead to overly
   * coupled tests.
   */
  public ExpressionSubject hasCode(String... instructions) {
    evaluator.compile(actual());
    String formatted = Joiner.on('\n').join(instructions);
    if (!formatted.equals(actual().trace().trim())) {
      fail("hasCode", formatted);
    }
    return this;
  }

  /**
   * Asserts on the literal code of the expression, use sparingly since it may lead to overly
   * coupled tests.
   */
  public ExpressionSubject doesNotContainCode(String... instructions) {
    evaluator.compile(actual());
    String formatted = Joiner.on('\n').join(instructions);
    String actual = actual().trace().trim();
    if (actual.contains(formatted)) {
      failWithBadResults("doesNotContainCode", formatted, "evaluates to", actual);
    }
    return this;
  }

  public ExpressionSubject evaluatesToInstanceOf(Class<?> expected) {
    evaluator.compile(actual());
    Object actual;
    try {
      actual = ((ObjectInvoker) evaluator.invoker).invoke();
    } catch (Throwable t) {
      failWithBadResults("evalutes to instance of", expected, "fails with", t);
      return this;
    }
    if (!expected.isInstance(actual)) {
      failWithBadResults("evaluates to instance of", expected, "evaluates to", actual);
    }
    return this;
  }

  public ExpressionSubject throwsException(Class<? extends Throwable> clazz) {
    return throwsException(clazz, null);
  }

  public ExpressionSubject throwsException(Class<? extends Throwable> clazz, String message) {
    evaluator.compile(actual());
    try {
      evaluator.invoker.voidInvoke();
    } catch (Throwable t) {
      if (!clazz.isInstance(t)) {
        failWithBadResults("throws an exception of type", clazz, "fails with", t);
      }
      if (message != null && !t.getMessage().equals(message)) {
        failWithBadResults("throws an exception with message", message, "fails with", t);
      }
      return this;
    }
    fail("throws an exception");
    return this; // dead code, but the compiler can't prove it
  }
}
