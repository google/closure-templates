/*
 * Copyright 2014 Google Inc.
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

package com.google.template.soy.types.proto;

import com.google.template.soy.types.SoyType;

/**
 * A {@link SoyType} subinterface which describes a protocol buffer type.
 *
 */
public interface SoyProtoType {
  /**
   * For ParseInfo generation, return a string that represents the Java
   * source expression for the static descriptor constant.
   * @return The Java source expression for this type's descriptor.
   */
  String getDescriptorExpression();
}
