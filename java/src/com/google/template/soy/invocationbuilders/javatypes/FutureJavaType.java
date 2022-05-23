/*
 * Copyright 2019 Google Inc.
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
package com.google.template.soy.invocationbuilders.javatypes;

import static com.google.common.base.Preconditions.checkArgument;

/** Represents a Future<T> type for generated Soy Java invocation builders. */
public final class FutureJavaType extends JavaType {

  private static final CodeGenUtils.Member AS_FUTURE = CodeGenUtils.castFunction("asFuture");
  private final JavaType type;

  public FutureJavaType(JavaType type) {
    super(/* isNullable= */ false);
    checkArgument(type.isGenericsTypeSupported());
    this.type = type;
  }

  @Override
  public String toJavaTypeString() {
    return "java.util.concurrent.Future<" + type.asGenericsTypeArgumentString() + ">";
  }

  @Override
  String asGenericsTypeArgumentString() {
    return null;
  }

  public JavaType getType() {
    return type;
  }

  @Override
  public String asInlineCast(String variableName, int depth) {
    return AS_FUTURE + "(" + variableName + ", " + type.getAsInlineCastFunction(depth) + ")";
  }

  @Override
  public JavaType asNullable() {
    // TODO(lukes): throw UnsupportedOperationExceptiopn?
    return this;
  }
}
