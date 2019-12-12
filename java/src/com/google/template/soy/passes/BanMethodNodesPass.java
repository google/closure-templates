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

import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.MethodNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import java.util.List;

/** Report errors on method nodes while under development */
final class BanMethodNodesPass extends CompilerFilePass {

  private static final SoyErrorKind METHOD_NODE_IS_BANNED =
      SoyErrorKind.of("Invoking methods is not supported yet.");
  private final ErrorReporter errorReporter;

  BanMethodNodesPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (MethodNode methodNode : getAllNodesOfType(file, MethodNode.class)) {
      errorReporter.report(methodNode.getSourceLocation(), METHOD_NODE_IS_BANNED);
      // to prevent a crash later in the compiler.  We need to remove the MethodNode from the AST
      // we can't just delete it, so instead we replace it. But we want to preserve sub expressions
      // so we can flag additional errors later on in the compiler.
      // The error function can only accept 1 or 2 arguments,  so to preserve them all we need to
      // create potentially several error functions
      methodNode
          .getParent()
          .replaceChild(
              methodNode,
              wrapWithErrorPlaceholder(methodNode.getChildren(), methodNode.getSourceLocation()));
    }
  }

  private static FunctionNode wrapWithErrorPlaceholder(
      List<ExprNode> exprs, SourceLocation location) {
    FunctionNode fnNode =
        new FunctionNode(
            Identifier.create(BuiltinFunction.ERROR_PLACEHOLDER.getName(), location), location);
    switch (exprs.size()) {
      case 0:
        throw new IllegalArgumentException();
      case 1:
      case 2:
        fnNode.addChildren(exprs);
        break;
      default:
        // recursively wrap the tail of the list.  This is essentially a cons-cell.
        fnNode.addChild(exprs.get(0));
        fnNode.addChild(wrapWithErrorPlaceholder(exprs.subList(1, exprs.size()), location));
        break;
    }
    return fnNode;
  }
}
