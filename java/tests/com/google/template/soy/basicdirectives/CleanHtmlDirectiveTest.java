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
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.shared.AbstractSoyPrintDirectiveTestCase;
import com.google.template.soy.shared.restricted.TagWhitelist.OptionalSafeTag;


/**
 * Unit tests for {@link CleanHtmlDirective}.
 */
public class CleanHtmlDirectiveTest extends AbstractSoyPrintDirectiveTestCase {


  private SanitizedContent sanitizedHtml(String s) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(s, ContentKind.HTML);
  }

  public void testApplyForTofu() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    assertTofuOutput(sanitizedHtml("boo hoo"), "boo hoo", cleanHtml);
    assertTofuOutput(sanitizedHtml("3 &lt; 5"), "3 < 5", cleanHtml);
    assertTofuOutput(sanitizedHtml(""), "<script type=\"text/javascript\">", cleanHtml);
    assertTofuOutput(sanitizedHtml(""), "<script><!--\nbeEvil();//--></script>", cleanHtml);
    // Known safe content is preserved.
    assertTofuOutput(sanitizedHtml("<script>beAwesome()</script>"),
        sanitizedHtml("<script>beAwesome()</script>"), cleanHtml);
    // Entities are preserved
    assertTofuOutput(sanitizedHtml("&nbsp;&nbsp;"), "&nbsp;&nbsp;", cleanHtml);
    // Safe tags are preserved. Others are not.
    assertTofuOutput(
        sanitizedHtml("Hello, <b>World!</b>"), "Hello, <b>World!<object></b>", cleanHtml);
  }

  public void testApplyForTofu_optionalSafeTags() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();

    // All possible OptionalSafeTags.
    Object[] optionalSafeTagsAsArgs =
        FluentIterable.from(ImmutableSet.copyOf(OptionalSafeTag.values()))
            .transform(OptionalSafeTag.TO_TAG_NAME)
            .toArray(String.class);

    // Safe tags are preserved. Others are not.
    assertTofuOutput(
        sanitizedHtml("Hello, <b><span><ol><li><ul>Worrell!</ul></li></ol></span></b>"),
        "Hello, <b><span><ol><li><ul>Worrell!</ul></li></ol></span></b>",
        cleanHtml,
        optionalSafeTagsAsArgs);

    // Only the specified optional safe tags are preserved.
    assertTofuOutput(
        sanitizedHtml("Hello, <b><span>Worrell!</span></b>"),
        "Hello, <b><span><ol><li><ul>Worrell!</ul></li></ol></span></b>",
        cleanHtml,
        "span");

    try {
      cleanHtml.applyForJava(
          StringData.forValue("test"),
          ImmutableList.<SoyValue>of(StringData.forValue("unsupported")));
      fail();
    } catch (IllegalArgumentException e) {
      // Test passes.
    }
  }

  public void testApplyForJsSrc() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertEquals(
        "soy.$$cleanHtml(opt_data.myKey)",
        cleanHtml.applyForJsSrc(dataRef, ImmutableList.<JsExpr>of()).getText());
  }

  public void testApplyForJsSrc_optionalSafeTags() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);

    // All possible OptionalSafeTags.
    ImmutableList<JsExpr> optionalSafeTagsAsJsExprs =
        FluentIterable.from(ImmutableSet.copyOf(OptionalSafeTag.values()))
            .transform(OptionalSafeTag.TO_TAG_NAME)
            .transform(new Function<String, JsExpr>() {
              @Override
              public JsExpr apply(String input) {
                return new JsExpr(String.format("'%s'", input), Integer.MAX_VALUE);
              }
            }).toList();

    assertEquals(
        "soy.$$cleanHtml(opt_data.myKey, ['li', 'ol', 'span', 'ul'])",
        cleanHtml.applyForJsSrc(dataRef, optionalSafeTagsAsJsExprs).getText());

    // Only the specified optional safe tags are passed to $$cleanHtml.
    assertEquals(
        "soy.$$cleanHtml(opt_data.myKey, ['span'])",
        cleanHtml.applyForJsSrc(
            dataRef, ImmutableList.of(new JsExpr("'span'", Integer.MAX_VALUE))).getText());

    // Invalid optional safe tags.
    try {
      cleanHtml.applyForJsSrc(
          dataRef, ImmutableList.of(new JsExpr("'unsupported'", Integer.MAX_VALUE)));
      fail();
    } catch (IllegalArgumentException e) {
      // Test passes.
    }
    try {
      cleanHtml.applyForJsSrc(
          dataRef, ImmutableList.of(new JsExpr("'li, ul'", Integer.MAX_VALUE)));
      fail();
    } catch (IllegalArgumentException e) {
      // Test passes.
    }

    // Invalid parameter syntax.
    try {
      cleanHtml.applyForJsSrc(
          dataRef, ImmutableList.of(new JsExpr("$myExtraSafeTags", Integer.MAX_VALUE)));
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("The cleanHtml directive expects arguments to be tag name string "
          + "literals, such as 'span'. Encountered: $myExtraSafeTags", e.getMessage());
    }
  }
}
