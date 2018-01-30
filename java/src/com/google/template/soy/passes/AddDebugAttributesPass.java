/*
 * Copyright 2018 Google Inc.
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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.SoyFileKind;
import com.google.template.soy.basicfunctions.DebugSoyTemplateInfoFunction;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.restricted.Sanitizers;
import com.google.template.soy.soytree.HtmlAttributeNode;
import com.google.template.soy.soytree.HtmlAttributeValueNode;
import com.google.template.soy.soytree.HtmlOpenTagNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyTreeUtils;
import com.google.template.soy.soytree.TemplateNode;

/**
 * Adds html attributes to all html tags that document what template the tag came from.
 *
 * <p>This pass supports the debug view for inspecting template information in rendered pages. See
 * go/inspect-template-info-fw for details.
 */
final class AddDebugAttributesPass extends CompilerFilePass {
  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    if (file.getSoyFileKind() != SoyFileKind.SRC) {
      return;
    }
    for (TemplateNode template : file.getChildren()) {

      for (HtmlOpenTagNode openTagNode :
          SoyTreeUtils.getAllNodesOfType(template, HtmlOpenTagNode.class)) {
        openTagNode.addChild(
            createSoyDebug(openTagNode.getSourceLocation(), nodeIdGen, template.getTemplateName()));
      }
    }
  }

  /**
   * Generates an AST fragment that looks like:
   *
   * <p>{@code {if debugSoyTemplateInfo()}data-debug-soy="<template> <file>:<line>"{/if}}
   *
   * @param insertionLocation The location where it is being inserted
   * @param nodeIdGen The id generator to use
   * @param htmlComment The content of the HTML comment
   */
  private IfNode createSoyDebug(
      SourceLocation insertionLocation, IdGenerator nodeIdGen, String templateName) {
    IfNode ifNode = new IfNode(nodeIdGen.genId(), insertionLocation);
    FunctionNode funcNode =
        new FunctionNode(DebugSoyTemplateInfoFunction.INSTANCE, insertionLocation);
    IfCondNode ifCondNode = new IfCondNode(nodeIdGen.genId(), insertionLocation, "if", funcNode);
    HtmlAttributeNode attribute =
        new HtmlAttributeNode(
            nodeIdGen.genId(), insertionLocation, insertionLocation.getBeginPoint());
    attribute.addChild(new RawTextNode(nodeIdGen.genId(), "data-debug-soy", insertionLocation));
    HtmlAttributeValueNode attrValue =
        new HtmlAttributeValueNode(
            nodeIdGen.genId(), insertionLocation, HtmlAttributeValueNode.Quotes.DOUBLE);
    attribute.addChild(attrValue);
    attrValue.addChild(
        new RawTextNode(
            nodeIdGen.genId(),
            // escape special characters
            Sanitizers.escapeHtmlAttribute(
                templateName
                    + " "
                    + insertionLocation.getFilePath()
                    + ":"
                    + insertionLocation.getBeginLine()),
            insertionLocation));
    ifCondNode.addChild(attribute);
    ifNode.addChild(ifCondNode);
    return ifNode;
  }
}
