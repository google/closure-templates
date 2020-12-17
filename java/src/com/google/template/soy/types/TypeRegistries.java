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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.internal.proto.Field;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Implementations of {@link TypeRegistry} and {@link TypeInterner}. */
public final class TypeRegistries {

  private static final SoyErrorKind PROTO_FQN =
      SoyErrorKind.of(
          "Proto types must be imported rather than referenced by their fully qualified names.");

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
      errorReporter.report(id.location(), PROTO_FQN);
    }

    return null;
  }

  private static final class TypeInternerImpl implements TypeInterner {

    private final Interner<SoyType> types = Interners.newStrongInterner();
    private final Map<String, SoyProtoType> protoTypes = new ConcurrentHashMap<>();
    private final Map<GenericDescriptor, ImmutableMap<String, GenericDescriptor>>
        protoMembersCache = new ConcurrentHashMap<>();
    private final Map<GenericDescriptor, ImportType> protoImportTypes = new ConcurrentHashMap<>();

    public TypeInternerImpl() {
      // Register the special number type so == comparisons work
      checkState(types.intern(NUMBER_TYPE) == NUMBER_TYPE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends SoyType> T intern(T type) {
      return (T) types.intern(type);
    }

    @Override
    public SoyProtoType getOrComputeProtoType(
        Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper) {
      return protoTypes.computeIfAbsent(descriptor.getFullName(), mapper);
    }

    @Override
    public ImportType getProtoImportType(GenericDescriptor descriptor) {
      return protoImportTypes.computeIfAbsent(
          descriptor,
          d -> {
            if (d instanceof FileDescriptor) {
              return ProtoModuleImportType.create((FileDescriptor) d);
            }
            if (d instanceof Descriptor) {
              return ProtoImportType.create((Descriptor) d);
            }
            if (d instanceof EnumDescriptor) {
              return ProtoEnumImportType.create((EnumDescriptor) d);
            }
            if (d instanceof FieldDescriptor && ((FieldDescriptor) d).isExtension()) {
              return ProtoExtensionImportType.create((FieldDescriptor) d);
            }
            throw new ClassCastException(d.getClass().getName());
          });
    }

    @Override
    public SoyType getProtoImportType(FileDescriptor descriptor, String member) {
      return getProtoImportType(
          protoMembersCache.computeIfAbsent(descriptor, TypeInternerImpl::buildMemberIndex),
          member);
    }

    @Override
    public SoyType getProtoImportType(Descriptor descriptor, String member) {
      return getProtoImportType(
          protoMembersCache.computeIfAbsent(descriptor, TypeInternerImpl::buildMemberIndex),
          member);
    }

    private SoyType getProtoImportType(Map<String, GenericDescriptor> index, String member) {
      GenericDescriptor d = index.get(member);
      if (d != null) {
        return getProtoImportType(d);
      }
      return UnknownType.getInstance();
    }

    private static ImmutableMap<String, GenericDescriptor> buildMemberIndex(GenericDescriptor d) {
      ImmutableMap.Builder<String, GenericDescriptor> index = ImmutableMap.builder();
      if (d instanceof FileDescriptor) {
        FileDescriptor fileDescriptor = (FileDescriptor) d;
        fileDescriptor.getMessageTypes().forEach(t -> index.put(t.getName(), t));
        fileDescriptor.getEnumTypes().forEach(t -> index.put(t.getName(), t));
        fileDescriptor.getExtensions().forEach(t -> index.put(Field.computeSoyName(t), t));
      } else if (d instanceof Descriptor) {
        Descriptor descriptor = (Descriptor) d;
        descriptor.getNestedTypes().forEach(t -> index.put(t.getName(), t));
        descriptor.getEnumTypes().forEach(t -> index.put(t.getName(), t));
        descriptor.getExtensions().forEach(t -> index.put(Field.computeSoyName(t), t));
      } else {
        throw new AssertionError(d.getClass().getName());
      }
      return index.build();
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
    public Iterable<String> getAllSortedTypeNames() {
      return typeRegistry.getAllSortedTypeNames();
    }

    @Override
    public <T extends SoyType> T intern(T type) {
      return typeInterner.intern(type);
    }

    @Override
    public SoyProtoType getOrComputeProtoType(
        Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper) {
      return typeInterner.getOrComputeProtoType(descriptor, mapper);
    }

    @Override
    public SoyType getOrCreateElementType(String tagName) {
      return typeInterner.getOrCreateElementType(tagName);
    }

    @Override
    public ImportType getProtoImportType(GenericDescriptor descriptor) {
      return typeInterner.getProtoImportType(descriptor);
    }

    @Override
    public SoyType getProtoImportType(FileDescriptor descriptor, String member) {
      return typeInterner.getProtoImportType(descriptor, member);
    }

    @Override
    public SoyType getProtoImportType(Descriptor descriptor, String member) {
      return typeInterner.getProtoImportType(descriptor, member);
    }
  }
}
