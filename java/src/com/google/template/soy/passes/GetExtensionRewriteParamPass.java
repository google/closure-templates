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
import com.google.template.soy.base.internal.QuoteStyle;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.Kind;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.MethodCallNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinMethod;
import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * A compiler pass that rewrites the parameter of the {@link BuiltinMethod#GET_EXTENSION} et al
 * methods into a StringNode from a GlobalNode to prevent unbound global errors.
 */
final class GetExtensionRewriteParamPass implements CompilerFilePass {

  GetExtensionRewriteParamPass() {}

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.getAllNodesOfType(file, MethodCallNode.class).stream()
        .filter(MethodCallNode::isMethodResolved)
        .filter(
            n -> {
              SoyMethod method = n.getSoyMethod();
              return method == BuiltinMethod.GET_EXTENSION;
            })
        .forEach(
            node -> {
              // Rewrite the global parameter of getExtension methods.
              for (int i = 1; i < node.numChildren(); i++) {
                ExprNode child = node.getChild(i);
                if (child.getKind() == Kind.GLOBAL_NODE) {
                  node.replaceChild(
                      i,
                      new StringNode(
                          ((GlobalNode) child).getName(),
                          QuoteStyle.DOUBLE,
                          child.getSourceLocation()));
                }
              }
            });
  }
}
