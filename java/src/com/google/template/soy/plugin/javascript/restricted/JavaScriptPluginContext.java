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

package com.google.template.soy.plugin.javascript.restricted;

/** The context for a {@link SoyJavaScriptSourceFunction}. */
public interface JavaScriptPluginContext {

  /**
   * A value that resolves to the direction at runtime. Will resolve to {@code -1} if the locale is
   * RTL, or {@code 1} if the current locale is LTR.
   *
   * <p>Very few plugins should require this, instead rely on the built-in bidi functions and common
   * localization libraries.
   */
  JavaScriptValue getBidiDir();
}
