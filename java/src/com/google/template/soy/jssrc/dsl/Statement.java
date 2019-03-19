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
package com.google.template.soy.jssrc.dsl;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;

/**
 * Marker class for {@link CodeChunk} instances that compile to one or more JavaScript statements.
 *
 * <p>It should be the case that any Statement will start and end in the same lexical scope.
 */
@Immutable
public abstract class Statement extends CodeChunk {
  Statement() {}

  /** Creates a new code chunk representing the concatenation of the given statements. */
  public static Statement of(Statement first, Statement... rest) {
    return of(ImmutableList.<Statement>builder().add(first).add(rest).build());
  }

  /** Creates a new code chunk representing the concatenation of the given statements. */
  public static Statement of(Iterable<Statement> stmts) {
    ImmutableList<Statement> copy = ImmutableList.copyOf(stmts);
    return copy.size() == 1 ? copy.get(0) : StatementList.create(copy);
  }

  /** Starts a conditional statement beginning with the given predicate and consequent chunks. */
  public static ConditionalBuilder ifStatement(Expression predicate, Statement consequent) {
    return new ConditionalBuilder(predicate, consequent);
  }

  /** Creates a code chunk that assigns value to a preexisting variable with the given name. */
  public static Statement assign(Expression lhs, Expression rhs) {
    return Assignment.create(lhs, rhs, null);
  }

  /** Creates a code chunk that assigns and prints jsDoc above the assignment. */
  public static Statement assign(Expression lhs, Expression rhs, JsDoc jsDoc) {
    return Assignment.create(lhs, rhs, jsDoc);
  }

  /** Starts a {@code switch} statement dispatching on the given chunk. */
  public static SwitchBuilder switchValue(Expression switchOn) {
    return new SwitchBuilder(switchOn);
  }

  /** Creates a code chunk representing a for loop. */
  public static Statement forLoop(
      String localVar, Expression initial, Expression limit, Expression increment, Statement body) {
    return For.create(localVar, initial, limit, increment, body);
  }

  /** Creates a code chunk representing a for of loop. */
  public static Statement forOf(String localVar, Expression collection, Statement body) {
    return ForOf.create(localVar, collection, body);
  }

  /** Creates a code chunk representing a for loop, with default values for initial & increment. */
  public static Statement forLoop(String localVar, Expression limit, Statement body) {
    return For.create(localVar, Expression.number(0), limit, Expression.number(1), body);
  }

  /** Creates a code chunk that represents a return statement returning the given value. */
  public static Statement returnValue(Expression returnValue) {
    return Return.create(returnValue);
  }

  /** Creates a code chunk that represents an empty return statement. */
  public static Statement returnNothing() {
    return Return.create();
  }

  /** Creates a code chunk that represents a throw statement. */
  public static Statement throwValue(Expression throwValue) {
    return Throw.create(throwValue);
  }

  /**
   * Creates a code chunk from the given text, treating it as a series of statements rather than an
   * expression. For use only by {@link
   * com.google.template.soy.jssrc.internal.GenJsCodeVisitor#visitReturningCodeChunk}.
   *
   * <p>TODO(b/33382980): remove.
   */
  public static Statement treatRawStringAsStatementLegacyOnly(
      String rawString, Iterable<GoogRequire> requires) {
    return LeafStatement.create(rawString, requires);
  }
}
