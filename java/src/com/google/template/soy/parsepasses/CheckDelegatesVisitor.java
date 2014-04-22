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
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallDelegateNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SoySyntaxExceptionUtils;
import com.google.template.soy.soytree.TemplateBasicNode;
import com.google.template.soy.soytree.TemplateDelegateNode;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.TemplateRegistry.DelegateTemplateDivision;
import com.google.template.soy.soytree.defn.TemplateParam;

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
    Map<DelTemplateKey, List<DelegateTemplateDivision>> delTemplatesMap =
        templateRegistry.getDelTemplatesMap();
    Map<String, Set<DelTemplateKey>> delTemplateNameToKeysMap =
        templateRegistry.getDelTemplateNameToKeysMap();

    // Check that no name is reused for both basic and delegate templates.
    Set<String> reusedTemplateNames = Sets.newLinkedHashSet();
    for (DelTemplateKey delTemplateKey : delTemplatesMap.keySet()) {
      if (basicTemplatesMap.containsKey(delTemplateKey.name)) {
        reusedTemplateNames.add(delTemplateKey.name);
      }
    }
    if (reusedTemplateNames.size() > 0) {
      throw SoySyntaxException.createWithoutMetaInfo(
          "Found template name " + reusedTemplateNames + " being reused for both basic and" +
              " delegate templates.");
    }

    // Check that all delegate templates with the same name have the same declared params and
    // content kind. First, we iterate over template names:
    for (Set<DelTemplateKey> delTemplateKeys : delTemplateNameToKeysMap.values()) {

      TemplateDelegateNode firstDelTemplate = null;
      Set<TemplateParam> firstParamSet = null;
      ContentKind firstContentKind = null;

      // Then, loop over keys that share the same name (effectively, over variants):
      for (DelTemplateKey delTemplateKey : delTemplateKeys) {
        // Then, loop over divisions with the same key (effectively, over priorities):
        for (DelegateTemplateDivision division : delTemplatesMap.get(delTemplateKey)) {
          // Now, over templates in the division (effectively, delpackages):
          for (TemplateDelegateNode delTemplate :
              division.delPackageNameToDelTemplateMap.values()) {
            String currDelPackageName =  (delTemplate.getDelPackageName() != null) ?
                delTemplate.getDelPackageName() : "<default>";

            if (firstDelTemplate == null) {
              // First template encountered.
              firstDelTemplate = delTemplate;
              firstParamSet = Sets.newHashSet(delTemplate.getParams());
              firstContentKind = delTemplate.getContentKind();

            } else {
              // Not first template encountered.
              Set<TemplateParam> currParamSet = Sets.newHashSet(delTemplate.getParams());
              if (! currParamSet.equals(firstParamSet)) {
                throw SoySyntaxExceptionUtils.createWithNode(
                    String.format(
                        "Found delegate template with same name '%s' but different param" +
                            " declarations compared to the definition at %s.",
                        firstDelTemplate.getDelTemplateName(),
                        firstDelTemplate.getSourceLocation().toString()),
                    delTemplate);
              }
              if (delTemplate.getContentKind() != firstContentKind) {
                // TODO: This is only *truly* a requirement if the strict mode deltemplates are
                // being called by contextual templates. For a strict-to-strict call, everything
                // is escaped at runtime at the call sites. You could imagine delegating between
                // either a plain-text or rich-html template. However, most developers will write
                // their deltemplates in a parallel manner, and will want to know when the
                // templates differ. Plus, requiring them all to be the same early-on will allow
                // future optimizations to avoid the run-time checks, so it's better to start out
                // as strict as possible and only open up if needed.
                throw SoySyntaxExceptionUtils.createWithNode(
                    String.format(
                        "If one deltemplate has strict autoescaping, all its peers must also be " +
                            "strictly autoescaped with the same content kind: %s != %s. " +
                            "Conflicting definition at %s.",
                        firstContentKind, delTemplate.getContentKind(),
                        firstDelTemplate.getSourceLocation().toString()),
                    delTemplate);
              }
            }
          }
        }
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
    if (templateRegistry.getDelTemplateKeysForAllVariants(calleeName) != null) {
      throw SoySyntaxExceptionUtils.createWithNode(
          String.format(
              "In template '%s', found a 'call' referencing a delegate template '%s'" +
                  " (expected 'delcall').",
              currTemplateNameForUserMsgs, calleeName),
          node);
    }

    // Check that the callee is either not in a delegate package or in the same delegate package.
    TemplateBasicNode callee = templateRegistry.getBasicTemplate(calleeName);
    if (callee != null) {
      String calleeDelPackageName = callee.getDelPackageName();
      if (calleeDelPackageName != null && ! calleeDelPackageName.equals(currDelPackageName)) {
        throw SoySyntaxExceptionUtils.createWithNode(
            String.format(
                "Found illegal call from '%s' to '%s', which is in a different delegate package.",
                currTemplateNameForUserMsgs, callee.getTemplateName()),
            node);
      }
    }
  }


  @Override protected void visitCallDelegateNode(CallDelegateNode node) {

    String delCalleeName = node.getDelCalleeName();

    // Check that the callee name is not a basic template name.
    if (templateRegistry.getBasicTemplate(delCalleeName) != null) {
      throw SoySyntaxExceptionUtils.createWithNode(
          String.format(
              "In template '%s', found a 'delcall' referencing a basic template '%s'" +
                  " (expected 'call').",
              currTemplateNameForUserMsgs, delCalleeName),
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
