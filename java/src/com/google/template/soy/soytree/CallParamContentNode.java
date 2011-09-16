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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.basetree.MixinParentNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;

import java.util.List;


/**
 * Node representing a 'param' with content.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CallParamContentNode extends CallParamNode implements BlockNode {


  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<StandaloneNode> parentMixin;

  /** The param key. */
  private final String key;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallParamContentNode(int id, String commandText) throws SoySyntaxException {
    super(id, commandText);
    parentMixin = new MixinParentNode<StandaloneNode>(this);

    CommandTextParseResult parseResult = parseCommandTextHelper(commandText);
    key = parseResult.key;

    if (parseResult.valueExprUnion != null) {
      throw new SoySyntaxException(
          "A 'param' tag should contain a value if and only if it is also self-ending (with a" +
          " trailing '/') (invalid tag is {param " + commandText + "}).");
    }
  }


  /**
   * Copy constructor.
   * @param orig The node to copy.
   */
  protected CallParamContentNode(CallParamContentNode orig) {
    super(orig);
    this.parentMixin = new MixinParentNode<StandaloneNode>(orig.parentMixin, this);
    this.key = orig.key;
  }


  @Override public Kind getKind() {
    return Kind.CALL_PARAM_CONTENT_NODE;
  }


  @Override public String getKey() {
    return key;
  }


  // -----------------------------------------------------------------------------------------------
  // ParentSoyNode stuff.
  // Note: Most concrete nodes simply inherit this functionality from AbstractParentCommandNode or
  // AbstractParentSoyNode. But this class need to include its own MixinParentNode field because
  // it needs to subclass CallParamNode (and Java doesn't allow multiple inheritance).


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/").append(getCommandName()).append("}");
    return sb.toString();
  }

  @Override public void setNeedsEnvFrameDuringInterp(Boolean needsEnvFrameDuringInterp) {
    parentMixin.setNeedsEnvFrameDuringInterp(needsEnvFrameDuringInterp);
  }

  @Override public Boolean needsEnvFrameDuringInterp() {
    return parentMixin.needsEnvFrameDuringInterp();
  }

  @Override public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override public StandaloneNode getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override public int getChildIndex(StandaloneNode child) {
    return parentMixin.getChildIndex(child);
  }

  @Override public List<StandaloneNode> getChildren() {
    return parentMixin.getChildren();
  }

  @Override public void addChild(StandaloneNode child) {
    parentMixin.addChild(child);
  }

  @Override public void addChild(int index, StandaloneNode child) {
    parentMixin.addChild(index, child);
  }

  @Override public void removeChild(int index) {
    parentMixin.removeChild(index);
  }

  @Override public void removeChild(StandaloneNode child) {
    parentMixin.removeChild(child);
  }

  @Override public void replaceChild(int index, StandaloneNode newChild) {
    parentMixin.replaceChild(index, newChild);
  }

  @Override public void replaceChild(StandaloneNode currChild, StandaloneNode newChild) {
    parentMixin.replaceChild(currChild, newChild);
  }

  @Override public void clearChildren() {
    parentMixin.clearChildren();
  }

  @Override public void addChildren(List<? extends StandaloneNode> children) {
    parentMixin.addChildren(children);
  }

  @Override public void addChildren(int index, List<? extends StandaloneNode> children) {
    parentMixin.addChildren(index, children);
  }

  @Override public void appendSourceStringForChildren(StringBuilder sb) {
    parentMixin.appendSourceStringForChildren(sb);
  }

  @Override public void appendTreeStringForChildren(StringBuilder sb, int indent) {
    parentMixin.appendTreeStringForChildren(sb, indent);
  }

  @Override public String toTreeString(int indent) {
    return parentMixin.toTreeString(indent);
  }

  @Override public CallParamContentNode clone() {
    return new CallParamContentNode(this);
  }

}
