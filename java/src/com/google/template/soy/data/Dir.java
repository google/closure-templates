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

package com.google.template.soy.data;

/** Enum for data directionality. */
public enum Dir {
  /** Left-to-right. */
  LTR(1),

  /** Right-to-left. */
  RTL(-1),

  /** Neither left-to-right nor right-to-left. */
  NEUTRAL(0);

  public final int ord;

  Dir(int ord) {
    this.ord = ord;
  }
}
