/*
 * Copyright 2018 Google.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.template.soy.pysrc.internalutils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utility methods for values of the {@code kind} attribute on {@code {param}} nodes.
 *
 * <p>
 * This attribute specifies the {@link SanitizedContentKind} that the content of the node will evaluate to, and in turn
 * determines the HTML context to use when contextually autoescaping the node's content (see {@link
 * com.google.template.soy.parsepasses.contextautoesc.Context#forContentKind(SanitizedContentKind)}).
 *
 */
public class NodeContentKinds {

  /**
   * The Python sanitized classes.
   */
  private static final ImmutableMap<SanitizedContentKind, String> KIND_TO_PY_SANITIZED_NAME
          = ImmutableMap.<SanitizedContentKind, String>builder()
          .put(SanitizedContentKind.HTML, "sanitize.SanitizedHtml")
          .put(SanitizedContentKind.ATTRIBUTES, "sanitize.SanitizedHtmlAttribute")
          .put(SanitizedContentKind.JS, "sanitize.SanitizedJs")
          .put(SanitizedContentKind.URI, "sanitize.SanitizedUri")
          .put(SanitizedContentKind.CSS, "sanitize.SanitizedCss")
          .put(SanitizedContentKind.TRUSTED_RESOURCE_URI, "sanitize.SanitizedTrustedResourceUri")
          .put(SanitizedContentKind.TEXT, "sanitize.UnsanitizedText")
          .build();

  static {
    Set<SanitizedContentKind> allKinds = EnumSet.allOf(SanitizedContentKind.class);

    if (!KIND_TO_PY_SANITIZED_NAME.keySet().containsAll(allKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a Python sanitizer");
    }
  }

  /**
   * Given a {@link SanitizedContentKind}, returns the corresponding Python sanitize class.
   */
  public static String toPySanitizedContentOrdainer(SanitizedContentKind contentKind) {
    // Sanitization classes are defined in sanitize.py.
    return Preconditions.checkNotNull(
            KIND_TO_PY_SANITIZED_NAME.get(contentKind),
            "expected to have an ordainer for %s",
            contentKind);
  }

  // Prevent instantiation.
  private NodeContentKinds() {
  }
}
