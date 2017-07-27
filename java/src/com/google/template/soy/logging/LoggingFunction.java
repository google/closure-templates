/*
 * Copyright 2017 Google Inc.
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
package com.google.template.soy.logging;

import com.google.template.soy.shared.restricted.SoyFunction;

/**
 * A LoggingFunction is a SoyFunction that is meant to interact with the logging subsystem.
 *
 * <p>Unlike a {@link SoyJsSrcFunction} or a {@link SoyJavaFunction} the author of a LoggingFunction
 * does not implement the function logic here, but instead it will be implemented by a user supplied
 * logging plugin at render time.
 */
public interface LoggingFunction extends SoyFunction {
  /**
   * A static placeholder that can be used in place of the real implementation in the logging
   * plugin. This can happen in several cases:
   *
   * <ul>
   *   <li>The function invocation is used in the context of a soy expression, and so the value is
   *       needed immediately.
   *   <li>The function is invoked in a template block that is ultimately escaped.
   *   <li>There is no configured logging plugin.
   * </ul>
   */
  String getPlaceholder();
}
