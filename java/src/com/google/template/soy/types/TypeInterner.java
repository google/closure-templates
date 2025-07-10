/*
 * Copyright 2020 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.data.restricted.PrimitiveData;
import com.google.template.soy.types.SanitizedType.ElementType;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

/**
 * Factory of {@link SoyType} instances allowing use of the == operator when comparing instances.
 */
public interface TypeInterner {

  /** Intern any type so that it can be used with the == operator. */
  <T extends SoyType> T intern(T type);

  default IterableType getOrCreateIterableType(SoyType elementType) {
    return intern(IterableType.of(elementType));
  }

  /**
   * Factory function which creates a list type, given an element type. This folds list types with
   * identical element types together, so asking for the same element type twice will return a
   * pointer to the same type object.
   *
   * @param elementType The element type of the list.
   * @return The list type.
   */
  default ListType getOrCreateListType(SoyType elementType) {
    return intern(ListType.of(elementType));
  }

  default SetType getOrCreateSetType(SoyType elementType) {
    return intern(SetType.of(elementType));
  }

  /**
   * Factory function which creates a legacy object map type, given a key and value type. This folds
   * map types with identical key/value types together, so asking for the same key/value type twice
   * will return a pointer to the same type object.
   *
   * @param keyType The key type of the map.
   * @param valueType The value type of the map.
   * @return The map type.
   */
  default LegacyObjectMapType getOrCreateLegacyObjectMapType(SoyType keyType, SoyType valueType) {
    return intern(LegacyObjectMapType.of(keyType, valueType));
  }

  /**
   * Factory function which creates a map type, given a key and value type. This folds map types
   * with identical key/value types together, so asking for the same key/value type twice will
   * return a pointer to the same type object.
   *
   * @param keyType The key type of the map.
   * @param valueType The value type of the map.
   * @return The map type.
   */
  default MapType getOrCreateMapType(SoyType keyType, SoyType valueType) {
    return intern(MapType.of(keyType, valueType));
  }

  /**
   * Factory function which creates a union type, given the member types. This folds identical union
   * types together.
   *
   * @param members The members of the union.
   * @return The union type.
   */
  default SoyType getOrCreateUnionType(Collection<SoyType> members) {
    SoyType type = UnionType.of(members);
    if (type instanceof UnionType) {
      type = intern(type);
    }
    return type;
  }

  default SoyType getOrCreateIntersectionType(Collection<SoyType> members) {
    SoyType type = IntersectionType.of(members);
    if (type instanceof IntersectionType) {
      type = intern(type);
    }
    return type;
  }

  default SoyType getOrCreateNamedType(String name, String namespace) {
    // Cannot create a NamedType without its RHS type.
    throw new UnsupportedOperationException();
  }

  /** Factory function which creates a nullable type. */
  default SoyType getOrCreateNullishType(SoyType type) {
    return getOrCreateUnionType(ImmutableList.of(type, UndefinedType.getInstance()));
  }

  /**
   * Factory function which creates a union type, given the member types. This folds identical union
   * types together.
   *
   * @param members The members of the union.
   * @return The union type.
   */
  default SoyType getOrCreateUnionType(SoyType... members) {
    return getOrCreateUnionType(Arrays.asList(members));
  }

  /**
   * Factory function which creates a record type, given a list of fields. This folds identical
   * record types together.
   *
   * @param members The list of members, in parse order.
   * @return The record type.
   */
  default RecordType getOrCreateRecordType(Iterable<RecordType.Member> members) {
    return intern(RecordType.of(members));
  }

  /**
   * Factory function for template types that folds identical template types together. Takes a
   * TemplateType so callers can use the convenient builder methods or factory methods on the class
   * to construct.
   */
  default TemplateType internTemplateType(TemplateType typeToIntern) {
    return intern(typeToIntern);
  }

  /**
   * Factory function which creates and returns a {@code ve} type with the given {@code dataType}.
   * This folds identical ve types together.
   */
  default VeType getOrCreateVeType(String dataType) {
    return intern(VeType.of(dataType));
  }

  SoyProtoType getOrComputeProtoType(
      Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper);

  default SoyProtoEnumType getOrCreateProtoEnumType(EnumDescriptor descriptor) {
    return intern(SoyProtoEnumType.create(descriptor));
  }

  default SoyType getOrCreateElementType(String tagName) {
    return intern(ElementType.getInstance(tagName));
  }

  default SoyType getOrCreateLiteralType(PrimitiveData literal) {
    return intern(LiteralType.create(literal));
  }

  ImportType getProtoImportType(GenericDescriptor descriptor);

  SoyType getProtoImportType(FileDescriptor descriptor, String member);

  SoyType getProtoImportType(Descriptor descriptor, String member);
}
