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
package com.google.template.soy.jbcsrc.restricted;

/**
 * Provides plugins access to rendering contextual information.
 *
 * <p>Subject to change.
 */
public interface JbcSrcPluginContext {
  /** Returns an expression that evaluates to the current {@link BidiGlobalDir} */
  Expression getBidiGlobalDir();

  /** Returns an expression that evaluates to the current {@code ULocale}. */
  Expression getULocale();

  /** Returns all required css namespaces. */
  Expression getAllRequiredCssNamespaces(Expression template);
}
