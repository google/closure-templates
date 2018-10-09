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
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import java.util.List;
import java.util.Set;

/**
 * A directive that maybe wraps the output within Unicode bidi control characters -- start character
 * is either LRE (U+202A) or RLE (U+202B), and end character is always PDF (U+202C). This wrapping
 * is only applied when the output text's bidi directionality is different from the bidi global
 * directionality.
 *
 */
final class BidiUnicodeWrapDirective
    implements SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective.Streamable {

  /** Supplier for the current bidi global directionality. */
  private final Supplier<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Supplier for the current bidi global directionality. */
  BidiUnicodeWrapDirective(Supplier<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public String getName() {
    return "|bidiUnicodeWrap";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    return BidiDirectivesRuntime.bidiUnicodeWrap(bidiGlobalDirProvider.get(), value);
  }

  private static final class JbcSrcMethods {
    static final MethodRef BIDI_UNICODE_WRAP =
        MethodRef.create(
                BidiDirectivesRuntime.class, "bidiUnicodeWrap", BidiGlobalDir.class, SoyValue.class)
            .asNonNullable();
    static final MethodRef BIDI_UNICODE_WRAP_STREAMING =
        MethodRef.create(
                BidiDirectivesRuntime.class,
                "bidiUnicodeWrapStreaming",
                LoggingAdvisingAppendable.class,
                BidiGlobalDir.class)
            .asNonNullable();
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forSoyValue(
        UnionType.of(StringType.getInstance(), HtmlType.getInstance()),
        JbcSrcMethods.BIDI_UNICODE_WRAP.invoke(context.getBidiGlobalDir(), value.box()));
  }

  @Override
  public AppendableAndOptions applyForJbcSrcStreaming(
      JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
    return AppendableAndOptions.createCloseable(
        JbcSrcMethods.BIDI_UNICODE_WRAP_STREAMING.invoke(
            delegateAppendable, context.getBidiGlobalDir()));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new JsExpr(
        "soy.$$bidiUnicodeWrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new PyExpr(
        "bidi.unicode_wrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }
}
