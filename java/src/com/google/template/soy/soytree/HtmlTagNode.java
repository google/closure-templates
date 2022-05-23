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
import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.soytree.SoyNode.StandaloneNode;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Base class for html tags. Provides easy access to the {@link TagName}.
 *
 * <p>The first child is guaranteed to be the tag name, any after that are guaranteed to be in
 * attribute context. There is always at least one child.
 *
 * <p>TODO(b/123090196): Merge {@link TagName} API into this class.
 */
public abstract class HtmlTagNode extends AbstractParentSoyNode<StandaloneNode>
    implements StandaloneNode {

  /**
   * Indicates whether this tag was created organically from the template source, or if it is a a
   * synthetic tag node, created by injecting it into the AST during a validation phase, such as
   * {@link StrictHtmlValidationPass}
   *
   * <p>Some backends do not render synthetic tags, others (like iDOM) do special processing on
   * synthetic tags.
   *
   * <ul>
   *   <li>{@code IN_TEMPLATE} tags come from parsing the original template.
   *   <li>{@code SYNTHETIC} tags are injected during an AST rewrite.
   * </ul>
   */
  public enum TagExistence {
    IN_TEMPLATE,
    SYNTHETIC
  }

  private final TagName tagName;

  private final TagExistence tagExistence;

  /**
   * Represents a list of tags that this HtmlTagNode might be paired with. For example, if we have
   * an element `<div></div>`, the HTMLOpenTagNode would have the HTMLCloseTagNode in its
   * taggedPairs (and vice versa). This is a list because an open tag node might have multiple close
   * tag nodes (and vice versa) depending on control flow.
   */
  private final List<HtmlTagNode> taggedPairs = new ArrayList<>();

  protected HtmlTagNode(
      int id, StandaloneNode node, SourceLocation sourceLocation, TagExistence tagExistence) {
    super(id, sourceLocation);
    checkNotNull(node);
    checkState(node.getParent() == null);
    addChild(node);
    this.tagName = tagNameFromNode(node);
    this.tagExistence = tagExistence;
  }

  protected HtmlTagNode(HtmlTagNode orig, CopyState copyState) {
    super(orig, copyState);
    this.tagExistence = orig.tagExistence;
    //  Rebuild the TagName object
    if (numChildren() > 0) {
      StandaloneNode tagChild = getChild(0);
      this.tagName = tagNameFromNode(tagChild);
    } else {
      this.tagName = null;
    }
    // The taggedPairs field contains references to other tag nodes.
    // We need to register ourselves so that people who reference us get updated and we need to
    // listen to updates
    copyState.updateRefs(orig, this);
    for (HtmlTagNode matchingNode : orig.taggedPairs) {
      copyState.registerRefListener(
          matchingNode,
          new CopyState.Listener<HtmlTagNode>() {
            @Override
            public void newVersion(HtmlTagNode newMatchingNode) {
              taggedPairs.add(newMatchingNode);
            }
          });
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public final ParentSoyNode<StandaloneNode> getParent() {
    return (ParentSoyNode<StandaloneNode>) super.getParent();
  }

  public final TagName getTagName() {
    return tagName;
  }

  public List<HtmlTagNode> getTaggedPairs() {
    return this.taggedPairs;
  }

  public void addTagPair(HtmlTagNode node) {
    if (!this.taggedPairs.contains(node)) {
      this.taggedPairs.add(node);
    }
  }

  /** Returns true if this node was inserted by the {@code StrictHtmlValidationPass}. */
  public boolean isSynthetic() {
    return tagExistence == TagExistence.SYNTHETIC;
  }

  /** Returns an attribute with the given static name if it is a direct child. */
  @Nullable
  public HtmlAttributeNode getDirectAttributeNamed(String attrName) {
    // the child at index 0 is the tag name
    for (int i = 1; i < numChildren(); i++) {
      StandaloneNode child = getChild(i);
      if (child instanceof HtmlAttributeNode) {
        HtmlAttributeNode attr = (HtmlAttributeNode) child;
        if (attr.definitelyMatchesAttributeName(attrName)) {
          return attr;
        }
      }
    }
    return null;
  }

  private static TagName tagNameFromNode(StandaloneNode rawTextOrPrintNode) {
    checkState(
        rawTextOrPrintNode instanceof RawTextNode || rawTextOrPrintNode instanceof PrintNode);
    return rawTextOrPrintNode instanceof RawTextNode
        ? new TagName((RawTextNode) rawTextOrPrintNode)
        : new TagName((PrintNode) rawTextOrPrintNode);
  }
}
