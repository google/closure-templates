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

package com.google.template.soy.passes;

import com.google.template.soy.shared.restricted.SoyMethod;
import com.google.template.soy.types.FunctionType;
import com.google.template.soy.types.SoyType;
import javax.annotation.Nullable;

/**
 * A specialized {@link SoyMethod} used during method resolution to represent invoking a
 * function-typed property on a record.
 *
 * <p>This method does not leak to backend code generation; it is used solely within {@link
 * ResolveExpressionTypesPass} to route method calls to function invocations.
 */
final class FunctionPropertyMethod implements SoyMethod {

  @Nullable final FunctionType functionType;

  FunctionPropertyMethod(@Nullable SoyType type) {
    this.functionType = type instanceof FunctionType fnType ? fnType : null;
  }

  @Override
  public int getNumArgs() {
    return functionType == null ? -1 : functionType.getParameters().size();
  }

  @Override
  public boolean acceptsArgCount(int count) {
    if (functionType == null) {
      return true;
    }
    return functionType.isVarArgs() ? count >= getNumArgs() - 1 : count == getNumArgs();
  }
}
