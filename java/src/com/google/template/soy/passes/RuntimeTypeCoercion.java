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

package com.google.template.soy.passes;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.NumberType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.UnionType;
import java.util.HashSet;
import java.util.Set;

/** Static utility for adding runtime casts to the Soy AST. */
final class RuntimeTypeCoercion {

  /**
   * For int values passed into template param float, perform automatic type coercion from the call
   * param value to the template param type.
   *
   * <p>Supported coercions:
   *
   * <ul>
   *   <li>int -> float/number
   *   <li>int|X -> float/number|X
   * </ul>
   *
   * @param node Node containing expression value to maybe-coerce.
   * @param toType The required type.
   * @return The new coerced type
   */
  @CanIgnoreReturnValue
  static SoyType maybeCoerceType(ExprNode node, SoyType toType) {
    SoyType fromType = node.getType();
    if (toType.isAssignableFromStrict(fromType)
        || !fromType.isAssignableFromStrict(IntType.getInstance())
        || !toType.isAssignableFromStrict(NumberType.getInstance())) {
      return fromType;
    }

    BuiltinFunction coercion = null;
    SoyType updatedType = fromType;

    if (fromType.getKind() == Kind.INT) {
      coercion = BuiltinFunction.TO_NUMBER;
      updatedType = NumberType.getInstance();
    } else if (fromType.getKind() == Kind.UNION) {
      UnionType unionType = (UnionType) fromType;
      if (fromType.isAssignableFromStrictWithoutCoercions(IntType.getInstance())
          && !toType.isAssignableFromStrictWithoutCoercions(IntType.getInstance())) {
        coercion = BuiltinFunction.INT_TO_NUMBER;
        updatedType = replaceMember(unionType, NumberType.getInstance(), Kind.INT);
      }
    }

    if (coercion == null) {
      return fromType;
    }

    // create a node to wrap param in coercion
    FunctionNode coercedValue =
        FunctionNode.newPositional(
            Identifier.create(coercion.getName(), node.getSourceLocation()),
            coercion,
            node.getSourceLocation());
    coercedValue.setType(updatedType);

    ParentExprNode parent = node.getParent();
    parent.replaceChild(node, coercedValue);
    coercedValue.addChild(node);
    return updatedType;
  }

  private static SoyType replaceMember(UnionType unionType, SoyType add, Kind... remove) {
    Set<SoyType> members = new HashSet<>();
    members.add(add);
    MEMBER:
    for (SoyType member : unionType.getMembers()) {
      for (Kind kind : remove) {
        if (kind == member.getKind()) {
          continue MEMBER;
        }
      }
      members.add(member);
    }
    return UnionType.of(members);
  }

  private RuntimeTypeCoercion() {}
}
