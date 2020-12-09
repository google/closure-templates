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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.base.internal.IdGenerator;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.proto.Field;
import com.google.template.soy.shared.SoyGeneralOptions;
import com.google.template.soy.soytree.ImportNode;
import com.google.template.soy.soytree.ImportNode.ImportType;
import com.google.template.soy.soytree.ImportsContext.ImportsTypeRegistry;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.UnknownType;
import java.util.HashMap;
import java.util.Map;

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
  private final SoyGeneralOptions options;
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;
  private final ImmutableMap<String, FileDescriptor> pathToDescriptor;

  ResolveProtoImportsPass(
      SoyTypeRegistry typeRegistry,
      SoyGeneralOptions options,
      ErrorReporter errorReporter,
      boolean disableAllTypeChecking) {
    this.typeRegistry = typeRegistry;
    this.options = options;
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
    this.pathToDescriptor =
        typeRegistry.getProtoDescriptors().stream()
            .collect(toImmutableMap(FileDescriptor::getName, d -> d));
  }

  @Override
  public void run(SoyFileNode file, IdGenerator nodeIdGen) {
    visitFile(file);
  }

  @Override
  ImportVisitor createImportVisitorForFile(SoyFileNode file) {
    return new ProtoImportVisitor(
        file, typeRegistry, pathToDescriptor, options, errorReporter, disableAllTypeChecking);
  }

  private static final class ProtoImportVisitor extends ImportVisitor {
    private final SoyTypeRegistry typeRegistry;
    private final boolean disableAllTypeChecking;
    private final ImmutableMap<String, FileDescriptor> pathToDescriptor;
    private final Map<String, String> msgAndEnumLocalToFqn = new HashMap<>();
    private final Map<String, String> extLocalToFqn = new HashMap<>();

    ProtoImportVisitor(
        SoyFileNode file,
        SoyTypeRegistry typeRegistry,
        ImmutableMap<String, FileDescriptor> pathToDescriptor,
        SoyGeneralOptions options,
        ErrorReporter errorReporter,
        boolean disableAllTypeChecking) {
      super(file, ImmutableSet.of(ImportType.PROTO), options, errorReporter);
      this.pathToDescriptor = pathToDescriptor;
      this.typeRegistry = typeRegistry;
      this.disableAllTypeChecking = disableAllTypeChecking;
    }

    @Override
    void processImportedSymbols(ImportNode node) {
      if (disableAllTypeChecking) {
        return;
      }
      FileDescriptor fd = pathToDescriptor.get(node.getPath());

      ImmutableMap<String, Descriptor> messages =
          fd.getMessageTypes().stream().collect(toImmutableMap(Descriptor::getName, f -> f));
      ImmutableMap<String, EnumDescriptor> enums =
          fd.getEnumTypes().stream().collect(toImmutableMap(EnumDescriptor::getName, f -> f));
      ImmutableMap<String, FieldDescriptor> extensions =
          fd.getExtensions().stream().collect(toImmutableMap(Field::computeSoyName, f -> f));

      for (ImportedVar symbol : node.getIdentifiers()) {
        String name = symbol.getSymbol();
        String fullName = fd.getPackage().isEmpty() ? name : fd.getPackage() + "." + name;

        Descriptor messageDesc = messages.get(name);
        if (messageDesc != null) {
          putDistinct(msgAndEnumLocalToFqn, symbol.name(), fullName);
          symbol.setType(typeRegistry.getProtoImportType(messageDesc));
          continue;
        }

        EnumDescriptor enumDesc = enums.get(name);
        if (enumDesc != null) {
          putDistinct(msgAndEnumLocalToFqn, symbol.name(), fullName);
          symbol.setType(typeRegistry.getProtoImportType(enumDesc));
          continue;
        }

        FieldDescriptor extDesc = extensions.get(name);
        if (extDesc != null) {
          putDistinct(this.extLocalToFqn, symbol.name(), fullName);
          symbol.setType(typeRegistry.getProtoImportType(extDesc));
          continue;
        }

        symbol.setType(UnknownType.getInstance());

        reportUnknownSymbolError(
            symbol.nameLocation(),
            name,
            node.getPath(),
            Sets.union(messages.keySet(), Sets.union(enums.keySet(), extensions.keySet())));
      }
    }

    @Override
    void processImportedModule(ImportNode node) {
      if (disableAllTypeChecking) {
        return;
      }

      FileDescriptor fd = pathToDescriptor.get(node.getPath());
      node.getIdentifiers().get(0).setType(typeRegistry.getProtoImportType(fd));

      // Add a mapping from "moduleName.ExtensionName" -> fqn, for every top-level extension in the
      // file.
      for (FieldDescriptor ext : fd.getExtensions()) {
        String symbol = Field.computeSoyName(ext);
        String moduleRelativeName = node.getModuleAlias() + "." + symbol;
        String fullName = fd.getPackage().isEmpty() ? symbol : fd.getPackage() + "." + symbol;
        putDistinct(extLocalToFqn, moduleRelativeName, fullName);
      }

      // Add a mapping from "moduleName.ProtoName" -> protoFqn, for every top-level message and enum
      // in the file.
      for (GenericDescriptor descriptor :
          Iterables.concat(fd.getMessageTypes(), fd.getEnumTypes())) {
        String symbol = descriptor.getName();
        String moduleRelativeName = node.getModuleAlias() + "." + symbol;
        String fullName = fd.getPackage().isEmpty() ? symbol : fd.getPackage() + "." + symbol;
        putDistinct(msgAndEnumLocalToFqn, moduleRelativeName, fullName);
      }
    }

    private static <K, V> void putDistinct(Map<K, V> into, K key, V value) {
      V old = into.put(key, value);
      if (old != null) {
        Preconditions.checkArgument(value.equals(old));
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
                      typeRegistry,
                      ImmutableMap.copyOf(msgAndEnumLocalToFqn),
                      ImmutableMap.<String, String>builder()
                          .putAll(msgAndEnumLocalToFqn)
                          .putAll(extLocalToFqn)
                          .build()));
    }

    @Override
    ImmutableSet<String> getValidImportPathsForType(ImportType type) {
      // Get the names of all proto files registered in the file set (including its deps).
      // We can ignore the type param because this visitor only visits proto imports.
      return pathToDescriptor.keySet();
    }
  }
}
