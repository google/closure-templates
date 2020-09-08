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

import static com.google.common.base.Preconditions.checkState;
import static com.google.template.soy.types.SoyTypes.NUMBER_TYPE;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.types.RecordType.Member;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.ElementType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Implementations of {@link TypeRegistry} and {@link TypeInterner}. */
public final class TypeRegistries {

  private static final SoyErrorKind PROTO_FQN =
      SoyErrorKind.of(
          "Proto types should be imported rather than referenced by their fully qualified names.");

  private TypeRegistries() {}

  private static final BuiltinTypeRegistry INSTANCE = new BuiltinTypeRegistry();

  public static TypeInterner newTypeInterner() {
    return new TypeInternerImpl();
  }

  public static TypeRegistry builtinTypeRegistry() {
    return INSTANCE;
  }

  public static SoyTypeRegistry newComposite(TypeRegistry typeRegistry, TypeInterner typeInterner) {
    return new CompositeSoyTypeRegistry(typeRegistry, typeInterner);
  }

  private static final boolean PROTO_FQN_IS_ERROR =
      false;

  /**
   * Looks up a type by name, including by FQN proto name. Depending on whether FQN names are
   * allowed, deprecated, or disallowed this method may call {@code errorReporter} and may return
   * the type or null.
   */
  public static SoyType getTypeOrProtoFqn(
      SoyTypeRegistry registry, ErrorReporter errorReporter, Identifier id) {
    return getTypeOrProtoFqn(registry, errorReporter, id, id.identifier());
  }

  public static SoyType getTypeOrProtoFqn(
      SoyTypeRegistry registry, ErrorReporter errorReporter, Identifier id, String typeName) {

    SoyType nonProtoFqnType = registry.getType(typeName);
    if (nonProtoFqnType != null) {
      return nonProtoFqnType;
    }

    SoyType protoFqnType = registry.getProtoRegistry().getProtoType(typeName);
    if (protoFqnType != null) {
      if (PROTO_FQN_IS_ERROR) {
        errorReporter.report(id.location(), PROTO_FQN);
      } else {
        errorReporter.warn(id.location(), PROTO_FQN);
        return protoFqnType;
      }
    }

    return null;
  }

  private static final class TypeInternerImpl implements TypeInterner {

    private final Interner<ListType> listTypes = Interners.newStrongInterner();
    private final Interner<MapType> mapTypes = Interners.newStrongInterner();
    private final Interner<LegacyObjectMapType> legacyObjectMapTypes =
        Interners.newStrongInterner();
    private final Interner<UnionType> unionTypes = Interners.newStrongInterner();
    private final Interner<RecordType> recordTypes = Interners.newStrongInterner();
    private final Interner<TemplateType> templateTypes = Interners.newStrongInterner();
    private final Interner<VeType> veTypes = Interners.newStrongInterner();
    private final Map<String, SoyProtoType> protoTypes = new ConcurrentHashMap<>();
    private final Interner<SoyProtoEnumType> enumTypes = Interners.newStrongInterner();

    public TypeInternerImpl() {
      // Register the special number type so == comparisons work
      checkState(unionTypes.intern((UnionType) NUMBER_TYPE) == NUMBER_TYPE);
    }

    /**
     * Factory function which creates a list type, given an element type. This folds list types with
     * identical element types together, so asking for the same element type twice will return a
     * pointer to the same type object.
     *
     * @param elementType The element type of the list.
     * @return The list type.
     */
    @Override
    public ListType getOrCreateListType(SoyType elementType) {
      return listTypes.intern(ListType.of(elementType));
    }

    /**
     * Factory function which creates a legacy object map type, given a key and value type. This
     * folds map types with identical key/value types together, so asking for the same key/value
     * type twice will return a pointer to the same type object.
     *
     * @param keyType The key type of the map.
     * @param valueType The value type of the map.
     * @return The map type.
     */
    @Override
    public LegacyObjectMapType getOrCreateLegacyObjectMapType(SoyType keyType, SoyType valueType) {
      return legacyObjectMapTypes.intern(LegacyObjectMapType.of(keyType, valueType));
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
    @Override
    public MapType getOrCreateMapType(SoyType keyType, SoyType valueType) {
      return mapTypes.intern(MapType.of(keyType, valueType));
    }

    /**
     * Factory function which creates a union type, given the member types. This folds identical
     * union types together.
     *
     * @param members The members of the union.
     * @return The union type.
     */
    @Override
    public SoyType getOrCreateUnionType(Collection<SoyType> members) {
      SoyType type = UnionType.of(members);
      if (type.getKind() == SoyType.Kind.UNION) {
        type = unionTypes.intern((UnionType) type);
      }
      return type;
    }

    /**
     * Factory function which creates a union type, given the member types. This folds identical
     * union types together.
     *
     * @param members The members of the union.
     * @return The union type.
     */
    @Override
    public SoyType getOrCreateUnionType(SoyType... members) {
      return getOrCreateUnionType(Arrays.asList(members));
    }

    /**
     * Factory function which creates a record type, given a list of fields. This folds identical
     * record types together.
     *
     * @param members The list of members, in parse order.
     * @return The record type.
     */
    @Override
    public RecordType getOrCreateRecordType(Iterable<RecordType.Member> members) {
      return recordTypes.intern(RecordType.of(members));
    }

    /**
     * Factory function for template types that folds identical template types together. Takes a
     * TemplateType so callers can use the convenient builder methods or factory methods on the
     * class to construct.
     */
    @Override
    public TemplateType internTemplateType(TemplateType typeToIntern) {
      return templateTypes.intern(typeToIntern);
    }

    /**
     * Factory function which creates and returns a {@code ve} type with the given {@code dataType}.
     * This folds identical ve types together.
     */
    @Override
    public VeType getOrCreateVeType(String dataType) {
      return veTypes.intern(VeType.of(dataType));
    }

    @Override
    public SoyProtoType getOrComputeProtoType(
        Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper) {
      return protoTypes.computeIfAbsent(descriptor.getFullName(), mapper);
    }

    @Override
    public SoyProtoEnumType getOrCreateProtoEnumType(EnumDescriptor descriptor) {
      return enumTypes.intern(new SoyProtoEnumType(descriptor));
    }
  }

  private static final class BuiltinTypeRegistry implements TypeRegistry {

    private static final ImmutableMap<String, SoyType> BUILTIN_TYPES =
        ImmutableSortedMap.<String, SoyType>naturalOrder()
            .put("?", UnknownType.getInstance())
            .put("any", AnyType.getInstance())
            .put("null", NullType.getInstance())
            .put("bool", BoolType.getInstance())
            .put("int", IntType.getInstance())
            .put("float", FloatType.getInstance())
            .put("string", StringType.getInstance())
            .put("number", NUMBER_TYPE)
            .put("html", HtmlType.getInstance())
            .put("attributes", AttributesType.getInstance())
            .put("element", ElementType.getInstance())
            .put("css", StyleType.getInstance())
            .put("uri", UriType.getInstance())
            .put("trusted_resource_uri", TrustedResourceUriType.getInstance())
            .put("js", JsType.getInstance())
            .put("ve_data", VeDataType.getInstance())
            .put("Message", MessageType.getInstance())
            .build();

    private BuiltinTypeRegistry() {}

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      return BUILTIN_TYPES.get(typeName);
    }

    @Override
    public String findTypeWithMatchingNamespace(String prefix) {
      return null;
    }

    @Override
    public ImmutableSet<String> getAllSortedTypeNames() {
      return BUILTIN_TYPES.keySet();
    }
  }

  private static final class CompositeSoyTypeRegistry implements SoyTypeRegistry {

    private final TypeRegistry typeRegistry;
    private final TypeInterner typeInterner;

    public CompositeSoyTypeRegistry(TypeRegistry typeRegistry, TypeInterner typeInterner) {
      this.typeRegistry = typeRegistry;
      this.typeInterner = typeInterner;
    }

    @Override
    @Nullable
    public SoyType getType(String typeName) {
      return typeRegistry.getType(typeName);
    }

    @Override
    public String findTypeWithMatchingNamespace(String prefix) {
      return typeRegistry.findTypeWithMatchingNamespace(prefix);
    }

    @Override
    public Iterable<String> getAllSortedTypeNames() {
      return typeRegistry.getAllSortedTypeNames();
    }

    @Override
    public ListType getOrCreateListType(SoyType elementType) {
      return typeInterner.getOrCreateListType(elementType);
    }

    @Override
    public LegacyObjectMapType getOrCreateLegacyObjectMapType(SoyType keyType, SoyType valueType) {
      return typeInterner.getOrCreateLegacyObjectMapType(keyType, valueType);
    }

    @Override
    public MapType getOrCreateMapType(SoyType keyType, SoyType valueType) {
      return typeInterner.getOrCreateMapType(keyType, valueType);
    }

    @Override
    public SoyType getOrCreateUnionType(Collection<SoyType> members) {
      return typeInterner.getOrCreateUnionType(members);
    }

    @Override
    public SoyType getOrCreateUnionType(SoyType... members) {
      return typeInterner.getOrCreateUnionType(members);
    }

    @Override
    public RecordType getOrCreateRecordType(Iterable<Member> members) {
      return typeInterner.getOrCreateRecordType(members);
    }

    @Override
    public TemplateType internTemplateType(TemplateType typeToIntern) {
      return typeInterner.internTemplateType(typeToIntern);
    }

    @Override
    public VeType getOrCreateVeType(String dataType) {
      return typeInterner.getOrCreateVeType(dataType);
    }

    @Override
    public SoyProtoType getOrComputeProtoType(
        Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper) {
      return typeInterner.getOrComputeProtoType(descriptor, mapper);
    }

    @Override
    public SoyProtoEnumType getOrCreateProtoEnumType(EnumDescriptor descriptor) {
      return typeInterner.getOrCreateProtoEnumType(descriptor);
    }
  }
}
