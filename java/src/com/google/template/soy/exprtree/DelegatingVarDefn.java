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

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/** Delegating implementation of {@link VarDefn}. */
public abstract class DelegatingVarDefn implements VarDefn {
  private final VarDefn delegate;

  protected DelegatingVarDefn(VarDefn delegate) {
    this.delegate = delegate;
  }

  @Override
  public Kind kind() {
    return delegate.kind();
  }

  @Override
  public String name() {
    return delegate.name();
  }

  @Override
  public String refName() {
    return delegate.refName();
  }

  @Override
  @Nullable
  public SourceLocation nameLocation() {
    return delegate.nameLocation();
  }

  @Override
  public SoyType type() {
    return delegate.type();
  }

  @Override
  public boolean hasType() {
    return delegate.hasType();
  }

  @Override
  public boolean isInjected() {
    return delegate.isInjected();
  }
}
