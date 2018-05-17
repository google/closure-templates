/*
 * Copyright 2018 Google Inc.
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
package com.google.template.soy.data.internal;

import com.google.template.soy.data.SoyDict;
import com.google.template.soy.data.SoyMap;

/**
 * Tracks the runtime type of Java objects that could represent Soy {@code legacy_object_map}s, Soy
 * {@code map}s, or Soy records. See the {@link DictImpl} javadoc for discussion of why this is
 * necessary.
 *
 * <p>This class should only be used as a collaborator by {@link DictImpl} and {@link
 * com.google.template.soy.data.SoyMapData}.
 *
 * <p>TODO(b/69051863): this should go away when legacy_object_map does.
 */
public final class RuntimeMapTypeTracker {

  /**
   * The runtime type. It can only be set once. Changing the state more than once will throw an
   * exception.
   */
  public enum Type {
    /**
     * This object could represent a Soy {@code legacy_object_map}, a Soy {@code map}, or a Soy
     * record ({@code [field1: type1, ...]}). The first {@link SoyDict} or {@link
     * com.google.template.soy.data.SoyRecord} method invoked on this object will transition the
     * state to {@link #LEGACY_OBJECT_MAP_OR_RECORD}, while the first {@link SoyMap} method invoked
     * on this object will transition the state to {@link #MAP}.
     */
    UNKNOWN,
    /** This object represents a Soy {@code legacy_object_map} or record at runtime. */
    // TODO(b/76108656): Split legacy_object_map and record type so that an object can only be used
    // at runtime as a legacy_object_map or record, but not both.
    LEGACY_OBJECT_MAP_OR_RECORD,
    /** This object represents a Soy {@code map} at runtime. */
    MAP,
  }

  private Type type;

  public RuntimeMapTypeTracker(Type initialType) {
    this.type = initialType;
  }

  public Type type() {
    return type;
  }

  /**
   * Sets the internal state to {@link Type#MAP}. If the state has already been set, throws an
   * exception.
   */
  public void maybeSetMapType() {
    if (type == Type.UNKNOWN) {
      type = Type.MAP;
    } else if (type == Type.LEGACY_OBJECT_MAP_OR_RECORD) {
      throw new IllegalStateException(
          "Expected a value of type `map`, got `legacy_object_map`. "
              + "These two map types are not interoperable. "
              + "Use `legacyObjectMapToMap()` to convert this object to a map.");
    }
  }

  /**
   * Sets the internal state to {@link Type#LEGACY_OBJECT_MAP_OR_RECORD}. If the state has already
   * been set, throws an exception.
   */
  public void maybeSetLegacyObjectMapOrRecordType() {
    if (type == Type.UNKNOWN) {
      type = Type.LEGACY_OBJECT_MAP_OR_RECORD;
    } else if (type == Type.MAP) {
      throw new IllegalStateException(
          "Expected a value of type `legacy_object_map`, got `map`. "
              + "These two map types are not interoperable. "
              + "Use `mapToLegacyObjectMap()` to convert this object to a legacy_object_map.");
    }
  }
}
