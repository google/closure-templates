/*
 * Copyright 2021 Google Inc.
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

package com.google.template.soy.parsepasses.contextautoesc;

import com.google.common.collect.ImmutableSet;
import java.util.regex.Pattern;

/** Constants used to determine html attribute types. */
public final class Constants {

  /**
   * Lower case names of attributes whose value is a URI. This does not identify attributes like
   * {@code <meta content>} which is conditionally a URI depending on the value of other attributes.
   *
   * @see <a href="http://www.w3.org/TR/html4/index/attributes.html">HTML4 attrs with type %URI</a>
   */
  public static final ImmutableSet<String> URI_ATTR_NAMES =
      ImmutableSet.of(
          "action",
          "archive",
          "base",
          "background",
          "cite",
          "classid",
          "codebase",
          /**
           * TODO: content is only a URL sometimes depending on other parameters and existing
           * templates use content with non-URL values. Fix those templates or otherwise flag
           * interpolations into content.
           */
          // "content",
          "data",
          "dsync",
          "formaction",
          "href",
          "icon",
          "longdesc",
          "manifest",
          "poster",
          "src",
          "usemap",
          // Custom attributes that are reliably URLs in existing code.
          "entity");

  /** Matches lower-case attribute local names that start or end with "url" or "uri". */
  public static final Pattern CUSTOM_URI_ATTR_NAMING_CONVENTION =
      Pattern.compile("\\bur[il]|ur[il]s?$");

  // Non-instantiable.
  private Constants() {}
}
