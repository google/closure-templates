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

import com.google.template.soy.soytree.SoyTypeP;

/**
 * The "unknown" type is used to indicate that the type was unspecified or could not be inferred.
 * Variables with unknown type are considered to be dynamically-typed, and all operations are
 * allowed (but may fail at runtime).
 */
public final class UnknownType extends PrimitiveType {

  private static final UnknownType INSTANCE = new UnknownType();

  // Not constructible - use getInstance().
  private UnknownType() {}

  @Override
  public Kind getKind() {
    return Kind.UNKNOWN;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType) {
    // Allow assigning from all types except the map and ve types.
    // For maps, bracket access on "?"-typed values generates JS bracket access, which works
    // whether the actual value is a an array or an object. But this doesn't work for ES6 Maps
    // or jspb.Maps. Flag this at compile time so people upgrading from legacy_object_map
    // aren't surprised at runtime.
    // For ve and ve_data, usage is limited to prevent abuse of VEs. The unknown type can't be used
    // as these types, so disallow converting them to unknown as there's no reason to do this.
    return srcType.getKind() != Kind.MAP
        && srcType.getKind() != Kind.VE
        && srcType.getKind() != Kind.VE_DATA;
  }

  @Override
  public String toString() {
    return "?";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setPrimitive(SoyTypeP.PrimitiveTypeP.UNKNOWN);
  }
  /** Return the single instance of this type. */
  public static UnknownType getInstance() {
    return INSTANCE;
  }
}
