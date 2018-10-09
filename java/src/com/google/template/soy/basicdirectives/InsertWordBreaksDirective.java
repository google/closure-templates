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
import com.google.template.soy.data.LoggingAdvisingAppendable;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContentOperator;
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
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
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import com.google.template.soy.types.SanitizedType.HtmlType;
import com.google.template.soy.types.StringType;
import com.google.template.soy.types.UnionType;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.objectweb.asm.Type;

/**
 * A directive that inserts word breaks as necessary.
 *
 * <p>It takes a single argument : an integer specifying the max number of characters between
 * breaks.
 *
 */
@SoyPurePrintDirective
final class InsertWordBreaksDirective
    implements SanitizedContentOperator,
        SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective.Streamable {

  @Override
  public String getName() {
    return "|insertWordBreaks";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1);
  }

  @Override
  @Nonnull
  public SanitizedContent.ContentKind getContentKind() {
    // This directive expects HTML as input and produces HTML as output.
    return SanitizedContent.ContentKind.HTML;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {

    int maxCharsBetweenWordBreaks;
    try {
      maxCharsBetweenWordBreaks = args.get(0).integerValue();
    } catch (SoyDataException sde) {
      throw new IllegalArgumentException(
          "Could not parse 'insertWordBreaks' parameter as integer.");
    }
    return BasicDirectivesRuntime.insertWordBreaks(value, maxCharsBetweenWordBreaks);
  }

  private static final class JbcSrcMethods {
    static final MethodRef INSERT_WORD_BREAKS =
        MethodRef.create(
                BasicDirectivesRuntime.class, "insertWordBreaks", SoyValue.class, int.class)
            .asNonNullable();
    static final MethodRef INSERT_WORD_BREAKS_STREAMING =
        MethodRef.create(
            BasicDirectivesRuntime.class,
            "insertWordBreaksStreaming",
            LoggingAdvisingAppendable.class,
            int.class);
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forSoyValue(
        UnionType.of(StringType.getInstance(), HtmlType.getInstance()),
        JbcSrcMethods.INSERT_WORD_BREAKS.invoke(
            value.box(),
            BytecodeUtils.numericConversion(args.get(0).unboxAs(long.class), Type.INT_TYPE)));
  }

  @Override
  public AppendableAndOptions applyForJbcSrcStreaming(
      JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
    return AppendableAndOptions.create(
        JbcSrcMethods.INSERT_WORD_BREAKS_STREAMING.invoke(
            delegateAppendable,
            BytecodeUtils.numericConversion(args.get(0).unboxAs(long.class), Type.INT_TYPE)));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {

    return new JsExpr(
        "soy.$$insertWordBreaks(" + value.getText() + ", " + args.get(0).getText() + ")",
        Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    return new PyExpr("runtime.unsupported('|insertWordBreaks')", Integer.MAX_VALUE);
  }
}
