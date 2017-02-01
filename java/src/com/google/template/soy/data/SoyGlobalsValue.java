/*
 * Copyright 2010 Google Inc.
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

/**
 * Interface intended mainly to be implemented by enums, so that during the Soy Globals' generation
 * the returned value should be some secondary value different from the enum's primary(ordinal)
 * value.
 *
 * <p>NOTE: In case the enum's secondary value should never be changed once it is set, ensure to add
 * tests/add documentation on every use of this interface for enums.
 */
public interface SoyGlobalsValue {

  /**
   * Returns the Soy Value to Soy Globals. While exporting the value to SoyGlobals the object's
   * toString() value is returned.
   */
  Object getSoyGlobalValue();
}
