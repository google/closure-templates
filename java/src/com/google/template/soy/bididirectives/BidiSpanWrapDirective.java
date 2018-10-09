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
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
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
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A directive that maybe wraps the output within a 'span' with dir=ltr or dir=rtl. This wrapping is
 * only applied when the output text's bidi directionality is different from the bidi global
 * directionality.
 *
 */
final class BidiSpanWrapDirective
    implements SanitizedContentOperator,
        SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective.Streamable {

  /** Supplier for the current bidi global directionality. */
  private final Supplier<BidiGlobalDir> bidiGlobalDirProvider;

  /** @param bidiGlobalDirProvider Supplier for the current bidi global directionality. */
  BidiSpanWrapDirective(Supplier<BidiGlobalDir> bidiGlobalDirProvider) {
    this.bidiGlobalDirProvider = bidiGlobalDirProvider;
  }

  @Override
  public String getName() {
    return "|bidiSpanWrap";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  @Nonnull
  public ContentKind getContentKind() {
    // This directive expects HTML as input and produces HTML as output.
    return ContentKind.HTML;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    return StringData.forValue(
        BidiDirectivesRuntime.bidiSpanWrap(bidiGlobalDirProvider.get(), value));
  }

  private static final class JbcSrcMethods {
    static final MethodRef BIDI_SPAN_WRAP =
        MethodRef.create(
                BidiDirectivesRuntime.class, "bidiSpanWrap", BidiGlobalDir.class, SoyValue.class)
            .asNonNullable();
    static final MethodRef BIDI_SPAN_WRAP_STREAMING =
        MethodRef.create(
                BidiDirectivesRuntime.class,
                "bidiSpanWrapStreaming",
                LoggingAdvisingAppendable.class,
                BidiGlobalDir.class)
            .asNonNullable();
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forString(
        JbcSrcMethods.BIDI_SPAN_WRAP.invoke(context.getBidiGlobalDir(), value.box()));
  }

  @Override
  public AppendableAndOptions applyForJbcSrcStreaming(
      JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
    return AppendableAndOptions.createCloseable(
        JbcSrcMethods.BIDI_SPAN_WRAP_STREAMING.invoke(
            delegateAppendable, context.getBidiGlobalDir()));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new JsExpr(
        "soy.$$bidiSpanWrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    String codeSnippet = bidiGlobalDirProvider.get().getCodeSnippet();
    return new PyExpr(
        "bidi.span_wrap(" + codeSnippet + ", " + value.getText() + ")", Integer.MAX_VALUE);
  }
}
