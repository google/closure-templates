/*
 * Copyright 2021 Google Inc.
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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/** Reports errors for illegal symbol references. */
@RunAfter({ResolveExpressionTypesPass.class, MoreCallValidationsPass.class})
final class CheckValidVarrefsPass implements CompilerFilePass {

  private static final SoyErrorKind ILLEGAL_TYPE_OF_VARIABLE =
      SoyErrorKind.of("Illegal use of symbol ''{0}''.");

  private final ErrorReporter errorReporter;

  CheckValidVarrefsPass(ErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator idGenerator) {
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class).forEach(this::checkVarRef);
  }

  private void checkVarRef(VarRefNode varRef) {
    ParentExprNode parent = varRef.getParent();
    Kind parentKind = parent != null ? parent.getKind() : null;

    switch (varRef.getType().getKind()) {
      case TEMPLATE_MODULE:
      case PROTO_ENUM_TYPE:
      case PROTO_MODULE:
      case CSS_MODULE:
      case CSS_TYPE:
      case TOGGLE_TYPE:
        errorReporter.report(
            varRef.getSourceLocation(), ILLEGAL_TYPE_OF_VARIABLE, varRef.toSourceString());
        break;
      case FUNCTION:
        if (!(parentKind == Kind.FUNCTION_NODE && varRef.equals(parent.getChild(0)))) {
          // Using an extern/function name anywhere other than as the base of a function call.
          errorReporter.report(
              varRef.getSourceLocation(), ILLEGAL_TYPE_OF_VARIABLE, varRef.toSourceString());
        }
        break;
      case PROTO_TYPE:
        if (!(parent == null
            || parentKind == Kind.FIELD_ACCESS_NODE
            || parentKind == Kind.FUNCTION_NODE)) {
          // This is for imports like:
          // import {MyMessage} from 'path/to/my/proto/file.proto';
          // These are only allowed in:
          // - Proto init calls (i.e. `MyMessage()`). In this case, the parent is null (the
          // VarRefNode is the `nameExpr` on the FunctionNode).
          // - Field accesses, like `MyMessage.MySubMessage`
          // - `ve_def` data proto type parameters (i.e. `ve_def('MyVe', 123, MyMessage)`). However,
          //   this allows any FunctionNode, not just `ve_def`, to prevent duplicate/confusing
          //   errors on things like misspelling `ve_def`. Function parameters are type checked
          //   elsewhere, which will report any issues.
          errorReporter.report(
              varRef.getSourceLocation(), ILLEGAL_TYPE_OF_VARIABLE, varRef.toSourceString());
        }
        break;
      case PROTO_EXTENSION:
        if (parentKind != Kind.METHOD_CALL_NODE) {
          // This is for imports like:
          // import {TopLevelEnum} from 'path/to/my/proto/file.proto';
          // These are only allowed as the parameter to the `getExtension`, `getReadonlyExtension`
          // and `hasExtension` proto methods. However, this allows any MethodCallNode to prevent
          // duplicate/confusing errors on things like misspelling one of the method names. Method
          // parameters are type checked elsewhere, which which will report any issues.
          errorReporter.report(
              varRef.getSourceLocation(), ILLEGAL_TYPE_OF_VARIABLE, varRef.toSourceString());
        }
        break;
      default:
        break;
    }
  }
}
