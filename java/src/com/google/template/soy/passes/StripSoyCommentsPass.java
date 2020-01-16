/*
 * Copyright 2019 Google Inc.
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

import static com.google.template.soy.soytree.SoyTreeUtils.getAllNodesOfType;

import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.LineCommentNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.HashSet;
import java.util.Set;

/**
 * Visitor for deleting no-op Soy comment nodes from AST.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private). TODO(b/140196029):
 * Use line joining logic extracted from RawTextBuilder.
 *
 */
final class StripSoyCommentsPass implements CompilerFilePass {

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    HashSet<ParentSoyNode<StandaloneNode>> commentParents = new HashSet<>();
    for (LineCommentNode lineComment : getAllNodesOfType(file, LineCommentNode.class)) {
      commentParents.add(lineComment.getParent());
    }
    for (ParentSoyNode<StandaloneNode> parent : commentParents) {
      Set<LineCommentNode> commentsToDelete = new HashSet<>();
      SoyNode prev = null;
      for (int i = 0; i < parent.numChildren(); i++) {
        SoyNode node = parent.getChild(i);
        if (node instanceof LineCommentNode) {
          if (i < parent.numChildren() - 1) {
            SoyNode next = parent.getChild(i + 1);
            if (next instanceof RawTextNode) {
              RawTextNode nextRawTextNode = (RawTextNode) next;
              String nextRawText = nextRawTextNode.getRawText();
              if (!nextRawText.startsWith(" ")
                  && !nextRawText.startsWith("\n")
                  && !nextRawText.startsWith("<")
                  && !nextRawTextNode.commandAt(0)) {
                if (prev instanceof RawTextNode) {
                  RawTextNode prevRawTextNode = (RawTextNode) prev;
                  String prevRawText = prevRawTextNode.getRawText();
                  if (!prevRawText.endsWith(" ")
                      && !prevRawText.endsWith("\n")
                      && !prevRawText.endsWith(">")
                      && !prevRawTextNode.commandAt(prevRawText.length())) {
                    // When parsed, line comments greedily capture all the leading whitespace that
                    // otherwise could have been a part of a RawTextNode and would have been a
                    // subject to line joining algorithm. This means, that when we deleting a line
                    // comment, that is sandwiched between two RawTextNodes, and neither of them
                    // have whitespaces around adjacent to it, insted of deleting the comment we
                    // must replace it with a single whitespace — as line joining would.
                    RawTextNode replacement =
                        new RawTextNode(nodeIdGen.genId(), " ", node.getSourceLocation());
                    parent.replaceChild(i, replacement);
                    prev = replacement;
                    continue;
                  }
                }
              }
            }
          }
          commentsToDelete.add((LineCommentNode) node);
        } else {
          prev = node;
        }
      }

      commentsToDelete.forEach(parent::removeChild);
    }
  }
}
