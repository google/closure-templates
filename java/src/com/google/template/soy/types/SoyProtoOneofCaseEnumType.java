/*
 * Copyright 2026 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.CaseFormat;
import com.google.common.base.Objects;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.soytree.SoyTypeP;

/** A {@link SoyType} implementation which describes a synthesized proto oneof case enum type. */
public final class SoyProtoOneofCaseEnumType extends SoyType {

  public static SoyProtoOneofCaseEnumType create(OneofDescriptor descriptor) {
    return new SoyProtoOneofCaseEnumType(checkNotNull(descriptor));
  }

  private final OneofDescriptor descriptor;

  private SoyProtoOneofCaseEnumType(OneofDescriptor descriptor) {
    this.descriptor = descriptor;
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_ENUM; // We treat it as a proto enum in the type system
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType fromType) {
    return fromType == this
        || (fromType.getClass() == this.getClass()
            && ((SoyProtoOneofCaseEnumType) fromType).descriptor == descriptor);
  }

  public String getName() {
    return descriptor.getContainingType().getFullName()
        + "."
        + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, descriptor.getName())
        + "Case";
  }

  public String getNameForBackend(SoyBackendKind backend) {
    return switch (backend) {
      case JS_SRC ->
          ProtoUtils.calculateUnprefixedJsName(descriptor.getContainingType())
              + "."
              + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, descriptor.getName())
              + "Case";
      case TOFU -> ProtoUtils.getQualifiedOuterClassname(descriptor);
      case PYTHON_SRC, JBC_SRC -> throw new UnsupportedOperationException();
    };
  }

  public Integer getValue(String memberName) {
    String upperOneofName =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, descriptor.getName());
    if (memberName.equals(upperOneofName + "_NOT_SET")) {
      return 0;
    }
    for (FieldDescriptor field : descriptor.getFields()) {
      String upperFieldName =
          CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, field.getName());
      if (memberName.equals(upperFieldName)) {
        return field.getNumber();
      }
    }
    return null;
  }

  public String getNameForValue(int value) {
    String upperOneofName =
        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, descriptor.getName());
    if (value == 0) {
      return upperOneofName + "_NOT_SET";
    }
    for (FieldDescriptor field : descriptor.getFields()) {
      if (field.getNumber() == value) {
        return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, field.getName());
      }
    }
    return null;
  }

  public OneofDescriptor getDescriptor() {
    return descriptor;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  protected void doToProto(SoyTypeP.Builder builder) {
    builder.setProtoEnum(getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SoyProtoOneofCaseEnumType that = (SoyProtoOneofCaseEnumType) o;
    return Objects.equal(descriptor.getFullName(), that.descriptor.getFullName());
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hashCode(descriptor.getFullName());
  }
}
