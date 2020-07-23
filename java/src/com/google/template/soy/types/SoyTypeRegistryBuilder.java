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

import static java.util.Comparator.naturalOrder;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.internal.proto.ProtoUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Helper class that assists in the construction of {@link SoyTypeRegistry}. */
public final class SoyTypeRegistryBuilder {

  /** Creates a type registry with only the built-in types. Mostly used for testing. */
  public static SoyTypeRegistry create() {
    return TypeRegistries.newComposite(
        TypeRegistries.builtinTypeRegistry(), TypeRegistries.newTypeInterner());
  }

  private final ImmutableList.Builder<GenericDescriptor> descriptors = ImmutableList.builder();

  public SoyTypeRegistryBuilder() {}

  /** Registers a collection of descriptors of any type. */
  public SoyTypeRegistryBuilder addDescriptors(
      Iterable<? extends GenericDescriptor> descriptorsToAdd) {
    descriptors.addAll(descriptorsToAdd);
    return this;
  }

  public SoyTypeRegistry build() {
    ImmutableList<GenericDescriptor> tmp = descriptors.build();
    ProtoFqnRegistryBuilder builder = new ProtoFqnRegistryBuilder(tmp);
    SoyTypeRegistry base = create();
    ProtoFqnTypeRegistry registry = (ProtoFqnTypeRegistry) builder.build(base);
    return new SoyTypeRegistryImpl(base, ImmutableSet.copyOf(builder.files.values()), registry);
  }

  /** Builder for {@link ProtoTypeRegistry}. */
  public static class ProtoFqnRegistryBuilder {
    private final ImmutableSet<GenericDescriptor> inputs;
    private final Predicate<GenericDescriptor> alreadyVisited;
    private final Map<String, GenericDescriptor> msgAndEnumFqnToDesc = new HashMap<>();
    private final SetMultimap<String, FieldDescriptor> msgFqnToExts = HashMultimap.create();
    private final Map<String, FileDescriptor> files = new LinkedHashMap<>();

    public ProtoFqnRegistryBuilder(Iterable<GenericDescriptor> inputs) {
      this.inputs = ImmutableSet.copyOf(inputs); // maintain order
      Set<GenericDescriptor> visited = new HashSet<>();
      alreadyVisited = d -> !visited.add(d);
    }

    public ProtoTypeRegistry build(SoyTypeRegistry interner) {
      Set<FileDescriptor> fileInputs =
          inputs.stream()
              .filter(d -> d instanceof FileDescriptor)
              .map(FileDescriptor.class::cast)
              .collect(Collectors.toSet());

      // Visit all the file descriptors explicitly passed.
      fileInputs.forEach(this::visitFile);
      // Visit all descriptors explicitly passed not descending from any of the file inputs.
      inputs.stream().filter(d -> !fileInputs.contains(d.getFile())).forEach(this::visitGeneric);
      // Visit to collection extensions any file of any input not already visited.
      inputs.stream()
          .map(GenericDescriptor::getFile)
          .distinct()
          .filter(d -> !fileInputs.contains(d))
          .forEach(this::visitFileForExtensions);

      return new ProtoFqnTypeRegistry(
          interner,
          ImmutableMap.copyOf(msgAndEnumFqnToDesc),
          ImmutableSetMultimap.copyOf(msgFqnToExts));
    }

    private void visitGeneric(GenericDescriptor descriptor) {
      if (descriptor instanceof Descriptor) {
        visitMessage((Descriptor) descriptor);
      } else if (descriptor instanceof FieldDescriptor) {
        FieldDescriptor fd = (FieldDescriptor) descriptor;
        if (fd.isExtension()) {
          visitExtension(fd);
        }
        visitField(fd);
      } else if (descriptor instanceof EnumDescriptor) {
        visitEnum((EnumDescriptor) descriptor);
      } else if (descriptor instanceof FileDescriptor) {
        throw new IllegalArgumentException();
      } // services, etc. not needed thus far so neither gathered nor dispatched
    }

    private void visitFile(FileDescriptor fd) {
      if (alreadyVisited.test(fd)) {
        return;
      }
      files.putIfAbsent(fd.getName(), fd);
      fd.getDependencies().forEach(this::visitFile);
      fd.getExtensions().forEach(this::visitExtension);
      fd.getMessageTypes().forEach(this::visitMessage);
      fd.getEnumTypes().forEach(this::visitEnum);
    }

    private void visitFileForExtensions(FileDescriptor fd) {
      if (alreadyVisited.test(fd)) {
        return;
      }
      files.putIfAbsent(fd.getName(), fd);
      fd.getDependencies().forEach(this::visitFileForExtensions);
      fd.getExtensions().forEach(this::visitExtension);
      fd.getMessageTypes().forEach(this::visitMessageForExtensions);
    }

    private void visitMessageForExtensions(Descriptor d) {
      d.getExtensions().forEach(this::visitExtension);
      d.getNestedTypes().forEach(this::visitMessageForExtensions);
    }

    private void visitMessage(Descriptor m) {
      if (alreadyVisited.test(m)) {
        return;
      }
      msgAndEnumFqnToDesc.put(m.getFullName(), m);

      m.getEnumTypes().forEach(this::visitEnum);
      m.getExtensions().forEach(this::visitExtension);
      m.getNestedTypes().forEach(this::visitMessage);
      m.getFields().forEach(this::visitField);
    }

    private void visitField(FieldDescriptor f) {
      if (f.getType() == FieldDescriptor.Type.MESSAGE) {
        visitMessage(f.getMessageType());
      }
      if (f.getType() == FieldDescriptor.Type.ENUM) {
        visitEnum(f.getEnumType());
      }
    }

    private void visitEnum(EnumDescriptor e) {
      msgAndEnumFqnToDesc.put(e.getFullName(), e);
    }

    private void visitExtension(FieldDescriptor f) {
      Preconditions.checkArgument(f.isExtension());
      if (!ProtoUtils.shouldJsIgnoreField(f)) {
        msgFqnToExts.put(f.getContainingType().getFullName(), f);
      }
    }
  }

  /** The standard implementation of SoyTypeRegistry, which supports protobuf types. */
  static class SoyTypeRegistryImpl extends DelegatingSoyTypeRegistry {

    /** All of the known type names for this registry (including its delegate), sorted. */
    private final Supplier<Iterable<String>> allSortedTypeNames;

    /**
     * Map of the first dotted prefix in a type to its full type name (e.g. "foo." ->
     * "foo.bar.Baz"). Used to check for namespace conflicts in {@link
     * com.google.template.soy.passes.ValidateAliasesPass}.
     */
    private final Supplier<ImmutableMap<String, String>> prefixesToTypeNames;

    private final ImmutableSet<FileDescriptor> fileDescriptors;
    private final ProtoFqnTypeRegistry protoFqnRegistry;

    public SoyTypeRegistryImpl(
        SoyTypeRegistry delegate,
        ImmutableSet<FileDescriptor> fileDescriptors,
        ProtoFqnTypeRegistry protoFqnRegistry) {
      super(delegate);
      this.protoFqnRegistry = protoFqnRegistry;
      this.fileDescriptors = fileDescriptors;

      this.allSortedTypeNames = Suppliers.memoize(this::buildAllSortedTypeNames);
      this.prefixesToTypeNames = Suppliers.memoize(this::buildPrefixToTypeNamesMap);
    }

    @Override
    public ImmutableSet<FileDescriptor> getProtoDescriptors() {
      return fileDescriptors;
    }

    @Override
    public ProtoTypeRegistry getProtoRegistry() {
      return protoFqnRegistry;
    }

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      SoyType type = super.getType(typeName);
      if (type != null) {
        return type;
      }
      return protoFqnRegistry.getProtoType(typeName);
    }

    @Override
    public String findTypeWithMatchingNamespace(String prefix) {
      return prefixesToTypeNames.get().get(prefix + ".");
    }

    @Override
    public Iterable<String> getAllSortedTypeNames() {
      return allSortedTypeNames.get();
    }

    private Iterable<String> buildAllSortedTypeNames() {
      return Iterables.mergeSorted(
          ImmutableList.of(
              super.getAllSortedTypeNames(),
              ImmutableList.sortedCopyOf(protoFqnRegistry.getAllKeys())),
          naturalOrder());
    }

    /**
     * Takes a list of fully qualified type names (e.g. "foo.bar.Baz"), and returns a map of the
     * first dotted prefix to each full name (e.g. "foo." -> "foo.bar.Baz"). If multiple types have
     * the same prefix, the map will store the first one.
     */
    private ImmutableMap<String, String> buildPrefixToTypeNamesMap() {
      Map<String, String> prefixesToTypeNamesBuilder = new HashMap<>();
      for (String typeName : allSortedTypeNames.get()) {
        String prefix = typeName;
        int indexOfFirstDot = typeName.indexOf(".");
        // If there was no dot, or a dot was the last char, return the whole string.
        // Otherwise, return "foo." in "foo.bar.baz".
        if (indexOfFirstDot >= 0 && indexOfFirstDot < typeName.length() - 1) {
          prefix = typeName.substring(0, indexOfFirstDot + 1);
        }
        prefixesToTypeNamesBuilder.putIfAbsent(prefix, typeName);
      }
      return ImmutableMap.copyOf(prefixesToTypeNamesBuilder);
    }
  }

  private abstract static class DelegatingProtoTypeRegistry implements ProtoTypeRegistry {

    private final ProtoTypeRegistry delegate;

    protected DelegatingProtoTypeRegistry(ProtoTypeRegistry delegate) {
      this.delegate = delegate;
    }

    @Override
    public SoyType getProtoType(String protoFqn) {
      return delegate.getProtoType(protoFqn);
    }

    @Override
    public Iterable<String> getAllKeys() {
      return delegate.getAllKeys();
    }
  }

  private static class ProtoFqnTypeRegistry extends DelegatingProtoTypeRegistry {

    private final TypeInterner interner;
    /** Map of FQN to descriptor for all message and enum descendants of imported symbols. */
    private final ImmutableMap<String, GenericDescriptor> msgAndEnumFqnToDesc;
    /** Multimap of FQN to extensions descriptor for all message descendants of imported symbols. */
    private final ImmutableSetMultimap<String, FieldDescriptor> msgFqnToExts;

    public ProtoFqnTypeRegistry(
        SoyTypeRegistry delegate,
        ImmutableMap<String, GenericDescriptor> msgAndEnumFqnToDesc,
        ImmutableSetMultimap<String, FieldDescriptor> msgFqnToExts) {
      super(delegate.getProtoRegistry());
      this.interner = delegate;
      this.msgAndEnumFqnToDesc = msgAndEnumFqnToDesc;
      this.msgFqnToExts = msgFqnToExts;
    }

    @Nullable
    @Override
    public SoyType getProtoType(String protoFqn) {
      GenericDescriptor descriptor = msgAndEnumFqnToDesc.get(protoFqn);
      if (descriptor instanceof EnumDescriptor) {
        return interner.getOrCreateProtoEnumType((EnumDescriptor) descriptor);
      } else if (descriptor instanceof Descriptor) {
        return interner.getOrComputeProtoType(
            (Descriptor) descriptor,
            name ->
                new SoyProtoType(
                    interner, this, (Descriptor) descriptor, msgFqnToExts.get(protoFqn)));
      }
      return super.getProtoType(protoFqn);
    }

    @Override
    public ImmutableSet<String> getAllKeys() {
      return msgAndEnumFqnToDesc.keySet();
    }
  }
}
