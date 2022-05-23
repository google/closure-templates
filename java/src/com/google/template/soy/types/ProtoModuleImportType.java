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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.template.soy.internal.proto.Field;

/** Representing an imported proto module/file type, i.e. "import * as p from 'p.proto'; */
@AutoValue
public abstract class ProtoModuleImportType extends ImportType {

  public static ProtoModuleImportType create(FileDescriptor descriptor) {
    return new AutoValue_ProtoModuleImportType(descriptor);
  }

  public abstract FileDescriptor getDescriptor();

  @Override
  public final String toString() {
    return getDescriptor().getFullName();
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_MODULE;
  }

  @Override
  public ImmutableCollection<String> getNestedSymbolNames() {
    ImmutableSet.Builder<String> allNames = ImmutableSet.builder();
    getDescriptor().getMessageTypes().forEach(t -> allNames.add(t.getName()));
    getDescriptor().getEnumTypes().forEach(t -> allNames.add(t.getName()));
    getDescriptor().getExtensions().forEach(t -> allNames.add(Field.computeSoyName(t)));
    return allNames.build();
  }
}
