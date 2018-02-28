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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.ErrorReporter.Checkpoint;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.internal.proto.ProtoUtils;
import com.google.template.soy.types.SanitizedType.AttributesType;
import com.google.template.soy.types.SanitizedType.CssType;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.SanitizedType.JsType;
import com.google.template.soy.types.SanitizedType.TrustedResourceUriType;
import com.google.template.soy.types.SanitizedType.UriType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.ast.GenericTypeNode;
import com.google.template.soy.types.ast.NamedTypeNode;
import com.google.template.soy.types.ast.RecordTypeNode;
import com.google.template.soy.types.ast.TypeNode;
import com.google.template.soy.types.ast.TypeNodeVisitor;
import com.google.template.soy.types.ast.UnionTypeNode;
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

  private static final SoyErrorKind UNKNOWN_TYPE =
      SoyErrorKind.of("Unknown type ''{0}''.{1}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind DUPLICATE_RECORD_FIELD =
      SoyErrorKind.of("Duplicate field ''{0}'' in record declaration.");

  private static final SoyErrorKind UNEXPECTED_TYPE_PARAM =
      SoyErrorKind.of(
          "Unexpected type parameter: ''{0}'' only has {1}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind EXPECTED_TYPE_PARAM =
      SoyErrorKind.of("Expected a type parameter: ''{0}'' has {1}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind NOT_A_GENERIC_TYPE =
      SoyErrorKind.of("''{0}'' is not a generic type, expected ''list'' or ''map''.");

  private static final SoyErrorKind MISSING_GENERIC_TYPE_PARAMETERS =
      SoyErrorKind.of("''{0}'' is a generic type, expected {1}.");

  // TODO(b/72409542): consider allowing string|int
  private static final ImmutableSet<SoyType.Kind> ALLOWED_MAP_KEY_TYPES =
      ImmutableSet.of(Kind.BOOL, Kind.INT, Kind.STRING, Kind.PROTO_ENUM);

  private static final SoyErrorKind BAD_MAP_KEY_TYPE;

  static {
    StringBuilder sb =
        new StringBuilder("''{0}'' is not allowed as a map key type. Allowed map key types: ");
    ImmutableList<SoyType.Kind> allowed = ALLOWED_MAP_KEY_TYPES.asList();
    for (int i = 0; i < allowed.size() - 1; ++i) {
      sb.append(allowed.get(i).toString().toLowerCase()).append(", ");
    }
    sb.append(allowed.get(allowed.size() - 1).toString().toLowerCase()).append(".");
    BAD_MAP_KEY_TYPE = SoyErrorKind.of(sb.toString());
  }

  // TODO(shwetakarwa): Rename consistently to use "URL".
  private static final ImmutableMap<String, SoyType> BUILTIN_TYPES =
      ImmutableMap.<String, SoyType>builder()
          .put("?", UnknownType.getInstance())
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

  private static final ImmutableMap<String, SanitizedType> SAFE_PROTO_TO_SANITIZED_TYPE =
      ImmutableMap.<String, SanitizedType>builder()
          .put(SafeHtmlProto.getDescriptor().getFullName(), SanitizedType.HtmlType.getInstance())
          .put(SafeScriptProto.getDescriptor().getFullName(), SanitizedType.JsType.getInstance())
          .put(SafeStyleProto.getDescriptor().getFullName(), SanitizedType.CssType.getInstance())
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              SanitizedType.CssType.getInstance())
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

  private Iterable<String> getAllSortedTypeNames() {
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
   * Converts a TypeNode into a SoyType.
   *
   * <p>If any errors are encountered they are reported to the error reporter.
   */
  public SoyType getOrCreateType(@Nullable TypeNode node, ErrorReporter errorReporter) {
    if (node == null) {
      return UnknownType.getInstance();
    }
    return node.accept(new TypeNodeConverter(errorReporter));
  }

  private static final ImmutableMap<String, GenericTypeInfo> GENERIC_TYPES =
      ImmutableMap.of(
          "list",
          new GenericTypeInfo(1) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateListType(types.get(0));
            }
          },
          "legacy_object_map",
          new GenericTypeInfo(2) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateLegacyObjectMapType(types.get(0), types.get(1));
            }
          },
          "map",
          new GenericTypeInfo(2) {
            @Override
            SoyType create(List<SoyType> types, SoyTypeRegistry registry) {
              return registry.getOrCreateMapType(types.get(0), types.get(1));
            }

            @Override
            void checkPermissibleGenericTypes(
                List<SoyType> types, List<TypeNode> typeNodes, ErrorReporter errorReporter) {
              SoyType keyType = types.get(0);
              if (!ALLOWED_MAP_KEY_TYPES.contains(keyType.getKind())) {
                errorReporter.report(typeNodes.get(0).sourceLocation(), BAD_MAP_KEY_TYPE, keyType);
              }
            }
          });

  /** Simple representation of a generic type specification. */
  private abstract static class GenericTypeInfo {
    final int numParams;

    GenericTypeInfo(int numParams) {
      this.numParams = numParams;
    }

    final String formatNumTypeParams() {
      return numParams + " type parameter" + (numParams > 1 ? "s" : "");
    }

    /**
     * Creates the given type. There are guaranteed to be exactly {@link #numParams} in the list.
     */
    abstract SoyType create(List<SoyType> types, SoyTypeRegistry registry);

    /**
     * Subclasses can override to implement custom restrictions on their generic type parameters.
     *
     * @param types The generic types.
     * @param typeNodes TypeNodes corresponding to each of the generic types (for reporting source
     *     locations in error messages)
     * @param errorReporter For reporting an error condition.
     */
    void checkPermissibleGenericTypes(
        List<SoyType> types, List<TypeNode> typeNodes, ErrorReporter errorReporter) {}
  }

  private final class TypeNodeConverter
      implements TypeNodeVisitor<SoyType>, Function<TypeNode, SoyType> {
    final ErrorReporter errorReporter;

    TypeNodeConverter(ErrorReporter errorReporter) {
      this.errorReporter = errorReporter;
    }

    @Override
    public SoyType visit(NamedTypeNode node) {
      String name = node.name();
      SoyType type = getType(name);
      if (type == null) {
        GenericTypeInfo genericType = GENERIC_TYPES.get(name);
        if (genericType != null) {
          errorReporter.report(
              node.sourceLocation(),
              MISSING_GENERIC_TYPE_PARAMETERS,
              name,
              genericType.formatNumTypeParams());
        } else {
          errorReporter.report(
              node.sourceLocation(),
              UNKNOWN_TYPE,
              name,
              SoyErrors.getDidYouMeanMessage(getAllSortedTypeNames(), name));
        }
        type = ErrorType.getInstance();
      }
      return type;
    }

    @Override
    public SoyType visit(GenericTypeNode node) {
      ImmutableList<TypeNode> args = node.arguments();
      String name = node.name();
      GenericTypeInfo genericType = GENERIC_TYPES.get(name);
      if (genericType == null) {
        errorReporter.report(node.sourceLocation(), NOT_A_GENERIC_TYPE, name);
        return ErrorType.getInstance();
      }
      if (args.size() < genericType.numParams) {
        errorReporter.report(
            // blame the '>'
            node.sourceLocation().getEndLocation(),
            EXPECTED_TYPE_PARAM,
            name,
            genericType.formatNumTypeParams());
        return ErrorType.getInstance();
      } else if (args.size() > genericType.numParams) {
        errorReporter.report(
            // blame the first unexpected argument
            args.get(genericType.numParams).sourceLocation(),
            UNEXPECTED_TYPE_PARAM,
            name,
            genericType.formatNumTypeParams());
        return ErrorType.getInstance();
      }

      List<SoyType> genericTypes = Lists.transform(args, this);
      Checkpoint checkpoint = errorReporter.checkpoint();
      genericType.checkPermissibleGenericTypes(genericTypes, args, errorReporter);
      return errorReporter.errorsSince(checkpoint)
          ? ErrorType.getInstance()
          : genericType.create(genericTypes, SoyTypeRegistry.this);
    }

    @Override
    public SoyType visit(UnionTypeNode node) {
      return getOrCreateUnionType(Collections2.transform(node.candidates(), this));
    }

    @Override
    public SoyType visit(RecordTypeNode node) {
      Map<String, SoyType> map = Maps.newLinkedHashMap();
      for (RecordTypeNode.Property property : node.properties()) {
        SoyType oldType = map.put(property.name(), property.type().accept(this));
        if (oldType != null) {
          errorReporter.report(property.nameLocation(), DUPLICATE_RECORD_FIELD, property.name());
          // restore old mapping and keep going
          map.put(property.name(), oldType);
        }
      }
      return getOrCreateRecordType(map);
    }

    @Override
    public SoyType apply(TypeNode node) {
      return node.accept(this);
    }
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
