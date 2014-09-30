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
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1.
   */
  public SoyType computeLeastCommonType(SoyType t0, SoyType t1) {
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
   * Compute the most specific type that is assignable from all types within
   * a collection.
   * @param types list of types.
   * @return A type that is assignable from all of the listed types.
   */
  public SoyType computeLeastCommonType(Collection<SoyType> types) {
    SoyType result = null;
    for (SoyType type : types) {
      result = (result == null) ? type : computeLeastCommonType(result, type);
    }
    return result;
  }


  /**
   * Compute the most specific type that is assignable from both t0 and t1, taking
   * into account arithmetic promotions - that is, converting int to float if needed.
   * @param t0 A type.
   * @param t1 Another type.
   * @return A type that is assignable from both t0 and t1.
   */
  public SoyType computeLeastCommonTypeArithmetic(SoyType t0, SoyType t1) {
    if (t0.isAssignableFrom(t1)) {
      return t0;
    } else if (t1.isAssignableFrom(t0)) {
      return t1;
    } else if (t0.getKind() == SoyType.Kind.FLOAT && t1.getKind() == SoyType.Kind.INT ||
        t1.getKind() == SoyType.Kind.FLOAT && t0.getKind() == SoyType.Kind.INT) {
      return FloatType.getInstance();
    } else {
      // TODO: At some point we should just give up and use 'any'.
      // Probably this should happen if the types have no relation with
      // each other.
      return typeRegistry.getOrCreateUnionType(t0, t1);
    }
  }
}
