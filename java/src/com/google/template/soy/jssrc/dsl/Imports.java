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

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

/** Manages imports for a source file. */
@Immutable
public final class Imports {

  private final String importsString;

  Imports(String importsString) {
    this.importsString = importsString;
  }

  public String toSourceString() {
    return importsString;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Allows building imports incrementally. */
  public static class Builder {

    private final TreeMap<String, ImportList> imports;

    public Builder() {
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

    /** Convenience function to import multiple symbols. */
    public void importSymbols(String file, ImmutableSet<String> symbols) {
      symbols.forEach(symbol -> importSymbol(file, symbol));
    }

    /** Add a module level import. */
    public void importModule(String file, String symbol) {
      imports.computeIfAbsent(file, k -> new ImportList()).setModuleImport(symbol);
    }

    public Imports build() {
      List<String> importStatements = new ArrayList<>();
      for (Entry<String, ImportList> entry : imports.entrySet()) {
        if (!entry.getValue().getImportedSymbolsString().isEmpty()) {
          importStatements.add(
              String.format(
                  "import %s from '%s';",
                  entry.getValue().getImportedSymbolsString(), entry.getKey()));
        }
        if (!entry.getValue().getImportedModuleString().isEmpty()) {
          importStatements.add(
              String.format(
                  "import %s from '%s';",
                  entry.getValue().getImportedModuleString(), entry.getKey()));
        }
      }
      return new Imports(Joiner.on("\n").join(importStatements));
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

    void setModuleImport(String symbol) {
      Preconditions.checkState(
          moduleImport.isEmpty() || symbol.equals(moduleImport),
          String.format(
              "Attempting to reset import symbol from '%s' to '%s'.", moduleImport, symbol));
      moduleImport = symbol;
    }

    String getImportedSymbolsString() {
      if (importedSymbols.isEmpty()) {
        return "";
      }
      return String.format("{%s}", Joiner.on(", ").join(importedSymbols));
    }

    String getImportedModuleString() {
      if (moduleImport.isEmpty()) {
        return "";
      }
      return String.format("* as %s", moduleImport);
    }
  }
}
