/*
 * Copyright 2022 Google Inc.
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
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.types.StringType;
import java.util.ArrayList;
import java.util.List;

/**
 * A pass that finds css() calls, and if there are multiple classes (i.e. spaces), breaks it up into
 * multiple css() calls so that each class is individually renamed.
 */
@RunAfter(ResolvePluginsPass.class) // Run after all passes that resolve function nodes.
final class SplitCssFunctionPass implements CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    SoyTreeUtils.allNodesOfType(file, FunctionNode.class)
        .forEach(SplitCssFunctionPass::maybeSplitCssCall);
  }

  static void maybeSplitCssCall(FunctionNode node) {
    if (!node.isResolved()
        || node.getSoyFunction() != BuiltinFunction.CSS
        || node.getParams().size() == 0) {
      return;
    }
    ExprNode exprNode =
        node.getParams().size() == 2 ? node.getParams().get(1) : node.getParams().get(0);
    ExprNode baseClassExpr = node.getParams().size() == 2 ? node.getParams().get(0) : null;
    if (!(exprNode instanceof StringNode)) {
      return;
    }
    StringNode stringNode = (StringNode) exprNode;
    String[] stringParts = stringNode.getValue().split("\\s+");
    if (stringParts.length == 1) {
      return;
    }
    List<ExprNode> exprNodeParts = new ArrayList<>();
    CopyState copyState = new CopyState();
    exprNodeParts.add(makeCss(stringParts[0], baseClassExpr, node.getSourceLocation(), copyState));
    for (int i = 1; i < stringParts.length; i++) {
      exprNodeParts.add(new StringNode(" ", QuoteStyle.SINGLE, node.getSourceLocation()));
      exprNodeParts.add(
          makeCss(stringParts[i], baseClassExpr, node.getSourceLocation(), copyState));
    }
    node.getParent().replaceChild(node, concat(exprNodeParts, node.getSourceLocation()));
  }

  static ExprNode concat(List<ExprNode> parts, SourceLocation sourceLocation) {
    PlusOpNode plusOp = new PlusOpNode(sourceLocation, sourceLocation);
    plusOp.setType(StringType.getInstance());
    plusOp.addChild(parts.get(0));
    if (parts.size() == 2) {
      plusOp.addChild(parts.get(1));
      return plusOp;
    }
    plusOp.addChild(concat(parts.subList(1, parts.size()), sourceLocation));
    return plusOp;
  }

  static FunctionNode makeCss(
      String selector, ExprNode baseClassExpr, SourceLocation sourceLocation, CopyState copyState) {
    FunctionNode node =
        FunctionNode.newPositional(
            Identifier.create(BuiltinFunction.CSS.getName(), sourceLocation),
            BuiltinFunction.CSS,
            sourceLocation);
    node.setType(StringType.getInstance());
    if (baseClassExpr != null) {
      node.addChild(baseClassExpr.copy(copyState));
    }
    node.addChild(new StringNode(selector, QuoteStyle.SINGLE, sourceLocation));
    return node;
  }
}
