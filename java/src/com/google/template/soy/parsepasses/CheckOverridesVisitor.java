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

package com.google.template.soy.parsepasses;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Map;


/**
 * Visitor for processing overrides.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Overrides are only allowed in Soy V1. An override is a template with the same name as an
 * earlier template, intended to replace the earlier definition.
 * 
 * <p> {@link #exec} should be called on a full parse tree. This visitor will check that all
 * overrides are explicit. There is no return value. A {@code SoySyntaxException} is thrown if a
 * non-explicit override is found.
 *
 * @author Kai Huang
 */
public class CheckOverridesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** Map of template name to template node for basic templates seen so far. */
  private Map<String, TemplateBasicNode> basicTemplatesMap;


  @Override public Void exec(SoyNode node) {
    basicTemplatesMap = Maps.newHashMap();
    visit(node);
    return null;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If a non-explicit override is found.
   */
  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    visitChildren(node);
  }


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If a non-explicit override is found.
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {
    visitChildren(node);
  }


  /**
   * {@inheritDoc}
   * @throws SoySyntaxException If a non-explicit override is found.
   */
  @Override protected void visitTemplateBasicNode(TemplateBasicNode node) {

    String templateName = node.getTemplateName();
    // Template name should be full name (not start with '.').
    Preconditions.checkArgument(templateName.charAt(0) != '.');

    if (basicTemplatesMap.containsKey(templateName)) {
      TemplateNode prevTemplate = basicTemplatesMap.get(templateName);
      // If this duplicate definition is not an explicit Soy V1 override, report error.
      if (!node.isOverride()) {
        SoyFileNode prevTemplateFile = prevTemplate.getNearestAncestor(SoyFileNode.class);
        SoyFileNode currTemplateFile = node.getNearestAncestor(SoyFileNode.class);
        if (currTemplateFile == prevTemplateFile) {
          throw SoySyntaxExceptionUtils.createWithNode(
              "Found two definitions for template name '" + templateName + "', both in the file " +
                  currTemplateFile.getFilePath() + ".",
              node);
        } else {
          String prevTemplateFilePath = prevTemplateFile.getFilePath();
          String currTemplateFilePath = currTemplateFile.getFilePath();
          if (currTemplateFilePath != null && currTemplateFilePath.equals(prevTemplateFilePath)) {
            throw SoySyntaxExceptionUtils.createWithNode(
                "Found two definitions for template name '" + templateName +
                    "' in two different files with the same name " + currTemplateFilePath +
                    " (perhaps the file was accidentally included twice).",
                node);
          } else {
            throw SoySyntaxExceptionUtils.createWithNode(
                "Found two definitions for template name '" + templateName +
                    "' in two different files " + prevTemplateFilePath + " and " +
                    currTemplateFilePath + ".",
                node);
          }
        }
      }
    } else {
      basicTemplatesMap.put(templateName, node);
    }
  }


  @Override protected void visitTemplateDelegateNode(TemplateDelegateNode node) {
    return;  // nothing to do
  }

}
