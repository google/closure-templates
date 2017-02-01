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

package com.google.template.soy.bidifunctions;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.internal.i18n.SoyBidiUtils;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcFunction;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Soy function that maybe inserts a bidi mark character (LRM or RLM) for the current global bidi
 * directionality. The function requires the text string preceding the point where the bidi mark
 * character is to be inserted. If the preceding text string would change the bidi directionality
 * going forward, then the bidi mark is inserted to restore the global bidi directionality.
 * Otherwise, nothing is inserted.
 *
 */
@Singleton
final class BidiMarkAfterFunction
    implements SoyJavaFunction, SoyLibraryAssistedJsSrcFunction, SoyPySrcFunction {

  /** Provider for the current bidi global directionality. */
  private final Provider<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Provider for the current bidi global directionality. */
  @Inject
  BidiMarkAfterFunction(Provider<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public String getName() {
    return "bidiMarkAfter";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }

  @Override
  public SoyValue computeForJava(List<SoyValue> args) {
    SoyValue value = args.get(0);
    boolean isHtml = args.size() == 2 && args.get(1).booleanValue();
    Dir valueDir = null;
    if (value instanceof SanitizedContent) {
      SanitizedContent sanitizedContent = (SanitizedContent) value;
      valueDir = sanitizedContent.getContentDirection();
      isHtml = isHtml || sanitizedContent.getContentKind() == ContentKind.HTML;
    }

    int bidiGlobalDir = bidiGlobalDirProvider.get().getStaticValue();
    return StringData.forValue(
        SoyBidiUtils.getBidiFormatter(bidiGlobalDir)
            .markAfterKnownDir(valueDir, value.coerceToString(), isHtml));
  }

  @Override
  public JsExpr computeForJsSrc(List<JsExpr> args) {
    JsExpr value = args.get(0);
    JsExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        "soy.$$bidiMarkAfter("
            + bidiGlobalDirProvider.get().getCodeSnippet()
            + ", "
            + value.getText()
            + (isHtml != null ? ", " + isHtml.getText() : "")
            + ")";

    return new JsExpr(callText, Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.<String>builder()
        .addAll(bidiGlobalDirProvider.get().getNamespace().asSet())
        .add("soy")
        .build();
  }

  @Override
  public PyExpr computeForPySrc(List<PyExpr> args) {
    PyExpr value = args.get(0);
    PyExpr isHtml = (args.size() == 2) ? args.get(1) : null;

    String callText =
        "bidi.mark_after("
            + bidiGlobalDirProvider.get().getCodeSnippet()
            + ", "
            + value.getText()
            + (isHtml != null ? ", " + isHtml.getText() : "")
            + ")";

    return new PyExpr(callText, Integer.MAX_VALUE);
  }
}
