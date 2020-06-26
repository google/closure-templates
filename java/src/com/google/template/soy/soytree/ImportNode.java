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

  private final ImportType importType;

  /**
   * Whether the import has been resolved/validated yet (some import types need to be processed in
   * multiple passes).
   */
  private boolean isResolved;

  /** Only Proto is supported right now. */
  public enum ImportType {
    PROTO {
      @Override
      public boolean requiresSymbols() {
        return true;
      }
    },
    TEMPLATE {
      @Override
      public boolean requiresSymbols() {
        return true;
      }
    },
    UNKNOWN;

    public boolean allowsSymbols() {
      return true;
    }

    public boolean requiresSymbols() {
      return false;
    }
  }

  public ImportNode(int id, SourceLocation location, StringNode path, List<ImportedVar> defns) {
    super(id, location);
    this.identifiers = ImmutableList.copyOf(defns);
    this.path = path;
    this.importType = importTypeForPath(path.getValue());
    this.isResolved = false;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ImportNode(ImportNode orig, CopyState copyState) {
    super(orig, copyState);
    this.identifiers = orig.identifiers.stream().map(ImportedVar::clone).collect(toImmutableList());
    this.path = orig.path.copy(copyState);
    this.importType = orig.importType;
    this.isResolved = orig.isResolved;
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

  public ImportType getImportType() {
    return importType;
  }

  private static ImportType importTypeForPath(String path) {
    if (path.endsWith(".proto")) {
      return ImportType.PROTO;
    } else if (path.endsWith(".soy")) {
      return ImportType.TEMPLATE;
    }
    return ImportType.UNKNOWN;
  }

  public StringNode getPathNode() {
    return path;
  }

  public String getPath() {
    return path.getValue();
  }

  public boolean isResolved() {
    return isResolved;
  }

  public void setIsResolved() {
    this.isResolved = true;
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
                  .map(i -> i.isAliased() ? i.name() + " as " + i.getAlias() : i.name())
                  .collect(joining(",")));
    }
    return String.format("import %s'%s'", exprs, path.getValue());
  }
}
