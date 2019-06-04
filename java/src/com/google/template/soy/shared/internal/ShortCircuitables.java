/*
 * Copyright 2019 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

/** Utilities for working with ShortCircuitable print directives. */
public final class ShortCircuitables {
  /**
   * Identifies some cases where the combination of directives and content kind mean we can skip
   * applying the escapers. This is an opportunistic optimization, it is possible that we will fail
   * to skip escaping in some cases where we could and that is OK. However, there should never be a
   * case where we skip escaping and but the escapers would actually modify the input.
   */
  public static <T extends SoyPrintDirective> ImmutableList<T> filterDirectivesForKind(
      ContentKind kind, ImmutableList<T> directives) {
    for (int i = 0; i < directives.size(); i++) {
      T directive = directives.get(i);
      if (!(directive instanceof ShortCircuitable)
          || !((ShortCircuitable) directive).isNoopForKind(kind)) {
        return directives.subList(i, directives.size());
      }
    }
    return ImmutableList.of();
  }

  private ShortCircuitables() {}
}
