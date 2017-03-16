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


import com.google.template.soy.base.internal.IdGenerator;
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

  /** The node id generator for the parse tree. Retrieved from the root SoyFileSetNode. */
  private final IdGenerator nodeIdGen;

  public CombineConsecutiveRawTextNodesVisitor(IdGenerator nodeIdGen) {
    this.nodeIdGen = nodeIdGen;
  }

  @Override
  protected void visitSoyNode(SoyNode node) {

    if (!(node instanceof ParentSoyNode<?>)) {
      return;
    }

    ParentSoyNode<?> nodeAsParent = (ParentSoyNode<?>) node;

    // where the most recent sequence of raw text nodes starts
    int rawTextSeqStart = -1;
    for (int i = 0; i < nodeAsParent.numChildren(); i++) {
      SoyNode child = nodeAsParent.getChild(i);
      if (child instanceof RawTextNode) {
        if (rawTextSeqStart == -1) {
          rawTextSeqStart = i;
        }
        // the next node is not a raw text node (or we are at the end)
        if (i == nodeAsParent.numChildren() - 1
            || !(nodeAsParent.getChild(i + 1) instanceof RawTextNode)) {
          // we have more than one raw text node, combine them
          if (rawTextSeqStart < i) {
            // This is safe because we already know it has RawTextNodes as children
            @SuppressWarnings("unchecked")
            ParentSoyNode<? super RawTextNode> typedParent =
                (ParentSoyNode<? super RawTextNode>) nodeAsParent;
            combineRawTextNodes(typedParent, rawTextSeqStart, i + 1);
            // We just replaced [rawTextSeqStart, i] with a single node at rawTextSeqStart
            // so reset i to be rawTextSeqStart so that on the next loop iteration, we move on to
            // the next item. In other words, the item that was previously at i+1 is now at
            // rawTextSeqStart+1.
            i = rawTextSeqStart;
          } else {
            // exactly one node, is it empty?
            if (((RawTextNode) child).isEmpty()) {
              nodeAsParent.removeChild(i);
              i--; // move back
            }
          }
          // reset the start of the sequence
          rawTextSeqStart = -1;
        }
      } else {
        visit(child); // recurse
      }
    }
  }

  /**
   * Collapses a range of RawTextNodes between start and end into a single raw text node using
   * {@link RawTextNode#concat(int, RawTextNode)}
   */
  private void combineRawTextNodes(ParentSoyNode<? super RawTextNode> parent, int start, int end) {
    // Since we know this parent had raw text nodes as children it must be able to handle
    // standalone nodes.
    // We have just finished a sequence of raw text nodes that is more than one item
    int newId = nodeIdGen.genId();
    RawTextNode newNode = (RawTextNode) parent.getChild(start);
    for (int i = start + 1; i < end; i++) {
      newNode = newNode.concat(newId, (RawTextNode) parent.getChild(i));
    }
    // it is slightly more efficient to remove in reverse order
    // TODO(lukes): add a 'removeRange' method, it would be faster
    for (int i = end - 1; i > start; i--) {
      parent.removeChild(i);
    }
    parent.replaceChild(start, newNode);
  }
}
