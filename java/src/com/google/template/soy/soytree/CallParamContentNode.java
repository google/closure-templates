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

package com.google.template.soy.soytree;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.MixinParentNode;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.soytree.SoyNode.RenderUnitNode;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Node representing a 'param' with content.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class CallParamContentNode extends CallParamNode implements RenderUnitNode {

  private static final SoyErrorKind PARAM_HAS_VALUE_BUT_IS_NOT_SELF_CLOSING =
      SoyErrorKind.of(
          "A ''param'' tag should contain a value if and only if it is also self-ending "
              + "(with a trailing ''/'') (invalid tag is '{'param {0}'}').");

  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<StandaloneNode> parentMixin;

  /** The param key. */
  private final String key;

  /** The param's content kind, or null if no 'kind' attribute was present. */
  @Nullable private final ContentKind contentKind;

  /**
   * @param id The id for this node.
   * @param sourceLocation The node's source location.
   * @param commandText The command text.
   */
  private CallParamContentNode(
      int id,
      SourceLocation sourceLocation,
      String key,
      ContentKind contentKind,
      String commandText) {
    super(id, sourceLocation, commandText);
    parentMixin = new MixinParentNode<>(this);

    this.key = key;
    this.contentKind = contentKind;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private CallParamContentNode(CallParamContentNode orig, CopyState copyState) {
    super(orig, copyState);
    this.parentMixin = new MixinParentNode<>(orig.parentMixin, this, copyState);
    this.key = orig.key;
    this.contentKind = orig.contentKind;
  }

  @Override
  public Kind getKind() {
    return Kind.CALL_PARAM_CONTENT_NODE;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  @Nullable
  public ContentKind getContentKind() {
    return contentKind;
  }

  // -----------------------------------------------------------------------------------------------
  // ParentSoyNode stuff.
  // Note: Most concrete nodes simply inherit this functionality from AbstractParentCommandNode or
  // AbstractParentSoyNode. But this class need to include its own MixinParentNode field because
  // it needs to subclass CallParamNode (and Java doesn't allow multiple inheritance).

  @Override
  public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/").append(getCommandName()).append('}');
    return sb.toString();
  }

  @Override
  public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override
  public StandaloneNode getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override
  public int getChildIndex(StandaloneNode child) {
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

  @Override
  public CallParamContentNode copy(CopyState copyState) {
    return new CallParamContentNode(this, copyState);
  }

  public static final class Builder extends CallParamNode.Builder {

    private static CallParamContentNode error() {
      return new CallParamContentNode(-1, SourceLocation.UNKNOWN, "error", null, "error");
    }

    public Builder(int id, CommandTextParseResult parseResult, SourceLocation sourceLocation) {
      super(id, parseResult, sourceLocation);
    }

    public CallParamContentNode build(Checkpoint checkpoint, ErrorReporter errorReporter) {
      if (parseResult.valueExprUnion != null) {
        errorReporter.report(
            sourceLocation,
            PARAM_HAS_VALUE_BUT_IS_NOT_SELF_CLOSING,
            parseResult.originalCommantText);
      }

      if (errorReporter.errorsSince(checkpoint)) {
        return error();
      }

      return new CallParamContentNode(
          id,
          sourceLocation,
          parseResult.key,
          parseResult.contentKind,
          parseResult.originalCommantText);
    }
  }
}
