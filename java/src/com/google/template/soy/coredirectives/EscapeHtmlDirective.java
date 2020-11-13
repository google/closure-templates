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

package com.google.template.soy.coredirectives;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.internal.ShortCircuitable;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import com.google.template.soy.types.SanitizedType;
import java.util.List;
import java.util.Set;

/**
 * A directive that HTML-escapes the output.
 *
 */
@SoyPurePrintDirective
public class EscapeHtmlDirective
    implements SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective.Streamable,
        ShortCircuitable {

  public static final String NAME = "|escapeHtml";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(0);
  }

  @Override
  public boolean isNoopForKind(ContentKind kind) {
    return kind == SanitizedContent.ContentKind.HTML;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    return CoreDirectivesRuntime.escapeHtml(value);
  }

  private static final class JbcSrcMethods {
    static final MethodRef ESCAPE_HTML =
        MethodRef.create(CoreDirectivesRuntime.class, "escapeHtml", SoyValue.class).asNonNullable();
    static final MethodRef STREAMING_ESCAPE_HTML =
        MethodRef.create(
                CoreDirectivesRuntime.class, "streamingEscapeHtml", LoggingAdvisingAppendable.class)
            .asNonNullable();
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forSoyValue(
        SanitizedType.HtmlType.getInstance(), JbcSrcMethods.ESCAPE_HTML.invoke(value.box()));
  }

  @Override
  public AppendableAndOptions applyForJbcSrcStreaming(
      JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
    return AppendableAndOptions.create(
        JbcSrcMethods.STREAMING_ESCAPE_HTML.invoke(delegateAppendable));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr("soy.$$escapeHtml(" + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    return new PyExpr("sanitize.escape_html(" + value.getText() + ")", Integer.MAX_VALUE);
  }
}
