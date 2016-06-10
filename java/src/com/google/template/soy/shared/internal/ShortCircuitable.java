/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.template.soy.data.SanitizedContent.ContentKind;

/**
 * An interface that may be implemented by a SoyPrintDirective to inform callers that it will
 * short-circuit execution for certain kinds of inputs.
 *
 * <p>Implementing this interface is encouraged for print directives that have short circuiting
 * behavior but it is not required for correctness.
 */
public interface ShortCircuitable {
  /**
   * Returns true if this directive will turn into a no-op when passed values of the given content
   * kind.
   */
  boolean isNoopForKind(ContentKind kind);
}
