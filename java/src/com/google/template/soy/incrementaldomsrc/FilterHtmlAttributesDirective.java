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
package com.google.template.soy.incrementaldomsrc;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import java.util.List;
import java.util.Set;

/** Implements the |filterHtmlAttributes directive for incremental dom only. */
final class FilterHtmlAttributesDirective implements SoyLibraryAssistedJsSrcPrintDirective {

  /**
   * Gets the name of the Soy print directive.
   *
   * @return The name of the Soy print directive.
   */
  @Override
  public String getName() {
    return "|filterHtmlAttributes";
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
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr(
        String.format(
            "goog.module.get('%s').$$filterHtmlAttributes(%s)",
            "google3.javascript.template.soy.soyutils_directives", value.getText()),
        Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("google3.javascript.template.soy.soyutils_directives");
  }
}
