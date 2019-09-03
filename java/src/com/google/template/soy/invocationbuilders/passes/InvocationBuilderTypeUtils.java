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

import com.google.template.soy.invocationbuilders.javatypes.JavaType;
import com.google.template.soy.invocationbuilders.javatypes.ListJavaType;
import com.google.template.soy.invocationbuilders.javatypes.ProtoEnumJavaType;
import com.google.template.soy.invocationbuilders.javatypes.ProtoJavaType;
import com.google.template.soy.invocationbuilders.javatypes.SimpleJavaType;
import com.google.template.soy.types.ListType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import java.util.Optional;

/** Utils for handling types used in Soy Java invocation builders. */
final class InvocationBuilderTypeUtils {

  private InvocationBuilderTypeUtils() {}

  /**
   * Gets Java type from Soy type.
   *
   * <p>NOTE: TODO(b/140064271): Add handling for composite types. Update this method's javadoc when
   * this returns a list of java types (for handling unions).
   */
  static final Optional<JavaType> getJavaType(SoyType soyType) {
    switch (soyType.getKind()) {
      case BOOL:
        return Optional.of(SimpleJavaType.BOOLEAN);
      case INT:
        return Optional.of(SimpleJavaType.LONG);
      case FLOAT:
        return Optional.of(SimpleJavaType.DOUBLE);
      case STRING:
        return Optional.of(SimpleJavaType.STRING);
      case HTML:
        return Optional.of(SimpleJavaType.HTML);
      case JS:
        return Optional.of(SimpleJavaType.JS);
      case URI:
        return Optional.of(SimpleJavaType.URL);
      case TRUSTED_RESOURCE_URI:
        return Optional.of(SimpleJavaType.TRUSTED_RESOURCE_URL);
      case PROTO:
        SoyProtoType asProto = (SoyProtoType) soyType;
        return Optional.of(new ProtoJavaType(asProto.getDescriptor()));
      case PROTO_ENUM:
        SoyProtoEnumType asProtoEnum = (SoyProtoEnumType) soyType;
        return Optional.of(new ProtoEnumJavaType(asProtoEnum.getDescriptor()));
      case LIST:
        Optional<JavaType> listElementType = getJavaType(((ListType) soyType).getElementType());
        return listElementType.map(elementType -> new ListJavaType(elementType));
      case ANY:
      case UNKNOWN:
        return Optional.of(SimpleJavaType.OBJECT);
      case ATTRIBUTES:
      case CSS:
      case RECORD:
      case LEGACY_OBJECT_MAP:
      case MAP:
      case UNION:
      case ERROR:
      case NULL:
      case VE:
      case VE_DATA:
        return Optional.empty();
    }
    throw new AssertionError("impossible");
  }
}
