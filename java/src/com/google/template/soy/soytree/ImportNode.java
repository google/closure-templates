/*
 * Copyright 2011 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.soytree.defn.ImportedVar;
import java.util.List;

/**
 * Node representing a 'import' statement with a value expression.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ImportNode extends AbstractSoyNode {

  /** The value expression that the variable is set to. */
  private final ImmutableList<ImportedVar> identifiers;

  private final StringNode path;

  private ImportType importType;

  /** Only Proto is supported right now. */
  public enum ImportType {
    PROTO,
    TEMPLATE,
    UNKNOWN;
  }

  public ImportNode(int id, SourceLocation location, StringNode path, List<ImportedVar> defns) {
    super(id, location);
    this.identifiers = ImmutableList.copyOf(defns);
    this.path = path;
    this.importType = ImportType.UNKNOWN;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ImportNode(ImportNode orig, CopyState copyState) {
    super(orig, copyState);
    this.identifiers =
        orig.identifiers.stream()
            .map(
                prev -> {
                  ImportedVar next = prev.clone();
                  copyState.updateRefs(prev, next);
                  return next;
                })
            .collect(toImmutableList());
    this.path = orig.path.copy(copyState);
    this.importType = orig.importType;
  }

  @Override
  public Kind getKind() {
    return Kind.IMPORT_NODE;
  }

  @Override
  public ImportNode copy(CopyState copyState) {
    return new ImportNode(this, copyState);
  }

  public boolean isSideEffectImport() {
    return identifiers.isEmpty();
  }

  public void setImportType(ImportType importType) {
    this.importType = importType;
  }

  public ImportType getImportType() {
    return importType;
  }

  public String getPath() {
    return path.getValue();
  }

  /**
   * Whether this is a module import (e.g. "import * as foo from ..."), as opposed to a symbol
   * import node (e.g. "import {foo,bar,baz} from ...").
   */
  public boolean isModuleImport() {
    return identifiers.size() == 1 && identifiers.get(0).isModuleImport();
  }

  /**
   * Returns the module alias (e.g. "foo" if the import is "import * as foo from 'my_foo.soy';").
   * This should only be called on module import nodes (i.e. if {@link #isModuleImport} node is
   * true).
   */
  public String getModuleAlias() {
    checkState(
        isModuleImport(),
        "Module alias can only be retrieved for module imports (e.g. \"import * as fooTemplates"
            + " from 'my_foo.soy';\")");
    return identifiers.get(0).name();
  }

  public SourceLocation getPathSourceLocation() {
    return path.getSourceLocation();
  }

  public ImmutableList<ImportedVar> getIdentifiers() {
    return identifiers;
  }

  @Override
  public String toSourceString() {
    String exprs = "";
    if (!identifiers.isEmpty()) {
      exprs =
          String.format(
              "{%s} from ",
              identifiers.stream()
                  .map(i -> i.isAliased() ? i.getSymbol() + " as " + i.name() : i.name())
                  .collect(joining(",")));
    }
    return String.format("import %s'%s'", exprs, path.getValue());
  }
}
