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

package com.google.template.soy.types.primitive;

import com.google.template.soy.types.SoyType;

/**
 * The "any" type is the supertype of all other types. The only operations allowed on this type are
 * explicit coercions to other types (i.e. downcasting), or operations that implicitly coerce to
 * string or boolean type (e.g. printing).
 *
 */
public final class AnyType extends PrimitiveType {

  private static final AnyType INSTANCE = new AnyType();

  // Not constructible - use getInstance().
  private AnyType() {}

  @Override
  public Kind getKind() {
    return Kind.ANY;
  }

  @Override
  public boolean isAssignableFrom(SoyType srcType) {
    return true;
  }

  @Override
  public String toString() {
    return "any";
  }

  /** Return the single instance of this type. */
  public static AnyType getInstance() {
    return INSTANCE;
  }
}
