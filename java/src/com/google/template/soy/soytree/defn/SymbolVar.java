/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.soytree.defn;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.base.internal.SetOnce;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.basetree.Copyable;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.types.SoyType;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A reference to an imported symbol. For proto deps, which have nested symbols under the imported
 * module or type, we may create nested instances of this class via {@link #nested}. Likewise for
 * templates and constants under a template module import.
 */
public final class SymbolVar extends AbstractVarDefn implements Copyable<SymbolVar> {

  /** The kind of symbol this var references. */
  public enum SymbolKind {
    SOY_FILE,
    TEMPLATE,
    CONST,
    EXTERN,
    TYPEDEF,

    PROTO_MESSAGE,
    PROTO_ENUM,
    PROTO_EXT,

    CSS_MODULE,
    CSS_CLASS,

    TOGGLE,

    UNKNOWN
  }

  public static final String MODULE_IMPORT = "*";

  private final String symbol;
  // A registry of all lazily created nested types. Store these in a BIDI tree here for reachability
  // during AST copying as well as during constant type resolution.
  private final Map<String, SymbolVar> nestedVarDefns;
  // A back reference to the parent if this is a nested type.
  private final SymbolVar parent;
  // The file path of the ImportNode that owns this var. Only set if parent == null.
  private final SetOnce<SourceLogicalPath> filePath;
  private final SetOnce<Boolean> imported;
  private final SetOnce<SymbolKind> kind;

  public SymbolVar(String name, @Nullable String alias, SourceLocation nameLocation) {
    super(alias != null ? alias : name, nameLocation, null);
    Preconditions.checkArgument(alias == null || !alias.isEmpty());
    this.nestedVarDefns = new LinkedHashMap<>();
    this.symbol = name;
    this.parent = null;
    this.filePath = new SetOnce<>();
    this.imported = new SetOnce<>();
    this.kind = new SetOnce<>();
  }

  private SymbolVar(SymbolVar parent, String symbol) {
    super(parent.name() + "." + symbol, parent.nameLocation(), null);
    this.nestedVarDefns = new LinkedHashMap<>();
    this.symbol = symbol;
    this.parent = parent;
    this.filePath = parent.filePath;
    this.imported = parent.imported;
    this.kind = new SetOnce<>();
  }

  private SymbolVar(SymbolVar var, SymbolVar parent, CopyState copyState) {
    super(var);
    this.nestedVarDefns = new LinkedHashMap<>();
    for (Map.Entry<String, SymbolVar> entry : var.nestedVarDefns.entrySet()) {
      SymbolVar newNested = new SymbolVar(entry.getValue(), this, copyState);
      this.nestedVarDefns.put(entry.getKey(), newNested);
      copyState.updateRefs(entry.getValue(), newNested);
    }
    this.symbol = var.symbol;
    this.parent = parent;
    this.filePath = var.filePath.copy();
    this.imported = var.imported.copy();
    this.kind = var.kind.copy();
  }

  public void initFromSoyNode(boolean imported, SourceLogicalPath path) {
    Preconditions.checkState(parent == null);
    if (this.imported.isPresent()) {
      Preconditions.checkArgument(imported == this.imported.get());
      Preconditions.checkArgument(path.equals(this.filePath.get()));
    } else {
      this.imported.set(imported);
      this.filePath.set(path);
    }
  }

  public boolean isImported() {
    return imported.get();
  }

  @Nullable
  public SymbolKind getSymbolKind() {
    return kind.isPresent() ? kind.get() : null;
  }

  public void setSymbolKind(SymbolKind kind) {
    this.kind.set(kind);
  }

  @Override
  public SymbolVar copy(CopyState copyState) {
    return new SymbolVar(this, parent, copyState);
  }

  /**
   * Traverses up through any nested types and returns the top-level var corresponding to what's in
   * the source code.
   */
  public SymbolVar getRoot() {
    return parent != null ? parent.getRoot() : this;
  }

  /** Returns all the lazily created nested vars. */
  public Collection<SymbolVar> getNestedVars() {
    return Collections.unmodifiableCollection(nestedVarDefns.values());
  }

  /** Creates if necessary and returns a var representing a nested symbol. */
  public SymbolVar nested(String symbolName) {
    return nestedVarDefns.computeIfAbsent(symbolName, n -> new SymbolVar(this, n));
  }

  /**
   * Returns the non-prefixed name of the symbol as it appears in the dependency, or "*" for module
   * imports.
   */
  public String getSymbol() {
    return symbol;
  }

  @Override
  public Kind kind() {
    return Kind.SYMBOL;
  }

  public boolean isAliased() {
    return !name().equals(symbol);
  }

  @Override
  public boolean isInjected() {
    return false;
  }

  public boolean isModuleImport() {
    return MODULE_IMPORT.equals(symbol);
  }

  public void setType(SoyType type) {
    this.type = type;
  }

  public SourceLogicalPath getSourceFilePath() {
    return filePath.get();
  }

  /** Returns a list of imported vars, from the root imported symbol to the leaf symbol. */
  public ImmutableList<SymbolVar> getChain() {
    ImmutableList.Builder<SymbolVar> builder = ImmutableList.builder();
    SymbolVar var = this;
    do {
      builder.add(var);
      var = var.parent;
    } while (var != null);
    return builder.build().reverse();
  }

  private static boolean isProtoImport(SymbolVar var) {
    return var.getSymbolKind() == SymbolKind.PROTO_MESSAGE
        || var.getSymbolKind() == SymbolKind.PROTO_ENUM
        || var.getSymbolKind() == SymbolKind.PROTO_EXT;
  }

  @Override
  public boolean isEquivalent(VarDefn other) {
    // TODO: This logic is incomplete, there are other cases where imported vars should be
    // considered equivalent. For now, only imported proto types are handled.
    if (this == other) {
      return true;
    }
    if (!(other instanceof SymbolVar)) {
      return false;
    }
    return hasType()
        && other.hasType()
        && isProtoImport(this)
        && isProtoImport((SymbolVar) other)
        && type().equals(other.type());
  }
}
