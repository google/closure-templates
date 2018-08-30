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

package com.google.template.soy.soytree.defn;

import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.UnknownType;

/**
 * An injected parameter.
 *
 */
public final class InjectedParam extends AbstractVarDefn {

  /** @param name The variable name. */
  public InjectedParam(String name, SourceLocation nameLocation) {
    this(name, nameLocation, UnknownType.getInstance());
  }

  public InjectedParam(String name, SourceLocation nameLocation, SoyType type) {
    super(name, nameLocation, type);
  }

  @Override
  public Kind kind() {
    return Kind.IJ_PARAM;
  }

  @Override
  public int localVariableIndex() {
    return -1;
  }

  @Override
  public void setLocalVariableIndex(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInjected() {
    return true;
  }
}
