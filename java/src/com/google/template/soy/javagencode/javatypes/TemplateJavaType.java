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

package com.google.template.soy.javagencode.javatypes;

/** Represents a template type. */
public final class TemplateJavaType extends JavaType {

  private static final CodeGenUtils.Member AS_TEMPLATE_VALUE =
      CodeGenUtils.castFunction("asTemplateValue");

  public TemplateJavaType() {
    this(/* isNullable= */ false);
  }

  public TemplateJavaType(boolean isNullable) {
    super(isNullable);
  }

  @Override
  public String toJavaTypeString() {
    return "com.google.template.soy.data.SoyTemplate";
  }

  @Override
  public JavaType asNullable() {
    return new TemplateJavaType(/* isNullable= */ true);
  }

  @Override
  String asGenericsTypeArgumentString() {
    return toJavaTypeString();
  }

  @Override
  public String asInlineCast(String variable, int depth) {
    return AS_TEMPLATE_VALUE + "(" + variable + ")";
  }
}
