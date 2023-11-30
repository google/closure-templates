/*
 * Copyright 2023 Google Inc.
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

import com.google.protobuf.Descriptors.FieldDescriptor;
import javax.annotation.Nullable;

/** Utilities related to {@link SoyProtoType}. */
public final class SoyProtoTypes {
  private SoyProtoTypes() {}

  public static FieldDescriptor.Type protoFieldType(SoyType protoType, String fieldName) {
    FieldDescriptor.Type protoFieldType = protoFieldTypeHelper(protoType, fieldName);
    if (protoFieldType == null) {
      throw new IllegalArgumentException(fieldName);
    }
    return protoFieldType;
  }

  @Nullable
  private static FieldDescriptor.Type protoFieldTypeHelper(SoyType protoType, String fieldName) {
    FieldDescriptor descriptor = protoFieldDescriptorHelper(protoType, fieldName);
    if (descriptor != null) {
      return descriptor.getType();
    }
    return null;
  }

  @Nullable
  public static FieldDescriptor protoFieldDescriptorHelper(SoyType protoType, String fieldName) {
    if (protoType instanceof SoyProtoType) {
      return ((SoyProtoType) protoType).getFieldDescriptor(fieldName);
    } else if (protoType instanceof UnionType) {
      // Just return the type of the field of the first proto type encountered.
      for (SoyType memberType : ((UnionType) protoType).getMembers()) {
        FieldDescriptor descriptor = protoFieldDescriptorHelper(memberType, fieldName);
        if (descriptor != null) {
          return descriptor;
        }
      }
    }
    return null;
  }
}
