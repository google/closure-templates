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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileNode.ImportsContext;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder.ProtoSoyTypeRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Checks and processes file-level imports. */
final class ImportsPass implements CompilerFilePass {

  private static final SoyErrorKind IMPORT_NOT_IN_DEPS = SoyErrorKind.of("Unknown import dep {0}.");
  private static final SoyErrorKind UNKNOWN_SYMBOL =
      SoyErrorKind.of("Unknown symbol {0} in {1}.{2}", StyleAllowance.NO_PUNCTUATION);
  private static final SoyErrorKind IMPORT_COLLISION =
      SoyErrorKind.of("Imported symbol {0} conflicts with previously imported symbol.");

  private final SoyTypeRegistry registry;
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;

  ImportsPass(
      SoyTypeRegistry registry, ErrorReporter errorReporter, boolean disableAllTypeChecking) {
    this.registry = registry;
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
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

    private final List<ImportNode> allImports = new ArrayList<>();
    private final ImmutableMap<String, FileDescriptor> pathToDescriptor;
    private final Set<String> allProtoSymbols = new HashSet<>();
    private final ImmutableMap.Builder<String, String> localToFullExtName = ImmutableMap.builder();

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

      for (VarDefn symbol : node.getIdentifiers()) {
        String name = symbol.name();
        if (!allProtoSymbols.add(name)) {
          errorReporter.report(symbol.nameLocation(), IMPORT_COLLISION, name);
          return;
        }
      }

      allImports.add(node);

      switch (node.getImportType()) {
        case CSS:
          visitCss(node);
          return;
        case PROTO:
          visitProto(node);
          return;
        default:
          throw new IllegalArgumentException(node.getImportType().name());
      }
    }

    private void visitCss(ImportNode unusedNode) {}

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

      for (VarDefn symbol : node.getIdentifiers()) {
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
          localToFullExtName.put(name, fd.getPackage() + "." + name);
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

    private boolean cssImportExists(String unusedPath) {
      return true;
    }

    private boolean protoImportExists(String path) {
      if (disableAllTypeChecking) {
        return true;
      }
      return pathToDescriptor.containsKey(path);
    }

    public ImportsContext getImportsContext() {
      return disableAllTypeChecking
          ? () -> registry
          : new ImportsTypeRegistry(
              registry, pathToDescriptor, allImports, localToFullExtName.build());
    }
  }

  private static final class ImportsTypeRegistry extends DelegatingSoyTypeRegistry
      implements ImportsContext {

    private final ImmutableMap<String, String> symbolToPath;
    private final ImmutableMap<String, FileDescriptor> pathToDescriptor;
    private final ImmutableMap<String, String> localToFullExtName;

    ImportsTypeRegistry(
        SoyTypeRegistry delegate,
        ImmutableMap<String, FileDescriptor> pathToDescriptor,
        List<ImportNode> imports,
        ImmutableMap<String, String> localToFullExtName) {
      super(delegate);

      this.pathToDescriptor = pathToDescriptor;
      this.localToFullExtName = localToFullExtName;
      ImmutableMap.Builder<String, String> symbolToPath = ImmutableMap.builder();
      imports.stream()
          .filter(i -> i.getImportType() == ImportType.PROTO)
          .forEach(
              i -> {
                String path = i.getPath();
                for (VarDefn symbol : i.getIdentifiers()) {
                  symbolToPath.put(symbol.name(), path);
                }
              });
      this.symbolToPath = symbolToPath.build();
    }

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      int index = typeName.indexOf('.');
      String path;

      if (index >= 0) {
        // Handle nested types like Foo.Bar. We lookup the path/package of Foo and prepend.
        String firstToken = typeName.substring(0, index);
        path = symbolToPath.get(firstToken);
      } else {
        // Handle non-nested types.
        path = symbolToPath.get(typeName);
      }

      // Once we remove the global proto type registration we will need to implement this here
      // rather than delegating to super.
      if (path != null) {
        // Pass the FQ proto message name to the delegate. The delegate should be a
        // ProtoSoyTypeRegistry, which can resolve any registered FQ proto name.
        return super.getType(pathToPackage(path) + "." + typeName);
      }

      return super.getType(typeName);
    }

    private String pathToPackage(String path) {
      return pathToDescriptor.get(path).getPackage();
    }

    @Override
    public SoyTypeRegistry getTypeRegistry() {
      return this;
    }

    @Override
    public Identifier resolveAlias(Identifier id, SoyFileHeaderInfo headerInfo) {
      String fullName = localToFullExtName.get(id.identifier());
      if (fullName != null) {
        return Identifier.create(fullName, id.location());
      }
      return headerInfo.resolveAlias(id);
    }
  }
}
