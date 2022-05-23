/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.soytree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Multimaps.asMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class to record all the edits to the AST we need to make.
 *
 * <p>Instead of editing the AST as we go, we record the edits and wait until the end, this makes a
 * few things easier.
 *
 * <ul>
 *   <li>we don't have to worry about editing nodes while visiting them
 *   <li>we can easily avoid making any edits if errors were recorded.
 *       <p>This is encapsulated in its own class so we can easily pass it around and to provide
 *       some encapsulation.
 * </ul>
 */
public final class AstEdits {
  final Set<StandaloneNode> toRemove = new LinkedHashSet<>();
  final ListMultimap<StandaloneNode, StandaloneNode> replacements =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();
  final ListMultimap<ParentSoyNode<StandaloneNode>, StandaloneNode> newChildren =
      MultimapBuilder.linkedHashKeys().arrayListValues().build();

  /** Apply all edits. */
  public void apply() {
    for (StandaloneNode nodeToRemove : toRemove) {
      ParentSoyNode<StandaloneNode> parent = nodeToRemove.getParent();
      int index = parent.getChildIndex(nodeToRemove);
      // NOTE:  we need to remove the child before adding the new children  to handle the case
      // where we are doing a no-op replacement or the replacement nodes contains nodeToRemove.
      // no-op replacements can occur when there are pcdata sections that contain no tags
      parent.removeChild(index);
      List<StandaloneNode> children = replacements.get(nodeToRemove);
      if (!children.isEmpty()) {
        parent.addChildren(index, children);
      }
    }
    for (Map.Entry<ParentSoyNode<StandaloneNode>, List<StandaloneNode>> entry :
        asMap(newChildren).entrySet()) {
      entry.getKey().addChildren(entry.getValue());
    }
    clear();
  }

  /** Mark a node for removal. */
  public void remove(StandaloneNode node) {
    checkNotNull(node);
    // only record this if the node is actually in the tree already.  Sometimes we call remove
    // on new nodes that don't have parents yet.
    if (node.getParent() != null) {
      toRemove.add(node);
    }
  }

  /** Add children to the given parent. */
  public void addChildren(ParentSoyNode<StandaloneNode> parent, Iterable<StandaloneNode> children) {
    checkNotNull(parent);
    newChildren.putAll(parent, children);
  }

  /** Adds the child to the given parent. */
  public void addChild(ParentSoyNode<StandaloneNode> parent, StandaloneNode child) {
    checkNotNull(parent);
    checkNotNull(child);
    newChildren.put(parent, child);
  }

  /** Replace a given node with the new nodes. */
  public void replace(StandaloneNode oldNode, Iterable<StandaloneNode> newNodes) {
    checkState(oldNode.getParent() != null, "oldNode must be in the tree in order to replace it");
    remove(oldNode);
    replacements.putAll(oldNode, newNodes);
  }

  /** Replace a given node with the new node. */
  public void replace(StandaloneNode oldNode, StandaloneNode newNode) {
    replace(oldNode, ImmutableList.of(newNode));
  }

  /** Clear all the edits. */
  public void clear() {
    toRemove.clear();
    replacements.clear();
    newChildren.clear();
  }
}
