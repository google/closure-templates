/*
 * Copyright 2011 Google Inc.
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
import com.google.template.soy.data.SoyDataException;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jbcsrc.restricted.BytecodeUtils;
import com.google.template.soy.jbcsrc.restricted.Expression;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.dsl.Expressions;
import com.google.template.soy.jssrc.restricted.ModernSoyJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.PyFunctionExprBuilder;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Type;

/**
 * A directive that truncates a string to a maximum length if it is too long, optionally adding
 * ellipsis.
 */
@SoyPurePrintDirective
final class TruncateDirective
    implements SoyJavaPrintDirective,
        ModernSoyJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective.Streamable {

  @Override
  public String getName() {
    return "|truncate";
  }

  @Override
  public Set<Integer> getValidArgsSizes() {
    return ImmutableSet.of(1, 2);
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    int maxLen;
    try {
      maxLen = args.get(0).integerValue();
    } catch (SoyDataException sde) {
      throw new IllegalArgumentException(
          "Could not parse first parameter of '|truncate' as integer (value was \""
              + args.get(0).stringValue()
              + "\").",
          sde);
    }

    String str = value.coerceToString();
    boolean doAddEllipsis;
    if (args.size() == 2) {
      try {
        doAddEllipsis = args.get(1).booleanValue();
      } catch (SoyDataException sde) {
        throw new IllegalArgumentException(
            "Could not parse second parameter of '|truncate' as boolean.", sde);
      }
    } else {
      doAddEllipsis = true; // default to true
    }

    return StringData.forValue(BasicDirectivesRuntime.truncate(str, maxLen, doAddEllipsis));
  }

  private static final class JbcSrcMethods {
    static final MethodRef TRUNCATE =
        MethodRef.createPure(
            BasicDirectivesRuntime.class, "truncate", String.class, int.class, boolean.class);
    static final MethodRef TRUNCATE_STREAMING =
        MethodRef.createNonPure(
            BasicDirectivesRuntime.class,
            "truncateStreaming",
            LoggingAdvisingAppendable.class,
            int.class,
            boolean.class);
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forString(
        JbcSrcMethods.TRUNCATE.invoke(
            value.coerceToString(),
            BytecodeUtils.numericConversion(args.get(0).unboxAsDouble(), Type.INT_TYPE),
            args.size() > 1 ? args.get(1).unboxAsBoolean() : BytecodeUtils.constant(true)));
  }

  @Override
  public AppendableAndOptions applyForJbcSrcStreaming(
      JbcSrcPluginContext context, Expression delegateAppendable, List<SoyExpression> args) {
    return AppendableAndOptions.createCloseable(
        JbcSrcMethods.TRUNCATE_STREAMING.invoke(
            delegateAppendable,
            BytecodeUtils.numericConversion(args.get(0).unboxAsDouble(), Type.INT_TYPE),
            args.size() > 1 ? args.get(1).unboxAsBoolean() : BytecodeUtils.constant(true)));
  }

  @Override
  public com.google.template.soy.jssrc.dsl.Expression applyForJsSrc(
      com.google.template.soy.jssrc.dsl.Expression value,
      List<com.google.template.soy.jssrc.dsl.Expression> args) {
    return SOY.dotAccess("$$truncate")
        .call(value, args.get(0), args.size() == 2 ? args.get(1) : Expressions.LITERAL_TRUE);
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    // Truncation always wants a string, so to potentially save an unnecessary conversion, we do
    // optional coercing at compile time.
    PyExpr input = value.toPyString();
    PyExpr maxLen = args.get(0);
    PyExpr doAddEllipsis = (args.size() == 2) ? args.get(1) : new PyExpr("True", Integer.MAX_VALUE);

    PyFunctionExprBuilder fnBuilder = new PyFunctionExprBuilder("directives.truncate");
    fnBuilder.addArg(input).addArg(maxLen).addArg(doAddEllipsis);
    return fnBuilder.asPyStringExpr();
  }
}
