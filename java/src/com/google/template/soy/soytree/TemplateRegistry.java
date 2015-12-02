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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A registry or index of all templates in a Soy tree.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class TemplateRegistry {

  private static final SoyErrorKind DUPLICATE_TEMPLATES =
      SoyErrorKind.of("Template ''{0}'' already defined at {1}");
  private static final SoyErrorKind BASIC_AND_DELTEMPLATE_WITH_SAME_NAME =
      SoyErrorKind.of("Found deltemplate {0} with the same name as a basic template at {1}.");
  private static final SoyErrorKind DUPLICATE_DEFAULT_DELEGATE_TEMPLATES =
      SoyErrorKind.of("Delegate template ''{0}'' already has a default defined at {1}");
  private static final SoyErrorKind DUPLICATE_DELEGATE_TEMPLATES_IN_DELPACKAGE =
      SoyErrorKind.of("Delegate template ''{0}'' already defined in delpackage {1}: {2}");

  /** Map from basic template name to node. */
  private final ImmutableMap<String, TemplateBasicNode> basicTemplatesMap;
  private final DelTemplateSelector<TemplateDelegateNode> delTemplateSelector;
  private final ImmutableList<TemplateNode> allTemplates;

  /**
   * Constructor.
   * @param soyTree The Soy tree from which to build a template registry.
   */
  public TemplateRegistry(SoyFileSetNode soyTree, ErrorReporter errorReporter) {

    // ------ Iterate through all templates to collect data. ------
    ImmutableList.Builder<TemplateNode> allTemplatesBuilder = ImmutableList.builder();
    DelTemplateSelector.Builder<TemplateDelegateNode> delTemplateSelectorBuilder =
        new DelTemplateSelector.Builder<>();
    Map<String, TemplateBasicNode> basicTemplates = new LinkedHashMap<>();
    Multimap<String, TemplateDelegateNode> delegateTemplates = HashMultimap.create();
    for (SoyFileNode soyFile : soyTree.getChildren()) {
      for (TemplateNode template : soyFile.getChildren()) {
        allTemplatesBuilder.add(template);
        if (template instanceof TemplateBasicNode) {
          // Case 1: Basic template.
          TemplateBasicNode prev = basicTemplates.put(
              template.getTemplateName(), (TemplateBasicNode) template);
          if (prev != null) {
            errorReporter.report(
                template.getSourceLocation(),
                DUPLICATE_TEMPLATES,
                template.getTemplateName(),
                prev.getSourceLocation());
          }
        } else {
          // Case 2: Delegate template.
          TemplateDelegateNode delTemplate = (TemplateDelegateNode) template;
          String delTemplateName = delTemplate.getDelTemplateName();
          String delPackageName = delTemplate.getDelPackageName();
          String variant = delTemplate.getDelTemplateVariant();
          TemplateDelegateNode previous;
          if (delPackageName == null) {
            // default delegate
            previous = delTemplateSelectorBuilder.addDefault(delTemplateName, variant, delTemplate);
            if (previous != null) {
              errorReporter.report(
                  delTemplate.getSourceLocation(),
                  DUPLICATE_DEFAULT_DELEGATE_TEMPLATES,
                  delTemplateName,
                  previous.getSourceLocation());
            }
          } else {
            previous =
                delTemplateSelectorBuilder.add(
                    delTemplateName, delPackageName, variant, delTemplate);
            if (previous != null) {
              errorReporter.report(
                  delTemplate.getSourceLocation(),
                  DUPLICATE_DELEGATE_TEMPLATES_IN_DELPACKAGE,
                  delTemplateName,
                  delPackageName,
                  previous.getSourceLocation());
            }
          }
          delegateTemplates.put(delTemplateName, delTemplate);
        }
      }
    }
    // make sure no basic nodes conflict with deltemplates
    for (Map.Entry<String, TemplateDelegateNode> entry : delegateTemplates.entries()) {
      TemplateBasicNode basicNode = basicTemplates.get(entry.getKey());
      if (basicNode != null) {
        errorReporter.report(
            entry.getValue().getSourceLocation(),
            BASIC_AND_DELTEMPLATE_WITH_SAME_NAME,
            entry.getKey(),
            basicNode.getSourceLocation());
      }
    }

    // ------ Build the final data structures. ------

    basicTemplatesMap = ImmutableMap.copyOf(basicTemplates);
    delTemplateSelector = delTemplateSelectorBuilder.build();
    this.allTemplates = allTemplatesBuilder.build();
  }


  /**
   * Returns a map from basic template name to node.
   */
  public ImmutableMap<String, TemplateBasicNode> getBasicTemplatesMap() {
    return basicTemplatesMap;
  }


  /**
   * Retrieves a basic template given the template name.
   * @param templateName The basic template name to retrieve.
   * @return The corresponding basic template, or null if the template name is not defined.
   */
  @Nullable
  public TemplateBasicNode getBasicTemplate(String templateName) {
    return basicTemplatesMap.get(templateName);
  }

  /**
   * Returns a multimap from delegate template name to set of keys.
   */
  public DelTemplateSelector<TemplateDelegateNode> getDelTemplateSelector() {
    return delTemplateSelector;
  }

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and
   * {@link TemplateDelegateNode delegate} nodes), in no particular order.
   */
  public ImmutableList<TemplateNode> getAllTemplates() {
    return allTemplates;
  }

  /**
   * Selects a delegate template based on the rendering rules, given the delegate template key (name
   * and variant) and the set of active delegate package names.
   *
   * @param delTemplateKey The delegate template key (name and variant) to select an implementation
   *     for.
   * @param activeDelPackageNames The set of active delegate package names.
   * @return The selected delegate template, or null if there are no active implementations.
   * @throws IllegalArgumentException If there are two or more active implementations with
   *     equal priority (unable to select one over the other).
   */
  @Nullable
  public TemplateDelegateNode selectDelTemplate(
      DelTemplateKey delTemplateKey, Set<String> activeDelPackageNames) {
    // TODO(lukes): eliminate this method and DelTemplateKey
    return delTemplateSelector.selectTemplate(
        delTemplateKey.name(), delTemplateKey.variant(), activeDelPackageNames);
  }
}
