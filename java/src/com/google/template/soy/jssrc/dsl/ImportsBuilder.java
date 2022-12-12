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
import javax.annotation.Nullable;

/**
 * Allows building imports incrementally.
 *
 * <p>Also manages proto imports, keeping track of which imported symbols are referenced via
 * getImportedProtoSymbol() and only emitting referenced symbols in the emitted import statements.
 */
public class ImportsBuilder {

  // A map of imported file to imported symbol(s).
  private final SortedMap<String, ImportList> imports;

  @AutoValue
  abstract static class ProtoImportData {

    static ProtoImportData create(String file, String symbol, String alias) {
      return new AutoValue_ImportsBuilder_ProtoImportData(file, symbol, alias);
    }

    abstract String file();

    abstract String symbol();

    abstract String alias();
  }

  // A map of fully qualified proto name to the imported file, symbol, and alias.
  private final Map<String, ProtoImportData> protoImportData;

  public ImportsBuilder() {
    this.imports = new TreeMap<>();
    this.protoImportData = new HashMap<>();
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
    imports.computeIfAbsent(file, k -> new ImportList()).setModuleImport(symbol);
  }

  /**
   * Add an import for a single proto from a single file. Note that it won't be emitted from
   * `build()` unless it is marked as used with `useImportedProtoSymbol()`.
   */
  public void importProto(String file, String symbol, String fqn) {
    protoImportData.put(fqn, ProtoImportData.create(file, symbol, ""));
  }

  /**
   * Add an aliased import for a single proto from a single file. Note that it won't be emitted from
   * `build()` unless it is marked as used with `useImportedProtoSymbol()`.
   */
  public void importProtoAlias(String file, String symbol, String alias, String fqn) {
    protoImportData.put(fqn, ProtoImportData.create(file, symbol, alias));
  }

  /**
   * Given the fully qualified proto name, return the file local name (the imported symbol, plus any
   * additional parts for nested protos), and mark the symbol as referenced.
   */
  @Nullable
  public String useImportedProtoSymbol(String fqn) {
    String symbol = fqn;
    String rest = "";
    do {
      if (protoImportData.containsKey(symbol)) {
        ProtoImportData data = protoImportData.get(symbol);
        if (data.alias().isEmpty()) {
          importSymbol(data.file(), data.symbol());
        } else {
          importSymbolAlias(data.file(), data.symbol(), data.alias());
        }
        return (data.alias().isEmpty() ? data.symbol() : data.alias()) + rest;
      }
      int dotIndex = symbol.lastIndexOf(".");
      if (dotIndex == -1) {
        break;
      }
      rest = symbol.substring(dotIndex) + rest;
      symbol = symbol.substring(0, dotIndex);
    } while (true);
    return null;
  }

  public CodeChunk build() {
    List<Statement> importStatements = new ArrayList<>();
    for (Entry<String, ImportList> entry : imports.entrySet()) {
      ImportList importList = entry.getValue();
      if (!importList.importedSymbols.isEmpty()) {
        importStatements.add(Import.symbolImport(importList.importedSymbols, entry.getKey()));
      }
      if (!importList.moduleImport.isEmpty()) {
        importStatements.add(Import.moduleImport(importList.moduleImport, entry.getKey()));
      }
    }
    return Statement.of(importStatements);
  }

  public void ingest(Iterable<Statement> statements) {
    List<GoogRequire> requires = new ArrayList<>();
    for (Statement statement : statements) {
      statement.collectRequires(requires::add);
    }
    for (GoogRequire require : requires) {
      if (require.chunk() instanceof Import) {
        Import i = (Import) require.chunk();
        ImportList list = imports.computeIfAbsent(i.path(), k -> new ImportList());
        for (String symbol : i.symbols()) {
          list.addSymbolRaw(symbol);
        }
      } else {
        throw new IllegalArgumentException("Not an import: " + require.chunk().getCode());
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
    private final TreeSet<String> importedSymbols;
    private String moduleImport;

    ImportList() {
      this.importedSymbols = new TreeSet<>();
      moduleImport = "";
    }

    void addSymbol(String symbol) {
      importedSymbols.add(symbol);
    }

    void addSymbolRaw(String symbol) {
      if (symbol.startsWith("* as ")) {
        setModuleImport(symbol.substring(5));
      } else {
        addSymbol(symbol);
      }
    }

    void setModuleImport(String symbol) {
      Preconditions.checkState(
          moduleImport.isEmpty() || symbol.equals(moduleImport),
          String.format(
              "Attempting to reset import symbol from '%s' to '%s'.", moduleImport, symbol));
      moduleImport = symbol;
    }
  }
}
