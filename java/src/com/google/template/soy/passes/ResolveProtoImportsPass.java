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
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.ImportsContext.ImportsTypeRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.SoyTypeRegistryBuilder.ProtoSoyTypeRegistry;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves Soy proto imports; verifies that the imports are valid and populates a local type
 * registry that maps the imported symbols to their types.
 */
@RunBefore({
  // Basically anything that needs types...
  ResolveExpressionTypesPass.class,
  // Need proto imports to distinguish empty proto inits from function calls.
  ResolvePluginsPass.class,
  ResolveTemplateParamTypesPass.class,
  ResolveExpressionTypesPass.class, // To resolve extensions.
  RewriteGlobalsPass.class, // To resolve extensions.
})
final class ResolveProtoImportsPass extends ImportsPass implements CompilerFilePass {
  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;
  private final ImmutableMap<String, FileDescriptor> pathToDescriptor;

  ResolveProtoImportsPass(
      SoyTypeRegistry typeRegistry, ErrorReporter errorReporter, boolean disableAllTypeChecking) {
    this.typeRegistry = typeRegistry;
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
    this.pathToDescriptor =
        typeRegistry instanceof ProtoSoyTypeRegistry
            ? ((ProtoSoyTypeRegistry) typeRegistry)
                .getFileDescriptors().stream()
                    .collect(toImmutableMap(FileDescriptor::getName, d -> d))
            : ImmutableMap.of();
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    visitFile(file);
  }

  @Override
  ImportVisitor createImportVisitorForFile(SoyFileNode file) {
    return new ProtoImportVisitor(
        file, typeRegistry, pathToDescriptor, errorReporter, disableAllTypeChecking);
  }

  private static final class ProtoImportVisitor extends ImportVisitor {
    private final SoyTypeRegistry typeRegistry;
    private final boolean disableAllTypeChecking;
    private final ImmutableMap<String, FileDescriptor> pathToDescriptor;
    private final ImmutableMap.Builder<String, String> messagesAndEnums = ImmutableMap.builder();
    private final ImmutableMap.Builder<String, String> extensions = ImmutableMap.builder();

    ProtoImportVisitor(
        SoyFileNode file,
        SoyTypeRegistry typeRegistry,
        ImmutableMap<String, FileDescriptor> pathToDescriptor,
        ErrorReporter errorReporter,
        boolean disableAllTypeChecking) {
      super(file, ImmutableSet.of(ImportType.PROTO), errorReporter);
      this.pathToDescriptor = pathToDescriptor;
      this.typeRegistry = typeRegistry;
      this.disableAllTypeChecking = disableAllTypeChecking;
    }

    @Override
    void visitImportNodeWithValidPathAndSymbol(ImportNode node) {
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
          reportUnknownSymbolError(symbol.nameLocation(), name, node.getPath(), validSymbols);
          continue;
        }

        if (extensionNames.contains(name)) {
          extensions.put(symbol.aliasOrName(), fd.getPackage() + "." + name);
        } else {
          messagesAndEnums.put(symbol.aliasOrName(), fd.getPackage() + "." + name);
        }
      }
    }

    @Override
    boolean importExists(ImportType importType, String path) {
      if (disableAllTypeChecking) {
        return true;
      }
      return pathToDescriptor.containsKey(path);
    }

    @Override
    void updateImportsContext() {
      // Sets the local type registry for the file's {@link ImportsContext}.
      file.getImportsContext()
          .setTypeRegistry(
              disableAllTypeChecking
                  ? typeRegistry
                  : new ImportsTypeRegistry(
                      typeRegistry, messagesAndEnums.build(), extensions.build()));
    }

    @Override
    ImmutableSet<String> getValidImportPathsForType(ImportType type) {
      // Get the names of all proto files registered in the file set (including its deps).
      // We can ignore the type param because this visitor only visits proto imports.
      return pathToDescriptor.keySet();
    }
  }
}
