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

import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;

import com.google.common.collect.ImmutableListMultimap;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.compilermetrics.Impression;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.ExternNode;
import com.google.template.soy.soytree.FileMetadata;
import com.google.template.soy.soytree.FileMetadata.Extern;
import com.google.template.soy.soytree.FileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.types.UnionType;
import java.util.function.Supplier;

/** Reports errors for illegal symbol references. */
@RunAfter({ResolveExpressionTypesPass.class, MoreCallValidationsPass.class})
final class CheckValidVarrefsPass implements CompilerFilePass {

  private static final SoyErrorKind ILLEGAL_TYPE_OF_VARIABLE =
      SoyErrorKind.of(
          "Illegal use of symbol ''{0}''.",
          Impression.ERROR_CHECK_VALID_VARREFS_PASS_ILLEGAL_TYPE_OF_VARIABLE);

  private static final SoyErrorKind EXTERN_OVERLOAD =
      SoyErrorKind.of(
          "Cannot reference or bind overloaded functions.",
          Impression.ERROR_CHECK_VALID_VARREFS_PASS_EXTERN_OVERLOAD);

  private static final SoyErrorKind ASYNC_EXTERN =
      SoyErrorKind.of(
          "Cannot reference or bind async functions.",
          Impression.ERROR_CHECK_VALID_VARREFS_PASS_ASYNC_EXTERN);

  private final ErrorReporter errorReporter;
  private final Supplier<FileSetMetadata> fileSetMetadata;
  private ImmutableListMultimap<String, ExternNode> externIndex;

  CheckValidVarrefsPass(ErrorReporter errorReporter, Supplier<FileSetMetadata> fileSetMetadata) {
    this.errorReporter = errorReporter;
    this.fileSetMetadata = fileSetMetadata;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator idGenerator) {
    externIndex =
        file.getExterns().stream()
            .collect(toImmutableListMultimap(e -> e.getIdentifier().identifier(), e -> e));
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class).forEach(this::checkVarRef);
  }

  private void checkVarRef(VarRefNode varRef) {
    VarDefn defn = varRef.getDefnDecl();
    if (isOverloadedExtern(defn)) {
      if (!isFunctionCallNameExpr(varRef)) {
        errorReporter.report(varRef.getSourceLocation(), EXTERN_OVERLOAD);
        return;
      }
    }
    // We forbid referencing async externs because the JBCSRC compiler needs to know whether an
    // extern is async or not (it assumes it isn't) to generate the correct byte code. The type
    // system does not have a way to represent this and so this information is lost.
    if (isAsyncExtern(defn)) {
      if (!isFunctionCallNameExpr(varRef)) {
        errorReporter.report(varRef.getSourceLocation(), ASYNC_EXTERN);
        return;
      }
    }

    ParentExprNode parent = varRef.getParent();
    Kind parentKind = parent != null ? parent.getKind() : null;

    switch (varRef.getType().getKind()) {
      case PROTO_ENUM_TYPE, NAMESPACE ->
          errorReporter.report(
              varRef.getSourceLocation(), ILLEGAL_TYPE_OF_VARIABLE, varRef.toSourceString());
      case PROTO_TYPE -> {
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
      }
      case PROTO_EXTENSION -> {
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
      }
      default -> {}
    }
  }

  private static boolean isFunctionCallNameExpr(VarRefNode varRef) {
    return varRef.getParent() instanceof FunctionNode functionNode
        && !functionNode.hasStaticName()
        && varRef.equals(functionNode.getNameExpr());
  }

  private boolean isOverloadedExtern(VarDefn defn) {
    if (defn instanceof SymbolVar symbolVar && symbolVar.getSymbolKind() == SymbolKind.EXTERN) {
      if (symbolVar.isImported()) {
        return symbolVar.type() instanceof UnionType;
      } else {
        return externIndex.get(symbolVar.name()).size() > 1;
      }
    }
    return false;
  }

  private boolean isAsyncExtern(VarDefn defn) {
    if (defn instanceof SymbolVar symbolVar && symbolVar.getSymbolKind() == SymbolKind.EXTERN) {
      if (symbolVar.isImported()) {
        FileMetadata fileMetadata = fileSetMetadata.get().getFile(symbolVar.getSourceFilePath());
        return fileMetadata != null
            && fileMetadata.getExterns(symbolVar.getSymbol()).stream()
                .anyMatch(Extern::isJavaAsync);
      } else {
        return externIndex.get(symbolVar.name()).stream().anyMatch(ExternNode::isJavaImplAsync);
      }
    }
    return false;
  }
}
