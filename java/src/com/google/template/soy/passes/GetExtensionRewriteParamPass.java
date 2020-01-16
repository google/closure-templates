/*
 * Copyright 2020 Google Inc.
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
import com.google.template.soy.basicmethods.GetExtensionMethod;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.MethodNode;
import com.google.template.soy.exprtree.ProtoExtensionIdNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * A compiler pass that rewrites the parameter of the {@link GetExtensionMethod} method into a
 * ProtoExtensionIdNode from a GlobalNode to prevent unbound global errors.
 */
final class GetExtensionRewriteParamPass implements CompilerFilePass {

  GetExtensionRewriteParamPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    for (MethodNode node : SoyTreeUtils.getAllNodesOfType(file, MethodNode.class)) {
      if (node.isMethodResolved() && node.getSoyMethods().get(0) instanceof GetExtensionMethod) {
        // Rewrite the global parameter of getExtension methods.
        ExprNode child = node.getChild(1);
        if (child.getKind() == Kind.GLOBAL_NODE) {
          node.replaceChild(
              1,
              new ProtoExtensionIdNode(((GlobalNode) child).getName(), child.getSourceLocation()));
        }
      }
    }
  }
}
