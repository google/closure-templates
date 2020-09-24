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

import com.google.common.collect.ImmutableMap;

/**
 * Represents a Soy RECORD type. Is handled with special logic in {@link
 * com.google.template.soy.invocationbuilders.passes.GenInvocationBuildersVisitor}.
 */
public class RecordJavaType extends JavaType {

  private final ImmutableMap<String, JavaType> javaTypeMap;
  private final boolean isList;

  public RecordJavaType(ImmutableMap<String, JavaType> javaTypeMap, boolean isList) {
    super(false);
    this.javaTypeMap = javaTypeMap;
    this.isList = isList;
  }

  public ImmutableMap<String, JavaType> getJavaTypeMap() {
    return javaTypeMap;
  }

  public boolean isList() {
    return isList;
  }

  @Override
  public String toJavaTypeString() {
    // Invocation builder code special cases this type and avoids calling this method altogether.
    return "java.util.Map<String, ?>";
  }

  @Override
  public JavaType asNullable() {
    return this;
  }

  @Override
  String asGenericsTypeArgumentString() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isGenericsTypeSupported() {
    return false;
  }

  @Override
  public boolean isTypeLiteralSupported() {
    return false;
  }

  @Override
  public String asInlineCast(String variable, int depth) {
    return CodeGenUtils.AS_RECORD + "(" + variable + ")";
  }
}
