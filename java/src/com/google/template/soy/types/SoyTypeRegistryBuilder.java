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

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.internal.proto.ProtoUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Helper class that assists in the construction of SoyTypeProviders. */
public final class SoyTypeRegistryBuilder {
  // use a linked hash map.  The descriptors will tend to be in dependency order, so by
  // constructing in the provided order we will limit the depth of the recusion below.
  private final Map<String, FileDescriptorProto> nameToProtos = new LinkedHashMap<>();
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

  public static SoyTypeRegistry create() {
    return new SoyTypeRegistry(new SoyTypeRegistryBuilder());
  }

  /**
   * Read a file descriptor set from a file and register any proto types found within.
   *
   * @deprecated Pass Descriptor objects to {@link #addDescriptors} instead.
   */
  @Deprecated
  public SoyTypeRegistryBuilder addFileDescriptorSetFromFile(File descriptorFile)
      throws IOException {
    // TODO(lukes): if we called buildDescriptors here we could force callers to pass files in
    // dependency order (and also throw DescriptorValidationException here).  This would improve
    // performance (slightly, due to less recursion and no need for the nameToProtos map), but
    // more importantly it would improve error locality.
    try (InputStream inputStream = new BufferedInputStream(new FileInputStream(descriptorFile))) {
      for (FileDescriptorProto file :
          FileDescriptorSet.parseFrom(inputStream, ProtoUtils.REGISTRY).getFileList()) {
        nameToProtos.put(file.getName(), file);
      }
    }
    return this;
  }

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

  void accept(DescriptorVisitor visitor) throws DescriptorValidationException {
    Map<String, FileDescriptor> parsedDescriptors = new HashMap<>();
    for (String name : nameToProtos.keySet()) {
      visitor.visitFile(
          buildDescriptor(null, name, parsedDescriptors, nameToProtos),
          /*onlyVisitingFiles=*/ areAllDescriptorsFileDescriptors);
    }
    for (GenericDescriptor descriptor : descriptors) {
      visitor.visitGeneric(descriptor, /*onlyVisitingFiles=*/ areAllDescriptorsFileDescriptors);
    }
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

  /** Walks a descriptor tree to build the descriptors, and extensions maps. */
  static final class DescriptorVisitor {
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
      final int size = descriptors.size();
      for (int i = 0; i < size; i++) {
        visitFile(descriptors.get(i), onlyVisitingFiles);
      }
    }

    void visitFile(FileDescriptor fileDescriptor, boolean onlyVisitingFiles) {
      if (!shouldVisitDescriptor(fileDescriptor, onlyVisitingFiles)) {
        return;
      }
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
      final int size = descriptors.size();
      for (int i = 0; i < size; i++) {
        visitMessage(descriptors.get(i), exploreDependencies, onlyVisitingFiles);
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
      final int size = enumDescriptors.size();
      for (int i = 0; i < size; i++) {
        visitEnum(enumDescriptors.get(i), onlyVisitingFiles);
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
      final int size = fieldDescriptors.size();
      for (int i = 0; i < size; i++) {
        visitField(fieldDescriptors.get(i), exploreDependencies, onlyVisitingFiles);
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
      // if we are only visiting files, then we don't need to check the visited hash set unless this
      // descriptor is a file, this is because the traversal strategy (where we disable
      // 'exploreDependencies') means that we are guaranteed to visit each descriptor exactly once.
      // So checking the visited set is redundant.
      if (onlyVisitingFiles && !(descriptor instanceof FileDescriptor)) {
        return true;
      }
      return visited.add(descriptor.getFullName());
    }
  }
}
