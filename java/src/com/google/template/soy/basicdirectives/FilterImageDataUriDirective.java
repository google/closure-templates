/*
 * Copyright 2013 Google Inc.
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
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jbcsrc.restricted.JbcSrcPluginContext;
import com.google.template.soy.jbcsrc.restricted.MethodRef;
import com.google.template.soy.jbcsrc.restricted.SoyExpression;
import com.google.template.soy.jbcsrc.restricted.SoyJbcSrcPrintDirective;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyLibraryAssistedJsSrcPrintDirective;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.internal.Sanitizers;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import com.google.template.soy.types.SanitizedType;
import java.util.List;
import java.util.Set;

/**
 * Implements the |filterImageDataUri directive, which only accepts data URI's corresponding to
 * images.
 *
 * <p>Note that this directive is not autoescape cancelling, and can thus be used in strict
 * templates. The directive returns its result as an object of type SanitizedContent of kind URI.
 */
@SoyPurePrintDirective
final class FilterImageDataUriDirective
    implements SoyJavaPrintDirective,
        SoyLibraryAssistedJsSrcPrintDirective,
        SoyPySrcPrintDirective,
        SoyJbcSrcPrintDirective {

  private static final ImmutableSet<Integer> VALID_ARGS_SIZES = ImmutableSet.of(0);

  @Override
  public String getName() {
    return "|filterImageDataUri";
  }

  @Override
  public final Set<Integer> getValidArgsSizes() {
    return VALID_ARGS_SIZES;
  }

  @Override
  public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    return Sanitizers.filterImageDataUri(value);
  }

  private static final class JbcSrcMethods {
    static final MethodRef FILTER_IMAGE_DATA =
        MethodRef.create(Sanitizers.class, "filterImageDataUri", SoyValue.class).asNonNullable();
  }

  @Override
  public SoyExpression applyForJbcSrc(
      JbcSrcPluginContext context, SoyExpression value, List<SoyExpression> args) {
    return SoyExpression.forSoyValue(
        SanitizedType.UriType.getInstance(), JbcSrcMethods.FILTER_IMAGE_DATA.invoke(value.box()));
  }

  @Override
  public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    return new JsExpr("soy.$$filterImageDataUri(" + value.getText() + ")", Integer.MAX_VALUE);
  }

  @Override
  public ImmutableSet<String> getRequiredJsLibNames() {
    return ImmutableSet.of("soy");
  }

  @Override
  public PyExpr applyForPySrc(PyExpr value, List<PyExpr> args) {
    return new PyExpr("sanitize.filter_image_data_uri(" + value.getText() + ")", Integer.MAX_VALUE);
  }
}
