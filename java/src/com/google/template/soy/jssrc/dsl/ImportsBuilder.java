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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

/** Allows building imports incrementally. */
public class ImportsBuilder {

  private final TreeMap<String, ImportList> imports;

  public ImportsBuilder() {
    this.imports = new TreeMap<>();
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

  public CodeChunk build() {
    List<Statement> importStatements = new ArrayList<>();
    for (Entry<String, ImportList> entry : imports.entrySet()) {
      ImportList list = entry.getValue();
      if (!list.importedSymbols.isEmpty()) {
        importStatements.add(Import.symbolImport(list.importedSymbols, entry.getKey()));
      }
      if (!list.moduleImport.isEmpty()) {
        importStatements.add(Import.moduleImport(list.moduleImport, entry.getKey()));
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
