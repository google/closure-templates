/*
 * Copyright 2025 Google Inc.
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
import com.google.common.base.Preconditions;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.exprtree.UndefinedNode;

/** Node representing a literal type. */
@AutoValue
public abstract class LiteralTypeNode extends TypeNode {

  public static LiteralTypeNode create(ExprNode literal) {
    Preconditions.checkArgument(
        literal instanceof StringNode
            || literal instanceof NullNode
            || literal instanceof UndefinedNode);
    return new AutoValue_LiteralTypeNode(literal.getSourceLocation(), literal);
  }

  public abstract ExprNode literal();

  @Override
  public final String toString() {
    return literal().toSourceString();
  }

  @Override
  public LiteralTypeNode copy(CopyState copyState) {
    return LiteralTypeNode.create(literal().copy(copyState));
  }
}
