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

package com.google.template.soy.jssrc.internal;

/**
 * Provides a way to look up local variable aliases for the JavaScript function that corresponds to
 * a given Soy template.
 */
public interface TemplateAliases {
  /**
   * @param fullyQualifiedName The full name, including the namespace, of a Soy template.
   * @return The variable that should be used when referring to the template.
   */
  String get(String fullyQualifiedName);

  /** Returns the symbol that should be used as an alias for the soy template namespace. */
  String getNamespaceAlias(String namespace);
}
