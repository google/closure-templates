/*
 * Copyright 2020 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.TemplateType.TemplateKind;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A registry or index of all templates in a file set & its dependencies.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public final class FileSetTemplateRegistry implements TemplateRegistry {

  private static final SoyErrorKind DUPLICATE_TEMPLATES =
      SoyErrorKind.of("Template/element ''{0}'' already defined at {1}.");
  private static final SoyErrorKind TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME =
      SoyErrorKind.of("Found deltemplate {0} with the same name as a template/element at {1}.");
  private static final SoyErrorKind DUPLICATE_DEFAULT_DELEGATE_TEMPLATES =
      SoyErrorKind.of("Delegate template ''{0}'' already has a default defined at {1}.");
  private static final SoyErrorKind DUPLICATE_DELEGATE_TEMPLATES_IN_DELPACKAGE =
      SoyErrorKind.of(
          "Delegate template ''{0}'' already defined in delpackage {1}: {2}",
          StyleAllowance.NO_PUNCTUATION);

  private final DelTemplateSelector<TemplateMetadata> delTemplateSelector;

  /**
   * Map of template names to metadata, for all types of templates. For deltemplates, this uses the
   * generated template name that includes delpackage + variant.
   */
  private final ImmutableMap<String, TemplateMetadata> allTemplates;

  /** Constructor. */
  private FileSetTemplateRegistry(
      DelTemplateSelector<TemplateMetadata> delTemplateSelector,
      ImmutableMap<String, TemplateMetadata> allTemplates) {
    this.delTemplateSelector = delTemplateSelector;
    this.allTemplates = allTemplates;
  }

  @Override
  public TemplateMetadata getMetadata(String templateFqn) {
    return allTemplates.get(checkNotNull(templateFqn));
  }

  /** Returns a multimap from delegate template name to set of keys. */
  @Override
  public DelTemplateSelector<TemplateMetadata> getDelTemplateSelector() {
    return delTemplateSelector;
  }

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order.
   */
  @Override
  public ImmutableCollection<TemplateMetadata> getAllTemplates() {
    return allTemplates.values();
  }

  public static Builder builder(ErrorReporter errorReporter) {
    return new Builder(errorReporter);
  }

  public Builder toBuilder(ErrorReporter errorReporter) {
    Builder builder = new Builder(errorReporter);
    builder.allTemplatesBuilder.putAll(allTemplates);
    builder.delTemplateSelectorBuilder = delTemplateSelector.toBuilder();
    return builder;
  }

  /** Builder for FileSetTemplateRegistry */
  public static final class Builder {
    private final ErrorReporter errorReporter;
    private DelTemplateSelector.Builder<TemplateMetadata> delTemplateSelectorBuilder =
        new DelTemplateSelector.Builder<>();
    private final Map<String, TemplateMetadata> allTemplatesBuilder = new LinkedHashMap<>();
    private final Map<String, TemplateMetadata> delegatesByName = new HashMap<>();

    private Builder(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    public void addTemplates(ImmutableList<TemplateMetadata> templates) {
      templates.forEach(this::addTemplate);
    }

    private void addTemplate(TemplateMetadata template) {
      TemplateMetadata prevMeta = allTemplatesBuilder.put(template.getTemplateName(), template);

      switch (template.getTemplateType().getTemplateKind()) {
        case BASIC:
        case ELEMENT:
          // Case 1: Basic Template or Element node
          if (prevMeta != null
              && !prevMeta
                  .getSourceLocation()
                  .getFileName()
                  .equals(template.getSourceLocation().getFileName())) {
            // Collisions in the same file are reported in LocalVariables.
            errorReporter.report(
                template.getSourceLocation(),
                DUPLICATE_TEMPLATES,
                template.getTemplateName(),
                prevMeta.getSourceLocation());
          }
          break;
        case DELTEMPLATE:
          // Case 2: Delegate template.
          String delTemplateName = template.getDelTemplateName();
          String delPackageName = template.getDelPackageName();
          String variant = template.getDelTemplateVariant();
          TemplateMetadata previous;
          if (delPackageName == null) {
            // default delegate
            previous = delTemplateSelectorBuilder.addDefault(delTemplateName, variant, template);
            if (previous != null) {
              errorReporter.report(
                  template.getSourceLocation(),
                  DUPLICATE_DEFAULT_DELEGATE_TEMPLATES,
                  delTemplateName,
                  previous.getSourceLocation());
            }
          } else {
            previous =
                delTemplateSelectorBuilder.add(delTemplateName, delPackageName, variant, template);
            if (previous != null) {
              errorReporter.report(
                  template.getSourceLocation(),
                  DUPLICATE_DELEGATE_TEMPLATES_IN_DELPACKAGE,
                  delTemplateName,
                  delPackageName,
                  previous.getSourceLocation());
            }
          }
          delegatesByName.put(delTemplateName, template);
          break;
      }
    }

    public FileSetTemplateRegistry build() {
      // make sure no basic nodes conflict with deltemplates
      for (String templateName : delegatesByName.keySet()) {
        TemplateMetadata node = allTemplatesBuilder.get(templateName);
        if (node != null && node.getTemplateType().getTemplateKind() != TemplateKind.DELTEMPLATE) {
          errorReporter.report(
              delegatesByName.get(templateName).getSourceLocation(),
              TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME,
              templateName,
              node.getSourceLocation());
        }
      }

      return new FileSetTemplateRegistry(
          delTemplateSelectorBuilder.build(), ImmutableMap.copyOf(allTemplatesBuilder));
    }
  }
}
