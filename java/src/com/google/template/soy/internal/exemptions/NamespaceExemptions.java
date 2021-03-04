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

package com.google.template.soy.internal.exemptions;

import com.google.common.collect.ImmutableSet;

/** A list of all allowed duplicate namespaces and a simple predicate for querying it. */
public final class NamespaceExemptions {

  public static boolean isKnownDuplicateNamespace(String namespace) {
    return ALLOWED_DUPLICATE_NAMESPACES.contains(namespace);
  }

  private static final ImmutableSet<String> ALLOWED_DUPLICATE_NAMESPACES =
      ImmutableSet.of(
          "testing.duplicate.namespaces",
          "_I_LIKE_TRAILING_COMMAS_");

  private NamespaceExemptions() {}
}
