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

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import java.util.Collection;
import java.util.function.Function;

/**
 * Factory of {@link SoyType} instances allowing use of the == operator when comparing instances.
 */
public interface TypeInterner {

  ListType getOrCreateListType(SoyType elementType);

  LegacyObjectMapType getOrCreateLegacyObjectMapType(SoyType keyType, SoyType valueType);

  MapType getOrCreateMapType(SoyType keyType, SoyType valueType);

  SoyType getOrCreateUnionType(Collection<SoyType> members);

  SoyType getOrCreateUnionType(SoyType... members);

  RecordType getOrCreateRecordType(Iterable<RecordType.Member> members);

  TemplateType internTemplateType(TemplateType typeToIntern);

  VeType getOrCreateVeType(String dataType);

  SoyProtoType getOrComputeProtoType(
      Descriptor descriptor, Function<? super String, ? extends SoyProtoType> mapper);

  SoyProtoEnumType getOrCreateProtoEnumType(EnumDescriptor descriptor);
}
