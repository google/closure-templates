/*
 * Copyright 2023 Google Inc.
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
import com.google.common.collect.Sets;
import com.google.template.soy.exprtree.ExprEquivalence;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.types.FloatType;
import com.google.template.soy.types.IntType;
import com.google.template.soy.types.NumberType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypes;
import java.util.Map;
import javax.annotation.Nullable;

/** Class that is used to temporarily substitute the type of a variable. */
final class TypeSubstitutions {

  static class Checkpoint {
    private final Vector vector;

    public Checkpoint(Vector vector) {
      this.vector = vector;
    }
  }

  private final ExprEquivalence exprEquivalence;
  private Vector substitutions;

  public TypeSubstitutions(ExprEquivalence exprEquivalence) {
    this.exprEquivalence = exprEquivalence;
  }

  // Given a map of type substitutions, add all the entries to the current set of
  // active substitutions.
  void addAll(Map<ExprEquivalence.Wrapper, SoyType> substitutionsToAdd) {
    for (Map.Entry<ExprEquivalence.Wrapper, SoyType> entry : substitutionsToAdd.entrySet()) {
      ExprNode expr = entry.getKey().get();
      // Get the existing type
      SoyType previousType = expr.getType();
      for (Vector subst = substitutions; subst != null; subst = subst.parent) {
        if (exprEquivalence.equivalent(subst.expression, expr)) {
          previousType = subst.type;
          break;
        }
      }

      // If the new type is different than the current type, then add a new type substitution.
      if (!equalsWithNoNumberDistinction(previousType, entry.getValue())) {
        substitutions = new Vector(substitutions, expr, entry.getValue());
      }
    }
  }

  private static final ImmutableSet<SoyType> NUMBER_TYPES =
      ImmutableSet.of(IntType.getInstance(), FloatType.getInstance(), NumberType.getInstance());

  static boolean equalsWithNoNumberDistinction(SoyType origType, SoyType newType) {
    // While migrating from int/float to number (b/395679605) we need to not narrow from number to
    // int or float. Specifically this interacts poorly with the PluginValidator, which might then
    // expect an IntegerData when it receives a FloatData.
    if (newType.isEffectivelyEqual(origType)) {
      return true;
    }
    ImmutableSet<SoyType> members1 = SoyTypes.flattenUnionToSet(origType);
    ImmutableSet<SoyType> members2 = SoyTypes.flattenUnionToSet(newType);
    boolean numberRemoved =
        members1.contains(NumberType.getInstance()) && !members2.contains(NumberType.getInstance());
    boolean intAdded =
        !members1.contains(IntType.getInstance()) && members2.contains(IntType.getInstance());
    boolean floatAdded =
        !members1.contains(FloatType.getInstance()) && members2.contains(FloatType.getInstance());

    if (numberRemoved && (intAdded || floatAdded)) {
      var set1 = Sets.difference(members1, NUMBER_TYPES);
      var set2 = Sets.difference(members2, NUMBER_TYPES);
      return set1.size() == set2.size() && set2.containsAll(set1);
    }

    return false;
  }

  @Nullable
  SoyType getTypeSubstitution(ExprNode expr) {
    // If there's a type substitution in effect for this expression, then change
    // the type of the variable reference to the substituted type.
    for (Vector subst = substitutions; subst != null; subst = subst.parent) {
      if (exprEquivalence.equivalent(subst.expression, expr)) {
        return subst.type;
      }
    }
    return null;
  }

  Checkpoint checkpoint() {
    return new Checkpoint(substitutions);
  }

  void restore(Checkpoint checkpoint) {
    this.substitutions = checkpoint.vector;
  }

  /**
   * Type substitution preferences are implemented via a custom stack in order for new substitutions
   * to override old ones. This means that lookups for type substitutions are linear in the number
   * of active substitutions. This should be fine because the stack depth is unlikely to be >10. If
   * we end up observing large stacks (100s of active substitutions), then we should rewrite to a
   * hashed data structure to make it faster to do negative lookups.
   */
  private static final class Vector {
    @Nullable final Vector parent;

    /** The expression whose type we are overriding. */
    final ExprNode expression;

    /** The new type of the variable. */
    final SoyType type;

    Vector(@Nullable Vector parent, ExprNode expression, SoyType type) {
      this.parent = parent;
      this.expression = expression;
      this.type = type;
    }
  }
}
