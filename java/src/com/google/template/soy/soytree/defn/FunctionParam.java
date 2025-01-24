/*
 * Copyright 2025 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.template.soy.exprtree.AbstractVarDefn;
import com.google.template.soy.types.SoyType;

/** A declared function parameter. */
public class FunctionParam extends AbstractVarDefn {

  public FunctionParam(String name, SoyType type) {
    super(name, null, Preconditions.checkNotNull(type));
  }

  @Override
  public String refName() {
    return "$" + name();
  }

  @Override
  public Kind kind() {
    return Kind.FUNCTION_PARAM;
  }

  @Override
  public boolean isInjected() {
    return false;
  }
}
