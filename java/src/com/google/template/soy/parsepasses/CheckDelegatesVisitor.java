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

package com.google.template.soy.parsepasses;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoytreeUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateNode.SoyDocParam;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateDivision;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Checks various rules regarding the use of delegates (including delegate packages, delegate
 * templates, and delegate calls).
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> {@link #exec} should be called on a full parse tree. There is no return value. A
 * {@code SoySyntaxException} is thrown if an error is found.
 *
 */
public class CheckDelegatesVisitor extends AbstractSoyNodeVisitor<Void> {


  /** A template registry built from the Soy tree. */
  private TemplateRegistry templateRegistry;

  /** The current enclosing template's name, as suitable for user messages (during pass). */
  private String currTemplateNameForUserMsgs;

  /** Current delegate package name, or null if none (during pass). */
  private String currDelPackageName;


  @Override public Void exec(SoyNode soyNode) {

    Preconditions.checkArgument(soyNode instanceof SoyFileSetNode);
    templateRegistry = new TemplateRegistry((SoyFileSetNode) soyNode);

    // Perform checks that only involve templates (uses templateRegistry only, no traversal).
    checkTemplates();

    // Perform checks that involve calls (uses traversal).
    super.exec(soyNode);

    return null;
  }


  /**
   * Performs checks that only involve templates (uses templateRegistry only).
   */
  private void checkTemplates() {

    Map<String, TemplateBasicNode> basicTemplatesMap = templateRegistry.getBasicTemplatesMap();
    Map<String, List<DelegateTemplateDivision>> delTemplatesMap =
        templateRegistry.getDelegateTemplatesMap();

    // Check that no name is reused for both basic and delegate templates.
    Set<String> reusedTemplateNames =
        Sets.intersection(basicTemplatesMap.keySet(), delTemplatesMap.keySet());
    if (reusedTemplateNames.size() > 0) {
      throw new SoySyntaxException(String.format(
          "Found template name %s being reused for both basic and delegate templates.",
          reusedTemplateNames));
    }

    // Check that all delegate templates with the same name have the same declared params.
    for (List<DelegateTemplateDivision> divisions : delTemplatesMap.values()) {

      TemplateDelegateNode firstDelTemplate = null;
      Set<SoyDocParam> firstSoyDocParamsSet = null;

      for (DelegateTemplateDivision division : divisions) {
        for (TemplateDelegateNode delTemplate : division.delPackageNameToDelTemplateMap.values()) {

          if (firstDelTemplate == null) {
            // First template encountered.
            firstDelTemplate = delTemplate;
            firstSoyDocParamsSet = Sets.newHashSet(delTemplate.getSoyDocParams());

          } else {
            // Not first template encountered.
            Set<SoyDocParam> currSoyDocParamsSet = Sets.newHashSet(delTemplate.getSoyDocParams());
            if (! currSoyDocParamsSet.equals(firstSoyDocParamsSet)) {
              String firstDelPackageName = (firstDelTemplate.getDelPackageName() != null) ?
                  firstDelTemplate.getDelPackageName() : "none";
              String currDelPackageName =  (delTemplate.getDelPackageName() != null) ?
                  delTemplate.getDelPackageName() : "none";
              throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
                  String.format(
                      "Found delegate templates with same name '%s' but different param" +
                          " declarations in delegate packages '%s' and '%s'.",
                      firstDelTemplate.getDelTemplateName(), firstDelPackageName,
                      currDelPackageName),
                  null,
                  firstDelTemplate);
            }
          }
        }
      }
    }

    // Check that all basic templates within delegate packages are private.
    for (TemplateBasicNode basicTemplate : basicTemplatesMap.values()) {
      if (basicTemplate.getDelPackageName() != null && ! basicTemplate.isPrivate()) {
        throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
            String.format(
                "Found public template '%s' in delegate package '%s' (must mark as private).",
                basicTemplate.getTemplateName(), basicTemplate.getDelPackageName()),
            null,
            basicTemplate);
      }
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for specific nodes.


  @Override protected void visitTemplateNode(TemplateNode node) {
    this.currTemplateNameForUserMsgs = node.getTemplateNameForUserMsgs();
    this.currDelPackageName = node.getDelPackageName();
    visitChildren(node);
  }


  @Override protected void visitCallBasicNode(CallBasicNode node) {

    String calleeName = node.getCalleeName();

    // Check that the callee name is not a delegate template name.
    if (templateRegistry.getSortedDelegateTemplateDivisions(calleeName) != null) {
      throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
          String.format(
              "In template '%s', found a 'call' referencing a delegate template '%s'" +
                  " (expected 'delcall').",
              currTemplateNameForUserMsgs, calleeName),
          null,
          node);
    }

    // Check that the callee is either not in a delegate package or in the same delegate package.
    TemplateBasicNode callee = templateRegistry.getBasicTemplate(calleeName);
    if (callee != null) {
      String calleeDelPackageName = callee.getDelPackageName();
      if (calleeDelPackageName != null && ! calleeDelPackageName.equals(currDelPackageName)) {
        throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
            String.format(
                "Found illegal call from '%s' to '%s', which is in a different delegate package.",
                currTemplateNameForUserMsgs, callee.getTemplateName()),
            null,
            node);
      }
    }
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {

    String delCalleeName = node.getDelCalleeName();

    // Check that the callee name is not a basic template name.
    if (templateRegistry.getBasicTemplate(delCalleeName) != null) {
      throw SoytreeUtils.createSoySyntaxExceptionWithMetaInfo(
          String.format(
              "In template '%s', found a 'delcall' referencing a basic template '%s'" +
                  " (expected 'call').",
              currTemplateNameForUserMsgs, delCalleeName),
          null,
          node);
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Fallback implementation.


  @Override protected void visitSoyNode(SoyNode node) {
    if (node instanceof ParentSoyNode<?>) {
      visitChildren((ParentSoyNode<?>) node);
    }
  }

}
