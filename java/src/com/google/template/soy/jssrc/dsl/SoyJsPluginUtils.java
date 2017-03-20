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

import static com.google.template.soy.jssrc.dsl.CodeChunk.fromExpr;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.jssrc.dsl.CodeChunk.RequiresCollector;
import com.google.template.soy.jssrc.dsl.CodeChunk.WithValue;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
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

  private static final Function<WithValue, JsExpr> TO_JS_EXPR =
      new Function<WithValue, JsExpr>() {
        @Override
        public JsExpr apply(WithValue chunk) {
          return chunk.singleExprOrName();
        }
      };

  private SoyJsPluginUtils() {}

  /** Generates a JS expression for the given operator and operands. */
  public static JsExpr genJsExprUsingSoySyntax(Operator op, List<JsExpr> operandJsExprs) {
    List<CodeChunk.WithValue> operands =
        Lists.transform(
            operandJsExprs,
            new Function<JsExpr, CodeChunk.WithValue>() {
              @Override
              public CodeChunk.WithValue apply(JsExpr input) {
                return fromExpr(input, ImmutableList.<GoogRequire>of());
              }
            });
    return CodeChunk.operation(op, operands).assertExpr();
  }

  /**
   * Applies the given print directive to {@code expr} and returns the result.
   *
   * @param generator The CodeChunk generator to use.
   * @param expr The expression to apply the print directive to.
   * @param directive The print directive to apply.
   * @param args Print directive args, if any.
   */
  public static CodeChunk.WithValue applyDirective(
      CodeChunk.Generator generator,
      CodeChunk.WithValue expr,
      SoyJsSrcPrintDirective directive,
      List<CodeChunk.WithValue> args) {
    List<JsExpr> argExprs = Lists.transform(args, TO_JS_EXPR);
    JsExpr applied = directive.applyForJsSrc(expr.singleExprOrName(), argExprs);
    RequiresCollector.IntoImmutableSet collector = new RequiresCollector.IntoImmutableSet();
    expr.collectRequires(collector);
    for (CodeChunk.WithValue arg : args) {
      arg.collectRequires(collector);
    }
    if (directive instanceof SoyLibraryAssistedJsSrcPrintDirective) {
      for (String name :
          ((SoyLibraryAssistedJsSrcPrintDirective) directive).getRequiredJsLibNames()) {
        collector.add(GoogRequire.create(name));
      }
    }

    ImmutableList.Builder<CodeChunk> initialStatements =
        ImmutableList.<CodeChunk>builder().addAll(expr.initialStatements());
    for (CodeChunk.WithValue arg : args) {
      initialStatements.addAll(arg.initialStatements());
    }
    return fromExpr(applied, collector.get()).withInitialStatements(initialStatements.build());
  }
}
