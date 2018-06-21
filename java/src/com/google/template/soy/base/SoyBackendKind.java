/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.base;

/**
 * Enumeration which represents which Soy backend we're currently using. This is used for
 * non-backend components that need to modify their output in ways that are specific to a given
 * backend.
 */
public enum SoyBackendKind {
  TOFU,
  JBC_SRC,
  JS_SRC,
  PYTHON_SRC
}
