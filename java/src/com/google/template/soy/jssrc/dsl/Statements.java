/*
 * Copyright 2023 Google Inc.
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

import static com.google.template.soy.jssrc.dsl.Expressions.ERROR_EXPR;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.template.soy.jssrc.dsl.Expressions.DecoratedExpression;
import java.util.List;
import java.util.stream.Stream;

/** Static functions related to Expressions. */
public final class Statements {

  public static final Statement ERROR_STMT = ExpressionStatement.of(ERROR_EXPR);
  public static final Statement EMPTY = of(ImmutableList.of());

  private Statements() {}

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
      Id localVar, Expression initial, Expression limit, Expression increment, Statement body) {
    return For.create(localVar, initial, limit, increment, body);
  }

  /** Creates a code chunk representing a for loop, with default values for initial & increment. */
  public static Statement forLoop(Id localVar, Expression limit, Statement body) {
    return For.create(localVar, Expressions.number(0), limit, Expressions.number(1), body);
  }

  /** Creates a code chunk representing a for of loop. */
  public static Statement forOf(Id localVar, Expression collection, Statement body) {
    return ForOf.create(localVar, collection, body);
  }

  public static Statement whileLoop(Expression condition, Statement body) {
    return While.create(condition, body);
  }

  /** Creates a code chunk that represents a return statement returning the given value. */
  public static Statement returnValue(Expression returnValue) {
    return Return.create(returnValue);
  }

  /** Creates a code chunk that represents an empty return statement. */
  public static Statement returnNothing() {
    return Return.create();
  }

  public static Statement breakStatement() {
    return Break.create();
  }

  public static Statement continueStatement() {
    return Continue.create();
  }

  /** Creates a code chunk that represents a throw statement. */
  public static Statement throwValue(Expression throwValue) {
    return Throw.create(throwValue);
  }

  /** Creates a code chunk that represents a debugger statement. */
  public static Statement debugger() {
    return Debugger.INSTANCE;
  }

  @AutoValue
  abstract static class DecoratedStatement extends Statement {

    public static Statement create(
        Statement statement, List<SpecialToken> before, List<SpecialToken> after) {
      if (before.isEmpty() && after.isEmpty()) {
        return statement;
      }
      return new AutoValue_Statements_DecoratedStatement(
          ImmutableList.copyOf(before), statement, ImmutableList.copyOf(after));
    }

    abstract ImmutableList<SpecialToken> beforeTokens();

    abstract Statement statement();

    abstract ImmutableList<SpecialToken> afterTokens();

    @Override
    public Expression asExpr() {
      Expression expr = statement().asExpr();
      return DecoratedExpression.create(expr, beforeTokens(), afterTokens());
    }

    @Override
    void doFormatStatement(FormattingContext ctx) {
      for (CodeChunk chunk : beforeTokens()) {
        ctx.appendAll(chunk);
      }
      ctx.appendAll(statement());
      for (CodeChunk chunk : afterTokens()) {
        ctx.appendAll(chunk);
      }
    }

    @Override
    public boolean isTerminal() {
      return statement().isTerminal();
    }

    @Override
    Stream<? extends CodeChunk> childrenStream() {
      return Streams.concat(
          beforeTokens().stream(), Stream.of(statement()), afterTokens().stream());
    }

    @Override
    public Statement append(List<SpecialToken> tokens) {
      if (tokens.isEmpty()) {
        return this;
      }
      return create(
          statement(),
          beforeTokens(),
          ImmutableList.<SpecialToken>builder().addAll(tokens).addAll(afterTokens()).build());
    }

    @Override
    public Statement prepend(List<SpecialToken> tokens) {
      if (tokens.isEmpty()) {
        return this;
      }
      return create(
          statement(),
          ImmutableList.<SpecialToken>builder().addAll(tokens).addAll(beforeTokens()).build(),
          afterTokens());
    }
  }
}
