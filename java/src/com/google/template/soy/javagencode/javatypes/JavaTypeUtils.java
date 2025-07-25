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

package com.google.template.soy.javagencode.javatypes;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.types.SoyTypes.INT_OR_FLOAT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.template.soy.types.AbstractIterableType;
import com.google.template.soy.types.AbstractMapType;
import com.google.template.soy.types.RecordType;
import com.google.template.soy.types.SoyProtoEnumType;
import com.google.template.soy.types.SoyProtoType;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.SoyTypes;
import com.google.template.soy.types.TemplateType;
import com.google.template.soy.types.UnionType;
import com.google.template.soy.types.UnknownType;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Utils for handling types used in Soy Java generators. */
public final class JavaTypeUtils {

  private JavaTypeUtils() {}

  public static ImmutableList<JavaType> getJavaTypes(SoyType soyType) {
    return getJavaTypes(soyType, ImmutableSet.of());
  }

  /**
   * Gets Java type from Soy type.
   *
   * <p>NOTE: TODO(b/140064271): Add handling for composite types. Update this method's javadoc when
   * this returns a list of java types (for handling unions).
   */
  public static ImmutableList<JavaType> getJavaTypes(
      SoyType soyType, Set<SoyType.Kind> skipSoyTypes) {
    boolean nonLegacyMap = true;
    ImmutableList<JavaType> types = ImmutableList.of();
    SoyType.Kind kind = soyType.getKind();
    if (skipSoyTypes.contains(kind)) {
      return types;
    }
    switch (kind) {
      case BOOL:
        types = ImmutableList.of(SimpleJavaType.BOOLEAN);
        break;
      case INT:
        types = ImmutableList.of(SimpleJavaType.INT);
        break;
      case FLOAT:
      case NUMBER:
        types = ImmutableList.of(SimpleJavaType.FLOAT);
        break;
      case STRING:
        types = ImmutableList.of(SimpleJavaType.STRING);
        break;
      case GBIGINT:
        types = ImmutableList.of(SimpleJavaType.BIGINT);
        break;
      case ELEMENT:
      case HTML:
        types = ImmutableList.of(SimpleJavaType.HTML);
        break;
      case JS:
        types = ImmutableList.of(SimpleJavaType.JS);
        break;
      case URI:
        types = ImmutableList.of(SimpleJavaType.URL);
        break;
      case TRUSTED_RESOURCE_URI:
        types = ImmutableList.of(SimpleJavaType.TRUSTED_RESOURCE_URL);
        break;
      case MESSAGE:
        types = ImmutableList.of(SimpleJavaType.MESSAGE);
        break;
      case PROTO:
        SoyProtoType asProto = (SoyProtoType) soyType;
        types = ImmutableList.of(new ProtoJavaType(asProto.getDescriptor()));
        break;
      case PROTO_ENUM:
        SoyProtoEnumType asProtoEnum = (SoyProtoEnumType) soyType;
        types = ImmutableList.of(new ProtoEnumJavaType(asProtoEnum.getDescriptor()));
        break;
      case ITERABLE:
      case SET:
      case LIST:
        SoyType elementType = ((AbstractIterableType) soyType).getElementType().getEffectiveType();
        if (elementType instanceof RecordType) {
          // Hacky handling of list<record>. Probably less code than modifying ListJavaType to
          // handle RecordJavaType element but should consider that alternative.
          types = trySimpleRecordType((RecordType) elementType, /* list= */ true, skipSoyTypes);
        } else {
          List<JavaType> listElementTypes = getJavaTypes(elementType, skipSoyTypes);
          if (listElementTypes.size() == 1 && listElementTypes.get(0).isGenericsTypeSupported()) {
            return ImmutableList.of(
                new CollectionJavaType(
                    CollectionJavaType.Subtype.forSoyType(kind), listElementTypes.get(0)));
          } // Currently, we don't handle multiple element types b/c of type erasure.
          types = ImmutableList.of();
        }
        break;
      case LEGACY_OBJECT_MAP:
        nonLegacyMap = false; // fall through
      case MAP:
        AbstractMapType soyAbstractMapType = (AbstractMapType) soyType;
        List<JavaType> keyTypes = getJavaTypes(soyAbstractMapType.getKeyType(), skipSoyTypes);
        if (keyTypes.size() != 1 || !keyTypes.get(0).isGenericsTypeSupported()) {
          break;
        }
        List<JavaType> valueTypes = getJavaTypes(soyAbstractMapType.getValueType(), skipSoyTypes);
        if (valueTypes.size() != 1 || !valueTypes.get(0).isGenericsTypeSupported()) {
          break;
        }
        types = ImmutableList.of(new MapJavaType(keyTypes.get(0), valueTypes.get(0), nonLegacyMap));
        break;
      case UNION:
        types = convertSoyUnionTypeToJavaTypes((UnionType) soyType, skipSoyTypes);
        break;
      case COMPUTED:
        return getJavaTypes(soyType.getEffectiveType(), skipSoyTypes);
      case ANY:
      case UNKNOWN:
        // The Soy type system assumes any and ? include null and does not expand param? of these
        // types to any|null. Therefore we need to make these types always nullable.
        types = ImmutableList.of(SimpleJavaType.OBJECT.asNullable());
        break;
      case ATTRIBUTES:
        types = ImmutableList.of(SimpleJavaType.ATTRIBUTES);
        break;
      case CSS:
        types = ImmutableList.of(SimpleJavaType.CSS);
        break;
      case RECORD:
        types = trySimpleRecordType((RecordType) soyType, /* list= */ false, skipSoyTypes);
        break;
      case TEMPLATE:
        types = ImmutableList.of(new TemplateJavaType((TemplateType) soyType));
        break;
      case VE:
        types = ImmutableList.of(new VeJavaType());
        break;
      case VE_DATA:
        types = ImmutableList.of(new VeDataJavaType());
        break;
      case LITERAL:
      case NAMESPACE:
      case PROTO_TYPE:
      case PROTO_ENUM_TYPE:
      case PROTO_EXTENSION:
      case TEMPLATE_TYPE:
      case NEVER:
        throw new UnsupportedOperationException();
      case FUNCTION:
      case NULL:
      case UNDEFINED:
        break;
    }

    return types;
  }

  public static ImmutableList<JavaType> makeNullable(List<JavaType> types) {
    return types.stream().map(JavaType::asNullable).collect(toImmutableList());
  }

  private static ImmutableList<JavaType> trySimpleRecordType(
      RecordType recordType, boolean list, Set<SoyType.Kind> skipSoyTypes) {
    if (recordType.getMembers().isEmpty()) {
      return ImmutableList.of();
    }

    // No records of records.
    if (SoyTypes.allConcreteTypes(recordType, null)
        .skip(/* skip root record */ 1)
        .anyMatch(t -> t.isOfKind(Kind.RECORD))) {
      return ImmutableList.of();
    }

    ImmutableMap.Builder<String, JavaType> javaTypeMap = ImmutableMap.builder();
    for (RecordType.Member member : recordType.getMembers()) {
      ImmutableList<JavaType> types = getJavaTypes(member.checkedType(), skipSoyTypes);
      if (types.size() != 1) {
        // No overloaded record setters.
        return ImmutableList.of();
      }
      javaTypeMap.put(member.name(), types.get(0));
    }
    return ImmutableList.of(new RecordJavaType(javaTypeMap.build(), list));
  }

  /**
   * Returns whether {@code type} is unsettable from Java. Params of this type should not count
   * against whether a template is fully handled by this generated API.
   */
  public static boolean isJavaIncompatible(SoyType type) {
    return SoyTypes.allConcreteTypes(type, null)
        .anyMatch(t -> t.isOfKind(Kind.VE) || t.isOfKind(Kind.VE_DATA));
  }

  public static Optional<SoyType> upcastTypesForIndirectParams(Set<SoyType> allTypes) {
    if (allTypes.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(allTypes));
    }

    // If any type is ? then just return that and create an setter that takes Object.
    if (allTypes.contains(UnknownType.getInstance())) {
      return Optional.of(UnknownType.getInstance());
    }

    // If one type is the nullable version of the other then return the nullable version.
    if (allTypes.size() == 2) {
      Iterator<SoyType> i = allTypes.iterator();
      SoyType first = i.next();
      SoyType firstNotNullish = SoyTypes.excludeNullish(first);
      SoyType second = i.next();
      SoyType secondNotNullish = SoyTypes.excludeNullish(second);
      if (firstNotNullish.isEffectivelyEqual(secondNotNullish)) {
        return Optional.of(UnionType.of(first, second));
      }
    }

    return Optional.empty();
  }

  /**
   * Converts a soy {@link UnionType} to a list of {@link JavaTypes} corresponding to the union
   * members.
   *
   * <p>Caveat #1: If we don't yet support one of the union member types, this returns an empty list
   * so we can skip over the param until it can be fully handled.
   *
   * <p>Caveat #2: If the types in the union would lead to type erasure problems (e.g. setFoo({@code
   * List<Long>} val) and setFoo({@code List<String>} val)), then we return an empty list and skip
   * over the entire union for now.
   */
  private static ImmutableList<JavaType> convertSoyUnionTypeToJavaTypes(
      UnionType unionType, Set<SoyType.Kind> skipSoyTypes) {
    if (SoyTypes.isNullish(unionType)
        && SoyTypes.excludeNullish(unionType).isEffectivelyEqual(INT_OR_FLOAT)) {
      return ImmutableList.of(SimpleJavaType.NUMBER.asNullable());
    }

    if (unionType.isEffectivelyEqual(INT_OR_FLOAT)) {
      return ImmutableList.of(SimpleJavaType.NUMBER);
    }

    // Figure out if the union contains the {@link NullType}, which tells us if the param setters
    // should be nullable.
    boolean unionAllowsNull = unionType.getMembers().stream().anyMatch(SoyTypes::isNullOrUndefined);

    // Collect a list of the Java types for each of the union member types.
    ImmutableList.Builder<JavaType> javaTypeListBuilder = new ImmutableList.Builder<>();
    for (SoyType soyUnionMemberType : unionType.getMembers()) {
      if (SoyTypes.isNullOrUndefined(soyUnionMemberType)) {
        continue;
      }
      ImmutableList<JavaType> javaTypesForUnionMember =
          getJavaTypes(soyUnionMemberType, skipSoyTypes);
      if (unionAllowsNull) {
        javaTypesForUnionMember = makeNullable(javaTypesForUnionMember);
      }

      // If we don't know how to handle one of the member types, skip over the entire union.
      if (javaTypesForUnionMember.isEmpty()) {
        return ImmutableList.of();
      }

      javaTypeListBuilder.addAll(javaTypesForUnionMember);
    }

    // If the param would cause type erasure problems, skip over it.
    return clearListIfHasTypeErasureOverloadCollisions(javaTypeListBuilder.build());
  }

  /**
   * If given list of types would lead to type erasure problems (when we generate a setFoo method
   * for each type), then return an empty list. Otherwise return the {@code types} list as-is. (e.g.
   *
   * <p>For example, {@code setFoo(List<String> strings)} and {@code setFoo(List<Number> numbers)}
   * would cause a collision.
   */
  private static ImmutableList<JavaType> clearListIfHasTypeErasureOverloadCollisions(
      ImmutableList<JavaType> types) {

    long numTopLevelListTypes =
        types.stream().filter(type -> type instanceof CollectionJavaType).count();

    long numTopLevelMapTypes = types.stream().filter(type -> type instanceof MapJavaType).count();

    if (numTopLevelListTypes > 1 || numTopLevelMapTypes > 1) {
      return ImmutableList.of();
    }
    return types;
  }
}
