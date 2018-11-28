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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import java.util.List;

/**
 * Visitor for combining any consecutive sequences of {@code RawTextNode}s into one equivalent
 * {@code RawTextNode}.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CombineConsecutiveRawTextNodesPass extends CompilerFileSetPass {

  @Override
  public Result run(
      ImmutableList<SoyFileNode> sourceFiles, IdGenerator idGenerator, TemplateRegistry registry) {
    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getChildren()) {
        run(template);
      }
    }
    return Result.CONTINUE;
  }

  /** Run the pass on a single node. */
  public void run(ParentSoyNode<?> node) {
    visit(node);
  }

  private void visit(ParentSoyNode<?> node) {
    // The raw text node at the beginning of the current sequence
    int start = -1;
    int lastNonEmptyRawTextNode = -1;
    int i = 0;
    for (; i < node.numChildren(); i++) {
      SoyNode child = node.getChild(i);
      if (child instanceof RawTextNode) {
        RawTextNode childAsRawText = (RawTextNode) child;
        if (start == -1) {
          // drop empty raw text nodes at the prefix
          if (childAsRawText.getRawText().isEmpty()) {
            node.removeChild(i);
            i--;
          } else {
            // mark the beginning of a sequence of nonempty raw text
            start = i;
            lastNonEmptyRawTextNode = i;
          }
        } else {
          if (!childAsRawText.getRawText().isEmpty()) {
            lastNonEmptyRawTextNode = i;
          }
        }
      } else {
        i = mergeRange(node, start, lastNonEmptyRawTextNode, i);
        // reset
        start = -1;
        if (child instanceof ParentSoyNode) {
          visit((ParentSoyNode<?>) child); // recurse
        }
        // else do nothing since it cannot contain raw text nodes
      }
    }
    mergeRange(node, start, lastNonEmptyRawTextNode, i);
  }

  // There is no generic type we could give to ParentSoyNode that wouldn't require unchecked casts
  // either here or in our caller.  This is safe however since we are only adding or removing
  // RawTextNodes and if we can remove a RawTextNode, we can also add one.
  @SuppressWarnings("unchecked")
  private int mergeRange(ParentSoyNode<?> parent, int start, int lastNonEmptyRawTextNode, int end) {
    checkArgument(start < end);
    if (start == -1 || end == start + 1) {
      return end;
    }
    // general case, there are N rawtextnodes to merge where n > 1
    // merge all the nodes together, then drop all the raw text nodes from the end
    RawTextNode newNode =
        RawTextNode.concat(
            (List<RawTextNode>) parent.getChildren().subList(start, lastNonEmptyRawTextNode + 1));
    ((ParentSoyNode) parent).replaceChild(start, newNode);
    for (int i = end - 1; i > start; i--) {
      parent.removeChild(i);
    }
    return start + 1;
  }
}
