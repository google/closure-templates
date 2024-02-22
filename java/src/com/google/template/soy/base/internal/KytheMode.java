/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.base.internal;

/** Compiler flag --kythe_mode. */
public enum KytheMode {
  /** Output no Kythe metadata. */
  DISABLED,
  /** Output the standard base64 encoded proto. */
  BASE64,
  /** Output human "readable" text format of proto, for tests. */
  TEXT;

  public boolean isEnabled() {
    return this != DISABLED;
  }
}
