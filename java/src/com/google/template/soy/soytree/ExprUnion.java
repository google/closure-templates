/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.soytree;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.error.AbstractErrorReporter;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprparse.ExpressionParser;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Represents a Soy expression in either V2 or V1 syntax.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>If this expression is in V2 syntax, then {@link #getExpr()} will return a nonnull expression
 * tree. If this expression is in V1 syntax, then {@code #getExpr()} will return null. In either
 * case, the expression text can be obtained from {@link #getExprText()}.
 *
 */
public final class ExprUnion {

  /** Creates an ExprUnion from the given expression text. */
  static ExprUnion parseWithV1Fallback(
      String exprText, SourceLocation location, SoyParsingContext context) {
    DelayedErrorReporter errorReporter = new DelayedErrorReporter();
    Checkpoint checkpoint = errorReporter.checkpoint();
    ExprNode expr =
        new ExpressionParser(exprText, location, context.withErrorReporter(errorReporter))
            .parseExpression();
    return errorReporter.errorsSince(checkpoint)
        ? new ExprUnion(exprText, errorReporter.reports)
        : new ExprUnion(expr);
  }

  /**
   * Utility to create a list of {@code ExprUnion}s from a list of expression trees.
   *
   * @param exprs The list of expression trees.
   * @return A new list of corresponding {@code ExprUnion}s.
   */
  public static List<ExprUnion> createList(List<? extends ExprRootNode> exprs) {
    List<ExprUnion> exprUnions = Lists.newArrayListWithCapacity(exprs.size());
    for (ExprRootNode expr : exprs) {
      exprUnions.add(new ExprUnion(expr));
    }
    return exprUnions;
  }

  /** The expression tree, or null if the expression is in V1 syntax. */
  @Nullable private final ExprRootNode expr;

  /** The V1 expression text, or null if the expression is in V2 syntax. */
  @Nullable private final String exprText;

  /** The errors that were reported when this expression failed to parse as v2. */
  private final ImmutableList<DelayedErrorReport> delayedErrorReports;

  /**
   * Constructor for an instance that represents a V2 expression.
   *
   * @param expr The expression tree.
   */
  public ExprUnion(ExprNode expr) {
    this(new ExprRootNode(expr));
  }

  /**
   * Constructor for an instance that represents a V2 expression.
   *
   * @param expr The expression tree.
   */
  public ExprUnion(ExprRootNode expr) {
    this.expr = expr;
    this.exprText = null;
    this.delayedErrorReports = ImmutableList.of();
  }

  /**
   * Constructor for an instance that represents an expression in V1 syntax.
   *
   * @param exprTextV1 The text of the V1 expression.
   */
  private ExprUnion(String exprTextV1, List<DelayedErrorReport> delayedErrorReports) {
    Preconditions.checkArgument(!delayedErrorReports.isEmpty());
    this.expr = null;
    this.exprText = exprTextV1;
    this.delayedErrorReports = ImmutableList.copyOf(delayedErrorReports);
  }

  private ExprUnion(ExprUnion orig, CopyState copyState) {
    this.expr = orig.expr != null ? orig.expr.copy(copyState) : null;
    this.exprText = orig.exprText;
    this.delayedErrorReports = orig.delayedErrorReports;
  }

  /** Returns the expression tree if the expression is in V2 syntax, else null. */
  public ExprRootNode getExpr() {
    return expr;
  }

  /** Returns the expression text. This method works for both V2 and V1 expressions. */
  public String getExprText() {
    return (expr != null) ? expr.toSourceString() : exprText;
  }

  /** Returns a (deep) clone of this object. */
  public ExprUnion copy(CopyState copyState) {
    return new ExprUnion(this, copyState);
  }

  /**
   * Adds all the errors from trying to parse this as a V2 expression to the given error reporter.
   *
   * <p>Guaranteed to add at least one error if {@link #getExpr()} is null.
   */
  public void reportV2ParseErrors(ErrorReporter reporter) {
    for (DelayedErrorReport report : delayedErrorReports) {
      reporter.report(report.location(), report.error(), report.args().toArray());
    }
  }

  /**
   * An {@link ErrorReporter} that captures errors so that they can optionally be applied to another
   * error reporter at a later time.
   */
  private static final class DelayedErrorReporter extends AbstractErrorReporter {
    final List<DelayedErrorReport> reports = new ArrayList<>();

    @Override
    public void report(SourceLocation sourceLocation, SoyErrorKind error, Object... args) {
      reports.add(
          new AutoValue_ExprUnion_DelayedErrorReport(
              sourceLocation, error, ImmutableList.copyOf(args)));
    }

    @Override
    protected int getCurrentNumberOfErrors() {
      return reports.size();
    }
  }

  @AutoValue
  abstract static class DelayedErrorReport {
    abstract SourceLocation location();

    abstract SoyErrorKind error();

    abstract List<?> args();
  }
}
