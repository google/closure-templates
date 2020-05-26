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

import static java.util.stream.Collectors.joining;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.soytree.SoyNode.Kind;
import java.util.List;

/**
 * Node representing a 'import' statement with a value expression.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class ImportNode extends AbstractSoyNode {

  /** The value expression that the variable is set to. */
  private final List<VarDefn> identifiers;

  private final StringNode path;

  /** Only CSS is supported right now. */
  public enum ImportType {
    CSS,
    UNKNOWN,
  }

  public ImportNode(int id, SourceLocation location, StringNode path, List<VarDefn> defns) {
    super(id, location);
    this.identifiers = defns;
    this.path = path;
  }

  /**
   * Copy constructor.
   *
   * @param orig The node to copy.
   */
  private ImportNode(ImportNode orig, CopyState copyState) {
    super(orig, copyState);
    this.identifiers = orig.identifiers;
    this.path = orig.path.copy(copyState);
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

  private ImportType getImportType() {
    // TODO(tomnguyen): Throw an error if any aliases are extracted from CSS imports, as they do not
    // exist yet.
    if (path.getValue().endsWith(".gss") || path.getValue().endsWith(".scss")) {
      return ImportType.CSS;
    }
    return ImportType.UNKNOWN;
  }

  public String getPath() {
    return path.getValue();
  }

  public boolean isCss() {
    return getImportType() == ImportType.CSS;
  }

  public List<VarDefn> getIdentifiers() {
    return identifiers;
  }

  @Override
  public String toSourceString() {
    String exprs = "";
    if (!identifiers.isEmpty()) {
      exprs =
          String.format(
              "{%s} from ", identifiers.stream().map(VarDefn::name).collect(joining(",")));
    }
    return String.format("import %s'%s'", exprs, path.getValue());
  }
}
