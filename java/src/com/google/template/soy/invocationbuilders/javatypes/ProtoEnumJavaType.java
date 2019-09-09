/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.invocationbuilders.javatypes;

import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.template.soy.internal.proto.JavaQualifiedNames;

/** Represents a proto enum for generated Soy Java invocation builders. */
public final class ProtoEnumJavaType extends JavaType {
  final EnumDescriptor enumDescriptor;

  public ProtoEnumJavaType(EnumDescriptor enumDescriptor) {
    this(enumDescriptor, /* isNullable= */ false);
  }

  public ProtoEnumJavaType(EnumDescriptor enumDescriptor, boolean isNullable) {
    super(isNullable);
    this.enumDescriptor = enumDescriptor;
  }

  @Override
  boolean isPrimitive() {
    return false;
  }

  @Override
  public String toJavaTypeString() {
    return JavaQualifiedNames.getQualifiedName(enumDescriptor);
  }

  @Override
  String asGenericsTypeArgumentString() {
    return toJavaTypeString();
  }

  @Override
  public ProtoEnumJavaType asNullable() {
    return new ProtoEnumJavaType(enumDescriptor, /* isNullable= */ true);
  }
}
