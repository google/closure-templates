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

import com.google.common.base.Preconditions;
import com.google.template.soy.soytree.SoyTypeP;
import java.util.Objects;

/** Represents the type of a signal, a wrapper around a primitive value. */
public final class SignalType extends SoyType {

  public static SignalType of(SoyType dataType) {
    return new SignalType(dataType);
  }

  private final SoyType dataType;

  private SignalType(SoyType dataType) {
    this.dataType = Preconditions.checkNotNull(dataType);
  }

  @Override
  public Kind getKind() {
    return Kind.SIGNAL;
  }

  public SoyType getDataType() {
    return dataType;
  }

  @Override
  boolean doIsAssignableFromNonUnionType(SoyType srcType, UnknownAssignmentPolicy policy) {
    return srcType.getKind() == Kind.SIGNAL
        && dataType.isAssignableFromInternal(((SignalType) srcType).dataType, policy);
  }

  @Override
  public String toString() {
    return "Signal<" + dataType + ">";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setSignal(dataType.toProto());
  }

  @Override
  public <T> T accept(SoyTypeVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public final boolean equals(Object other) {
    return other != null
        && this.getClass() == other.getClass()
        && Objects.equals(((SignalType) other).dataType, dataType);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(this.getClass(), dataType);
  }
}
