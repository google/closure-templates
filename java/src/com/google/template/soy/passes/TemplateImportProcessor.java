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

package com.google.template.soy.passes;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSortedMap.toImmutableSortedMap;
import static java.util.Comparator.naturalOrder;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.template.soy.base.SourceFilePath;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.TemplateNode;
import com.google.template.soy.soytree.TemplateRegistry;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.TemplateModuleImportType;
import com.google.template.soy.types.UnknownType;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Resolves Soy template imports; verifies that the imports are valid and populates a local template
 * registry that maps the imported symbols to their types.
 */
public final class TemplateImportProcessor implements ImportsPass.ImportProcessor {

  private final ErrorReporter errorReporter;
  private final Supplier<TemplateRegistry> registryFromDeps;
  private ImmutableMap<SourceFilePath, TemplatesPerFile> allTemplates;

  private SoyTypeRegistry typeRegistry;

  TemplateImportProcessor(
      ErrorReporter errorReporter, Supplier<TemplateRegistry> registryFromDeps) {
    this.registryFromDeps = registryFromDeps;
    this.errorReporter = errorReporter;
  }

  @Override
  public void init(ImmutableList<SoyFileNode> sourceFiles) {
    SetMultimap<SourceFilePath, TemplateName> index = HashMultimap.create();

    registryFromDeps
        .get()
        .getAllTemplates()
        .forEach(
            metadata ->
                index.put(
                    metadata.getSourceLocation().getFilePath(),
                    TemplateName.create(metadata.getTemplateName())));

    for (SoyFileNode file : sourceFiles) {
      for (TemplateNode template : file.getTemplates()) {
        index.put(file.getFilePath(), TemplateName.create(template.getTemplateName()));
      }
    }

    allTemplates =
        index.asMap().entrySet().stream()
            .collect(toImmutableMap(Map.Entry::getKey, e -> TemplatesPerFile.create(e.getValue())));
  }

  @Override
  public void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports) {
    typeRegistry = file.getSoyTypeRegistry();

    for (ImportNode anImport : imports) {
      anImport.setImportType(ImportType.TEMPLATE);
      if (anImport.isModuleImport()) {
        processImportedModule(anImport);
      } else {
        processImportedSymbols(anImport);
      }
    }
  }

  /**
   * Registers the imported templates for a symbol-level import node (as opposed to a module-level
   * import node). Verifies that the template names are valid and stores a mapping from the imported
   * symbol to the template info. Note that this is only called after we've verified that the import
   * path exists and any alias symbols are valid.
   */
  private void processImportedSymbols(ImportNode node) {
    TemplatesPerFile templatesPerFile = allTemplates.get(SourceFilePath.create(node.getPath()));
    for (ImportedVar symbol : node.getIdentifiers()) {
      String name = symbol.getSymbol();
      // Report an error if the template name is invalid.
      if (!templatesPerFile.hasTemplateWithUnqualifiedName(name)) {
        ImportsPass.reportUnknownSymbolError(
            errorReporter,
            symbol.nameLocation(),
            name,
            node.getPath(),
            /* validSymbols= */ templatesPerFile.getUnqualifiedTemplateNames());
        symbol.setType(UnknownType.getInstance());
        continue;
      }

      // Needs to be able to handle duplicates, since the formatter fixes them, but it's not a
      // compiler error (if they have the same path).
      TemplateName templateName = templatesPerFile.getFullTemplateName(name);
      symbol.setType(
          typeRegistry.intern(TemplateImportType.create(templateName.fullyQualifiedName())));
    }
  }

  /**
   * Visits a template module import (e.g. "import * as fooTemplates from foo.soy"). Registers all
   * the templates in the imported file (e.g. "fooTemplates.render"). Note that this is only called
   * after we've verified that the import path exists and the module alias symbol is valid (doesn't
   * collide with other import symbol aliases).
   */
  private void processImportedModule(ImportNode node) {
    SourceFilePath path = SourceFilePath.create(node.getPath());
    TemplatesPerFile templatesPerFile = allTemplates.get(path);
    Iterables.getOnlyElement(node.getIdentifiers())
        .setType(
            typeRegistry.intern(
                TemplateModuleImportType.create(
                    templatesPerFile.getNamespace(),
                    path,
                    templatesPerFile.getTemplateNames().stream()
                        .map(TemplateName::unqualifiedName)
                        .collect(toImmutableSet()))));
  }

  @Override
  public boolean handlesPath(String path) {
    return allTemplates.containsKey(SourceFilePath.create(path));
  }

  @Override
  public ImmutableCollection<String> getAllPaths() {
    return allTemplates.keySet().stream().map(SourceFilePath::path).collect(toImmutableSet());
  }

  /**
   * Template registry for a single soy file. This holds metadata for all the templates in the file
   * (NOT its dependencies).
   */
  @AutoValue
  abstract static class TemplatesPerFile {

    private static TemplatesPerFile create(Collection<TemplateName> value) {
      return new AutoValue_TemplateImportProcessor_TemplatesPerFile(ImmutableSet.copyOf(value));
    }

    public abstract ImmutableSet<TemplateName> getTemplateNames();

    @Memoized
    ImmutableMap<String, TemplateName> templatesByUnqualifiedName() {
      return getTemplateNames().stream()
          .collect(toImmutableSortedMap(naturalOrder(), TemplateName::unqualifiedName, n -> n));
    }

    public String getNamespace() {
      return Iterables.getFirst(getTemplateNames(), null).getNamespace();
    }

    /** Gets the short (unqualified) template names for all the templates in this file. */
    public ImmutableSet<String> getUnqualifiedTemplateNames() {
      return templatesByUnqualifiedName().keySet();
    }

    /** Whether this file has a template with the given unqualified name. */
    public boolean hasTemplateWithUnqualifiedName(String unqualifiedTemplateName) {
      return templatesByUnqualifiedName().containsKey(unqualifiedTemplateName);
    }

    /**
     * Gets the full template name wrapper object for the given unqualified template name. Throws an
     * error if the template does not exist in this file.
     */
    public TemplateName getFullTemplateName(String unqualifiedTemplateName) {
      return templatesByUnqualifiedName().get(unqualifiedTemplateName);
    }
  }

  /**
   * Wrapper for a template name. Stores the fully qualified and unqualified versions of the name
   * (e.g. "my.namespace.foo" and "foo").
   */
  @AutoValue
  abstract static class TemplateName {
    private static TemplateName create(String fullyQualifiedName) {
      Preconditions.checkArgument(
          !Strings.isNullOrEmpty(fullyQualifiedName),
          "Template name cannot be null or empty %s",
          fullyQualifiedName);

      int startOfUnqualifiedName = fullyQualifiedName.lastIndexOf('.');
      String unqualifiedName = fullyQualifiedName.substring(startOfUnqualifiedName + 1);
      return new AutoValue_TemplateImportProcessor_TemplateName(
          fullyQualifiedName, unqualifiedName);
    }

    public abstract String fullyQualifiedName();

    public abstract String unqualifiedName();

    public String getNamespace() {
      if (fullyQualifiedName().equals(unqualifiedName())) {
        return "";
      }
      return fullyQualifiedName()
          .substring(0, fullyQualifiedName().length() - (1 + unqualifiedName().length()));
    }
  }
}
