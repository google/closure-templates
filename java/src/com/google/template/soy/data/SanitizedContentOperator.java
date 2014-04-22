/*
 * Copyright 2011 Google Inc.
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

import javax.annotation.Nonnull;


/**
 * An operator that takes sanitized content of a particular kind and produces sanitized content of
 * the same kind.
 * Directives may be marked as producers of sanitized content, in which case, the autoescaper will
 * put any inferred directives before the escaping directive.
 * This allows directives that take sanitized content of a particular
 * {@link SanitizedContent.ContentKind kind} and wrap it to avoid over-escaping.
 *
 */
public interface SanitizedContentOperator {


  /**
   * The kind of content consumed and produced.
   */
  @Nonnull SanitizedContent.ContentKind getContentKind();

}
