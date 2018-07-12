/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.bididirectives;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

/** Lists all bidi directives. */
public final class BidiDirectives {
  private BidiDirectives() {}

  public static ImmutableSet<SoyPrintDirective> directives(Supplier<BidiGlobalDir> bidiProvider) {
    return ImmutableSet.of(
        new BidiSpanWrapDirective(bidiProvider), new BidiUnicodeWrapDirective(bidiProvider));
  }
}
