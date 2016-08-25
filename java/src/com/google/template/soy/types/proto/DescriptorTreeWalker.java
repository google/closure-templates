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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.protobuf.ExtensionRegistry;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Recursively walks descriptors to provide access to message descriptors, fields, etc.
 * <p>
 * This class is comprised of a series of abstract visit* methods that can be overridden to take
 * actions on nodes.
 * <p>
 * To start walk, call one of the final walk* methods which will call the corresponding visit*
 * method before recursing to any children.
 */
abstract class DescriptorTreeWalker {

  protected final ExtensionRegistry extensionRegistry;

  DescriptorTreeWalker(ExtensionRegistry extensionRegistry) {
    this.extensionRegistry = extensionRegistry;
  }

  /**
   * Called for each file set source.  Unless overridden, does nothing.
   */
  void visitFileDescriptorSetFromByteSource(ByteSource source) {
    // no-op
  }

  /**
   * Called for each file descriptor set.  Unless overridden, does nothing.
   */
  void visitFileDescriptorSet(FileDescriptorSet fileDescriptorSet) {
    // no-op
  }

  /**
   * Called for each file descriptor.  Unless overridden, does nothing.
   */
  void visitFileDescriptor(FileDescriptor fileDescriptor) {
    // no-op
  }

  /**
   * Called for each scope that has a set of generic descriptor definitions.
   * Unless overridden, does nothing.
   */
  void visitGenericDescriptors(
      ImmutableList<GenericDescriptor> descriptors) {
    // no-op
  }

  /**
   * Called for each message descriptor.  Unless overridden, does nothing.
   */
  void visitMessageDescriptor(Descriptor descriptor) {
    // no-op
  }

  /**
   * Called for each field descriptor that is not an {@link #visitExtensionDescriptor extension}.
   * Unless overridden, does nothing.
   */
  void visitFieldDescriptor(FieldDescriptor descriptor) {
    // no-op
  }

  /**
   * Called for each enum descriptor.  Unless overridden, does nothing.
   */
  void visitEnumDescriptor(EnumDescriptor descriptor) {
    // no-op
  }

  /**
   * Called for each extension descriptor.  Unless overridden, does nothing.
   */
  void visitExtensionDescriptor(FieldDescriptor descriptor) {
    // no-op
  }

  /** Read a file descriptor set and walk any proto types found within. */
  final void walkFileDescriptorSetFromByteSource(ByteSource source)
      throws FileNotFoundException, IOException, DescriptorValidationException {
    visitFileDescriptorSetFromByteSource(source);

    try (InputStream inputStream = source.openStream()) {
      walkFileDescriptorSet(
          FileDescriptorSet.parseFrom(inputStream, extensionRegistry));
    }
  }

  /** Given file descriptor set, register any proto types found within. */
  final void walkFileDescriptorSet(FileDescriptorSet descriptorSet)
      throws DescriptorValidationException {
    visitFileDescriptorSet(descriptorSet);

    Map<String, FileDescriptor> fileDescriptors = Maps.newLinkedHashMap();
    for (FileDescriptorProto fileDescriptorProto : descriptorSet.getFileList()) {
      // Look up the imported files from previous file descriptors.
      // It is sufficient to look at only previous file descriptors because
      // CodeGeneratorRequest guarantees that the files are sorted in topological order.
      FileDescriptor[] deps = new FileDescriptor[fileDescriptorProto.getDependencyCount()];
      for (int i = 0; i < fileDescriptorProto.getDependencyCount(); i++) {
        String name = fileDescriptorProto.getDependency(i);
        deps[i] = Preconditions.checkNotNull(fileDescriptors.get(name),
            "Missing file descriptor for [%s]", name);
      }

      // Populate the typeMap with types derived from the proto message descriptors.
      FileDescriptor fileDescriptor = FileDescriptor.buildFrom(fileDescriptorProto, deps);
      fileDescriptors.put(fileDescriptor.getName(), fileDescriptor);
    }
    for (FileDescriptor fileDescriptor : fileDescriptors.values()) {
      walkFileDescriptor(fileDescriptor);
    }
  }

  final void walkFileDescriptor(final FileDescriptor fileDescriptor) {
    visitFileDescriptor(fileDescriptor);

    ImmutableList<GenericDescriptor> descriptors =
        ImmutableList.<GenericDescriptor>builder()
        .addAll(fileDescriptor.getMessageTypes())
        .addAll(fileDescriptor.getExtensions())
        .addAll(fileDescriptor.getEnumTypes())
        .build();
    walkGenericDescriptors(descriptors);
  }

  final void walkGenericDescriptors(Iterable<? extends GenericDescriptor> descriptors) {
    final ImmutableList<GenericDescriptor> descriptorList =
        ImmutableList.<GenericDescriptor>copyOf(descriptors);
    visitGenericDescriptors(descriptorList);

    for (GenericDescriptor descriptor : descriptorList) {
      walkGenericDescriptor(descriptor);
    }
  }

  final void walkGenericDescriptor(GenericDescriptor descriptor) {
    if (descriptor instanceof Descriptor) {
      walkMessageDescriptor((Descriptor) descriptor);
    } else if (descriptor instanceof FieldDescriptor) {
      FieldDescriptor fieldDescriptor = (FieldDescriptor) descriptor;
      if (fieldDescriptor.isExtension()) {
        visitExtensionDescriptor(fieldDescriptor);
      } else {
        visitFieldDescriptor(fieldDescriptor);
      }
    } else if (descriptor instanceof EnumDescriptor) {
      visitEnumDescriptor((EnumDescriptor) descriptor);
    }  // services, etc. not needed thus far so neither gathered nor dispatched
  }

  final void walkMessageDescriptor(final Descriptor descriptor) {
    visitMessageDescriptor(descriptor);

    walkGenericDescriptors(
        ImmutableList.<GenericDescriptor>builder()
        .addAll(descriptor.getNestedTypes())
        .addAll(descriptor.getExtensions())
        .addAll(descriptor.getEnumTypes())
        .addAll(descriptor.getFields())
        .build());
  }
}
