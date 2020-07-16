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

package com.google.template.soy.types;

import com.google.common.base.Objects;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.soytree.SoyTypeP;

/** A {@link SoyType} implementation which describes a protocol buffer enum type. */
public final class SoyProtoEnumType extends SoyType {
  private final EnumDescriptor descriptor;

  public SoyProtoEnumType(EnumDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_ENUM;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType fromType) {
    return fromType == this
        || (fromType.getClass() == this.getClass()
            && ((SoyProtoEnumType) fromType).descriptor == descriptor);
  }

  public String getName() {
    return descriptor.getFullName();
  }

  public String getNameForBackend(SoyBackendKind backend) {
    switch (backend) {
      case JS_SRC:
        return ProtoUtils.calculateJsEnumName(descriptor);
      case TOFU:
        return ProtoUtils.getQualifiedOuterClassname(descriptor);
      case PYTHON_SRC:
      case JBC_SRC:
        throw new UnsupportedOperationException();
    }
    throw new AssertionError(backend);
  }

  public Integer getValue(String memberName) {
    EnumValueDescriptor value = descriptor.findValueByName(memberName);
    if (value != null) {
      return value.getNumber();
    }
    return null;
  }

  public EnumDescriptor getDescriptor() {
    return descriptor;
  }

  public String getDescriptorExpression() {
    return getNameForBackend(SoyBackendKind.TOFU);
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setProtoEnum(getName());
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SoyProtoEnumType that = (SoyProtoEnumType) o;
    return Objects.equal(descriptor.getFullName(), that.descriptor.getFullName());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hashCode(descriptor.getFullName());
  }
}
