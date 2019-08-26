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

package com.google.template.soy.incrementaldomsrc;

import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.HtmlTagNode;
import com.google.template.soy.soytree.SkipNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import com.google.template.soy.soytree.SoyTreeUtils;

/**
 * Transforms
 *
 * <pre>{@code
 * ```soy
 * <div {key 0} {skip}></div>
 * ```
 * to
 * ```soy
 * {skip}
 *   <div {key 0}></div>
 * {/skip}
 *
 * This makes it easier for Incremental DOM to transform it into the following
 *
 * ```
 * if (openForSSR('div', '0')) {
 *   close();
 * }
 * ```
 * In other backends, this is just translated to
 * ```
 *   <div soy-server-key="0"></div>
 * ```
 * }</pre>
 *
 * which allows Incremental DOM to find these elements even if they have not been hydrated.
 */
final class TransformSkipNodeVisitor {

  static void reparentSkipNodes(SoyNode node) {
    for (SkipNode skipNode : SoyTreeUtils.getAllNodesOfType(node, SkipNode.class)) {
      HtmlOpenTagNode openTag = (HtmlOpenTagNode) skipNode.getParent();
      ParentSoyNode<StandaloneNode> parent = openTag.getParent();
      HtmlTagNode closeTag = openTag;
      if (openTag.getTaggedPairs().size() == 1) {
        closeTag = openTag.getTaggedPairs().get(0);
      }
      int startIndex = parent.getChildIndex(openTag);
      int end = parent.getChildIndex(closeTag);
      for (int i = startIndex; i <= end; i++) {
        StandaloneNode child = parent.getChild(startIndex);
        skipNode.addChild(child);
        parent.removeChild(child);
        child.setParent(skipNode);
      }
      parent.addChild(startIndex, skipNode);
      skipNode.setParent(parent);
    }
  }

  private TransformSkipNodeVisitor() {}
}
