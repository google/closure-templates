/*
 * Copyright 2009 Google Inc.
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

import static com.google.template.soy.jssrc.dsl.Expression.dontTrustPrecedenceOf;
import static com.google.template.soy.jssrc.dsl.Expression.fromExpr;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Convenience utilities for generating JS code from plugins (functions and directives).
 *
 * <p>The main code-generating classes of the JS backend understand {@link CodeChunk code chunks}
 * and have no need for these utilities. By contrast, plugins understand only {@link JsExpr}s. These
 * utilities are provided so that plugins do not needs to convert between code chunks and JsExprs
 * manually.
 *
 */
public final class SoyJsPluginUtils {

  private SoyJsPluginUtils() {}

  /** Generates a JS expression for the given operator and operands. */
  public static JsExpr genJsExprUsingSoySyntax(Operator op, List<JsExpr> operandJsExprs) {
    List<Expression> operands =
        Lists.transform(operandJsExprs, input -> fromExpr(input, ImmutableList.<GoogRequire>of()));
    return Expression.operation(op, operands).assertExpr();
  }

  private static final SoyErrorKind UNEXPECTED_PLUGIN_ERROR =
      SoyErrorKind.of(formatPlain("{3}"), StyleAllowance.NO_PUNCTUATION);

  private static String formatPlain(String innerFmt) {
    return "Error in plugin implementation for {0} ''{1}''."
        + "\n"
        + innerFmt
        + "\nPlugin implementation: {2}";
  }

  /**
   * Applies the given print directive to {@code expr} and returns the result.
   *
   * @param generator The CodeChunk generator to use.
   * @param expr The expression to apply the print directive to.
   * @param directive The print directive to apply.
   * @param args Print directive args, if any.
   */
  public static Expression applyDirective(
      Expression expr,
      SoyJsSrcPrintDirective directive,
      List<Expression> args,
      SourceLocation location,
      ErrorReporter errorReporter) {
    List<JsExpr> argExprs = Lists.transform(args, Expression::singleExprOrName);
    JsExpr applied;
    try {
      applied = directive.applyForJsSrc(expr.singleExprOrName(), argExprs);
    } catch (Throwable t) {
      applied = report(location, directive, t, errorReporter);
    }
    ImmutableSet.Builder<GoogRequire> requiresBuilder = ImmutableSet.builder();
    expr.collectRequires(requiresBuilder::add);
    for (Expression arg : args) {
      arg.collectRequires(requiresBuilder::add);
    }
    if (directive instanceof SoyLibraryAssistedJsSrcPrintDirective) {
      for (String name :
          ((SoyLibraryAssistedJsSrcPrintDirective) directive).getRequiredJsLibNames()) {
        requiresBuilder.add(GoogRequire.create(name));
      }
    }

    ImmutableList.Builder<Statement> initialStatements =
        ImmutableList.<Statement>builder().addAll(expr.initialStatements());
    for (Expression arg : args) {
      initialStatements.addAll(arg.initialStatements());
    }
    return fromExpr(applied, requiresBuilder.build())
        .withInitialStatements(initialStatements.build());
  }

  public static Expression applySoyFunction(
      SoyJsSrcFunction soyJsSrcFunction,
      List<Expression> args,
      SourceLocation location,
      ErrorReporter errorReporter) {
    List<JsExpr> functionInputs = new ArrayList<>(args.size());
    List<Statement> initialStatements = new ArrayList<>();
    ImmutableSet.Builder<GoogRequire> requiresBuilder = ImmutableSet.builder();

    // SoyJsSrcFunction doesn't understand CodeChunks; it needs JsExprs.
    // Grab the JsExpr for each CodeChunk arg to deliver to the SoyToJsSrcFunction as input.
    for (Expression arg : args) {
      arg.collectRequires(requiresBuilder::add);
      functionInputs.add(arg.singleExprOrName());
      initialStatements.addAll(arg.initialStatements());
    }

    // Compute the function on the JsExpr inputs.
    if (soyJsSrcFunction instanceof SoyLibraryAssistedJsSrcFunction) {
      Collection<String> requires = ImmutableSet.of();
      try {
        requires = ((SoyLibraryAssistedJsSrcFunction) soyJsSrcFunction).getRequiredJsLibNames();
      } catch (Throwable t) {
        report(location, soyJsSrcFunction, t, errorReporter);
      }
      for (String name : requires) {
        requiresBuilder.add(GoogRequire.create(name));
      }
    }
    JsExpr outputExpr;
    try {
      outputExpr = soyJsSrcFunction.computeForJsSrc(functionInputs);
    } catch (Throwable t) {
      outputExpr = report(location, soyJsSrcFunction, t, errorReporter);
    }
    Expression functionOutput = dontTrustPrecedenceOf(outputExpr, requiresBuilder.build());

    return functionOutput.withInitialStatements(initialStatements);
  }

  private static JsExpr report(
      SourceLocation location, Object obj, Throwable t, ErrorReporter errorReporter) {
    BaseUtils.trimStackTraceTo(t, SoyJsPluginUtils.class);
    errorReporter.report(
        location,
        UNEXPECTED_PLUGIN_ERROR,
        obj instanceof SoyJsSrcFunction ? "function" : "directive",
        obj instanceof SoyJsSrcFunction
            ? ((SoyJsSrcFunction) obj).getName()
            : ((SoyJsSrcPrintDirective) obj).getName(),
        obj.getClass().getName(),
        Throwables.getStackTraceAsString(t));
    return new JsExpr("if you see this, soy swallowed an error", Integer.MAX_VALUE);
  }
}
