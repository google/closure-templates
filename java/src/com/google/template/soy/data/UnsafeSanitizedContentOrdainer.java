/*
 * Copyright 2012 Google Inc.
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

import com.google.template.soy.data.SanitizedContent.ContentKind;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Restricted class to create SanitizedContent objects.
 *
 * Creating a SanitizedContent object is potentially dangerous, as it means you're swearing in
 * advance the content won't cause a cross site scripting vulnerability. In the long term it is
 * nearly impossible to show that any piece of code will always produce safe content -- for
 * example, a parameter that is safe one day may be vulnerable after a refactoring that uses it in
 * a different way.
 *
 * <p>We suggest you limit your usage of this to just a few files in your code base. Create a small
 * set of utility files that generate and sanitize at the same time. Example utilities:
 * <ul>
 * <li>Serializing JSON objects from a data structure.
 * <li>Running a sanitizer on HTML for an email message.
 * <li>Extracting a field from a protocol message that is always run-time sanitized by a backend.
 * It's useful to label the protocol message fields with a "SafeHtml" suffix to reinforce.
 * </ul>
 *
 * @author Garrett Boyer
 */
@ParametersAreNonnullByDefault
public final class UnsafeSanitizedContentOrdainer {
  
  /** No constructor. */
  private UnsafeSanitizedContentOrdainer() {}

  /**
   * Faithfully assumes the provided value is "safe" and marks it not to be re-escaped.
   *
   * When you "ordain" a string as safe content, it means that Soy will NOT re-escape or validate
   * the contents if printed in the relevant context. You can use this to insert known-safe HTML
   * into a template via a parameter.
   *
   * This doesn't do a lot of strict checking, but makes it easier to differentiate safe constants
   * in your code.
   */
  public static SanitizedContent ordainAsSafe(String value, ContentKind kind) {
    return new SanitizedContent(value, kind);
  }
}
