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
package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.dsl.Expression.id;

import com.google.template.soy.jssrc.dsl.CodeChunk;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.jssrc.dsl.GoogRequire;

/**
 * Constants for commonly used lit-html runtime functions and objects.
 *
 * <p>Unlike {@code JsExprUtils}, this is only intended for use by the compiler itself and deals
 * exclusively with the {@link CodeChunk} api.
 */
public final class LitRuntime {
  private LitRuntime() {}

  // TODO(user): Add destructuring support to GoogRequire so instead of `litHtml.html` we can
  // just say `html`.
  private static final GoogRequire LIT_HTML =
      GoogRequire.createWithAlias("google3.third_party.javascript.lit_html", "litHtml");

  public static final Expression HTML = LIT_HTML.dotAccess("html");

  public static final Expression DATA = id(StandardNames.DOLLAR_DATA);
}
