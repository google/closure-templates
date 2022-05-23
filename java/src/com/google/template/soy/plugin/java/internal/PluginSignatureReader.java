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

package com.google.template.soy.plugin.java.internal;

import com.google.template.soy.plugin.java.ReadMethodData;
import com.google.template.soy.plugin.java.restricted.MethodSignature;
import javax.annotation.Nullable;

/** Returns info about plugin runtime method signatures. */
interface PluginSignatureReader {

  /**
   * Checks to see if a method runtime with the given name & arguments exists, returning info about
   * it if so. Callers should explicitly validate the return type matches their expected type.
   * Returns null if no method matches the expected signature.
   */
  @Nullable
  ReadMethodData findMethod(MethodSignature methodSignature);

}
