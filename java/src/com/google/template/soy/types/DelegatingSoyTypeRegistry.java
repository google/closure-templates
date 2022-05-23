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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.GenericDescriptor;
import com.google.template.soy.base.internal.Identifier;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Implementation of {@link SoyTypeRegistry} that delegates all calls to another instance of {@link
 * SoyTypeRegistry}. Used for building chains of registries.
 */
public abstract class DelegatingSoyTypeRegistry implements SoyTypeRegistry {

  private final SoyTypeRegistry delegate;

  protected DelegatingSoyTypeRegistry(SoyTypeRegistry delegate) {
    this.delegate = delegate;
  }

  protected SoyTypeRegistry getDelegate() {
    return delegate;
  }

  @Override
  public ImmutableSet<FileDescriptor> getProtoDescriptors() {
    return delegate.getProtoDescriptors();
  }

  @Override
  public Identifier resolve(Identifier id) {
    return delegate.resolve(id);
  }

  @Override
  public ProtoTypeRegistry getProtoRegistry() {
    return delegate.getProtoRegistry();
  }

  @Override
  public <T extends SoyType> T intern(T type) {
    return delegate.intern(type);
  }

  @Override
  public SoyProtoType getOrComputeProtoType(
      Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper) {
    return delegate.getOrComputeProtoType(descriptor, mapper);
  }

  @Override
  public SoyType getOrCreateElementType(String tagName) {
    return delegate.getOrCreateElementType(tagName);
  }

  @Override
  public ImportType getProtoImportType(GenericDescriptor descriptor) {
    return delegate.getProtoImportType(descriptor);
  }

  @Override
  public SoyType getProtoImportType(FileDescriptor descriptor, String member) {
    return delegate.getProtoImportType(descriptor, member);
  }

  @Override
  public SoyType getProtoImportType(Descriptor descriptor, String member) {
    return delegate.getProtoImportType(descriptor, member);
  }

  @Override
  @Nullable
  public SoyType getType(String typeName) {
    return delegate.getType(typeName);
  }

  @Override
  public Iterable<String> getAllSortedTypeNames() {
    return delegate.getAllSortedTypeNames();
  }
}
