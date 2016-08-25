/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.proto;

import com.google.common.base.Preconditions;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.concurrent.GuardedBy;

/**
 * SoyTypeProvider implementation which handles protocol buffer message types.
 *
 */
public final class SoyProtoTypeProvider implements SoyTypeProvider {

  /**
   * Helper class that assists in the construction of SoyTypeProviders.
   */
  public static final class Builder {
    private final List<ByteSource> descriptorSources = new ArrayList<>();
    private final List<FileDescriptorSet> descriptorSets = new ArrayList<>();
    private final List<GenericDescriptor> descriptors = new ArrayList<>();

    public Builder() {
    }

    /** Read a file descriptor set from a file and register any proto types found within. */
    public Builder addFileDescriptorSetFromFile(File descriptorFile) {
      return addFileDescriptorSetFromByteSource(Files.asByteSource(descriptorFile));
    }

    /**
     * Read a file descriptor set from a byte source and register any proto types found
     * within.
     */
    public Builder addFileDescriptorSetFromByteSource(ByteSource descriptorSource) {
      descriptorSources.add(descriptorSource);
      return this;
    }

    /** Given file descriptor set, register any proto types found within. */
    public Builder addFileDescriptorSet(FileDescriptorSet descriptorSet) {
      descriptorSets.add(descriptorSet);
      return this;
    }

    /** Registers a collection of descriptors of any type. */
    public Builder addDescriptors(Iterable<? extends GenericDescriptor> descriptorsToAdd) {
      for (GenericDescriptor descriptorToAdd : descriptorsToAdd) {
        descriptors.add(descriptorToAdd);
      }
      return this;
    }

    /**
     * Assumes there are no empty descriptor files and descriptor sets which is mostly true
     * in practice.
     */
    public boolean isEmpty() {
      return descriptorSources.isEmpty() && descriptorSets.isEmpty() && descriptors.isEmpty();
    }

    private void walkAll(DescriptorTreeWalker walker)
        throws FileNotFoundException, IOException, DescriptorValidationException {
      for (ByteSource descriptorSource : descriptorSources) {
        walker.walkFileDescriptorSetFromByteSource(descriptorSource);
      }
      for (FileDescriptorSet descriptorSet : descriptorSets) {
        walker.walkFileDescriptorSet(descriptorSet);
      }
      walker.walkGenericDescriptors(descriptors);
    }

    /**
     * Builds the type provider and returns it.
     */
    public SoyProtoTypeProvider build()
        throws FileNotFoundException, IOException, DescriptorValidationException {
      SoyProtoTypeProvider provider = new SoyProtoTypeProvider();

      ExtensionRegistry extensionRegistry = ExtensionRegistry.getEmptyRegistry();
      if (!descriptorSources.isEmpty()) {
        extensionRegistry = ExtensionRegistry.newInstance();
        walkAll(new RegisterExtensionsDescriptorTreeWalker(extensionRegistry));
      }

      DescriptorAddingDescriptorTreeWalker walker
          = new DescriptorAddingDescriptorTreeWalker(extensionRegistry);
      walkAll(walker);
      walker.commitInto(provider);

      return provider;
    }

    /**
     * Like {@link #build}, but doesn't propagate exceptions that can only arise when descriptors
     * need to be fetched from the filesystem.
     */
    public SoyProtoTypeProvider buildNoFiles() {
      Preconditions.checkState(
          descriptorSources.isEmpty(),
          "use build(), not buildNoFiles() to load descriptors from files");
      try {
        return build();
      } catch (DescriptorValidationException | IOException ex) {
        throw new AssertionError("File system should not have been touched", ex);
      }
    }
  }
  private final Object lock = new Object();

  /** Map of all the protobuf type descriptors that we've discovered. */
  // reads can be performed without the lock, but writes need to hold the lock.
  private final ConcurrentMap<String, GenericDescriptor> descriptors =
      new ConcurrentHashMap<>();

  @GuardedBy("lock")
  private final SetMultimap<String, FieldDescriptor> extensions =
      MultimapBuilder.hashKeys()
          // We need a custom comparator since FieldDescriptor doesn't implement equals/hashCode
          // reasonably.  We don't really care about the order, just deduplication.
          .treeSetValues(new Comparator<FieldDescriptor>() {
            @Override public int compare(FieldDescriptor left, FieldDescriptor right) {
              return ComparisonChain.start()
                  .compare(
                      left.getNumber(),
                      right.getNumber())
                  .compare(
                      left.getContainingType().getFullName(),
                      right.getContainingType().getFullName())
                  .result();
            }
          }).build();

  /**
   * Map of SoyTypes that have been created from the type descriptors. Gets filled in
   * lazily as types are requested.
   */
  @GuardedBy("lock")
  private final Map<String, SoyType> typeCache;

  private SoyProtoTypeProvider() {
    Map<String, SoyType> typeCache = new HashMap<>();
    // Seed the type cache with types that are handled specially by SoyProtoValueConverter.
    typeCache.putAll(SafeStringTypes.SAFE_STRING_PROTO_NAME_TO_SANITIZED_TYPE);
    this.typeCache = typeCache;
  }

  public static SoyProtoTypeProvider empty() {
    return new Builder().buildNoFiles();
  }

  @Override
  public SoyType getType(String name, SoyTypeRegistry typeRegistry) {
    GenericDescriptor descriptor = descriptors.get(name);
    if (descriptor == null) {
      return null;
    }
    return doGetType(name, typeRegistry, descriptor);
  }

  /**
   * Internal helper method to construct a type based on a Descriptor.  This is for the Tofu backend
   * which doesn't require all protos to be pre-registered.
   */
  SoyProtoTypeImpl getType(Descriptor descriptor, SoyTypeRegistry registry) {
    String fullName = descriptor.getFullName();
    if (!descriptors.containsKey(fullName)) {
      DescriptorAddingDescriptorTreeWalker walker = new DescriptorAddingDescriptorTreeWalker();
      walker.walkMessageDescriptor(descriptor);
      walker.commitInto(this);
    }
    return (SoyProtoTypeImpl) doGetType(fullName, registry, descriptor);
  }

  private SoyType doGetType(String name, SoyTypeRegistry typeRegistry,
      GenericDescriptor descriptor) {
    SoyType type;
    synchronized (lock) {
      type = typeCache.get(name);
      if (type == null) {
        if (descriptor instanceof EnumDescriptor) {
          type = new SoyProtoEnumTypeImpl((EnumDescriptor) descriptor);
        } else {
          type = new SoyProtoTypeImpl(typeRegistry, (Descriptor) descriptor, extensions.get(name));
        }
        typeCache.put(name, type);
      }
    }
    return type;
  }


  /**
   * A pass over a proto descriptor file set that registers all extensions.
   */
  static final class RegisterExtensionsDescriptorTreeWalker extends DescriptorTreeWalker {

    RegisterExtensionsDescriptorTreeWalker(ExtensionRegistry extensionRegistry) {
      super(extensionRegistry);
    }

    @Override
    void visitFileDescriptor(FileDescriptor fileDescriptor) {
      walkGenericDescriptors(fileDescriptor.getExtensions());
    }

    @Override
    void visitMessageDescriptor(Descriptor descriptor) {
      walkGenericDescriptors(descriptor.getExtensions());
    }

    @Override
    void visitExtensionDescriptor(FieldDescriptor descriptor) {
      extensionRegistry.add(descriptor);
    }
  }


  /** Walks a descriptor tree to build the descriptors, and extensions maps. */
  static final class DescriptorAddingDescriptorTreeWalker extends DescriptorTreeWalker {

    private final Map<String, GenericDescriptor> descriptors = new LinkedHashMap<>();
    private final Multimap<String, FieldDescriptor> extensions = LinkedListMultimap.create();

    DescriptorAddingDescriptorTreeWalker(ExtensionRegistry extensionRegistry) {
      super(extensionRegistry);
    }

    DescriptorAddingDescriptorTreeWalker() {
      this(ExtensionRegistry.getEmptyRegistry());
    }

    void commitInto(SoyProtoTypeProvider provider) {
      synchronized (provider.lock) {
        provider.descriptors.putAll(descriptors);
        provider.extensions.putAll(extensions);
      }
    }

    @Override
    void visitMessageDescriptor(Descriptor descriptor) {
      if (descriptors.containsKey(descriptor.getFullName())) {
        return;
      }
      // Register a message descriptor for a proto type.
      descriptors.put(descriptor.getFullName(), descriptor);
    }

    @Override
    void visitFieldDescriptor(FieldDescriptor fieldDescriptor) {
      if (Protos.shouldJsIgnoreField(fieldDescriptor)) {
        return;
      } else if (fieldDescriptor.getType() == FieldDescriptor.Type.MESSAGE) {
        maybeWalkMessageDescriptor(fieldDescriptor.getMessageType());
      } else if (fieldDescriptor.getType() == FieldDescriptor.Type.ENUM) {
        visitEnumDescriptor(fieldDescriptor.getEnumType());
      }
    }

    @Override
    void visitEnumDescriptor(EnumDescriptor enumDescriptor) {
      descriptors.put(enumDescriptor.getFullName(), enumDescriptor);
    }

    @Override
    void visitExtensionDescriptor(FieldDescriptor extension) {
      if (Protos.shouldJsIgnoreField(extension)) {
        return;
      }
      String containingType = extension.getContainingType().getFullName();
      if (extensions.put(containingType, extension)
          && extension.getType() == FieldDescriptor.Type.MESSAGE) {
        maybeWalkMessageDescriptor(extension.getMessageType());
      }
    }

    private void maybeWalkMessageDescriptor(Descriptor descriptor) {
      if (!descriptors.containsKey(descriptor.getFullName())) {
        walkMessageDescriptor(descriptor);
      }
    }
  }
}
