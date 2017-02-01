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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import java.util.HashSet;
import java.util.Set;

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

  private final Set<String> visited = new HashSet<>();

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

  private final void walkFileDescriptor(final FileDescriptor fileDescriptor) {
    walkDescriptors(fileDescriptor.getMessageTypes());
    walkDescriptors(fileDescriptor.getExtensions());
    walkDescriptors(fileDescriptor.getEnumTypes());
  }

  final void walkDescriptors(Iterable<? extends GenericDescriptor> descriptors) {
    for (GenericDescriptor descriptor : descriptors) {
      walkDescriptor(descriptor);
    }
  }

  final void walkDescriptor(GenericDescriptor descriptor) {
    if (!visited.add(descriptor.getFullName())) {
      // skip if we have already seen this descriptor
      return;
    }
    if (descriptor instanceof FileDescriptor) {
      walkFileDescriptor((FileDescriptor) descriptor);
    } else if (descriptor instanceof Descriptor) {
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

  private final void walkMessageDescriptor(final Descriptor descriptor) {
    visitMessageDescriptor(descriptor);
    walkDescriptors(descriptor.getNestedTypes());
    walkDescriptors(descriptor.getExtensions());
    walkDescriptors(descriptor.getEnumTypes());
    walkDescriptors(descriptor.getFields());
  }
}
