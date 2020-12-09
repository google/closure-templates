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
import com.google.protobuf.Descriptors.Descriptor;

/** Representing an imported proto message type. */
@AutoValue
public abstract class ProtoImportType extends ImportType {

  public static ProtoImportType create(Descriptor descriptor) {
    return new AutoValue_ProtoImportType(descriptor);
  }

  public abstract Descriptor getDescriptor();

  @Override
  public final String toString() {
    return getDescriptor().getFullName();
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_TYPE;
  }
}
