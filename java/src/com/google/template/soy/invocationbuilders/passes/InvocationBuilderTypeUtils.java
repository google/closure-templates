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
package com.google.template.soy.invocationbuilders.passes;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.internal.proto.JavaQualifiedNames;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import java.util.List;

/** Utils for handling types used in Soy Java invocation builders. */
final class InvocationBuilderTypeUtils {

  private InvocationBuilderTypeUtils() {}

  /**
   * Gets Java types from Soy type.
   *
   * <p>NOTE: TODO(b/140064271): Add handling for composite types + tests for this file.
   */
  static List<String> getJavaTypes(SoyType soyType) {
    switch (soyType.getKind()) {
      case BOOL:
        return ImmutableList.of("boolean");
      case INT:
        return ImmutableList.of("long");
      case FLOAT:
        return ImmutableList.of("double");
      case STRING:
        return ImmutableList.of("String");
      case HTML:
        return ImmutableList.of("SafeHtml");
      case JS:
        return ImmutableList.of("SafeScript");
      case URI:
        return ImmutableList.of("SafeUrl");
      case TRUSTED_RESOURCE_URI:
        return ImmutableList.of("TrustedResourceUrl");
      case PROTO:
        SoyProtoType asProto = (SoyProtoType) soyType;
        return ImmutableList.of(JavaQualifiedNames.getQualifiedName(asProto.getDescriptor()));
      case PROTO_ENUM:
        SoyProtoEnumType asProtoEnum = (SoyProtoEnumType) soyType;
        return ImmutableList.of(JavaQualifiedNames.getQualifiedName(asProtoEnum.getDescriptor()));
      case UNKNOWN:
        return ImmutableList.of("Object");
      case ATTRIBUTES:
      case CSS:
      case LIST:
      case RECORD:
      case LEGACY_OBJECT_MAP:
      case MAP:
      case UNION:
      case ANY:
      case ERROR:
      case NULL:
      case VE:
      case VE_DATA:
        return ImmutableList.of();
    }
    throw new AssertionError("impossible");
  }
}
