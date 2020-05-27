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

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.css.CssRegistry;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileNode.ImportsContext;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder.ProtoSoyTypeRegistry;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Checks and processes file-level imports. */
final class ImportsPass implements CompilerFilePass {

  private static final SoyErrorKind IMPORT_NOT_IN_DEPS = SoyErrorKind.of("Unknown import dep {0}.");
  private static final SoyErrorKind UNKNOWN_SYMBOL =
      SoyErrorKind.of("Unknown symbol {0} in {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind IMPORT_COLLISION =
      SoyErrorKind.of("Imported symbol {0} conflicts with previously imported symbol.");
  private static final SoyErrorKind SYMBOLS_NOT_ALLOWED =
      SoyErrorKind.of("Imported symbols are not allowed from import type {0}.");
  private static final SoyErrorKind SYMBOLS_REQUIRED =
      SoyErrorKind.of("One or more imported symbols are required for import type {0}.");

  private final SoyTypeRegistry registry;
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;
  private final Optional<CssRegistry> cssRegistry;

  ImportsPass(
      SoyTypeRegistry registry,
      Optional<CssRegistry> cssRegistry,
      ErrorReporter errorReporter,
      boolean disableAllTypeChecking) {
    this.registry = registry;
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
    this.cssRegistry = cssRegistry;
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    ImportVisitor visitor = new ImportVisitor();

    for (ImportNode importNode : file.getImports()) {
      visitor.visit(importNode);
    }

    file.setImportsContext(visitor.getImportsContext());
  }

  private final class ImportVisitor {

    private final ImmutableMap<String, FileDescriptor> pathToDescriptor;
    private final Set<String> allProtoSymbols = new HashSet<>();
    private final ImmutableMap.Builder<String, String> messagesAndEnums = ImmutableMap.builder();
    private final ImmutableMap.Builder<String, String> extensions = ImmutableMap.builder();

    ImportVisitor() {
      this.pathToDescriptor =
          registry instanceof ProtoSoyTypeRegistry
              ? ((ProtoSoyTypeRegistry) registry)
                  .getFileDescriptors().stream()
                      .collect(toImmutableMap(FileDescriptor::getName, d -> d))
              : ImmutableMap.of();
    }

    void visit(ImportNode node) {
      if (!importExists(node.getImportType(), node.getPath())) {
        errorReporter.report(node.getPathSourceLocation(), IMPORT_NOT_IN_DEPS, node.getPath());
        return;
      }

      if (node.getImportType().allowsSymbols()) {
        if (node.getImportType().requiresSymbols() && node.getIdentifiers().isEmpty()) {
          errorReporter.report(node.getSourceLocation(), SYMBOLS_REQUIRED, node.getImportType());
          return;
        }

        for (ImportedVar symbol : node.getIdentifiers()) {
          String name = symbol.aliasOrName();
          if (!allProtoSymbols.add(name)) {
            errorReporter.report(symbol.nameLocation(), IMPORT_COLLISION, name);
            return;
          }
        }
      } else if (!node.getIdentifiers().isEmpty()) {
        errorReporter.report(
            node.getIdentifiers().get(0).nameLocation(), SYMBOLS_NOT_ALLOWED, node.getImportType());
        return;
      }

      switch (node.getImportType()) {
        case CSS:
          // There is nothing else to do here
          return;
        case PROTO:
          visitProto(node);
          return;
        default:
          throw new IllegalArgumentException(node.getImportType().name());
      }
    }

    private void visitProto(ImportNode node) {
      if (disableAllTypeChecking) {
        return;
      }
      FileDescriptor fd = pathToDescriptor.get(node.getPath());
      Set<String> extensionNames = new HashSet<>();
      fd.getExtensions()
          .forEach(
              t ->
                  extensionNames.add(
                      CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, t.getName())));

      Set<String> validSymbols = new HashSet<>();
      fd.getMessageTypes().forEach(t -> validSymbols.add(t.getName()));
      fd.getEnumTypes().forEach(t -> validSymbols.add(t.getName()));
      validSymbols.addAll(extensionNames);

      for (ImportedVar symbol : node.getIdentifiers()) {
        String name = symbol.name();
        if (!validSymbols.contains(name)) {
          errorReporter.report(
              symbol.nameLocation(),
              UNKNOWN_SYMBOL,
              name,
              node.getPath(),
              SoyErrors.getDidYouMeanMessage(validSymbols, name));
          continue;
        }

        if (extensionNames.contains(name)) {
          extensions.put(symbol.aliasOrName(), fd.getPackage() + "." + name);
        } else {
          messagesAndEnums.put(symbol.aliasOrName(), fd.getPackage() + "." + name);
        }
      }
    }

    private boolean importExists(ImportType importType, String path) {
      switch (importType) {
        case CSS:
          return cssImportExists(path);
        case PROTO:
          return protoImportExists(path);
        default:
          throw new IllegalArgumentException(importType.name());
      }
    }

    private boolean cssImportExists(String path) {
      return !cssRegistry.isPresent() || cssRegistry.get().isInRegistry(path);
    }

    private boolean protoImportExists(String path) {
      if (disableAllTypeChecking) {
        return true;
      }
      return pathToDescriptor.containsKey(path);
    }

    ImportsContext getImportsContext() {
      return disableAllTypeChecking
          ? () -> registry
          : new ImportsTypeRegistry(registry, messagesAndEnums.build(), extensions.build());
    }
  }

  private static final class ImportsTypeRegistry extends DelegatingSoyTypeRegistry
      implements ImportsContext {

    // Map of symbol (possibly aliased) to fully qualified proto name (messages and enums).
    private final ImmutableMap<String, String> messagesAndEnums;
    // Map of symbol (possibly aliased) to fully qualified proto name (extensions).
    private final ImmutableMap<String, String> extensions;

    ImportsTypeRegistry(
        SoyTypeRegistry delegate,
        ImmutableMap<String, String> messagesAndEnums,
        ImmutableMap<String, String> extensions) {
      super(delegate);

      this.messagesAndEnums = messagesAndEnums;
      this.extensions = extensions;
    }

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      // Support nested messages by resolving the first token against the map and then appending
      // subsequent tokens.
      int index = typeName.indexOf('.');
      String baseRefType = index >= 0 ? typeName.substring(0, index) : typeName;
      String baseType = messagesAndEnums.get(baseRefType);

      if (baseType == null) {
        return super.getType(typeName);
      }

      String fullType = index >= 0 ? baseType + typeName.substring(index) : baseType;

      // Pass the FQ proto message name to the delegate. The delegate should be a
      // ProtoSoyTypeRegistry, which can resolve any registered FQ proto name. Once we remove the
      // global proto type registration we will need to implement this here rather than delegating
      // to super.
      return super.getType(fullType);
    }

    @Override
    public SoyTypeRegistry getTypeRegistry() {
      return this;
    }

    @Override
    public Identifier resolveAlias(Identifier id, SoyFileHeaderInfo headerInfo) {
      String fullName = extensions.get(id.identifier());
      if (fullName != null) {
        return Identifier.create(fullName, id.location());
      }
      return headerInfo.resolveAlias(id);
    }
  }
}
