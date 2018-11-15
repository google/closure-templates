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
import static com.google.template.soy.types.SoyTypes.NUMBER_TYPE;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.StyleType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

/**
 * Registry of types which can be looked up by name.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SoyTypeRegistry {

  private static final ExtensionRegistry REGISTRY = createRegistry();

  private static final ExtensionRegistry createRegistry() {
    ExtensionRegistry instance = ExtensionRegistry.newInstance();
    // Add extensions needed for parsing descriptors here.
    return instance;
  }

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

  private static final ImmutableMap<String, SanitizedType> SAFE_PROTO_TO_SANITIZED_TYPE =
      ImmutableMap.<String, SanitizedType>builder()
          .put(SafeHtmlProto.getDescriptor().getFullName(), SanitizedType.HtmlType.getInstance())
          .put(SafeScriptProto.getDescriptor().getFullName(), SanitizedType.JsType.getInstance())
          .put(SafeStyleProto.getDescriptor().getFullName(), SanitizedType.StyleType.getInstance())
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              SanitizedType.StyleType.getInstance())
          .put(SafeUrlProto.getDescriptor().getFullName(), SanitizedType.UriType.getInstance())
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              SanitizedType.TrustedResourceUriType.getInstance())
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

  private SoyTypeRegistry(Builder builder) {
    DescriptorVisitor visitor = new DescriptorVisitor();
    try {
      builder.accept(visitor);
    } catch (DescriptorValidationException e) {
      throw new RuntimeException("Malformed descriptor set", e);
    }
    this.descriptors = ImmutableMap.copyOf(visitor.descriptors);
    this.extensions = ImmutableSetMultimap.copyOf(visitor.extensions);
    // TODO(lukes): this is wrong.  The safe string protos should not be usable as types
    this.protoTypeCache = new HashMap<>(SAFE_PROTO_TO_SANITIZED_TYPE);
    // Register the special number type so == comparisons work
    checkState(unionTypes.intern((UnionType) NUMBER_TYPE) == NUMBER_TYPE);
  }

  public SoyTypeRegistry() {
    this(new Builder());
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
            Ordering.natural()
                .immutableSortedCopy(
                    Iterables.concat(BUILTIN_TYPES.keySet(), descriptors.keySet()));
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
   * Factory function which creates a record type, given a map of fields. This folds map types with
   * identical key/value types together, so asking for the same key/value type twice will return a
   * pointer to the same type object.
   *
   * @param fields The map containing field names and types.
   * @return The record type.
   */
  public RecordType getOrCreateRecordType(Map<String, SoyType> fields) {
    return recordTypes.intern(RecordType.of(fields));
  }

  /**
   * Factory function which creates and returns a {@code ve} type with the given {@code dataType}.
   * This folds identical ve types together.
   */
  public VeType getOrCreateVeType(SoyType dataType) {
    return veTypes.intern(VeType.of(dataType));
  }

  /** Helper class that assists in the construction of SoyTypeProviders. */
  public static final class Builder {
    // use a linked hash map.  The descriptors will tend to be in dependency order, so by
    // constructing in the provided order we will limit the depth of the recusion below.
    private final Map<String, FileDescriptorProto> nameToProtos = new LinkedHashMap<>();
    private final List<GenericDescriptor> descriptors = new ArrayList<>();

    public Builder() {}

    /** Read a file descriptor set from a file and register any proto types found within. */
    public Builder addFileDescriptorSetFromFile(File descriptorFile) throws IOException {
      // TODO(lukes): if we called buildDescriptors here we could force callers to pass files in
      // dependency order (and also throw DescriptorValidationException here).  This would improve
      // performance (slightly, due to less recursion and no need for the nameToProtos map), but
      // more importantly it would improve error locality.
      try (InputStream inputStream = new BufferedInputStream(new FileInputStream(descriptorFile))) {
        for (FileDescriptorProto file :
            FileDescriptorSet.parseFrom(inputStream, REGISTRY).getFileList()) {
          nameToProtos.put(file.getName(), file);
        }
      }
      return this;
    }

    /** Registers a collection of descriptors of any type. */
    public Builder addDescriptors(Iterable<? extends GenericDescriptor> descriptorsToAdd) {
      for (GenericDescriptor descriptorToAdd : descriptorsToAdd) {
        descriptors.add(descriptorToAdd);
      }
      return this;
    }

    private void accept(DescriptorVisitor visitor) throws DescriptorValidationException {
      Map<String, FileDescriptor> parsedDescriptors = new HashMap<>();
      for (String name : nameToProtos.keySet()) {
        visitor.visit(buildDescriptor(null, name, parsedDescriptors, nameToProtos));
      }
      visitor.visit(descriptors);
    }

    private static FileDescriptor buildDescriptor(
        String requestor,
        String name,
        Map<String, FileDescriptor> descriptors,
        Map<String, FileDescriptorProto> protos)
        throws DescriptorValidationException {
      FileDescriptor file = descriptors.get(name);
      if (file != null) {
        return file;
      }
      FileDescriptorProto proto = protos.get(name);
      if (proto == null) {
        throw new IllegalStateException(
            "Cannot find proto descriptor for " + name + " which is a dependency of " + requestor);
      }
      FileDescriptor[] deps = new FileDescriptor[proto.getDependencyCount()];
      for (int i = 0; i < proto.getDependencyCount(); i++) {
        deps[i] = buildDescriptor(name, proto.getDependency(i), descriptors, protos);
      }
      file = FileDescriptor.buildFrom(proto, deps);
      descriptors.put(name, file);
      return file;
    }

    public SoyTypeRegistry build() {
      return new SoyTypeRegistry(this);
    }
  }

  /** Walks a descriptor tree to build the descriptors, and extensions maps. */
  private static final class DescriptorVisitor {
    final Set<String> visited = new HashSet<>();
    final Map<String, GenericDescriptor> descriptors = new LinkedHashMap<>();
    final SetMultimap<String, FieldDescriptor> extensions =
        MultimapBuilder.linkedHashKeys()
            // We need a custom comparator since FieldDescriptor doesn't implement equals/hashCode
            // reasonably.  We don't really care about the order, just deduplication.
            .treeSetValues(
                new Comparator<FieldDescriptor>() {
                  @Override
                  public int compare(FieldDescriptor left, FieldDescriptor right) {
                    return ComparisonChain.start()
                        .compare(left.getNumber(), right.getNumber())
                        .compare(
                            left.getContainingType().getFullName(),
                            right.getContainingType().getFullName())
                        .result();
                  }
                })
            .build();

    void visit(Iterable<? extends GenericDescriptor> descriptors) {
      for (GenericDescriptor descriptor : descriptors) {
        visit(descriptor);
      }
    }

    void visit(GenericDescriptor descriptor) {
      if (!visited.add(descriptor.getFullName())) {
        return;
      }
      if (descriptor instanceof FileDescriptor) {
        FileDescriptor fileDescriptor = (FileDescriptor) descriptor;
        visit(fileDescriptor.getMessageTypes());
        visit(fileDescriptor.getExtensions());
        visit(fileDescriptor.getEnumTypes());
        visit(fileDescriptor.getDependencies());
      } else if (descriptor instanceof Descriptor) {
        Descriptor messageDescriptor = (Descriptor) descriptor;
        descriptors.put(messageDescriptor.getFullName(), messageDescriptor);
        visit(messageDescriptor.getEnumTypes());
        visit(messageDescriptor.getExtensions());
        visit(messageDescriptor.getNestedTypes());
        visit(messageDescriptor.getFields());
      } else if (descriptor instanceof FieldDescriptor) {
        FieldDescriptor fieldDescriptor = (FieldDescriptor) descriptor;
        if (fieldDescriptor.getType() == Type.MESSAGE) {
          visit(fieldDescriptor.getMessageType());
        }
        if (fieldDescriptor.getType() == Type.ENUM) {
          visit(fieldDescriptor.getEnumType());
        }
        if (fieldDescriptor.isExtension() && !ProtoUtils.shouldJsIgnoreField(fieldDescriptor)) {
          extensions.put(fieldDescriptor.getContainingType().getFullName(), fieldDescriptor);
        }
      } else if (descriptor instanceof EnumDescriptor) {
        EnumDescriptor enumDescriptor = (EnumDescriptor) descriptor;
        descriptors.put(descriptor.getFullName(), enumDescriptor);
      } // services, etc. not needed thus far so neither gathered nor dispatched
    }
  }
}
