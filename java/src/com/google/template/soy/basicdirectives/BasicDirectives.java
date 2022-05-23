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
package com.google.template.soy.basicdirectives;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

/** Lists all basic directives. */
public final class BasicDirectives {
  private BasicDirectives() {}

  public static ImmutableSet<SoyPrintDirective> directives() {
    return ImmutableSet.of(

        // Basic escape directives.
        new BasicEscapeDirective.EscapeCssString(),
        new BasicEscapeDirective.FilterCssValue(),
        new BasicEscapeDirective.NormalizeHtml(),
        new BasicEscapeDirective.EscapeHtmlRcdata(),
        new BasicEscapeDirective.EscapeHtmlAttribute(),
        new BasicEscapeDirective.EscapeHtmlHtmlAttribute(),
        new BasicEscapeDirective.EscapeHtmlAttributeNospace(),
        new BasicEscapeDirective.FilterHtmlAttributes(),
        new BasicEscapeDirective.FilterNumber(),
        new BasicEscapeDirective.FilterHtmlElementName(),
        new BasicEscapeDirective.EscapeJsRegex(),
        new BasicEscapeDirective.EscapeJsString(),
        new BasicEscapeDirective.EscapeJsValue(),
        new BasicEscapeDirective.FilterNormalizeUri(),
        new BasicEscapeDirective.FilterNormalizeMediaUri(),
        new BasicEscapeDirective.FilterNormalizeRefreshUri(),
        new BasicEscapeDirective.FilterTrustedResourceUri(),
        new BasicEscapeDirective.NormalizeUri(),
        new BasicEscapeDirective.EscapeUri(),
        new BasicEscapeDirective.FilterHtmlScriptPhrasingData(),
        new BasicEscapeDirective.FilterCspNonceValue(),
        new BasicEscapeDirective.WhitespaceHtmlAttributesDirective(),

        // Other directives.
        new ChangeNewlineToBrDirective(),
        new InsertWordBreaksDirective(),
        new TruncateDirective(),
        new TextDirective(),
        new CleanHtmlDirective(),
        new FilterImageDataUriDirective(),
        new FilterSipUriDirective(),
        new FilterTelUriDirective());
  }
}
