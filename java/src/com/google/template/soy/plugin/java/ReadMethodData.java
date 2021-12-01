/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.plugin.java;

import com.google.auto.value.AutoValue;

/** Information about a method that was read. */
@AutoValue
public abstract class ReadMethodData {

  /** True if both the method and the containing class are public. */
  public abstract boolean isPublic();

  /** True if the method is an instance method, false if it's static. */
  public abstract boolean instanceMethod();

  /** True if the declaring class is an interface, false if it's not. */
  public abstract boolean classIsInterface();

  /** The return type of the method. */
  public abstract String returnType();

  public static ReadMethodData create(
      boolean isPublic, boolean instance, boolean classIsInterface, String returnType) {
    return new AutoValue_ReadMethodData(isPublic, instance, classIsInterface, returnType);
  }
}
