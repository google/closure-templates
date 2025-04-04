/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.jssrc.restricted;

import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.List;

/** Internal version of {@link SoyJsSrcPrintDirective} that avoids {@link JsExpr}. */
public interface ModernSoyJsSrcPrintDirective extends SoyPrintDirective {
  GoogRequire SOY = GoogRequire.create("soy");

  Expression SOYUTILS_DIRECTIVES =
      GoogRequire.create("google3.javascript.template.soy.soyutils_directives").googModuleGet();

  Expression applyForJsSrc(Expression value, List<Expression> args);

  /**
   * Whether the print directive just passes through SanitizedHtml. When compiling lazy blocks,
   * these directives don't need to be deferred sicne they are no-ops.
   */
  default boolean isJsImplNoOpForSanitizedHtml() {
    return false;
  }
}
