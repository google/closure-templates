/*
 * Copyright 2026 Google Inc.
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
import com.google.common.base.CaseFormat;
import com.google.protobuf.Descriptors.OneofDescriptor;

/** Representing an imported synthesized proto oneof case enum type. */
@AutoValue
public abstract class ProtoOneofCaseImportType extends ImportType {

  public static ProtoOneofCaseImportType create(OneofDescriptor descriptor) {
    return new AutoValue_ProtoOneofCaseImportType(descriptor);
  }

  public abstract OneofDescriptor getDescriptor();

  @Override
  public final String toString() {
    return getDescriptor().getContainingType().getFullName()
        + "."
        + CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, getDescriptor().getName())
        + "Case";
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_ENUM_TYPE;
  }
}
