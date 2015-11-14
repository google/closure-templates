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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.template.soy.types.aggregate.ListType;
import com.google.template.soy.types.aggregate.MapType;
import com.google.template.soy.types.aggregate.RecordType;
import com.google.template.soy.types.aggregate.UnionType;
import com.google.template.soy.types.primitive.AnyType;
import com.google.template.soy.types.primitive.BoolType;
import com.google.template.soy.types.primitive.FloatType;
import com.google.template.soy.types.primitive.IntType;
import com.google.template.soy.types.primitive.NullType;
import com.google.template.soy.types.primitive.SanitizedType.AttributesType;
import com.google.template.soy.types.primitive.SanitizedType.CssType;
import com.google.template.soy.types.primitive.SanitizedType.HtmlType;
import com.google.template.soy.types.primitive.SanitizedType.JsType;
import com.google.template.soy.types.primitive.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.primitive.SanitizedType.UriType;
import com.google.template.soy.types.primitive.StringType;
import com.google.template.soy.types.primitive.UnknownType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Registry of types which can be looked up by name.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
@Singleton
public final class SoyTypeRegistry {

  /** A type registry that defaults all unknown types to the 'unknown' type. */
  public static final SoyTypeRegistry DEFAULT_UNKNOWN =
      new SoyTypeRegistry(
          ImmutableSet.<SoyTypeProvider>of(
              new SoyTypeProvider() {
                @Override
                public SoyType getType(String typeName, SoyTypeRegistry typeRegistry) {
                  return UnknownType.getInstance();
                }
              }));

  // TODO(shwetakarwa): Rename consistently to use "URL".
  private static final Map<String, SoyType> BUILTIN_TYPES =
      ImmutableMap.<String, SoyType>builder()
          .put("any", AnyType.getInstance())
          .put("null", NullType.getInstance())
          .put("bool", BoolType.getInstance())
          .put("int", IntType.getInstance())
          .put("float", FloatType.getInstance())
          .put("string", StringType.getInstance())
          .put("number", SoyTypes.NUMBER_TYPE)
          .put("html", HtmlType.getInstance())
          .put("attributes", AttributesType.getInstance())
          .put("css", CssType.getInstance())
          .put("uri", UriType.getInstance())
          .put("trusted_resource_url", TrustedResourceUriType.getInstance())
          .put("js", JsType.getInstance())
          .build();


  private final ImmutableSet<SoyTypeProvider> typeProviders;
  private final Interner<ListType> listTypes = Interners.newStrongInterner();
  private final Interner<MapType> mapTypes = Interners.newStrongInterner();
  private final Interner<UnionType> unionTypes = Interners.newStrongInterner();
  private final Interner<RecordType> recordTypes = Interners.newStrongInterner();


  @Inject
  public SoyTypeRegistry(Set<SoyTypeProvider> typeProviders) {
    this.typeProviders = ImmutableSet.copyOf(typeProviders);
  }


  @VisibleForTesting
  public SoyTypeRegistry() {
    this.typeProviders = ImmutableSet.of();
  }


  /**
   * Look up a type by name. Returns null if there is no such type.
   * @param typeName The fully-qualified name of the type.
   * @return The type object, or {@code null}.
   */
  public SoyType getType(String typeName) {
    SoyType result = BUILTIN_TYPES.get(typeName);
    if (result != null) {
      return result;
    }
    for (SoyTypeProvider provider : typeProviders) {
      result = provider.getType(typeName, this);
      if (result != null) {
        return result;
      }
    }
    return null;
  }


  /**
   * Factory function which creates a list type, given an element type.
   * This folds list types with identical element types together, so asking for the same element
   * type twice will return a pointer to the same type object.
   *
   * @param elementType The element type of the list.
   * @return The list type.
   */
  public ListType getOrCreateListType(SoyType elementType) {
    return listTypes.intern(ListType.of(elementType));
  }


  /**
   * Factory function which creates a map type, given a key and value type.
   * This folds map types with identical key/value types together, so asking for the same key/value
   * type twice will return a pointer to the same type object.
   *
   * @param keyType The key type of the map.
   * @param valueType The value type of the map.
   * @return The map type.
   */
  public MapType getOrCreateMapType(SoyType keyType, SoyType valueType) {
    return mapTypes.intern(MapType.of(keyType, valueType));
  }


  /**
   * Factory function which creates a union type, given the member types.
   * This folds identical union types together.
   *
   * @param members The members of the union.
   * @return The union type.
   */
  public SoyType getOrCreateUnionType(Collection<SoyType> members) {
    SoyType type = UnionType.of(members);
    if (type instanceof UnionType) {
      type = unionTypes.intern((UnionType) type);
    }
    return type;
  }


  /**
   * Factory function which creates a union type, given the member types.
   * This folds identical union types together.
   *
   * @param members The members of the union.
   * @return The union type.
   */
  public SoyType getOrCreateUnionType(SoyType... members) {
    return getOrCreateUnionType(Arrays.asList(members));
  }


  /**
   * Factory function which creates a record type, given a map of fields.
   * This folds map types with identical key/value types together, so asking for
   * the same key/value type twice will return a pointer to the same type object.
   *
   * @param fields The map containing field names and types.
   * @return The record type.
   */
  public RecordType getOrCreateRecordType(Map<String, SoyType> fields) {
    return recordTypes.intern(RecordType.of(fields));
  }
}
