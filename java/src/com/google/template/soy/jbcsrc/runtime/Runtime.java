/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jbcsrc.runtime;

/**
 * Runtime utilities uniquely for the {@code jbcsrc} backend.
 */
public final class Runtime {
  public static RuntimeException missingRequiredParameter(String paramName) {
    return new RuntimeException("parameter '$" + paramName + "' is undefined");
  }

  public static AssertionError unexpectedStateError(int state) {
    return new AssertionError("Unexpected state requested: " + state);
  }
}
