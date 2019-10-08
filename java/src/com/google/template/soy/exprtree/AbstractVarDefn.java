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

package com.google.template.soy.exprtree;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * Implementation of common features of {@link VarDefn}.
 *
 */
public abstract class AbstractVarDefn implements VarDefn {

  /** The name of the value. */
  private final String name;

  @Nullable private final SourceLocation nameLocation;

  /** The data type of the value. */
  @Nullable protected SoyType type;

  /**
   * @param name The name of the value.
   * @param type The data type of the value.
   */
  public AbstractVarDefn(
      String name, @Nullable SourceLocation nameLocation, @Nullable SoyType type) {
    this.name = checkNotNull(name);
    this.nameLocation = nameLocation;
    this.type = type;
  }

  protected AbstractVarDefn(AbstractVarDefn var) {
    this.name = var.name;
    this.nameLocation = var.nameLocation;
    this.type = var.type;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public SourceLocation nameLocation() {
    return nameLocation;
  }

  @Override
  public SoyType type() {
    checkState(type != null, "type of %s is null @%s", name(), nameLocation());
    return type;
  }

  public boolean hasType() {
    return type != null;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{name = " + name() + "}";
  }

  // enforce identity semantics

  @Override
  public final boolean equals(Object obj) {
    return super.equals(obj);
  }

  @Override
  public final int hashCode() {
    return super.hashCode();
  }
}
