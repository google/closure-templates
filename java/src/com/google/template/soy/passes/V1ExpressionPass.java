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
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites {@code v1Expression('$a.b($c)')} to {@code v1Expression('$a.b($c)', $a, $c)} so that the
 * variables could be resolved.
 */
final class V1ExpressionPass extends CompilerFilePass {

  private static final SoyErrorKind USING_IJ_VARIABLE =
      SoyErrorKind.of("''v1Expression'' does not support using the ''$ij'' variable.");

  // For simplicity, we ignore strings and comments.
  private static final Pattern VARIABLE_PATTERN =
      Pattern.compile("\\$(" + BaseUtils.IDENT_RE + ")");

  private final ErrorReporter errorReporter;

  V1ExpressionPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode fn : SoyTreeUtils.getAllNodesOfType(file, FunctionNode.class)) {
      // Comparing getSoyFunction() to BuiltinFunction.V1_EXPRESSION doesn't work because it returns
      // PluginResolver.ERROR_PLACEHOLDER_FUNCTION when executed from RunParser.
      if (!fn.getFunctionName().equals("v1Expression")) {
        continue;
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
          fn.addChild(
              new VarRefNode(
                  matcher.group(1),
                  varLocation,
                  /* isDollarSignIjParameter= */ false,
                  /* defn= */ null));
        }
      }
    }
  }
}
