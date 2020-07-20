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

package com.google.template.soy.logging;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.ExtensionRegistry;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.List;

/**
 * Creates an {@link ExtensionRegistry} for proto parsing VE metadata extensions from the extensions
 * in the given {@link SoyTypeRegistry}.
 *
 * <p>This traverses the descriptors for all protos in the type registry and adds their extensions
 * to the extension registry.
 */
final class VeMetadataExtensionRegistry {

  private final SoyTypeRegistry typeRegistry;

  VeMetadataExtensionRegistry(SoyTypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
  }

  ExtensionRegistry createRegistry() {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    for (FileDescriptor descriptor : typeRegistry.getProtoDescriptors()) {
      addAllExtensions(registry, descriptor.getExtensions());
      visitAllMessages(registry, descriptor.getMessageTypes());
    }
    return registry;
  }

  private static void addAllExtensions(
      ExtensionRegistry registry, List<FieldDescriptor> extensions) {
    for (FieldDescriptor extension : extensions) {
      addExtension(registry, extension);
    }
  }

  private static void addExtension(ExtensionRegistry registry, FieldDescriptor extension) {
    if (extension.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
      registry.add(extension, DynamicMessage.getDefaultInstance(extension.getMessageType()));
    } else {
      registry.add(extension);
    }
  }

  private static void visitAllMessages(ExtensionRegistry registry, List<Descriptor> messages) {
    for (Descriptor message : messages) {
      visitMessage(registry, message);
    }
  }

  private static void visitMessage(ExtensionRegistry registry, Descriptor message) {
    addAllExtensions(registry, message.getExtensions());
    visitAllMessages(registry, message.getNestedTypes());
  }
}
