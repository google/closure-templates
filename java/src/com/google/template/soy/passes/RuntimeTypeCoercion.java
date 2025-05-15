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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.NumberType;
import com.google.template.soy.types.SoyType;

/** Static utility for adding runtime casts to the Soy AST. */
final class RuntimeTypeCoercion {

  /**
   * For int values passed into template param float, perform automatic type coercion from the call
   * param value to the template param type.
   *
   * <p>Supported coercions:
   *
   * <ul>
   *   <li>int -> float
   *   <li>int -> float|null
   * </ul>
   *
   * @param node Node containing expression value to maybe-coerce.
   * @param toTypes Acceptable types to attempt to coerce to.
   * @return The new coerced type
   */
  @CheckReturnValue
  static SoyType maybeCoerceType(ExprNode node, ImmutableSet<SoyType> toTypes) {
    SoyType fromType = node.getType();
    if (toTypes.contains(fromType)) {
      return fromType;
    }

    BuiltinFunction coercion = null;
    SoyType coercionTargetType = fromType;
    switch (fromType.getKind()) {
      case INT:
        if (toTypes.contains(NumberType.getInstance())) {
          coercion = BuiltinFunction.TO_NUMBER;
          coercionTargetType = NumberType.getInstance();
        } else if (toTypes.contains(FloatType.getInstance())) {
          coercion = BuiltinFunction.TO_FLOAT;
          coercionTargetType = FloatType.getInstance();
        }
        break;
      case FLOAT:
        if (toTypes.contains(NumberType.getInstance())) {
          // no conversion
        } else if (toTypes.contains(IntType.getInstance())) {
          // Theoretically we could do this but historically it wasn't a feature.
          // coercion = BuiltinFunction.TO_INT;
        }
        break;
      case NUMBER:
        if (toTypes.contains(FloatType.getInstance())) {
          // Cast without conversion
          coercionTargetType = FloatType.getInstance();
        } else if (toTypes.contains(IntType.getInstance())) {
          coercion = BuiltinFunction.TO_INT;
          coercionTargetType = IntType.getInstance();
        }
        break;
      default:
        break;
    }

    if (coercion == null) {
      return coercionTargetType;
    }

    // create a node to wrap param in coercion
    FunctionNode coercedValue =
        FunctionNode.newPositional(
            Identifier.create(coercion.getName(), node.getSourceLocation()),
            coercion,
            node.getSourceLocation());
    coercedValue.setType(coercionTargetType);

    ParentExprNode parent = node.getParent();
    parent.replaceChild(node, coercedValue);
    coercedValue.addChild(node);
    return coercionTargetType;
  }

  private RuntimeTypeCoercion() {}
}
