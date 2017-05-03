/*
 * Copyright 2011 Google Inc.
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

import static com.google.template.soy.soytree.CommandTagAttribute.UNSUPPORTED_ATTRIBUTE_KEY_SINGLE;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.MixinParentNode;
import com.google.template.soy.basetree.Node;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.internalutils.NodeContentKinds;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.primitive.SanitizedType;
import com.google.template.soy.types.primitive.StringType;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node representing a 'let' statement with content.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class LetContentNode extends LetNode implements RenderUnitNode {

  /**
   * Creates a LetContentNode for a compiler-generated variable. Use this in passes that rewrite the
   * tree and introduce local temporary variables.
   */
  // TODO(user): Delete.
  public static LetContentNode forVariable(
      int id, SourceLocation sourceLocation, String varName, @Nullable ContentKind contentKind) {
    LetContentNode node = new LetContentNode(id, sourceLocation, varName, contentKind);
    SoyType type =
        (contentKind != null)
            ? SanitizedType.getTypeForContentKind(contentKind)
            : StringType.getInstance();
    node.getVar().setType(type);
    return node;
  }

  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<StandaloneNode> parentMixin;

  /** The let node's content kind, or null if no 'kind' attribute was present. */
  @Nullable private final ContentKind contentKind;

  public LetContentNode(
      int id,
      SourceLocation location,
      String varName,
      @Nullable CommandTagAttribute kindAttr,
      ErrorReporter errorReporter) {
    super(id, location, varName);
    this.parentMixin = new MixinParentNode<>(this);

    if (kindAttr != null && !kindAttr.hasName("kind")) {
      errorReporter.report(
          kindAttr.getName().location(),
          UNSUPPORTED_ATTRIBUTE_KEY_SINGLE,
          kindAttr.getName().identifier(),
          "let",
          "kind");
      kindAttr = null;
    }
    this.contentKind = (kindAttr != null) ? kindAttr.valueAsContentKind(errorReporter) : null;
  }

  private LetContentNode(
      int id, SourceLocation location, String varName, @Nullable ContentKind contentKind) {
    super(id, location, varName);
    this.parentMixin = new MixinParentNode<>(this);
    this.contentKind = contentKind;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private LetContentNode(LetContentNode orig, CopyState copyState) {
    super(orig, copyState);
    this.parentMixin = new MixinParentNode<>(orig.parentMixin, this, copyState);
    this.contentKind = orig.contentKind;
  }

  @Override
  public Kind getKind() {
    return Kind.LET_CONTENT_NODE;
  }

  @Override
  @Nullable
  public ContentKind getContentKind() {
    return contentKind;
  }

  @Override
  public String getCommandText() {
    return (contentKind == null)
        ? "$" + getVarName()
        : "$" + getVarName() + " kind=\"" + NodeContentKinds.toAttributeValue(contentKind) + "\"";
  }

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/").append(getCommandName()).append("}");
    return sb.toString();
  }

  @Override
  public LetContentNode copy(CopyState copyState) {
    return new LetContentNode(this, copyState);
  }

  // -----------------------------------------------------------------------------------------------
  // ParentSoyNode stuff.
  // Note: Most concrete nodes simply inherit this functionality from AbstractParentCommandNode or
  // AbstractParentSoyNode. But this class need to include its own MixinParentNode field because
  // it needs to subclass LetNode (and Java doesn't allow multiple inheritance).

  @Override
  public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override
  public StandaloneNode getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override
  public int getChildIndex(Node child) {
    return parentMixin.getChildIndex(child);
  }

  @Override
  public List<StandaloneNode> getChildren() {
    return parentMixin.getChildren();
  }

  @Override
  public void addChild(StandaloneNode child) {
    parentMixin.addChild(child);
  }

  @Override
  public void addChild(int index, StandaloneNode child) {
    parentMixin.addChild(index, child);
  }

  @Override
  public void removeChild(int index) {
    parentMixin.removeChild(index);
  }

  @Override
  public void removeChild(StandaloneNode child) {
    parentMixin.removeChild(child);
  }

  @Override
  public void replaceChild(int index, StandaloneNode newChild) {
    parentMixin.replaceChild(index, newChild);
  }

  @Override
  public void replaceChild(StandaloneNode currChild, StandaloneNode newChild) {
    parentMixin.replaceChild(currChild, newChild);
  }

  @Override
  public void clearChildren() {
    parentMixin.clearChildren();
  }

  @Override
  public void addChildren(List<? extends StandaloneNode> children) {
    parentMixin.addChildren(children);
  }

  @Override
  public void addChildren(int index, List<? extends StandaloneNode> children) {
    parentMixin.addChildren(index, children);
  }

  @Override
  public void appendSourceStringForChildren(StringBuilder sb) {
    parentMixin.appendSourceStringForChildren(sb);
  }
}
