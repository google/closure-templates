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
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnknownType;
import javax.annotation.Nullable;

/**
 * A reference to an imported variable. TODO(tomnguyen): This must be fleshed out to include type
 * information.
 */
public final class ImportedVar extends AbstractVarDefn {

  public static final String MODULE_IMPORT = "*";

  private final String symbol;

  /**
   * Returns a new unattached AST node representing a new VarDefn of type {@code type}. This is used
   * to define symbols nested within import vars. In the example:
   *
   * <pre>
   * import {Foo} from 'path/foo.proto';
   * ...
   * {let $f : Foo()}
   * {let $b : Foo.Bar()}
   * </pre>
   *
   * In the first let "Foo" is a VarRef to the ImportedVar {Foo}, which has type
   * ProtoImportType(Foo). In the second let "Foo.Bar" is a VarRef to a nested type whose parent is
   * {Foo} and whose type is ProtoImportType(Foo.Bar).
   *
   * @param parent the VarDefn from which the new VarDefn is derived.
   * @param type the type of the derived VarDefn.
   */
  public static VarDefn nested(VarDefn parent, SoyType type) {
    return new NestedVarDefn(parent, type);
  }

  /** @param name The variable name. */
  public ImportedVar(String name, @Nullable String alias, SourceLocation nameLocation) {
    super(alias != null ? alias : name, nameLocation, UnknownType.getInstance());
    Preconditions.checkArgument(alias == null || (!alias.isEmpty() && !alias.equals(name)));
    this.symbol = name;
  }

  private ImportedVar(ImportedVar var) {
    super(var);
    this.symbol = var.symbol;
  }

  public String getSymbol() {
    return symbol;
  }

  @Override
  public Kind kind() {
    return Kind.IMPORT_VAR;
  }

  public boolean isAliased() {
    return !name().equals(symbol);
  }

  @Override
  public ImportedVar clone() {
    return new ImportedVar(this);
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

  /**
   * This type of VarDefn is dangling in the AST, attached only to a VarRef. It implements
   * ImmutableVarDefn to prevent its being copied during tree cloning, which is problematic because
   * of how it dangles.
   */
  private static class NestedVarDefn implements ImmutableVarDefn {

    private final Identifier parentId;
    private final SoyType type;

    public NestedVarDefn(VarDefn parent, SoyType type) {
      this.parentId = Identifier.create(parent.name(), parent.nameLocation());
      this.type = Preconditions.checkNotNull(type);
    }

    @Override
    public Kind kind() {
      return Kind.IMPORT_VAR;
    }

    @Override
    public String name() {
      return parentId.identifier();
    }

    @Nullable
    @Override
    public SourceLocation nameLocation() {
      return parentId.location();
    }

    @Override
    public SoyType type() {
      return type;
    }

    @Override
    public boolean hasType() {
      return true;
    }

    @Override
    public boolean isInjected() {
      return false;
    }
  }
}
