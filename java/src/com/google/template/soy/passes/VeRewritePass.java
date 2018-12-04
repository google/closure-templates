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
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.VeLiteralNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * A compiler pass that rewrites syntactic sugar related to VE logging:
 *
 * <ul>
 *   <li>Rewrites {@code ve_data(MyVe, $data)} to {@code ve_data(ve(MyVe), $data)}
 *   <li>Rewrites {@code ve_data(ve(MyVe))} to {@code ve_data(ve(MyVe), null)}
 * </ul>
 */
final class VeRewritePass extends CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (FunctionNode node :
        SoyTreeUtils.getAllFunctionInvocations(file, BuiltinFunction.VE_DATA)) {
      maybeRewriteNode(node);
    }
  }

  private void maybeRewriteNode(FunctionNode node) {
    if (node.getChild(0).getKind() == Kind.GLOBAL_NODE) {
      // For something like ve_data(MyVe, $data) MyVe will be a global. Rewrite it to ve(MyVe).
      GlobalNode global = (GlobalNode) node.getChild(0);
      VeLiteralNode veNode =
          new VeLiteralNode(
              Identifier.create(global.getName(), global.getSourceLocation()),
              global.getSourceLocation());
      node.replaceChild(0, veNode);
    }
    if (node.numChildren() < 2) {
      // For ve_data(MyVe) set the data parameter to null.
      node.addChild(new NullNode(node.getSourceLocation().getEndLocation()));
    }
  }
}
