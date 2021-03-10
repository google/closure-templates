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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.internal.proto.Field;
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
final class ProtoImportProcessor implements ImportsPass.ImportProcessor {
  private final SoyTypeRegistry typeRegistry;
  private final ErrorReporter errorReporter;
  private final boolean disableAllTypeChecking;
  private final ImmutableMap<String, FileDescriptor> pathToDescriptor;
  private Map<String, String> msgAndEnumLocalToFqn;
  private Map<String, String> extLocalToFqn;

  ProtoImportProcessor(
      SoyTypeRegistry typeRegistry, ErrorReporter errorReporter, boolean disableAllTypeChecking) {
    this.typeRegistry = typeRegistry;
    this.errorReporter = errorReporter;
    this.disableAllTypeChecking = disableAllTypeChecking;
    this.pathToDescriptor =
        typeRegistry.getProtoDescriptors().stream()
            .collect(toImmutableMap(FileDescriptor::getName, d -> d));
  }

  @Override
  public boolean handlesPath(String path) {
    return pathToDescriptor.containsKey(path);
  }

  @Override
  public ImmutableCollection<String> getAllPaths() {
    return pathToDescriptor.keySet();
  }

  @Override
  public void handle(SoyFileNode file, ImmutableCollection<ImportNode> imports) {
    msgAndEnumLocalToFqn = new HashMap<>();
    extLocalToFqn = new HashMap<>();

    for (ImportNode anImport : imports) {
      anImport.setImportType(ImportType.PROTO);
      if (anImport.isModuleImport()) {
        processImportedModule(anImport);
      } else {
        processImportedSymbols(anImport);
      }
    }
    updateImportsContext(file);
  }

  @Override
  public void init(ImmutableList<SoyFileNode> sourceFiles) {}

  private void processImportedSymbols(ImportNode node) {
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

      ImportsPass.reportUnknownSymbolError(
          errorReporter,
          symbol.nameLocation(),
          name,
          node.getPath(),
          Sets.union(messages.keySet(), Sets.union(enums.keySet(), extensions.keySet())));
    }
  }

  private void processImportedModule(ImportNode node) {
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
    for (GenericDescriptor descriptor : Iterables.concat(fd.getMessageTypes(), fd.getEnumTypes())) {
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

  void updateImportsContext(SoyFileNode file) {
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
}
