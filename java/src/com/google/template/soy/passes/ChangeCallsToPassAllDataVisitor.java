/*
 * Copyright 2011 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.exprparse.SoyParsingContext;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.VarRefNode;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallNode.DataAttribute;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Visitor to change {@code call}s to use {@code data="all"} whenever possible.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p>This visitor must be called on a SoyFileSetNode, SoyFileNode, or TemplateNode (i.e. template
 * or ancestor of a template).
 *
 */
public final class ChangeCallsToPassAllDataVisitor extends AbstractSoyNodeVisitor<Void> {

  @Override
  public Void exec(SoyNode node) {

    Preconditions.checkArgument(
        node instanceof SoyFileSetNode
            || node instanceof SoyFileNode
            || node instanceof TemplateNode);
    visit(node);
    return null;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete nodes.

  @Override
  protected void visitCallNode(CallNode node) {

    // If there are no params (i.e. children), then this optimization doesn't apply.
    if (node.numChildren() == 0) {
      return;
    }

    // Recurse.
    visitChildrenAllowingConcurrentModification(node);

    // If this call already passes data (but not all data), then this optimization doesn't apply.
    if (node.dataAttribute().isPassingData() && !node.dataAttribute().isPassingAllData()) {
      return;
    }

    // Check whether the params (i.e. children) are all
    // (a) of the form {param <key>: $<key> /}, and
    // (b) referencing regular data (not local vars or injected data).
    // If not, then stop (i.e. return) because we cannot pass data="all" instead.
    for (CallParamNode param : node.getChildren()) {
      if (!(param instanceof CallParamValueNode)) {
        return;
      }
      CallParamValueNode valueParam = (CallParamValueNode) param;
      if (!("$" + valueParam.getKey()).equals(valueParam.getValueExprText())) {
        return;
      }
      ExprRootNode valueExprRoot = ((CallParamValueNode) param).getValueExprUnion().getExpr();
      if (valueExprRoot == null) {
        return;
      }
      VarRefNode valueDataRef = (VarRefNode) valueExprRoot.getRoot();
      if (valueDataRef.isLocalVar() || valueDataRef.isInjected()) {
        return;
      }
    }

    // Change this call to pass data="all" and remove all params. (We reuse the node id.)
    CallNode newCallNode;
    if (node instanceof CallBasicNode) {
      CallBasicNode nodeCast = (CallBasicNode) node;
      newCallNode =
          new CallBasicNode.Builder(node.getId(), node.getSourceLocation())
              .calleeName(nodeCast.getCalleeName())
              .sourceCalleeName(nodeCast.getSrcCalleeName())
              .dataAttribute(DataAttribute.all())
              .userSuppliedPlaceholderName(node.getUserSuppliedPhName())
              .escapingDirectiveNames(node.getEscapingDirectiveNames())
              // Use the exploding reporter since we know it won't report any errors
              .build(SoyParsingContext.exploding());
    } else {
      CallDelegateNode nodeCast = (CallDelegateNode) node;
      newCallNode =
          new CallDelegateNode.Builder(node.getId(), node.getSourceLocation())
              .delCalleeName(nodeCast.getDelCalleeName())
              .delCalleeVariantExpr(nodeCast.getDelCalleeVariantExpr())
              .allowEmptyDefault(nodeCast.allowsEmptyDefault())
              .dataAttribute(DataAttribute.all())
              .userSuppliedPlaceholderName(node.getUserSuppliedPhName())
              .escapingDirectiveNames(node.getEscapingDirectiveNames())
              // Use the exploding reporter since we know it won't report any errors
              .build(SoyParsingContext.exploding());
    }
    node.getParent().replaceChild(node, newCallNode);
  }

  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.

  @Override
  protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }
}
