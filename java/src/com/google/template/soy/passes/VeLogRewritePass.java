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

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.VeLogNode;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.VeDataType;

/**
 * A compiler pass that rewrites syntactic sugar related to the {@code {velog ...}} command:
 *
 * <ul>
 *   <li>Rewrites {@code {velog $ve}} (where {@code $ve} is any "ve" typed expression) to {@code
 *       {velog(ve_data($ve, null))}}.
 * </ul>
 *
 * <p>This must run after {@link ResolveExpressionTypesPass} because it needs the type information
 * on {@code $ve} to know to rewrite it.
 */
final class VeLogRewritePass extends CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (VeLogNode node : SoyTreeUtils.getAllNodesOfType(file, VeLogNode.class)) {
      maybeRewriteVeLogNode(node);
    }
  }

  private void maybeRewriteVeLogNode(VeLogNode node) {
    ExprNode veExpr = node.getVeDataExpression().getRoot();
    if (veExpr.getType().getKind() == Kind.VE) {
      FunctionNode veData =
          new FunctionNode(
              Identifier.create(BuiltinFunction.VE_DATA.getName(), veExpr.getSourceLocation()),
              BuiltinFunction.VE_DATA,
              veExpr.getSourceLocation());
      // Explicitly set the type here. This pass has to run after ResolveExpressionTypesPass since
      // it requires the type information set in ResolveExpressionTypesPass, so we need to set the
      // type on our own here.
      veData.setType(VeDataType.getInstance());
      veData.addChild(veExpr);
      // Explicitly set the second parameter to null. This parameter is optional, but it's rewritten
      // to null (if left off) by the VeRewritePass. The VeRewritePass runs before this so we can't
      // rely on that to add the null parameter, so we have to do it ourselves here.
      veData.addChild(new NullNode(veExpr.getSourceLocation()));
      node.getVeDataExpression().addChild(veData);
    }
  }
}
