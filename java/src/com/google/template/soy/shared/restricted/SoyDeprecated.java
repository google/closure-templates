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

package com.google.template.soy.shared.restricted;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation for {@link SoyFunction} or {@link SoyPrintDirective} types that marks it as
 * deprecated.
 *
 * <p>This will trigger warnings from the Soy compiler.
 *
 * <p>NOTE: don't use {@link java.lang.Deprecated} for a few reasons:
 *
 * <ol>
 *   <li>It doesn't provide a mechanism to associate an author defined comment. This is more helpful
 *       for Soy plugins because it is often difficult to navigate from plugin use to definition,
 *       whereas in Java this is a more standardized and supported workflow.
 *   <li>It allows authors to distinguish between deprecated implementations (for which {@link
 *       java.lang.Deprecated} would be appropriate ) and deprecated Soy plugins (for which this
 *       annotation is appropriate.)
 *   <li>It requires an explicit opt-in to prevent author confusion by adding a new behavior to
 *       {@link java.lang.Deprecated}.
 * </ul>
 */
@Target(TYPE)
@Retention(RUNTIME)
public @interface SoyDeprecated {
  /** A warning text to display with the deprecation notice. */
  String value();
}
