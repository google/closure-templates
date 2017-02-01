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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.soytree.TemplateDelegateNode.DelTemplateKey;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A registry or index of all templates in a Soy tree.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
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
   *
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
          TemplateBasicNode prev =
              basicTemplates.put(template.getTemplateName(), (TemplateBasicNode) template);
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

  /** Returns a map from basic template name to node. */
  public ImmutableMap<String, TemplateBasicNode> getBasicTemplatesMap() {
    return basicTemplatesMap;
  }

  /**
   * Retrieves a basic template given the template name.
   *
   * @param templateName The basic template name to retrieve.
   * @return The corresponding basic template, or null if the template name is not defined.
   */
  @Nullable
  public TemplateBasicNode getBasicTemplate(String templateName) {
    return basicTemplatesMap.get(templateName);
  }

  /** Returns a multimap from delegate template name to set of keys. */
  public DelTemplateSelector<TemplateDelegateNode> getDelTemplateSelector() {
    return delTemplateSelector;
  }

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order.
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
   * @param activeDelPackageNameSelector The predicate for testing whether a given delpackage is
   *     active.
   * @return The selected delegate template, or null if there are no active implementations.
   * @throws IllegalArgumentException If there are two or more active implementations with equal
   *     priority (unable to select one over the other).
   */
  @Nullable
  public TemplateDelegateNode selectDelTemplate(
      DelTemplateKey delTemplateKey, Predicate<String> activeDelPackageNameSelector) {
    // TODO(lukes): eliminate this method and DelTemplateKey
    return delTemplateSelector.selectTemplate(
        delTemplateKey.name(), delTemplateKey.variant(), activeDelPackageNameSelector);
  }

  /**
   * Gets the content kind that a call results in. If used with delegate calls, the delegate
   * templates must use strict autoescaping. This relies on the fact that all delegate calls must
   * have the same kind when using strict autoescaping. This is enforced by CheckDelegatesVisitor.
   *
   * @param node The {@link CallBasicNode} or {@link CallDelegateNode}.
   * @return The kind of content that the call results in.
   */
  public Optional<ContentKind> getCallContentKind(CallNode node) {
    TemplateNode templateNode = null;

    if (node instanceof CallBasicNode) {
      String calleeName = ((CallBasicNode) node).getCalleeName();
      templateNode = getBasicTemplate(calleeName);
    } else {
      String calleeName = ((CallDelegateNode) node).getDelCalleeName();
      ImmutableList<TemplateDelegateNode> templateNodes =
          getDelTemplateSelector().delTemplateNameToValues().get(calleeName);
      // For per-file compilation, we may not have any of the delegate templates in the compilation
      // unit.
      if (!templateNodes.isEmpty()) {
        templateNode = templateNodes.get(0);
      }
    }
    // The template node may be null if the template is being compiled in isolation.
    if (templateNode == null) {
      return Optional.absent();
    }
    Preconditions.checkState(
        templateNode instanceof TemplateBasicNode
            || templateNode.getAutoescapeMode() == AutoescapeMode.STRICT,
        "Cannot determine the content kind for a delegate template that does not use strict "
            + "autoescaping.");

    return Optional.of(templateNode.getContentKind());
  }
}
