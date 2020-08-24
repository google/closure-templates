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

import com.google.common.collect.ImmutableTable;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.SoyType;
import java.util.Collection;
import javax.annotation.CheckReturnValue;

/** Static utility for adding runtime casts to the Soy AST. */
final class RuntimeTypeCoercion {
  private static final ImmutableTable<SoyType, SoyType, BuiltinFunction>
      AVAILABLE_CALL_SITE_COERCIONS =
          new ImmutableTable.Builder<SoyType, SoyType, BuiltinFunction>()
              .put(IntType.getInstance(), FloatType.getInstance(), BuiltinFunction.TO_FLOAT)
              .build();

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
  static SoyType maybeCoerceType(ExprNode node, Collection<SoyType> toTypes) {
    SoyType fromType = node.getType();
    if (AVAILABLE_CALL_SITE_COERCIONS.row(fromType).isEmpty()) {
      return fromType;
    }
    for (SoyType formalType : toTypes) {
      if (formalType.isAssignableFrom(fromType)) {
        return fromType; // template already accepts value, no need to coerce
      }
    }
    for (SoyType coercionTargetType : AVAILABLE_CALL_SITE_COERCIONS.row(fromType).keySet()) {
      BuiltinFunction function = null;
      for (SoyType formalType : toTypes) {
        if (!formalType.isAssignableFrom(coercionTargetType)) {
          continue;
        }
        if (function == null) {
          function = AVAILABLE_CALL_SITE_COERCIONS.get(fromType, coercionTargetType);
        } else {
          // This is actually a bad state that shouldn't happen because there should only be one
          // coercing function.
          function = null;
          break;
        }
      }
      if (function == null) {
        continue;
      }

      // create a node to wrap param in coercion
      FunctionNode coercedValue =
          FunctionNode.newPositional(
              Identifier.create(function.getName(), node.getSourceLocation()),
              function,
              node.getSourceLocation());
      coercedValue.setType(coercionTargetType);

      ParentExprNode parent = node.getParent();
      parent.replaceChild(node, coercedValue);
      coercedValue.addChild(node);
      return coercionTargetType;
    }
    return fromType;
  }

  private RuntimeTypeCoercion() {}
}
