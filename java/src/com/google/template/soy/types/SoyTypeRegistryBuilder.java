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

import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.internal.proto.ProtoUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Helper class that assists in the construction of {@link SoyTypeRegistry}. */
public final class SoyTypeRegistryBuilder {

  /** Creates a type registry with only the built-in types. Mostly used for testing. */
  public static SoyTypeRegistry create() {
    return TypeRegistries.newComposite(
        TypeRegistries.builtinTypeRegistry(), TypeRegistries.newTypeInterner());
  }

  private final List<GenericDescriptor> descriptors = new ArrayList<>();

  /**
   * Whether or not all the descriptors added to {@link #descriptors} are {@link FileDescriptor}
   * objects. If they are we can optimize traversal in the {@link DescriptorVisitor}.
   *
   * <p>This case is always true when using the command line compiler. It is only possibly not true
   * when using the SoyFileSet apis directly.
   */
  private boolean areAllDescriptorsFileDescriptors = true;

  public SoyTypeRegistryBuilder() {}

  /** Registers a collection of descriptors of any type. */
  public SoyTypeRegistryBuilder addDescriptors(
      Iterable<? extends GenericDescriptor> descriptorsToAdd) {
    for (GenericDescriptor descriptorToAdd : descriptorsToAdd) {
      if (areAllDescriptorsFileDescriptors && !(descriptorToAdd instanceof FileDescriptor)) {
        areAllDescriptorsFileDescriptors = false;
      }
      descriptors.add(descriptorToAdd);
    }
    return this;
  }

  private void accept(DescriptorVisitor visitor) throws DescriptorValidationException {
    for (GenericDescriptor descriptor : descriptors) {
      visitor.visitGeneric(descriptor, /*onlyVisitingFiles=*/ areAllDescriptorsFileDescriptors);
    }
  }

  public SoyTypeRegistry build() {
    DescriptorVisitor visitor = new DescriptorVisitor();
    try {
      accept(visitor);
    } catch (DescriptorValidationException e) {
      throw new RuntimeException("Malformed descriptor set", e);
    }

    SoyTypeRegistry base = create();
    return new ProtoSoyTypeRegistry(
        base,
        ImmutableMap.copyOf(visitor.descriptors),
        ImmutableSetMultimap.copyOf(visitor.extensions),
        ImmutableSet.copyOf(visitor.files));
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
                comparingInt(FieldDescriptor::getNumber)
                    .thenComparing(left -> left.getContainingType().getFullName()))
            .build();
    final Set<FileDescriptor> files = new HashSet<>();

    /**
     * Collect all enum, message, and extension descriptors referenced by the given descriptor
     *
     * @param descriptor the descriptor to explore
     * @param onlyVisitingFiles whether or not we are only visiting files descriptors, in this
     *     scenario we can optimize our exploration to avoid visiting the same descriptors multiple
     *     times.
     */
    void visitGeneric(GenericDescriptor descriptor, boolean onlyVisitingFiles) {
      if (descriptor instanceof FileDescriptor) {
        visitFile((FileDescriptor) descriptor, onlyVisitingFiles);
      } else if (descriptor instanceof Descriptor) {
        visitMessage((Descriptor) descriptor, /*exploreDependencies=*/ true, onlyVisitingFiles);
      } else if (descriptor instanceof FieldDescriptor) {
        visitField((FieldDescriptor) descriptor, /*exploreDependencies=*/ true, onlyVisitingFiles);
      } else if (descriptor instanceof EnumDescriptor) {
        visitEnum((EnumDescriptor) descriptor, onlyVisitingFiles);
      } // services, etc. not needed thus far so neither gathered nor dispatched
    }

    private void visitFiles(List<FileDescriptor> descriptors, boolean onlyVisitingFiles) {
      for (FileDescriptor descriptor : descriptors) {
        visitFile(descriptor, onlyVisitingFiles);
      }
    }

    void visitFile(FileDescriptor fileDescriptor, boolean onlyVisitingFiles) {
      if (!shouldVisitDescriptor(fileDescriptor, onlyVisitingFiles)) {
        return;
      }
      files.add(fileDescriptor);
      visitFiles(fileDescriptor.getDependencies(), onlyVisitingFiles);
      // disable exploring dependencies when visiting all declarations in a file. Because we have
      // already visited all the file level dependencies we don't need to do more explorations
      // since we will just visit the same things over and over.
      visitMessages(
          fileDescriptor.getMessageTypes(), /* exploreDependencies=*/ false, onlyVisitingFiles);
      visitFields(
          fileDescriptor.getExtensions(), /* exploreDependencies=*/ false, onlyVisitingFiles);
      visitEnums(fileDescriptor.getEnumTypes(), onlyVisitingFiles);
    }

    private void visitMessages(
        List<Descriptor> descriptors, boolean exploreDependencies, boolean onlyVisitingFiles) {
      for (Descriptor descriptor : descriptors) {
        visitMessage(descriptor, exploreDependencies, onlyVisitingFiles);
      }
    }

    private void visitMessage(
        Descriptor messageDescriptor, boolean exploreDependencies, boolean onlyVisitingFiles) {
      if (!shouldVisitDescriptor(messageDescriptor, onlyVisitingFiles)) {
        return;
      }
      descriptors.put(messageDescriptor.getFullName(), messageDescriptor);
      visitEnums(messageDescriptor.getEnumTypes(), onlyVisitingFiles);
      visitFields(messageDescriptor.getExtensions(), exploreDependencies, onlyVisitingFiles);
      visitMessages(messageDescriptor.getNestedTypes(), exploreDependencies, onlyVisitingFiles);
      // we only need to visit fields to collect field types when we are exploring dependencies
      if (exploreDependencies) {
        visitFields(messageDescriptor.getFields(), exploreDependencies, onlyVisitingFiles);
      }
    }

    private void visitEnums(List<EnumDescriptor> enumDescriptors, boolean onlyVisitingFiles) {
      for (EnumDescriptor enumDescriptor : enumDescriptors) {
        visitEnum(enumDescriptor, onlyVisitingFiles);
      }
    }

    private void visitEnum(EnumDescriptor enumDescriptor, boolean onlyVisitingFiles) {
      if (!shouldVisitDescriptor(enumDescriptor, onlyVisitingFiles)) {
        return;
      }
      descriptors.put(enumDescriptor.getFullName(), enumDescriptor);
    }

    private void visitFields(
        List<FieldDescriptor> fieldDescriptors,
        boolean exploreDependencies,
        boolean onlyVisitingFiles) {
      for (FieldDescriptor fieldDescriptor : fieldDescriptors) {
        visitField(fieldDescriptor, exploreDependencies, onlyVisitingFiles);
      }
    }

    private void visitField(
        FieldDescriptor fieldDescriptor, boolean exploreDependencies, boolean onlyVisitingFiles) {
      if (!shouldVisitDescriptor(fieldDescriptor, onlyVisitingFiles)) {
        return;
      }
      if (exploreDependencies && fieldDescriptor.getType() == FieldDescriptor.Type.MESSAGE) {
        visitMessage(fieldDescriptor.getMessageType(), exploreDependencies, onlyVisitingFiles);
      }
      if (exploreDependencies && fieldDescriptor.getType() == FieldDescriptor.Type.ENUM) {
        visitEnum(fieldDescriptor.getEnumType(), onlyVisitingFiles);
      }
      if (fieldDescriptor.isExtension() && !ProtoUtils.shouldJsIgnoreField(fieldDescriptor)) {
        extensions.put(fieldDescriptor.getContainingType().getFullName(), fieldDescriptor);
      }
    }

    private boolean shouldVisitDescriptor(GenericDescriptor descriptor, boolean onlyVisitingFiles) {
      // if we are only visiting files, then we don't need to check the visited hash set unless
      // this
      // descriptor is a file, this is because the traversal strategy (where we disable
      // 'exploreDependencies') means that we are guaranteed to visit each descriptor exactly
      // once.
      // So checking the visited set is redundant.
      if (onlyVisitingFiles && !(descriptor instanceof FileDescriptor)) {
        return true;
      }
      return visited.add(descriptor.getFullName());
    }
  }

  /** The standard implementation of SoyTypeRegistry, which supports protobuf types. */
  public static class ProtoSoyTypeRegistry extends DelegatingSoyTypeRegistry
      implements TypeRegistry.ProtoRegistry {

    /** Map of all the protobuf type descriptors that we've discovered. */
    private final ImmutableMap<String, GenericDescriptor> descriptors;

    /** All of the known type names for this registry (including its delegate), sorted. */
    private final Iterable<String> allSortedTypeNames;

    /**
     * Map of the first dotted prefix in a type to its full type name (e.g. "foo." ->
     * "foo.bar.Baz"). Used to check for namespace conflicts in {@link
     * com.google.template.soy.passes.ValidateAliasesPass}.
     */
    private final ImmutableMap<String, String> prefixesToTypeNames;

    /* Multimap of all known extensions of a given proto */
    private final ImmutableSetMultimap<String, FieldDescriptor> extensions;

    private final ImmutableSet<FileDescriptor> fileDescriptors;

    public ProtoSoyTypeRegistry(
        SoyTypeRegistry delegate,
        ImmutableMap<String, GenericDescriptor> descriptors,
        ImmutableSetMultimap<String, FieldDescriptor> extensions,
        ImmutableSet<FileDescriptor> fileDescriptors) {
      super(delegate);
      this.descriptors = descriptors;

      this.allSortedTypeNames =
          Iterables.mergeSorted(
              ImmutableList.of(
                  super.getAllSortedTypeNames(), ImmutableList.sortedCopyOf(descriptors.keySet())),
              naturalOrder());

      prefixesToTypeNames = getPrefixToTypeNamesMap(allSortedTypeNames);

      this.extensions = extensions;
      this.fileDescriptors = fileDescriptors;
    }

    @Override
    public ImmutableSet<FileDescriptor> getFileDescriptors() {
      return fileDescriptors;
    }

    @Nullable
    @Override
    public SoyType getType(String typeName) {
      SoyType type = super.getType(typeName);
      if (type != null) {
        return type;
      }
      GenericDescriptor descriptor = descriptors.get(typeName);
      if (descriptor instanceof EnumDescriptor) {
        return getOrCreateProtoEnumType((EnumDescriptor) descriptor);
      } else if (descriptor instanceof Descriptor) {
        Descriptor d = (Descriptor) descriptor;
        return getOrComputeProtoType(
            d, name -> new SoyProtoType(this, d, extensions.get(typeName)));
      } else {
        return null;
      }
    }

    @Override
    public String findTypeWithMatchingNamespace(String prefix) {
      return prefixesToTypeNames.get(prefix + ".");
    }

    @Override
    public Iterable<String> getAllSortedTypeNames() {
      return allSortedTypeNames;
    }

    /**
     * Takes a list of fully qualified type names (e.g. "foo.bar.Baz"), and returns a map of the
     * first dotted prefix to each full name (e.g. "foo." -> "foo.bar.Baz"). If multiple types have
     * the same prefix, the map will store the first one.
     */
    private static ImmutableMap<String, String> getPrefixToTypeNamesMap(
        Iterable<String> fullTypeNames) {
      Map<String, String> prefixesToTypeNamesBuilder = new HashMap<>();
      for (String typeName : fullTypeNames) {
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
}
