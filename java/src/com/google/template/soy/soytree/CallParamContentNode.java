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
import com.google.template.soy.soytree.CommandTextAttributesParser.Attribute;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Node representing a 'param' with content.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class CallParamContentNode extends CallParamNode implements ParentSoyNode<SoyNode> {


  /** Pattern for a key and optional value not listed as attributes. */
  // Note: group 1 = key, group 2 = value (or null).
  private static final Pattern NONATTRIBUTE_COMMAND_TEXT =
      Pattern.compile("^ (?! key=\") ([\\w]+) (?: \\s* : \\s* (.+) )? $",
                      Pattern.COMMENTS);

  /** Parser for the command text. */
  private static final CommandTextAttributesParser ATTRIBUTES_PARSER =
      new CommandTextAttributesParser("param",
          new Attribute("key", Attribute.ALLOW_ALL_VALUES,
                        Attribute.NO_DEFAULT_VALUE_BECAUSE_REQUIRED),
          new Attribute("value", Attribute.ALLOW_ALL_VALUES, null));


  /** The mixin object that implements the ParentNode functionality. */
  private final MixinParentNode<SoyNode> parentMixin;

  /** The param key. */
  private final String key;


  /**
   * @param id The id for this node.
   * @param commandText The command text.
   * @throws SoySyntaxException If a syntax error is found.
   */
  public CallParamContentNode(String id, String commandText) throws SoySyntaxException {
    super(id, commandText);
    parentMixin = new MixinParentNode<SoyNode>(this);

    String valueText;

    Matcher nctMatcher = NONATTRIBUTE_COMMAND_TEXT.matcher(commandText);
    if (nctMatcher.matches()) {
      key = parseKeyHelper(nctMatcher.group(1));
      valueText = nctMatcher.group(2);
    } else {
      Map<String, String> attributes = ATTRIBUTES_PARSER.parse(commandText);
      key = parseKeyHelper(attributes.get("key"));
      valueText = attributes.get("value");
    }

    if (valueText != null) {
      throw new SoySyntaxException("If a 'param' tag contains a value, then the tag must be" +
                                   " self-ending (with a trailing '/').");
    }
  }


  @Override public String getKey() {
    return key;
  }


  // -----------------------------------------------------------------------------------------------
  // ParentSoyNode stuff.


  @Override public String toSourceString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getTagString());
    appendSourceStringForChildren(sb);
    sb.append("{/").append(getCommandName()).append("}");
    return sb.toString();
  }

  @Override public int numChildren() {
    return parentMixin.numChildren();
  }

  @Override public SoyNode getChild(int index) {
    return parentMixin.getChild(index);
  }

  @Override public int getChildIndex(SoyNode child) {
    return parentMixin.getChildIndex(child);
  }

  @Override public List<SoyNode> getChildren() {
    return parentMixin.getChildren();
  }

  @Override public void addChild(SoyNode child) {
    parentMixin.addChild(child);
  }

  @Override public void addChild(int index, SoyNode child) {
    parentMixin.addChild(index, child);
  }

  @Override public void removeChild(int index) {
    parentMixin.removeChild(index);
  }

  @Override public void removeChild(SoyNode child) {
    parentMixin.removeChild(child);
  }

  @Override public void setChild(int index, SoyNode newChild) {
    parentMixin.setChild(index, newChild);
  }

  @Override public void clearChildren() {
    parentMixin.clearChildren();
  }

  @Override public void addChildren(List<? extends SoyNode> children) {
    parentMixin.addChildren(children);
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

}
