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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.FileMetadata;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.PartialFileMetadata;
import com.google.template.soy.soytree.PartialFileSetMetadata;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.soytree.defn.ImportedVar.SymbolKind;
import com.google.template.soy.types.NamespaceType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TemplateImportType;
import com.google.template.soy.types.UnknownType;
import java.util.function.Supplier;

/**
 * Resolves Soy template imports; verifies that the imports are valid and populates a local template
 * registry that maps the imported symbols to their types.
 */
public final class TemplateImportProcessor implements ImportsPass.ImportProcessor {

  private final ErrorReporter errorReporter;
  private final Supplier<PartialFileSetMetadata> partialFileSetMetadata;

  private SoyTypeRegistry typeRegistry;

  TemplateImportProcessor(
      ErrorReporter errorReporter, Supplier<PartialFileSetMetadata> partialFileSetMetadata) {
    this.partialFileSetMetadata = partialFileSetMetadata;
    this.errorReporter = errorReporter;
  }

  @Override
  public void init(ImmutableList<SoyFileNode> sourceFiles) {}

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
    PartialFileMetadata fileMetadata =
        partialFileSetMetadata.get().getPartialFile(node.getSourceFilePath());

    for (ImportedVar symbol : node.getIdentifiers()) {
      String name = symbol.getSymbol();

      if (fileMetadata.hasConstant(name)) {
        symbol.setSymbolKind(SymbolKind.CONST);
      } else if (fileMetadata.hasExtern(name)) {
        symbol.setSymbolKind(SymbolKind.EXTERN);
      } else if (fileMetadata.hasTypeDef(name)) {
        symbol.setSymbolKind(SymbolKind.TYPEDEF);
      } else if (fileMetadata.hasTemplate(name)) {
        symbol.setSymbolKind(SymbolKind.TEMPLATE);
        symbol.setType(
            typeRegistry.intern(TemplateImportType.create(templateFqn(fileMetadata, name))));
      } else {
        symbol.setSymbolKind(SymbolKind.UNKNOWN);
        symbol.setType(UnknownType.getInstance());
        ImportsPass.reportUnknownSymbolError(
            errorReporter,
            symbol.nameLocation(),
            name,
            node.getPath(),
            /* validSymbols= */ fileMetadata.allSymbolNames());
      }

      if (!symbol.hasType() && fileMetadata instanceof FileMetadata) {
        setSymbolType(symbol, (FileMetadata) fileMetadata);
      }
    }
  }

  static String templateFqn(PartialFileMetadata file, String name) {
    String namespace = file.getNamespace();
    return namespace.isEmpty() ? name : namespace + "." + name;
  }

  static void setSymbolType(ImportedVar symbol, FileMetadata fileMetadata) {
    Preconditions.checkArgument(!symbol.hasType());
    String name = symbol.getSymbol();
    if (fileMetadata.hasConstant(name)) {
      symbol.setType(fileMetadata.getConstant(name).getType());
    } else if (fileMetadata.hasExtern(name)) {
      // The return type is what's important here, and extern overloads are
      // required to have the same return type, so it's okay to just grab the
      // first one.
      symbol.setType(Iterables.getFirst(fileMetadata.getExterns(name), null).getSignature());
    } else if (fileMetadata.hasTypeDef(name)) {
      symbol.setType(fileMetadata.getTypeDef(name).getType());
    } else if (fileMetadata.hasTemplate(name)) {
      symbol.setType(TemplateImportType.create(templateFqn(fileMetadata, name)));
    }
  }

  /**
   * Visits a template module import (e.g. "import * as fooTemplates from foo.soy"). Registers all
   * the templates in the imported file (e.g. "fooTemplates.render"). Note that this is only called
   * after we've verified that the import path exists and the module alias symbol is valid (doesn't
   * collide with other import symbol aliases).
   */
  private void processImportedModule(ImportNode node) {
    ImportedVar var = Iterables.getOnlyElement(node.getIdentifiers());
    var.setType(buildModuleType(node));
    var.setSymbolKind(SymbolKind.SOY_FILE);
  }

  private NamespaceType buildModuleType(ImportNode node) {
    SourceLogicalPath path = node.getSourceFilePath();
    PartialFileMetadata templatesPerFile = partialFileSetMetadata.get().getPartialFile(path);
    return new NamespaceType(path, templatesPerFile.allSymbolNames());
  }

  @Override
  public boolean handlesPath(SourceLogicalPath path) {
    return partialFileSetMetadata.get().getPartialFile(path) != null;
  }

  @Override
  public ImmutableCollection<SourceLogicalPath> getAllPaths() {
    return partialFileSetMetadata.get().getAllPartialFiles().stream()
        .map(f -> f.getPath().asLogicalPath())
        .collect(toImmutableSet());
  }
}
