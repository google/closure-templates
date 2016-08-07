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

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.types.SoyEnumType;
import com.google.template.soy.types.SoyType;

/**
 * A {@link SoyType} implementation which describes a protocol buffer enum type.
 *
 * <p>TODO(lukes): merge with SoyEnumType interface, we aren't going to support any other type of
 * enum (or if we do, it will likely need a new SoyType.Kind + implementation)
 */
public final class SoyProtoEnumTypeImpl implements SoyEnumType, SoyProtoType {
  private final EnumDescriptor descriptor;

  public SoyProtoEnumTypeImpl(EnumDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  @Override public Kind getKind() {
    return Kind.ENUM;
  }

  @Override public boolean isAssignableFrom(SoyType fromType) {
    return fromType == this
        || (fromType.getClass() == this.getClass()
            && ((SoyProtoEnumTypeImpl) fromType).descriptor == descriptor);
  }

  @Override public boolean isInstance(SoyValue value) {
    // For now, allow integer values.
    // TODO(user): May want to tighten this up if we ever have an enum value type.
    return value instanceof IntegerData;
  }

  @Override public String getName() {
    return descriptor.getFullName();
  }

  @Override public String getNameForBackend(SoyBackendKind backend) {
    switch (backend) {
      case JS_SRC:
        return Protos.calculateJsEnumName(descriptor);
      case TOFU: {
        return JavaQualifiedNames.getQualifiedName(descriptor) + ".getDescriptor()";
      }

      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override public Integer getValue(String memberName) {
    EnumValueDescriptor value = descriptor.findValueByName(memberName);
    if (value != null) {
      return value.getNumber();
    }
    return null;
  }

  @Override public String getDescriptorExpression() {
    return getNameForBackend(SoyBackendKind.TOFU);
  }

  @Override public String toString() {
    return getName();
  }
}
