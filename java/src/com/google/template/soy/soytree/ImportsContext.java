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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.soytree.TemplateNode.SoyFileHeaderInfo;
import com.google.template.soy.soytree.TemplatesPerFile.TemplateName;
import com.google.template.soy.types.DelegatingSoyTypeRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.TypeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * File-specific imports context. Holds info about the symbols that have been imported in a given
 * file.
 */
public final class ImportsContext {

  private SoyTypeRegistry typeRegistry;
  private ImportsTemplateRegistry templateRegistry;
  private final Set<String> allImportedSymbols;

  public ImportsContext() {
    this.typeRegistry = null;
    this.templateRegistry = null;
    this.allImportedSymbols = new LinkedHashSet<>();
  }

  public boolean addImportedSymbol(String symbol) {
    return allImportedSymbols.add(symbol);
  }

  public void setTypeRegistry(SoyTypeRegistry typeRegistry) {
    checkState(this.typeRegistry == null, "Type registry is already set; cannot be overwritten.");
    this.typeRegistry = typeRegistry;
  }

  public SoyTypeRegistry getTypeRegistry() {
    return checkNotNull(typeRegistry, "Type registry has not been set yet.");
  }

  public void setTemplateRegistry(ImportsTemplateRegistry templateRegistry) {
    checkState(
        this.templateRegistry == null,
        "Template registry is already set; use overrideTemplateRegistry if you're sure you want to"
            + " override.");
    this.templateRegistry = templateRegistry;
  }

  public void overrideTemplateRegistry(ImportsTemplateRegistry templateRegistry) {
    this.templateRegistry = templateRegistry;
  }

  public boolean hasTemplateRegistry() {
    return this.templateRegistry != null;
  }

  public ImportsTemplateRegistry getTemplateRegistry() {
    return checkNotNull(templateRegistry, "Template registry has not been set yet.");
  }

  public Identifier resolveAlias(Identifier id, SoyFileHeaderInfo headerInfo) {
    // If we have an imports type registry, try to resolve the potentially-aliased identifier as a
    // proto extension.
    if ((typeRegistry instanceof ImportsTypeRegistry)) {
      Identifier resolvedProtoExtension =
          ((ImportsTypeRegistry) typeRegistry).resolveExtensionAlias(id);
      if (resolvedProtoExtension != null) {
        return resolvedProtoExtension;
      }
    }
    return headerInfo.resolveAlias(id);
  }

  /**
   * A {@link SoyTypeRegistry} that includes imported symbols (possibly aliased) in a given file.
   */
  public static final class ImportsTypeRegistry extends DelegatingSoyTypeRegistry
      implements TypeRegistry.ProtoRegistry {

    // Map of symbol (possibly aliased) to fully qualified proto name (messages and enums).
    private final ImmutableMap<String, String> messagesAndEnums;
    // Map of symbol (possibly aliased) to fully qualified proto name (extensions).
    private final ImmutableMap<String, String> extensions;
    private final SoyTypeRegistry delegate;

    public ImportsTypeRegistry(
        SoyTypeRegistry delegate,
        ImmutableMap<String, String> messagesAndEnums,
        ImmutableMap<String, String> extensions) {
      super(delegate);
      this.delegate = delegate;
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

    /** Resolves a potentially-aliased identifier against the known extension symbols. */
    private Identifier resolveExtensionAlias(Identifier id) {
      String localSymbol = id.identifier();
      int dotIndex = localSymbol.indexOf('.');
      if (dotIndex >= 0) {
        // Extensions may be nested under top-level messages.
        String fullName = messagesAndEnums.get(localSymbol.substring(0, dotIndex));
        if (fullName != null) {
          return Identifier.create(fullName + localSymbol.substring(dotIndex), id.location());
        }
      } else {
        String fullName = extensions.get(localSymbol);
        if (fullName != null) {
          return Identifier.create(fullName, id.location());
        }
      }
      return null;
    }

    @Override
    public ImmutableSet<FileDescriptor> getFileDescriptors() {
      return delegate instanceof TypeRegistry.ProtoRegistry
          ? ((TypeRegistry.ProtoRegistry) delegate).getFileDescriptors()
          : ImmutableSet.of();
    }
  }

  /**
   * A {@link TemplateRegistry} that includes imported symbols (possibly aliased) in a given file.
   */
  public static final class ImportsTemplateRegistry extends DelegatingTemplateRegistry {
    // Map of import symbol (possibly aliased) to the template it refers to.
    private final ImmutableMap<String, TemplateName> symbolToTemplateMap;
    private final TemplateRegistry fileSetTemplateRegistry;

    public ImportsTemplateRegistry(
        TemplateRegistry fileSetTemplateRegistry,
        ImmutableMap<String, TemplateName> symbolToTemplateMap) {
      super(fileSetTemplateRegistry);
      this.symbolToTemplateMap = symbolToTemplateMap;
      this.fileSetTemplateRegistry = fileSetTemplateRegistry;
    }

    @Override
    public TemplateMetadata getBasicTemplateOrElement(String callTmplName) {
      // If the template name matches an imported template symbol, return the symbol's corresponding
      // template info.
      if (symbolToTemplateMap.containsKey(callTmplName)) {
        return fileSetTemplateRegistry.getBasicTemplateOrElement(
            symbolToTemplateMap.get(callTmplName).fullyQualifiedName());
      }
      // Otherwise, check the file set's template registry (which uses fully qualified names).
      return fileSetTemplateRegistry.getBasicTemplateOrElement(callTmplName);
    }

    public ImmutableMap<String, TemplateName> getSymbolsToTemplateNamesMap() {
      return symbolToTemplateMap;
    }

    public ImmutableSet<String> getImportedSymbols() {
      return symbolToTemplateMap.keySet();
    }
  }
}
