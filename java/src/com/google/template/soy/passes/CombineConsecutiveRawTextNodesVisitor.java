/*
 * Copyright 2009 Google Inc.
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

import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

/**
 * Visitor for combining any consecutive sequences of {@code RawTextNode}s into one equivalent
 * {@code RawTextNode}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CombineConsecutiveRawTextNodesVisitor extends AbstractSoyNodeVisitor<Void> {

  @Override
  protected void visitSoyNode(SoyNode node) {

    if (!(node instanceof ParentSoyNode<?>)) {
      return;
    }

    ParentSoyNode<?> nodeAsParent = (ParentSoyNode<?>) node;

    // The raw text node at the beginning of the current sequence
    RawTextNode current = null;
    for (int i = 0; i < nodeAsParent.numChildren(); i++) {
      SoyNode child = nodeAsParent.getChild(i);
      if (child instanceof RawTextNode) {
        RawTextNode childAsRawText = (RawTextNode) child;
        if (childAsRawText.getRawText().isEmpty()) {
          // just delete the node
          nodeAsParent.removeChild(i);
          i--;
        } else if (current == null) {
          current = childAsRawText;
        } else {
          current = current.concat(current.getId(), childAsRawText);
          nodeAsParent.removeChild(i);
          // use raw types to allow us to set the child.  This is a case where the generic types
          // on our ParentSoyNode class do not help us.
          ((ParentSoyNode) nodeAsParent).replaceChild(i - 1, current);
          i--; // to account for the ++ on the next iteration
        }
      } else {
        current = null; // reset
        visit(child); // recurse
      }
    }
  }
}
