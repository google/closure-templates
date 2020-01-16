/*
 * Copyright 2019 Google Inc.
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

import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Reports errors on improper uses of the unknownJsGlobal function. */
final class UnknownJsGlobalPass implements CompilerFilePass {

  private static final SoyErrorKind INCORRECT_UNKNOWN_JS_GLOBAL_USE =
      SoyErrorKind.of(
          "The ''unknownJsGlobal'' function can only be used in legacy JS only templates.");
  private static final SoyErrorKind INVALID_JS_GLOBAL_VALUE =
      SoyErrorKind.of("Parameters to ''unknownJsGlobal'' must be valid dotted identifiers.");

  private final ErrorReporter errorReporter;
  private final boolean allowFunction;

  UnknownJsGlobalPass(boolean allowFunction, ErrorReporter errorReporter) {
    this.allowFunction = allowFunction;
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode fn :
        SoyTreeUtils.getAllFunctionInvocations(file, BuiltinFunction.UNKNOWN_JS_GLOBAL)) {
      if (!allowFunction) {
        errorReporter.report(fn.getSourceLocation(), INCORRECT_UNKNOWN_JS_GLOBAL_USE);
      }
      // The ResolveFunctionsPass and ResolveExpressionTypesPass validate these 2 conditions so we
      // don't need to enforce them here.
      if (fn.numChildren() == 1) {
        ExprNode child = fn.getChild(0);
        if (child instanceof StringNode) {
          String parameter = ((StringNode) child).getValue();
          if (!BaseUtils.isDottedIdentifier(parameter)) {
            errorReporter.report(child.getSourceLocation(), INVALID_JS_GLOBAL_VALUE);
          }
        }
      }
    }
  }
}
