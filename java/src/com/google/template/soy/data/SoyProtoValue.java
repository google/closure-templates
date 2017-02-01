/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.data;

import com.google.protobuf.Message;

/** A value object containing a proto. */
// TODO(user): This should not implement SoyRecord.
public interface SoyProtoValue extends SoyRecord {

  /** Returns the underlying message. */
  Message getProto();

  /**
   * Checks whether the underlying proto object has a field of the given name that is set. Not
   * intended for general use.
   *
   * @param name The proto field name.
   * @return Whether the underlying proto object has a field of the given name and a set value
   *     exists in that field.
   */
  boolean hasProtoField(String name);

  /**
   * Gets a set value for the field for the underlying proto object. Not intended for general use.
   *
   * @param name The proto field name.
   * @return The value of the given field for the underlying proto object, or null if either the
   *     field does not exist or the value is not set in the underlying proto.
   */
  SoyValue getProtoField(String name);

  // -----------------------------------------------------------------------------------------------
  // Explicitly deprecated SoyRecord methods. Use the *ProtoField methods instead.

  @Deprecated
  @Override
  boolean hasField(String name);

  @Deprecated
  @Override
  SoyValue getField(String name);

  /** Proto fields are always immediately resolvable. */
  @Deprecated
  @Override
  SoyValueProvider getFieldProvider(String name);
}
