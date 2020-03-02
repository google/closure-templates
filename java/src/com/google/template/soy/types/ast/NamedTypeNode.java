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

import com.google.auto.value.AutoValue;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.Identifier;

/** A simple named type (may be an intrinsic type, '?', or a custom type). */
@AutoValue
public abstract class NamedTypeNode extends TypeNode {
  public static NamedTypeNode create(Identifier identifier) {
    return new AutoValue_NamedTypeNode(identifier.location(), identifier);
  }

  public static NamedTypeNode create(SourceLocation sourceLocation, String name) {
    return create(Identifier.create(name, sourceLocation));
  }

  NamedTypeNode() {}

  public abstract Identifier name();

  @Override
  public final String toString() {
    return name().identifier();
  }

  @Override
  public <T> T accept(TypeNodeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public NamedTypeNode copy() {
    NamedTypeNode copy = create(name());
    copy.copyResolvedTypeFrom(this);
    return copy;
  }
}
