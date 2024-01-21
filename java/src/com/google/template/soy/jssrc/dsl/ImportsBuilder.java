/*
 * Copyright 2022 Google Inc.
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

package com.google.template.soy.jssrc.dsl;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Allows building imports incrementally.
 *
 * <p>Also manages proto imports, keeping track of which imported symbols are referenced via
 * getImportedProtoSymbol() and only emitting referenced symbols in the emitted import statements.
 */
public class ImportsBuilder {

  public static final String NO_ALIAS = "";

  // A map of imported file to imported symbol(s).
  private final SortedMap<String, ImportList> imports;

  private boolean hasElementImport;
  private boolean hasFragmentImport;

  public boolean needsElm() {
    return hasElementImport;
  }

  public boolean needsFrag() {
    return hasFragmentImport;
  }

  @AutoValue
  abstract static class ProtoImportData {

    static ProtoImportData create(String file, String symbol, String alias) {
      Preconditions.checkArgument(!symbol.equals(alias));
      return new AutoValue_ImportsBuilder_ProtoImportData(file, symbol, alias);
    }

    abstract String file();

    abstract String symbol();

    abstract String alias();

    String aliasOrSymbol() {
      return alias().isEmpty() ? symbol() : alias();
    }
  }

  // A map of fully qualified proto name to the imported file, symbol, and alias.
  private final Map<String, ProtoImportData> protoImportData;

  public ImportsBuilder() {
    this.imports = new TreeMap<>();
    this.protoImportData = new HashMap<>();
  }

  private void importSymbol(ProtoImportData data) {
    if (data.alias().isEmpty()) {
      importSymbol(data.file(), data.symbol());
    } else {
      importSymbolAlias(data.file(), data.symbol(), data.alias());
    }
  }

  /** Add an import for a single symbol from a single file */
  public void importSymbol(String file, String symbol) {
    imports.computeIfAbsent(file, k -> new ImportList()).addSymbol(symbol);
  }

  /** Add an aliased import for a single symbol from a single file */
  public void importSymbolAlias(String file, String symbol, String alias) {
    imports
        .computeIfAbsent(file, k -> new ImportList())
        .addSymbol(String.format("%s as %s", symbol, alias));
  }

  /** Add a module level import. */
  public void importModule(String file, String symbol) {
    imports.computeIfAbsent(file, k -> new ImportList()).addModuleImport(symbol);
  }

  /**
   * Add an import for a single proto from a single file. Note that it won't be emitted from
   * `build()` unless it is marked as used with `useImportedProtoSymbol()`. Pass the non-immutable
   * proto name as it appears in Soy.
   */
  public void importProto(String file, String symbol, String fqn) {
    protoImportData.put(fqn, ProtoImportData.create(file, symbol, NO_ALIAS));
  }

  /**
   * Add an aliased import for a single proto from a single file. Note that it won't be emitted from
   * `build()` unless it is marked as used with `useImportedProtoSymbol()`. Pass the non-immutable
   * proto name as it appears in Soy.
   */
  public void importProtoAlias(String file, String symbol, String alias, String fqn) {
    protoImportData.put(fqn, ProtoImportData.create(file, symbol, alias));
  }

  private String makeImmutable(String fqn) {
    int immutableIndex = fqn.lastIndexOf(".") + 1;
    return fqn.substring(0, immutableIndex) + "Immutable" + fqn.substring(immutableIndex);
  }

  /**
   * Given the proto type, return the file local name (the imported symbol, plus any additional
   * parts for nested protos, possibly the immutable version) that should be used in Tsx, and mark
   * the symbol as referenced.
   */
  public String useImportedProtoSymbol(String fqn, boolean makeImmutable) {
    String topLevelMsg = fqn;
    String dotPlusNestedSymbol = "";

    // fqn may be a symbol nested inside a top-level Message. Find the top-level message.
    ProtoImportData data;
    while (true) {
      data = protoImportData.get(topLevelMsg);
      if (data != null) {
        break;
      }

      int dotIndex = topLevelMsg.lastIndexOf(".");
      if (dotIndex == -1) {
        break;
      }
      dotPlusNestedSymbol = topLevelMsg.substring(dotIndex) + dotPlusNestedSymbol;
      topLevelMsg = topLevelMsg.substring(0, dotIndex);
    }

    if (data == null) {
      throw new IllegalArgumentException("Unexpected proto: " + fqn);
    }

    if (!makeImmutable) {
      importSymbol(data);
      // import {Foo} -> Foo, Foo.Nested
      // import {Foo as FooAlias} -> FooAlias, FooAlias.Nested
      return data.aliasOrSymbol() + dotPlusNestedSymbol;
    }

    if (!dotPlusNestedSymbol.isEmpty()) {
      // import {Foo} -> Foo.ImmutableNested
      // import {Foo as FooAlias} -> FooAlias.ImmutableNested
      importSymbol(data);
      return data.aliasOrSymbol() + makeImmutable(dotPlusNestedSymbol);
    }

    if (data.alias().isEmpty()) {
      // import {Foo} -> ImmutableFoo
      importSymbol(data.file(), makeImmutable(data.symbol()));
      return makeImmutable(data.symbol());
    }

    // import {Foo as FooAlias} -> ImmutableFooAlias
    importSymbolAlias(data.file(), makeImmutable(data.symbol()), makeImmutable(data.alias()));
    return makeImmutable(data.alias());
  }

  public CodeChunk build() {
    List<Statement> importStatements = new ArrayList<>();
    for (Entry<String, ImportList> entry : imports.entrySet()) {
      ImportList importList = entry.getValue();
      if (!importList.importedSymbols.isEmpty()) {
        importStatements.add(Import.symbolImport(importList.importedSymbols, entry.getKey()));
      }
      for (String moduleAlias : importList.moduleAliases) {
        importStatements.add(Import.moduleImport(moduleAlias, entry.getKey()));
      }
    }
    return Statements.of(importStatements);
  }

  public void ingest(Iterable<? extends CodeChunk> statements) {
    List<GoogRequire> requires = new ArrayList<>();
    for (CodeChunk statement : statements) {
      statement.collectRequires(requires::add);
    }
    for (GoogRequire require : requires) {
      if (require == TsxFragmentElement.FRAGMENT) {
        hasFragmentImport = true;
      } else if (require == TsxFragmentElement.ELEMENT) {
        hasElementImport = true;
      } else if (require.type() == GoogRequire.Type.IMPORT) {
        Import i = (Import) require.type().getChunk(require);
        ImportList list = imports.computeIfAbsent(i.path(), k -> new ImportList());
        for (String symbol : i.symbols()) {
          list.addSymbolRaw(symbol);
        }
      } else {
        throw new IllegalArgumentException("Not an import: " + require);
      }
    }
  }

  public Collection<String> getCollisions() {
    SortedSet<String> collisions = new TreeSet<>();
    Map<String, String> symbolToPathMap = new HashMap<>();
    for (Entry<String, ImportList> entry : imports.entrySet()) {
      for (String symbol : entry.getValue().importedSymbols) {
        if (symbolToPathMap.containsKey(symbol)) {
          collisions.add("{" + symbol + "} from " + entry.getKey());
          collisions.add("{" + symbol + "} from " + symbolToPathMap.get(symbol));
        } else {
          symbolToPathMap.put(symbol, entry.getKey());
        }
      }
    }
    return collisions;
  }

  /** The imports from a single file. */
  private static class ImportList {
    private final SortedSet<String> importedSymbols;
    private final SortedSet<String> moduleAliases;

    ImportList() {
      this.importedSymbols = new TreeSet<>();
      this.moduleAliases = new TreeSet<>();
    }

    void addSymbol(String symbol) {
      importedSymbols.add(symbol);
    }

    void addSymbolRaw(String symbol) {
      if (symbol.startsWith("* as ")) {
        addModuleImport(symbol.substring(5));
      } else {
        addSymbol(symbol);
      }
    }

    void addModuleImport(String symbol) {
      moduleAliases.add(symbol);
    }
  }
}
