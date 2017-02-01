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

package com.google.template.soy.types;

import com.google.common.base.Optional;
import com.google.template.soy.types.primitive.ErrorType;
import com.google.template.soy.types.primitive.FloatType;
import java.util.Collection;
import javax.inject.Inject;

/**
 * Common operations on types.
 *
 */
public final class SoyTypeOps {
  private final SoyTypeRegistry typeRegistry;

  @Inject
  public SoyTypeOps(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  public SoyTypeRegistry getTypeRegistry() {
    return typeRegistry;
  }

  /**
   * Compute the most specific type that is assignable from both t0 and t1.
   *
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1.
   */
  public SoyType computeLowestCommonType(SoyType t0, SoyType t1) {
    if (t0 == ErrorType.getInstance() || t1 == ErrorType.getInstance()) {
      return ErrorType.getInstance();
    }
    if (t0.isAssignableFrom(t1)) {
      return t0;
    } else if (t1.isAssignableFrom(t0)) {
      return t1;
    } else {
      // TODO: At some point we should just give up and use 'any'.
      // Probably this should happen if the types have no relation with
      // each other.
      return typeRegistry.getOrCreateUnionType(t0, t1);
    }
  }

  /**
   * Compute the most specific type that is assignable from all types within a collection.
   *
   * @param types list of types.
   * @return A type that is assignable from all of the listed types.
   */
  public SoyType computeLowestCommonType(Collection<SoyType> types) {
    SoyType result = null;
    for (SoyType type : types) {
      result = (result == null) ? type : computeLowestCommonType(result, type);
    }
    return result;
  }

  /**
   * Compute the most specific type that is assignable from both t0 and t1, taking into account
   * arithmetic promotions - that is, converting int to float if needed.
   *
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1 or absent if the types are not arithmetic
   *     meaning a subtype of 'number' or unknown.
   */
  public Optional<SoyType> computeLowestCommonTypeArithmetic(SoyType t0, SoyType t1) {
    // If either of the types is an error type, return the error type
    if (t0 == ErrorType.getInstance() || t1 == ErrorType.getInstance()) {
      return Optional.<SoyType>of(ErrorType.getInstance());
    }
    // If either of the types isn't numeric or unknown, then this isn't valid for an arithmetic
    // operation.
    if (!isNumericOrUnknown(t0) || !isNumericOrUnknown(t1)) {
      return Optional.absent();
    }
    // Note: everything is assignable to unknown and itself.  So the first two conditions take care
    // of all cases but a mix of float and int.
    if (t0.isAssignableFrom(t1)) {
      return Optional.of(t0);
    } else if (t1.isAssignableFrom(t0)) {
      return Optional.of(t1);
    } else {
      // If we get here then we know that we have a mix of float and int.  In this case arithmetic
      // ops always 'upgrade' to float.  So just return that.
      return Optional.<SoyType>of(FloatType.getInstance());
    }
  }

  public boolean isNumericOrUnknown(SoyType t0) {
    return t0.getKind() == SoyType.Kind.UNKNOWN || SoyTypes.NUMBER_TYPE.isAssignableFrom(t0);
  }
}
