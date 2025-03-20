/*
 * Copyright 2020 Google Inc.
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

package com.google.template.soy.types;

import com.google.template.soy.base.SourceLogicalPath;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Set;

/** A type representing a namespace containing other symbols. */
public class NamespaceType extends SoyType {

  private final SourceLogicalPath path;
  private Set<String> nestedSymbolNames;

  public NamespaceType(SourceLogicalPath path, Set<String> nestedSymbolNames) {
    this.path = path;
    this.nestedSymbolNames = nestedSymbolNames;
  }

  @Override
  public Kind getKind() {
    return Kind.NAMESPACE;
  }

  @Override
  public String toString() {
    return "namespace " + path.path();
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    throw new UnsupportedOperationException();
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    throw new UnsupportedOperationException();
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    return false;
  }

  public boolean containsSymbol(String symbol) {
    return nestedSymbolNames.contains(symbol);
  }

  /** Returns the full list of any valid nested symbols, of any type, within this type. */
  public Set<String> getNestedSymbolNames() {
    return nestedSymbolNames;
  }
}
