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

package com.google.template.soy.html;

import com.google.common.collect.ImmutableSet;

/** Various HTML definitions from the w3 spec. */
public final class HtmlDefinitions {
  private HtmlDefinitions() {}

  /** The void elements, from http://www.w3.org/TR/html5/syntax.html#void-elements */
  public static final ImmutableSet<String> HTML5_VOID_ELEMENTS =
      ImmutableSet.of(
          "area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta",
          "param", "source", "track", "wbr");
}
