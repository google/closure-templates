/*
 * Copyright 2008 Google Inc.
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
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Visitor for removing HTML comments from raw text. Note that this is a best-effort process.
 * Currently, we only remove HTML comments that are completely contained within a single
 * RawTextNode.
 *
 * @author Kai Huang
 */
public class RemoveHtmlCommentsVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Regex pattern for an HTML comment. */
  private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->");


  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;


  @Override protected void setup() {
    nodeIdGen = null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitInternal(RawTextNode node) {

    Matcher matcher = HTML_COMMENT.matcher(node.getRawText());
    if (!matcher.find()) {
      return;
    }

    // Build the new raw text string.
    matcher.reset();
    StringBuffer newRawText = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(newRawText, "");
    }
    matcher.appendTail(newRawText);

    // If the new raw text string is nonempty, then create a new RawTextNode to replace this node,
    // else simply remove this node.

    @SuppressWarnings("unchecked")  // cast involving type parameter
    ParentSoyNode<? super RawTextNode> parent =
        (ParentSoyNode<? super RawTextNode>) node.getParent();

    if (newRawText.length() > 0) {
      if (nodeIdGen == null) {
        // Retrieve the node id generator from the root of the parse tree.
        nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGen();
      }
      RawTextNode newRawTextNode = new RawTextNode(nodeIdGen.genStringId(), newRawText.toString());
      parent.setChild(parent.getChildIndex(node), newRawTextNode);

    } else {
      parent.removeChild(node);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitInternal(SoyNode node) {
    // Nothing to do for non-parent node.
  }


  @Override protected void visitInternal(ParentSoyNode<? extends SoyNode> node) {
    // Note: We're possibly replacing/removing children while iterating through them. I thought
    // this would cause errors, but it seems to work fine. If this starts to cause errors, we'll
    // have to rewrite it.
    visitChildren(node);
  }

}
