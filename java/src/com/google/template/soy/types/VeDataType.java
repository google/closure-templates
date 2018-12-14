/*
 * Copyright 2018 Google Inc.
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
package com.google.template.soy.types;

import com.google.template.soy.soytree.SoyTypeP;

/** Soy's ve_data type, for holding a ve and its associated data. */
public final class VeDataType extends PrimitiveType {

  private static final VeDataType INSTANCE = new VeDataType();

  public static VeDataType getInstance() {
    return INSTANCE;
  }

  private VeDataType() {}

  @Override
  public Kind getKind() {
    return Kind.VE_DATA;
  }

  @Override
  public String toString() {
    return "ve_data";
  }

  @Override
  void doToProto(SoyTypeP.Builder builder) {
    builder.setPrimitive(SoyTypeP.PrimitiveTypeP.VE_DATA);
  }
}
