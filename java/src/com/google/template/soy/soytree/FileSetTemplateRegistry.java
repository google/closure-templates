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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.shared.internal.DelTemplateSelector;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A registry or index of all templates in a file set & its dependencies.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
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

  /** Map of file paths to a registry of templates in that file. */
  private final ImmutableMap<String, TemplatesPerFile> templatesPerFile;

  /**
   * Map from basic template or element name to node, for all files in the file set & its
   * dependencies.
   */
  private final ImmutableMap<String, TemplateMetadata> basicTemplatesOrElementsMap;

  private final DelTemplateSelector<TemplateMetadata> delTemplateSelector;

  /**
   * Map of template names to metadata, for all types of templates. For deltemplates, this uses the
   * generated template name that includes delpackage + variant.
   */
  private final ImmutableMap<String, TemplateMetadata> allTemplates;

  /** Constructor. */
  private FileSetTemplateRegistry(
      ImmutableMap<String, TemplatesPerFile> templatesPerFile,
      ImmutableMap<String, TemplateMetadata> basicTemplatesOrElementsMap,
      DelTemplateSelector<TemplateMetadata> delTemplateSelector,
      ImmutableMap<String, TemplateMetadata> allTemplates) {
    this.templatesPerFile = templatesPerFile;
    this.basicTemplatesOrElementsMap = basicTemplatesOrElementsMap;
    this.delTemplateSelector = delTemplateSelector;
    this.allTemplates = allTemplates;
  }

  public static Builder builder(ErrorReporter errorReporter) {
    return new Builder(errorReporter);
  }

  /** Builder for FileSetTemplateRegistry */
  public static final class Builder {
    private final ErrorReporter errorReporter;
    private final Map<String, TemplatesPerFile.Builder> templatesPerFileBuilder =
        new LinkedHashMap<>();
    DelTemplateSelector.Builder<TemplateMetadata> delTemplateSelectorBuilder =
        new DelTemplateSelector.Builder<>();
    Map<String, TemplateMetadata> basicTemplatesOrElementsMap = new LinkedHashMap<>();
    Multimap<String, TemplateMetadata> delegateTemplates = HashMultimap.create();
    Map<String, TemplateMetadata> allTemplatesBuilder = new LinkedHashMap<>();

    private Builder(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    public void addTemplatesForFile(String filePath, ImmutableList<TemplateMetadata> templates) {
      templates.forEach(t -> addTemplateForFile(filePath, t));
    }

    public void addTemplateForFile(String filePath, TemplateMetadata template) {
      addTemplateToPerFileRegistry(filePath, template);
      allTemplatesBuilder.put(template.getTemplateName(), template);

      switch (template.getTemplateKind()) {
        case BASIC:
        case ELEMENT:
          // Case 1: Basic Template or Element node
          TemplateMetadata prev =
              basicTemplatesOrElementsMap.put(template.getTemplateName(), template);
          if (prev != null) {
            errorReporter.report(
                template.getSourceLocation(),
                DUPLICATE_TEMPLATES,
                template.getTemplateName(),
                prev.getSourceLocation());
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
          delegateTemplates.put(delTemplateName, template);
          break;
      }
    }

    public Map<String, TemplatesPerFile.Builder> getTemplatesPerFileBuilder() {
      return templatesPerFileBuilder;
    }

    private void addTemplateToPerFileRegistry(String filePath, TemplateMetadata template) {
      TemplatesPerFile.Builder fileRegistry =
          templatesPerFileBuilder.computeIfAbsent(filePath, TemplatesPerFile::builder);
      fileRegistry.addTemplate(template);
    }

    public FileSetTemplateRegistry build() {
      // make sure no basic nodes conflict with deltemplates
      for (Map.Entry<String, TemplateMetadata> entry : delegateTemplates.entries()) {
        TemplateMetadata node = basicTemplatesOrElementsMap.get(entry.getKey());
        if (node != null) {
          errorReporter.report(
              entry.getValue().getSourceLocation(),
              TEMPLATE_OR_ELEMENT_AND_DELTEMPLATE_WITH_SAME_NAME,
              entry.getKey(),
              node.getSourceLocation());
        }
      }
      return new FileSetTemplateRegistry(
          templatesPerFileBuilder.entrySet().stream()
              .collect(toImmutableMap(Map.Entry::getKey, e -> e.getValue().build())),
          ImmutableMap.copyOf(basicTemplatesOrElementsMap),
          delTemplateSelectorBuilder.build(),
          ImmutableMap.copyOf(allTemplatesBuilder));
    }
  }

  /** Returns all basic template names. */
  @Override
  public ImmutableSet<String> getBasicTemplateOrElementNames() {
    return basicTemplatesOrElementsMap.keySet();
  }

  /** Look up possible targets for a call. */
  @Override
  public ImmutableList<TemplateType> getTemplates(CallNode node) {
    if (node instanceof CallBasicNode) {
      SoyType calleeType = ((CallBasicNode) node).getCalleeExpr().getType();
      if (calleeType == null) {
        return ImmutableList.of();
      }
      if (calleeType.getKind() == SoyType.Kind.TEMPLATE) {
        return ImmutableList.of((TemplateType) calleeType);
      } else if (calleeType.getKind() == SoyType.Kind.UNION) {
        ImmutableList.Builder<TemplateType> signatures = ImmutableList.builder();
        for (SoyType member : ((UnionType) calleeType).getMembers()) {
          // Rely on CheckTemplateCallsPass to catch this with nice error messages.
          Preconditions.checkState(member.getKind() == SoyType.Kind.TEMPLATE);
          signatures.add((TemplateType) member);
        }
        return signatures.build();
      } else if (calleeType.getKind() == SoyType.Kind.UNKNOWN) {
        // We may end up with UNKNOWN here for external calls.
        return ImmutableList.of();
      } else {
        // Rely on previous passes to catch this with nice error messages.
        throw new IllegalStateException("Unexpected type in call: " + calleeType);
      }
    } else {
      String calleeName = ((CallDelegateNode) node).getDelCalleeName();
      return delTemplateSelector.delTemplateNameToValues().get(calleeName).stream()
          .map(TemplateMetadata::asTemplateType)
          .collect(toImmutableList());
    }
  }

  @Override
  public ImmutableMap<String, TemplatesPerFile> getTemplatesPerFile() {
    return templatesPerFile;
  }

  @Override
  public TemplatesPerFile getTemplatesPerFile(String fileName) {
    return templatesPerFile.get(fileName);
  }

  /**
   * Retrieves a template or element given the template name.
   *
   * @param templateName The basic template name to retrieve.
   * @return The corresponding template/element, or null if the name is not defined.
   */
  @Override
  @Nullable
  public TemplateMetadata getBasicTemplateOrElement(String templateName) {
    return basicTemplatesOrElementsMap.get(templateName);
  }

  /** Returns a multimap from delegate template name to set of keys. */
  @Override
  public DelTemplateSelector<TemplateMetadata> getDelTemplateSelector() {
    return delTemplateSelector;
  }

  @Override
  public TemplateMetadata getMetadata(TemplateNode node) {
    return checkNotNull(
        allTemplates.get(checkNotNull(node.getTemplateName())),
        "Cannot find template metadata for file: %s. Known file names are: %s. Are you missing a"
            + " dependency?",
        node,
        templatesPerFile.keySet());
  }

  /**
   * Returns all registered templates ({@link TemplateBasicNode basic} and {@link
   * TemplateDelegateNode delegate} nodes), in no particular order.
   */
  @Override
  public ImmutableList<TemplateMetadata> getAllTemplates() {
    return ImmutableList.copyOf(allTemplates.values());
  }

  @Override
  public ImmutableSet<String> getAllFileNames() {
    return templatesPerFile.keySet();
  }

  /**
   * Gets the content kind that a call results in. If used with delegate calls, the delegate
   * templates must use strict autoescaping. This relies on the fact that all delegate calls must
   * have the same kind when using strict autoescaping. This is enforced by CheckDelegatesPass.
   *
   * @param node The {@link CallBasicNode} or {@link CallDelegateNode}.
   * @return The kind of content that the call results in.
   */
  @Override
  public Optional<SanitizedContentKind> getCallContentKind(CallNode node) {
    ImmutableList<TemplateType> templateNodes = getTemplates(node);
    // For per-file compilation, we may not have any of the delegate templates in the compilation
    // unit.
    if (!templateNodes.isEmpty()) {
      return Optional.of(templateNodes.get(0).getContentKind());
    }
    // The template node may be null if the template is being compiled in isolation.
    return Optional.empty();
  }
}
