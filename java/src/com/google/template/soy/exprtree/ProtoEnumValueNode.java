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

package com.google.template.soy.exprtree;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.template.soy.base.internal.Identifier;
import com.google.template.soy.basetree.CopyState;
import com.google.template.soy.types.SoyProtoEnumType;

/** Node representing a proto enum value. */
public final class ProtoEnumValueNode extends AbstractPrimitiveNode {

  private final Identifier id;
  private final SoyProtoEnumType type;
  private final int enumNumber;

  public ProtoEnumValueNode(Identifier id, SoyProtoEnumType type, int enumNumber) {
    super(id.location());
    this.id = id;
    this.type = type;
    this.enumNumber = enumNumber;
  }

  private ProtoEnumValueNode(ProtoEnumValueNode orig, CopyState copyState) {
    super(orig, copyState);
    this.id = orig.id;
    this.type = orig.type;
    this.enumNumber = orig.enumNumber;
  }

  public Identifier getIdentifier() {
    return id;
  }

  @Override
  public Kind getKind() {
    return Kind.PROTO_ENUM_VALUE_NODE;
  }

  @Override
  public SoyProtoEnumType getType() {
    return type;
  }

  /** Returns the Soy integer value. */
  public long getValue() {
    return enumNumber;
  }

  public EnumValueDescriptor getEnumValueDescriptor() {
    return type.getDescriptor().findValueByNumber(enumNumber);
  }

  @Override
  public String toSourceString() {
    return id.identifier();
  }

  @Override
  public ProtoEnumValueNode copy(CopyState copyState) {
    return new ProtoEnumValueNode(this, copyState);
  }
}
