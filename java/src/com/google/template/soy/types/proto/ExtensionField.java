/*
 * Copyright 2016 Google Inc.
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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.template.soy.types.SoyTypeRegistry;

/** An extension field for a proto type. */
final class ExtensionField extends NormalField {

  ExtensionField(SoyTypeRegistry typeRegistry, FieldDescriptor desc) {
    super(typeRegistry, desc);
  }
}
