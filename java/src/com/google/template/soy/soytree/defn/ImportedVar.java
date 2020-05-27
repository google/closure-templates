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
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.types.UnknownType;
import javax.annotation.Nullable;

/**
 * A reference to an imported variable. TODO(tomnguyen): This must be fleshed out to include type
 * information.
 */
public final class ImportedVar extends AbstractVarDefn {

  private final String alias;

  /** @param name The variable name. */
  public ImportedVar(String name, @Nullable String alias, SourceLocation nameLocation) {
    super(name, nameLocation, UnknownType.getInstance());
    Preconditions.checkArgument(alias == null || (!alias.isEmpty() && !alias.equals(name)));
    this.alias = alias;
  }

  private ImportedVar(ImportedVar var) {
    super(var);
    this.alias = var.alias;
  }

  @Override
  public Kind kind() {
    return Kind.UNDECLARED;
  }

  public String getAlias() {
    return alias;
  }

  public boolean isAliased() {
    return alias != null;
  }

  /** Returns the symbol by which this var can be referenced in the file. */
  public String aliasOrName() {
    return alias != null ? alias : name();
  }

  @Override
  public ImportedVar clone() {
    return new ImportedVar(this);
  }

  @Override
  public boolean isInjected() {
    return false;
  }
}
