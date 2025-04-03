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
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.defn.SymbolVar;
import com.google.template.soy.soytree.defn.SymbolVar.SymbolKind;
import com.google.template.soy.types.StringType;

/**
 * Rewrite toggle import statement and references to call built-in function that supports toggles as
 * an {@link SymbolVar} vardef.
 */
@RunAfter(ResolveNamesPass.class)
final class RewriteToggleRefsPass implements CompilerFilePass {

  public RewriteToggleRefsPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, VarRefNode.class)
        .forEach(
            ref -> {
              VarDefn defn = ref.getDefnDecl();
              if (defn instanceof SymbolVar) {
                SymbolVar symbolVar = (SymbolVar) defn;
                if (symbolVar.getSymbolKind() == SymbolKind.TOGGLE) {
                  rewriteToggleNode(ref, ref.getSourceLocation(), symbolVar);
                }
              }
            });
  }

  /**
   * Returns a new expr node that evaluates the value of the toggle named {@code toggleName} on the
   * {@code refn} value. Returns either a primitive node or var ref node or null if the field could
   * not be resolved.
   */
  private void rewriteToggleNode(VarRefNode refn, SourceLocation fullLocation, SymbolVar defn) {
    FunctionNode funcNode =
        FunctionNode.newPositional(
            Identifier.create(BuiltinFunction.EVAL_TOGGLE.getName(), fullLocation),
            BuiltinFunction.EVAL_TOGGLE,
            fullLocation);

    // Add toggle path and name as parameters to built-in function
    funcNode.addChild(
        new StringNode(
            defn.getSourceFilePath().path(), QuoteStyle.DOUBLE, refn.getSourceLocation()));
    funcNode.addChild(
        new StringNode(defn.getSymbol(), QuoteStyle.DOUBLE, refn.getSourceLocation()));
    funcNode.setType(StringType.getInstance());

    refn.getParent().replaceChild(refn, funcNode);
  }
}
