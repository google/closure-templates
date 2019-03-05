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

package com.google.template.soy.data;


/** Holds flags controlling runtime behavior. */
public final class Flags {
  private static final boolean STRING_IS_NOT_SANITIZED_CONTENT =
      Boolean.getBoolean("soy_sanitized_content_is_not_string");

  private static final boolean ALLOW_SOY_PROTO_REFLECTION =
      Boolean.getBoolean("ALLOW_SOY_PROTO_REFLECTION");

  /**
   * Disables a mode where string == SanitizedContent for Java runtime. This is for compatibility
   * while we fix the depot.
   */
  public static boolean stringIsNotSanitizedContent() {
    boolean retVal = STRING_IS_NOT_SANITIZED_CONTENT;
    return retVal;
  }

  static boolean allowReflectiveProtoAccess() {
    boolean allowReflection = ALLOW_SOY_PROTO_REFLECTION;
    return allowReflection;
  }

  private Flags() {}
}
