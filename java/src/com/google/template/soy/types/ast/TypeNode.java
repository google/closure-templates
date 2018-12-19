/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.ast;

import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/** The base class for an immutable node in the type AST. */
public abstract class TypeNode {

  @Nullable private SoyType resolvedType;

  TypeNode() {}

  public abstract SourceLocation sourceLocation();

  public abstract <T> T accept(TypeNodeVisitor<T> visitor);

  public void setResolvedType(SoyType type) {
    checkState(resolvedType == null, "type has already been set to %s", resolvedType);
    resolvedType = type;
  }

  public SoyType getResolvedType() {
    checkState(
        resolvedType != null, "type hasn't been set yet on %s at %s", toString(), sourceLocation());
    return resolvedType;
  }

  public boolean isTypeResolved() {
    return resolvedType != null;
  }

  /** Returns round-trippable (through the parser) source code for this node. */
  @Override
  public abstract String toString();

  public abstract TypeNode copy();

  void copyResolvedTypeFrom(TypeNode old) {
    this.resolvedType = old.resolvedType;
  }
}
