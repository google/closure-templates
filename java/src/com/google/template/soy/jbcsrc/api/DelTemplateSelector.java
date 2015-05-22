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

package com.google.template.soy.jbcsrc.api;

/**
 * Defines a strategy for looking up deltemplates by callee name and variant.
 */
public interface DelTemplateSelector {
  /**
   * Returns a compiled template for the given {@code calleeName} and {@code variant} value 
   * according to the current set of active DelPackages.
   * 
   * @param calleeName The template name
   * @param variant The variant value
   * @param allowEmpty If true, then returns a template that renders the empty string when no 
   *     delegates are active.
   */
  CompiledTemplate.Factory selectDelTemplate(
      String calleeName, String variant, boolean allowEmpty);
}
