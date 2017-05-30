/*
 * Copyright 2017 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import javax.annotation.Nullable;

/**
 * Base class for html tags. Provides easy access to the {@link TagName}.
 *
 * <p>The first child is guaranteed to be the tag name, any after that are guaranteed to be in
 * attribute context. There is always at least one child.
 */
public abstract class HtmlTagNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode {

  private final TagName tagName;

  protected HtmlTagNode(int id, TagName tagName, SourceLocation sourceLocation) {
    super(id, sourceLocation);
    this.tagName = checkNotNull(tagName);
  }

  protected HtmlTagNode(HtmlTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.tagName = orig.tagName;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  public final TagName getTagName() {
    return tagName;
  }

  /**
   * Returns a direct child attribute node for a {@code phname} attribute, or {@code null} if there
   * is no such attribute.
   *
   * <p>The {@code phname} attribute has special handling within {@code msg} tags, where it is used
   * to allow users to specify their own placeholders.
   */
  @Nullable
  public HtmlAttributeNode getPhNameNode() {
    // the child at index 0 is the tag name
    for (int i = 1; i < numChildren(); i++) {
      StandaloneNode child = getChild(i);
      if (child instanceof HtmlAttributeNode) {
        HtmlAttributeNode attr = (HtmlAttributeNode) child;
        if (attr.definitelyMatchesAttributeName("phname") && attr.hasValue()) {
          // leave actual value validation until later
          return attr;
        }
      }
    }
    return null;
  }
}
