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

package com.google.template.soy.passes;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites {@code v1Expression('$a.b($c)')} to {@code v1Expression('$a.b($c)', $a, $c)} so that the
 * variables could be resolved.
 */
@RunBefore(ResolveNamesPass.class)
final class V1ExpressionPass implements CompilerFilePass {

  private static final SoyErrorKind INCORRECT_V1_EXPRESSION_USE =
      SoyErrorKind.of(
          "The ''v1Expression'' function can only be used in legacy JS only templates.");

  private static final SoyErrorKind USING_IJ_VARIABLE =
      SoyErrorKind.of("''v1Expression'' does not support using the ''$ij'' variable.");

  // For simplicity, we ignore strings and comments.
  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("\\$(" + BaseUtils.IDENT_RE + ")");

  private final boolean allowV1Expression;
  private final ErrorReporter errorReporter;

  V1ExpressionPass(boolean allowV1Expression, ErrorReporter errorReporter) {
    this.allowV1Expression = allowV1Expression;
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode fn :
        SoyTreeUtils.getAllFunctionInvocations(file, BuiltinFunction.V1_EXPRESSION)) {
      if (!allowV1Expression) {
        errorReporter.report(fn.getSourceLocation(), INCORRECT_V1_EXPRESSION_USE);
      }
      // PluginResolver checks that the function has one argument, ResolveExpressionTypesPass checks
      // that it is a string literal.
      ExprNode param = fn.getChild(0);
      SourceLocation paramLocation = param.getSourceLocation();
      // We use toSourceString() instead of getValue() to get the location offsets right (quotes and
      // escaped characters would skew offset).
      String expression = param.toSourceString();
      Matcher matcher = VARIABLE_PATTERN.matcher(expression);
      while (matcher.find()) {
        SourceLocation varLocation =
            paramLocation
                .offsetStartCol(matcher.start())
                .offsetEndCol(matcher.end() - expression.length());
        if (matcher.group(1).equals("ij")) {
          errorReporter.report(varLocation, USING_IJ_VARIABLE);
        } else {
          // This might add the same variable more than once but who cares.
          fn.addChild(new VarRefNode("$" + matcher.group(1), varLocation, /* defn= */ null));
        }
      }
    }
  }
}
