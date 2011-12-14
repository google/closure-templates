/*
 * Copyright 2008 Google Inc.
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
 * A generator of int ids. Implementations can generate fixed ids, unique ids, or anything else.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public interface IdGenerator {

  /**
   * Generates and returns an id.
   * @return The generated id.
   */
  public int genId();

  /**
   * Clones this id generator, such that the original and new generators will generate the same list
   * of ids going forward.
   * @return A clone of this id generator.
   */
  public IdGenerator clone();

}
