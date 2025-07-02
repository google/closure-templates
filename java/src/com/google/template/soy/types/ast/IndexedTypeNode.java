/*
 * Copyright 2024 Google Inc.
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
import com.google.template.soy.basetree.CopyState;

/** An indexed type, e.g. NamedType["property"]. */
@AutoValue
public abstract class IndexedTypeNode extends TypeNode {
  public static IndexedTypeNode create(SourceLocation location, TypeNode type, TypeNode property) {
    return new AutoValue_IndexedTypeNode(location, type, property);
  }

  public abstract TypeNode type();

  public abstract TypeNode property();

  @Override
  public final String toString() {
    return type().toString() + "[" + property() + "]";
  }

  @Override
  public IndexedTypeNode copy(CopyState copyState) {
    return create(sourceLocation(), type().copy(copyState), property().copy(copyState));
  }
}
