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

package com.google.template.soy.shared.restricted;

import com.google.common.collect.ImmutableList;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.types.SoyType;

/** A plugin-provided {@link SoyMethod}, implemented with a {@link SoySourceFunction}. */
public final class SoySourceFunctionMethod implements SoyMethod {

  private final SoySourceFunction impl;
  private final SoyType baseType;
  private final SoyType returnType;
  private final ImmutableList<SoyType> paramTypes;
  private final String methodName;

  public SoySourceFunctionMethod(
      SoySourceFunction impl,
      SoyType baseType,
      SoyType returnType,
      ImmutableList<SoyType> paramTypes,
      String methodName) {
    this.impl = impl;
    this.baseType = baseType;
    this.returnType = returnType;
    this.paramTypes = paramTypes;
    this.methodName = methodName;
  }

  @Override
  public int getNumArgs() {
    return paramTypes.size();
  }

  @Override
  public boolean acceptsArgCount(int count) {
    return paramTypes.size() == count;
  }

  public boolean acceptsVarArgs() {
    if (paramTypes.get(paramTypes.size() - 1).getKind() == SoyType.Kind.LIST) {
      return true;
    }
    return false;
  }

  public SoySourceFunction getImpl() {
    return impl;
  }

  public SoyType getBaseType() {
    return baseType;
  }

  public SoyType getReturnType() {
    return returnType;
  }

  public ImmutableList<SoyType> getParamTypes() {
    return paramTypes;
  }

  public boolean appliesToBase(SoyType baseType) {
    return this.baseType.isAssignableFromStrict(baseType);
  }

  public String getMethodName() {
    return methodName;
  }
}
