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
import com.google.template.soy.exprtree.StringNode;

/** An indexed type, e.g. NamedType["property"]. */
@AutoValue
public abstract class IndexedTypeNode extends TypeNode {
  public static IndexedTypeNode create(
      SourceLocation location, TypeNode type, StringNode property) {
    return new AutoValue_IndexedTypeNode(location, type, property);
  }

  public abstract TypeNode type();

  public abstract StringNode property();

  @Override
  public final String toString() {
    return type().toString() + "[\"" + property() + "\"]";
  }

  @Override
  public IndexedTypeNode copy() {
    return create(sourceLocation(), type().copy(), property().copy(new CopyState()));
  }
}
