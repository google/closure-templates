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

package com.google.template.soy.types.proto;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.CaseFormat;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/** A member field of a proto type. */
class NormalField extends Field {

  @Nullable private final SoyTypeRegistry typeRegistry;

  protected final FieldDescriptor fieldDescriptor;
  protected final String name;
  private final boolean shouldCheckFieldPresenceToEmulateJspbNullability;

  // Lazily construct our impl to delay type lookups.  This enables us to resolve the types of
  // recursive proto definitions.
  @GuardedBy("this")
  private volatile FieldInterpreter interpreter;

  NormalField(@Nullable SoyTypeRegistry typeRegistry, FieldDescriptor desc) {
    this.typeRegistry = typeRegistry;
    this.fieldDescriptor = desc;
    this.name = computeSoyName(desc);
    this.shouldCheckFieldPresenceToEmulateJspbNullability =
        ProtoUtils.shouldCheckFieldPresenceToEmulateJspbNullability(desc);
  }

  @Override
  public boolean shouldCheckFieldPresenceToEmulateJspbNullability() {
    return shouldCheckFieldPresenceToEmulateJspbNullability;
  }

  @Override
  public final String getName() {
    return name;
  }

  @Override
  public SoyType getType() {
    checkState(typeRegistry != null, "cannot lookup types from runtime fields... why try?");
    return impl().type(typeRegistry);
  }

  private FieldInterpreter impl() {
    @SuppressWarnings("GuardedBy") // the checker can't prove Double checked locking safe
    FieldInterpreter local = interpreter;
    // Double checked locking
    if (local == null) {
      synchronized (this) {
        local = interpreter;
        if (local == null) {
          local = FieldInterpreter.create(fieldDescriptor);
          this.interpreter = local;
        }
      }
    }
    return local;
  }

  /** Return the field descriptor. */
  @Override
  public boolean hasField(Message proto) {
    // TODO(lukes):  Currently we assume that a field is present if it is repeated, has an
    // explicit default value or is set.  However, the type of fields is not generally nullable,
    // so we can return null for a non-nullable field type.  Given the current ToFu implementation
    // this is not a problem, but modifying the field types to be nullable for non-repeated fields
    // with non explicit defaults should probably happen.
    return !shouldCheckFieldPresenceToEmulateJspbNullability || proto.hasField(fieldDescriptor);
  }

  /**
   * Returns an appropriately typed {@link SoyValueProvider} for this field in the given message.
   */
  @Override
  public SoyValueProvider interpretField(Message message) {
    return impl().soyFromProto(message.getField(fieldDescriptor));
  }

  @Override
  public void assignField(Message.Builder builder, SoyValue value) {
    builder.setField(fieldDescriptor, impl().protoFromSoy(value));
  }

  @Override
  public FieldDescriptor getDescriptor() {
    return fieldDescriptor;
  }

  private static String computeSoyName(FieldDescriptor field) {
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, field.getName())
        + fieldSuffix(field);
  }

  private static String fieldSuffix(FieldDescriptor field) {
    if (field.isRepeated()) {
      if (ProtoUtils.hasJsMapKey(field)) {
        return "Map";
      }
      return "List";
    } else {
      return "";
    }
  }
}
