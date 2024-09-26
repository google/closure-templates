/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.base.internal;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** A representation of a Java type, possibly parameterized. */
@AutoValue
public abstract class TypeReference {

  /** The base type. */
  public abstract String className();

  /** The type parameters. */
  public abstract ImmutableList<TypeReference> parameters();

  public static TypeReference create(String type) {
    return create(type, ImmutableList.of());
  }

  public static TypeReference create(String type, List<TypeReference> parameters) {
    return new AutoValue_TypeReference(type, ImmutableList.copyOf(parameters));
  }

  public boolean isGeneric() {
    return !parameters().isEmpty();
  }

  /** Returns true if `other` is the same as or a raw-types version of this. */
  public boolean isAssignableFrom(TypeReference other) {
    if (!className().equals(other.className())) {
      return false;
    }
    if (!other.isGeneric()) {
      return true;
    }
    if (parameters().size() != other.parameters().size()) {
      return false;
    }
    for (int i = 0; i < parameters().size(); i++) {
      if (!parameters().get(i).isAssignableFrom(other.parameters().get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public final String toString() {
    if (parameters().isEmpty()) {
      return className();
    }
    return className() + "<" + Joiner.on(", ").join(parameters()) + ">";
  }
}
