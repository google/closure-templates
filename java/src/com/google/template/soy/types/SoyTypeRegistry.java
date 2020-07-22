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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.template.soy.base.internal.Identifier;
import javax.annotation.Nullable;

/** Registry of types which can be looked up by name. */
public interface SoyTypeRegistry extends TypeRegistry, TypeInterner {

  /** A type registry that defaults all unknown types to the 'unknown' type. */
  SoyTypeRegistry DEFAULT_UNKNOWN =
      new DelegatingSoyTypeRegistry(SoyTypeRegistryBuilder.create()) {
        @Nullable
        @Override
        public SoyType getType(String typeName) {
          SoyType type = super.getType(typeName);
          return type != null ? type : UnknownType.getInstance();
        }

        @Override
        public ProtoTypeRegistry getProtoRegistry() {
          return (fqn) -> UnknownType.getInstance();
        }
      };

  /** Returns the list of proto file descriptors with which this registry was created. */
  default ImmutableSet<FileDescriptor> getProtoDescriptors() {
    return ImmutableSet.of();
  }

  default Identifier resolve(Identifier id) {
    return id;
  }

  default ProtoTypeRegistry getProtoRegistry() {
    return (fqn) -> null;
  }
}
