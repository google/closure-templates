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
 */
public class RemoveHtmlCommentsVisitor extends AbstractSoyNodeVisitor<Void> {

  // TODO: Make sure this doesn't remove escaping text spans in CSS or JavaScript.
  // E.g. <style><!-- ... --></style>
  //      <script>while (i<!--x) { ... } while (j-->0) { ... }</script>


  /** Regex pattern for an HTML comment. */
  private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->");


  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private IdGenerator nodeIdGen;


  @Override public Void exec(SoyNode node) {
    nodeIdGen = null;
    visit(node);
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitRawTextNode(RawTextNode node) {

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
    if (newRawText.length() > 0) {
      if (nodeIdGen == null) {
        // Retrieve the node id generator from the root of the parse tree.
        nodeIdGen = node.getNearestAncestor(SoyFileSetNode.class).getNodeIdGenerator();
      }
      RawTextNode newRawTextNode = new RawTextNode(nodeIdGen.genId(), newRawText.toString());
      node.getParent().replaceChild(node, newRawTextNode);

    } else {
      node.getParent().removeChild(node);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildrenAllowingConcurrentModification((ParentSoyNode<?>) node);
    }
  }

}
