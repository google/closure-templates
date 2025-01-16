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

package com.google.template.soy.idomsrc;

import static com.google.template.soy.idomsrc.IdomRuntime.INCREMENTAL_DOM;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.ModernSoyJsSrcPrintDirective;
import java.util.List;
import java.util.Set;

/** Implements the |bidiUnicodeWrap directive for incremental dom only. */
final class BidiUnicodeWrapDirective implements ModernSoyJsSrcPrintDirective {

  /** Supplier for the current bidi global directionality. */
  private final BidiGlobalDir bidiGlobalDir;

  /**
   * @param bidiGlobalDir Current bidi global directionality.
   */
  BidiUnicodeWrapDirective(BidiGlobalDir bidiGlobalDir) {
    this.bidiGlobalDir = bidiGlobalDir;
  }

  /**
   * Gets the name of the Soy print directive.
   *
   * @return The name of the Soy print directive.
   */
  @Override
  public String getName() {
    return "|bidiUnicodeWrap";
  }

  /**
   * Gets the set of valid args list sizes. For example, the set {0, 2} would indicate that this
   * directive can take 0 or 2 arguments (but not 1).
   *
   * @return The set of valid args list sizes.
   */
  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  public Expression applyForJsSrc(Expression value, List<Expression> args) {
    Expression dir =
        Expressions.fromExpr(
            new JsExpr(bidiGlobalDir.getCodeSnippet(), Integer.MAX_VALUE), ImmutableList.of());
    return SOYUTILS_DIRECTIVES.dotAccess("$$bidiUnicodeWrap").call(dir, value, INCREMENTAL_DOM);
  }
}
