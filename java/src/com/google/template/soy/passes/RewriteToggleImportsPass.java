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

package com.google.template.soy.passes;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.ToggleImportType;

/**
 * Rewrite toggle import statement and references to call built-in function that supports toggles as
 * an {@link ImportedVar} vardef.
 */
@RunAfter(ResolveNamesPass.class)
final class RewriteToggleImportsPass implements CompilerFilePass {

  public RewriteToggleImportsPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class)
        .filter(v -> v.getDefnDecl().kind() == VarDefn.Kind.IMPORT_VAR)
        .filter(v -> v.getDefnDecl().hasType())
        .filter(v -> v.getDefnDecl().type().getKind() == SoyType.Kind.TOGGLE_TYPE)
        .forEach(v -> rewriteToggleNode(v, v.getSourceLocation()));
  }

  /**
   * Returns a new expr node that evaluates the value of the toggle named {@code toggleName} on the
   * {@code refn} value. Returns either a primitive node or var ref node or null if the field could
   * not be resolved.
   */
  private void rewriteToggleNode(VarRefNode refn, SourceLocation fullLocation) {
    ImportedVar defn = (ImportedVar) refn.getDefnDecl();
    ToggleImportType toggleType = (ToggleImportType) defn.type();
    FunctionNode funcNode =
        FunctionNode.newPositional(
            Identifier.create(BuiltinFunction.EVAL_TOGGLE.getName(), fullLocation),
            BuiltinFunction.EVAL_TOGGLE,
            fullLocation);

    // Add toggle path and name as parameters to built-in function
    funcNode.addChild(
        new StringNode(toggleType.getPath().path(), QuoteStyle.DOUBLE, refn.getSourceLocation()));
    funcNode.addChild(
        new StringNode(toggleType.getName(), QuoteStyle.DOUBLE, refn.getSourceLocation()));
    funcNode.setType(StringType.getInstance());

    refn.getParent().replaceChild(refn, (ExprNode) funcNode);
  }
}
