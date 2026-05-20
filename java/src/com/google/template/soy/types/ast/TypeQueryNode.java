/*
 * Copyright 2026 Google Inc.
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
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;

/** A type query i.e. "typeof $a" */
@AutoValue
public abstract class TypeQueryNode extends TypeNode {

  public static TypeQueryNode create(SourceLocation loc, ExprNode query) {
    return new AutoValue_TypeQueryNode(loc, new ExprRootNode(query));
  }

  TypeQueryNode() {}

  public abstract ExprRootNode query();

  @Override
  public final String toString() {
    return "typeof " + query().toSourceString();
  }

  @Override
  public TypeQueryNode copy(CopyState copyState) {
    TypeQueryNode copy = create(sourceLocation(), copyState.copyNullable(query().getRoot()));
    copy.copyInternal(this);
    return copy;
  }
}
