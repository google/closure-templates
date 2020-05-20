/*
 * Copyright 2013 Google Inc.
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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.template.soy.types.SoyTypes.NUMBER_TYPE;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyTypeRegistryBuilder.DescriptorVisitor;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Registry of types which can be looked up by name.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoyTypeRegistry {

  private static final ImmutableMap<String, SoyType> BUILTIN_TYPES =
      ImmutableMap.<String, SoyType>builder()
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
          .put("css", StyleType.getInstance())
          .put("uri", UriType.getInstance())
          .put("trusted_resource_uri", TrustedResourceUriType.getInstance())
          .put("js", JsType.getInstance())
          .put("ve_data", VeDataType.getInstance())
          .build();

  /** A type registry that defaults all unknown types to the 'unknown' type. */
  public static final SoyTypeRegistry DEFAULT_UNKNOWN =
      new SoyTypeRegistry() {
        @Override
        @Nullable
        public SoyType getType(String typeName) {
          SoyType type = super.getType(typeName);
          if (type == null) {
            return UnknownType.getInstance();
          }
          return type;
        }
      };

  private final Object lock = new Object();

  private final Interner<ListType> listTypes = Interners.newStrongInterner();
  private final Interner<MapType> mapTypes = Interners.newStrongInterner();
  private final Interner<LegacyObjectMapType> legacyObjectMapTypes = Interners.newStrongInterner();
  private final Interner<UnionType> unionTypes = Interners.newStrongInterner();
  private final Interner<RecordType> recordTypes = Interners.newStrongInterner();
  private final Interner<TemplateType> templateTypes = Interners.newStrongInterner();
  private final Interner<VeType> veTypes = Interners.newStrongInterner();

  @GuardedBy("lock")
  private ImmutableList<String> lazyAllSortedTypeNames;

  /**
   * Map of SoyTypes that have been created from the type descriptors. Gets filled in lazily as
   * types are requested.
   */
  @GuardedBy("lock")
  private final Map<String, SoyType> protoTypeCache;

  /** Map of all the protobuf type descriptors that we've discovered. */
  private final ImmutableMap<String, GenericDescriptor> descriptors;

  /* Multimap of all known extensions of a given proto */
  private final ImmutableSetMultimap<String, FieldDescriptor> extensions;

  SoyTypeRegistry(SoyTypeRegistryBuilder builder) {
    DescriptorVisitor visitor = new DescriptorVisitor();
    try {
      builder.accept(visitor);
    } catch (DescriptorValidationException e) {
      throw new RuntimeException("Malformed descriptor set", e);
    }
    this.descriptors = ImmutableMap.copyOf(visitor.descriptors);
    this.extensions = ImmutableSetMultimap.copyOf(visitor.extensions);
    this.protoTypeCache = new HashMap<>();
    // Register the special number type so == comparisons work
    checkState(unionTypes.intern((UnionType) NUMBER_TYPE) == NUMBER_TYPE);
  }

  private SoyTypeRegistry() {
    this(new SoyTypeRegistryBuilder());
  }

  /**
   * Look up a type by name. Returns null if there is no such type.
   *
   * @param typeName The fully-qualified name of the type.
   * @return The type object, or {@code null}.
   */
  @Nullable
  public SoyType getType(String typeName) {
    SoyType result = BUILTIN_TYPES.get(typeName);
    if (result != null) {
      return result;
    }
    synchronized (lock) {
      result = protoTypeCache.get(typeName);
      if (result == null) {
        GenericDescriptor descriptor = descriptors.get(typeName);
        if (descriptor == null) {
          return null;
        }
        if (descriptor instanceof EnumDescriptor) {
          result = new SoyProtoEnumType((EnumDescriptor) descriptor);
        } else {
          result = new SoyProtoType(this, (Descriptor) descriptor, extensions.get(typeName));
        }
        protoTypeCache.put(typeName, result);
      }
    }
    return result;
  }

  /** Finds a type whose top-level namespace is a specified prefix, or null if there are none. */
  public String findTypeWithMatchingNamespace(String prefix) {
    prefix = prefix + ".";
    // This must be sorted so that errors are deterministic, or we'll break integration tests.
    for (String name : getAllSortedTypeNames()) {
      if (name.startsWith(prefix)) {
        return name;
      }
    }
    return null;
  }

  /** Gets all known types, sorted alphabetically. */
  public Iterable<String> getAllSortedTypeNames() {
    synchronized (lock) {
      if (lazyAllSortedTypeNames == null) {
        lazyAllSortedTypeNames =
            Stream.concat(BUILTIN_TYPES.keySet().stream(), descriptors.keySet().stream())
                .sorted()
                .collect(toImmutableList());
      }
      return lazyAllSortedTypeNames;
    }
  }

  /**
   * Factory function which creates a list type, given an element type. This folds list types with
   * identical element types together, so asking for the same element type twice will return a
   * pointer to the same type object.
   *
   * @param elementType The element type of the list.
   * @return The list type.
   */
  public ListType getOrCreateListType(SoyType elementType) {
    return listTypes.intern(ListType.of(elementType));
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
  public MapType getOrCreateMapType(SoyType keyType, SoyType valueType) {
    return mapTypes.intern(MapType.of(keyType, valueType));
  }

  /**
   * Factory function which creates a union type, given the member types. This folds identical union
   * types together.
   *
   * @param members The members of the union.
   * @return The union type.
   */
  public SoyType getOrCreateUnionType(Collection<SoyType> members) {
    SoyType type = UnionType.of(members);
    if (type.getKind() == SoyType.Kind.UNION) {
      type = unionTypes.intern((UnionType) type);
    }
    return type;
  }

  /**
   * Factory function which creates a union type, given the member types. This folds identical union
   * types together.
   *
   * @param members The members of the union.
   * @return The union type.
   */
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
  public RecordType getOrCreateRecordType(Iterable<RecordType.Member> members) {
    return recordTypes.intern(RecordType.of(members));
  }

  /**
   * Factory function which creates a template type, given a list of arguments and a return type.
   * This folds identical template types together.
   */
  public TemplateType getOrCreateTemplateType(
      Iterable<TemplateType.Argument> arguments, SoyType returnType) {
    return templateTypes.intern(TemplateType.of(arguments, returnType));
  }

  /**
   * Factory function which creates and returns a {@code ve} type with the given {@code dataType}.
   * This folds identical ve types together.
   */
  public VeType getOrCreateVeType(String dataType) {
    return veTypes.intern(VeType.of(dataType));
  }
}
