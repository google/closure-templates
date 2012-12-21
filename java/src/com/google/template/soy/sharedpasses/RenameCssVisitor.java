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

package com.google.template.soy.sharedpasses;

import com.google.template.soy.base.IdGenerator;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.ExprUnion;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import javax.annotation.Nullable;


/**
 * Visitor for renaming CSS selector text in a Soy parse tree. This pass replaces the CssNodes
 * in the parse tree with RawTextNodes and PrintNodes (PrintNodes needed iff some CssNodes contain
 * component name). After this pass, the parse tree should no longer contain CssNodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Note that the Soy tree is usually simplifiable after this pass is run (e.g. it usually
 * contains consecutive RawTextNodes). It's usually advisable to run a simplification pass after
 * this pass.
 *
 * @author Kai Huang
 */
public class RenameCssVisitor extends AbstractSoyNodeVisitor<Void> {


  /** The CSS renaming map to use for renaming selector text, or null to use the source text. */
  private final SoyCssRenamingMap cssRenamingMap;

  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;


  /**
   * @param cssRenamingMap The CSS renaming map to use for renaming selector text, or null to use
   *     the source text.
   */
  public RenameCssVisitor(@Nullable SoyCssRenamingMap cssRenamingMap) {
    this.cssRenamingMap = cssRenamingMap;
  }


  @Override public Void exec(SoyNode node) {

    // Retrieve the node id generator from the root of the parse tree.
    nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();

    // Execute the pass.
    super.exec(node);

    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete nodes.


  @Override protected void visitCssNode(CssNode node) {

    // Remove this CssNode. Save the index because we'll need it for inserting the new nodes.
    BlockNode parent = node.getParent();
    int indexInParent = parent.getChildIndex(node);
    parent.removeChild(indexInParent);

    // If this CssNode has componentName, add a PrintNode (with '|id' directive) to print it.
    ExprRootNode<?> componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      PrintNode pn =
          new PrintNode(nodeIdGen.genId(), false, new ExprUnion(componentNameExpr), null);
      pn.addChild(new PrintDirectiveNode(nodeIdGen.genId(), "|id", ""));
      parent.addChild(indexInParent, pn);
      indexInParent += 1;
    }

    // Add a RawTextNode for the selectorText. Also includes preceding dash ("-") if there is a
    // preceding componentName.
    String selectorText = node.getSelectorText();
    if (cssRenamingMap != null) {
      String mappedText = cssRenamingMap.get(selectorText);
      if (mappedText != null) {
        selectorText = mappedText;
      }
    }
    if (componentNameExpr != null) {
      selectorText = "-" + selectorText;
    }
    parent.addChild(indexInParent, new RawTextNode(nodeIdGen.genId(), selectorText));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }

}
