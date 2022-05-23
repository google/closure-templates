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

import com.google.template.soy.soytree.SoyTypeP;

/**
 * A type that is a super type of all protos.
 *
 * <p>This is useful mostly for soy plugins that can operate on all protos.
 */
public final class MessageType extends SoyType {
  private static final MessageType INSTANCE = new MessageType();

  public static MessageType getInstance() {
    return INSTANCE;
  }

  private MessageType() {}

  @Override
  public Kind getKind() {
    return Kind.MESSAGE;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType fromType) {
    SoyType.Kind kind = fromType.getKind();
    return kind == SoyType.Kind.MESSAGE || kind == SoyType.Kind.PROTO;
  }

  @Override
  public String toString() {
    return "Message";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setMessage(true);
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
