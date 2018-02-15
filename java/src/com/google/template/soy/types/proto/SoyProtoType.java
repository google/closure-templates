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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.base.SoyBackendKind;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A {@link SoyType} subclass which describes a protocol buffer type.
 *
 */
public final class SoyProtoType implements SoyType {


  private final Descriptor typeDescriptor;
  private final ImmutableMap<String, Field> fields;

  SoyProtoType(
      SoyTypeRegistry typeRegistry, Descriptor descriptor, Set<FieldDescriptor> extensions) {
    this.typeDescriptor = descriptor;
    this.fields = Field.getFieldsForType(typeRegistry, descriptor, extensions);
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO;
  }

  @Override
  public boolean isAssignableFrom(SoyType fromType) {
    return fromType == this;
  }

  public Descriptor getDescriptor() {
    return typeDescriptor;
  }

  /**
   * For ParseInfo generation, return a string that represents the Java source expression for the
   * static descriptor constant.
   *
   * @return The Java source expression for this type's descriptor.
   */
  public String getDescriptorExpression() {
    // We only need to import the outermost descriptor.
    Descriptor descriptor = typeDescriptor;
    while (descriptor.getContainingType() != null) {
      descriptor = descriptor.getContainingType();
    }
    return JavaQualifiedNames.getQualifiedName(descriptor) + ".getDescriptor()";
  }

  /** Returns the {@link FieldDescriptor} of the given field. */
  public FieldDescriptor getFieldDescriptor(String fieldName) {
    return fields.get(fieldName).getDescriptor();
  }

  /** Returns the {@link SoyType} of the given field, or null if the field does not exist. */
  @Nullable
  public SoyType getFieldType(String fieldName) {
    Field field = fields.get(fieldName);
    return field != null ? field.getType() : null;
  }

  /** Returns all the field names of this proto. */
  public ImmutableSet<String> getFieldNames() {
    return fields.keySet();
  }

  /** Returns this proto's type name for the given backend. */
  public String getNameForBackend(SoyBackendKind backend) {
    switch (backend) {
      case JS_SRC:
        // The 'proto' prefix is JSPB-specific. If we ever support some other
        // JavaScript proto implementation, we'll need some way to determine which
        // proto implementation the user wants to use at this point.
        return ProtoUtils.calculateQualifiedJsName(typeDescriptor);
      case TOFU:
      case JBC_SRC:
        return JavaQualifiedNames.getClassName(typeDescriptor);
      default:
        throw new UnsupportedOperationException();
    }
  }

  @Override
  public String toString() {
    return typeDescriptor.getFullName();
  }

  /**
   * Whether or not server side emuluation of jspb semantics needs to check for field presence and
   * return null for absent fields.
   *
   * <p>This isn't necessary in the JS backends because we can rely on the proto->JS compiler to
   * create these semantics.
   */
  public boolean shouldCheckFieldPresenceToEmulateJspbNullability(String fieldName) {
    return fields.get(fieldName).shouldCheckFieldPresenceToEmulateJspbNullability();
  }
}
