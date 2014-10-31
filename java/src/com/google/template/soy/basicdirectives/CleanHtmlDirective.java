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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.restricted.Sanitizers;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPurePrintDirective;
import com.google.template.soy.shared.restricted.TagWhitelist.OptionalSafeTag;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * Implements the |cleanHtml directive, which removes all but a small, safe
 * subset of HTML from its input. Note that all attributes are removed except
 * dir for directionality.
 *
 * <p>
 * It may take a variable number of arguments which are additional tags to be
 * considered safe, from {@link TagWhitelist#OptionalSafeTag}.
 *
 * <p>
 * Note that this directive is not autoescape cancelling, and can thus be
 * used in strict templates.  The directive returns its result as an object
 * of type SanitizedContent of kind HTML.
 */
@Singleton
@SoyPurePrintDirective
public class CleanHtmlDirective implements SoyJavaPrintDirective, SoyJsSrcPrintDirective {


  // The directive may be called with a variable number of arguments indicating additional tags to
  // be considered safe, so we need to support each args size up to the number of possible
  // additional tags.
  private static final Set<Integer> VALID_ARGS_SIZES = ContiguousSet.create(
      Range.closed(0, OptionalSafeTag.values().length), DiscreteDomain.integers());


  @Inject
  public CleanHtmlDirective() {}


  @Override public String getName() {
    return "|cleanHtml";
  }


  @Override
  public final Set<Integer> getValidArgsSizes() {
    return VALID_ARGS_SIZES;
  }


  @Override public boolean shouldCancelAutoescape() {
    return false;
  }


  @Override public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
    ImmutableSet<OptionalSafeTag> optionalSafeTags = FluentIterable.from(args)
        .transform(SOY_VALUE_TO_STRING)
        // FROM_TAG_NAME throws IllegalArgumentException for invalid OptionalSafeTags.
        .transform(OptionalSafeTag.FROM_TAG_NAME)
        .toSet();

    return Sanitizers.cleanHtml(value, optionalSafeTags);
  }


  @Override public JsExpr applyForJsSrc(JsExpr value, List<JsExpr> args) {
    String optionalSafeTagsArg = "";
    if (!args.isEmpty()) {
      // TODO(user): Instead of parsing generated JS, we should have a CheckArgumentsPass that
      // allows directives and functions to examine their input expressions prior to compilation and
      // relay the input file and line number to the template author along with an error message.
      Iterable<String> optionalSafeTagExprs = Iterables.transform(args, JS_EXPR_TO_STRING);

      // Verify that all exprs are single-quoted valid OptionalSafeTags.
      FluentIterable.from(optionalSafeTagExprs)
          .transform(SINGLE_QUOTED_TO_UNQUOTED)
          .transform(OptionalSafeTag.FROM_TAG_NAME)
          .toSet();

      optionalSafeTagsArg = ", [" + JS_ARG_JOINER.join(optionalSafeTagExprs) + "]";
    }

    return new JsExpr(
        "soy.$$cleanHtml(" + value.getText() + optionalSafeTagsArg + ")", Integer.MAX_VALUE);
  }


  private static final Function<SoyValue, String> SOY_VALUE_TO_STRING =
      new Function<SoyValue, String>() {
    @Override public String apply(SoyValue soyValue) {
      return soyValue.stringValue();
    }
  };


  private static final Function<JsExpr, String> JS_EXPR_TO_STRING =
      new Function<JsExpr, String>() {
    @Override public String apply(JsExpr expr) {
      return expr.getText();
    }
  };


  private static final Function<String, String> SINGLE_QUOTED_TO_UNQUOTED =
      new Function<String, String>() {
    @Override public String apply(String singleQuoted) {
      if (singleQuoted.length() < 2
          || singleQuoted.charAt(0) != '\''
          || singleQuoted.charAt(singleQuoted.length() - 1) != '\'') {
        throw new IllegalArgumentException(
            String.format("The cleanHtml directive expects arguments to be tag name string "
                + "literals, such as 'span'. Encountered: %s", singleQuoted));
      }
      return singleQuoted.substring(1, singleQuoted.length() - 1);
    }
  };


  private static final Joiner JS_ARG_JOINER = Joiner.on(", ");
}
