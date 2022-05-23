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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.jssrc.restricted.JsExpr;
import com.google.template.soy.pysrc.restricted.PyExpr;
import com.google.template.soy.shared.internal.TagWhitelist.OptionalSafeTag;
import com.google.template.soy.testing.AbstractSoyPrintDirectiveTestCase;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CleanHtmlDirective}. */
@RunWith(JUnit4.class)
public class CleanHtmlDirectiveTest extends AbstractSoyPrintDirectiveTestCase {

  @Test
  public void testApplyForTofu() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    assertTofuOutput(sanitizedHtml("boo hoo"), "boo hoo", cleanHtml);
    assertTofuOutput(sanitizedHtml("3 &lt; 5"), "3 < 5", cleanHtml);
    assertTofuOutput(sanitizedHtml(""), "<script type=\"text/javascript\">", cleanHtml);
    assertTofuOutput(sanitizedHtml(""), "<script><!--\nbeEvil();//--></script>", cleanHtml);
    // Known safe content is preserved.
    assertTofuOutput(
        sanitizedHtml("<script>beAwesome()</script>"),
        sanitizedHtml("<script>beAwesome()</script>"),
        cleanHtml);
    // Entities are preserved
    assertTofuOutput(sanitizedHtml("&nbsp;&nbsp;"), "&nbsp;&nbsp;", cleanHtml);
    // Safe tags are preserved. Others are not.
    assertTofuOutput(
        sanitizedHtml("Hello, <b>World!</b>"), "Hello, <b>World!<object></b>", cleanHtml);
  }

  @Test
  public void testApplyForTofu_optionalSafeTags() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();

    // All possible OptionalSafeTags.
    Object[] optionalSafeTagsAsArgs =
        ImmutableSet.copyOf(OptionalSafeTag.values()).stream()
            .map(OptionalSafeTag::getTagName)
            .toArray(String[]::new);

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
          StringData.forValue("test"), ImmutableList.of(StringData.forValue("unsupported")));
      fail();
    } catch (IllegalArgumentException e) {
      // Test passes.
    }
  }

  @Test
  public void testApplyForJsSrc() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);
    assertThat(cleanHtml.applyForJsSrc(dataRef, ImmutableList.of()).getText())
        .isEqualTo("soy.$$cleanHtml(opt_data.myKey)");
  }

  @Test
  public void testApplyForJsSrc_optionalSafeTags() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    JsExpr dataRef = new JsExpr("opt_data.myKey", Integer.MAX_VALUE);

    // All possible OptionalSafeTags.
    ImmutableList<JsExpr> optionalSafeTagsAsJsExprs =
        Arrays.stream(OptionalSafeTag.values())
            .map(OptionalSafeTag::getTagName)
            .map(input -> new JsExpr(String.format("'%s'", input), Integer.MAX_VALUE))
            .collect(toImmutableList());

    assertThat(cleanHtml.applyForJsSrc(dataRef, optionalSafeTagsAsJsExprs).getText())
        .isEqualTo("soy.$$cleanHtml(opt_data.myKey, ['hr', 'li', 'ol', 'span', 'ul'])");

    // Only the specified optional safe tags are passed to $$cleanHtml.
    assertThat(
            cleanHtml
                .applyForJsSrc(dataRef, ImmutableList.of(new JsExpr("'span'", Integer.MAX_VALUE)))
                .getText())
        .isEqualTo("soy.$$cleanHtml(opt_data.myKey, ['span'])");

    // Invalid optional safe tags.
    try {
      cleanHtml.applyForJsSrc(
          dataRef, ImmutableList.of(new JsExpr("'unsupported'", Integer.MAX_VALUE)));
      fail();
    } catch (IllegalArgumentException e) {
      // Test passes.
    }
    try {
      cleanHtml.applyForJsSrc(dataRef, ImmutableList.of(new JsExpr("'li, ul'", Integer.MAX_VALUE)));
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
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "The cleanHtml directive expects arguments to be tag name string "
                  + "literals, such as 'span'. Encountered: $myExtraSafeTags");
    }
  }
  @Test
  public void testApplyForPySrc() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();

    PyExpr data = new PyExpr("'data'", Integer.MAX_VALUE);
    assertThat(cleanHtml.applyForPySrc(data, ImmutableList.of()).getText())
        .isEqualTo("sanitize.clean_html('data')");
  }
  @Test
  public void testApplyForPySrc_optionalSafeTags() {
    CleanHtmlDirective cleanHtml = new CleanHtmlDirective();
    PyExpr data = new PyExpr("'data'", Integer.MAX_VALUE);

    // All possible OptionalSafeTags.
    ImmutableList<PyExpr> optionalSafeTagsAsPyExprs =
        Arrays.stream(OptionalSafeTag.values())
            .map(OptionalSafeTag::getTagName)
            .map(input -> new PyExpr(String.format("'%s'", input), Integer.MAX_VALUE))
            .collect(toImmutableList());

    assertThat(cleanHtml.applyForPySrc(data, optionalSafeTagsAsPyExprs).getText())
        .isEqualTo("sanitize.clean_html('data', ['hr', 'li', 'ol', 'span', 'ul'])");

    // Only the specified optional safe tags are passed to $$cleanHtml.
    PyExpr span = new PyExpr("'span'", Integer.MAX_VALUE);
    assertThat(cleanHtml.applyForPySrc(data, ImmutableList.of(span)).getText())
        .isEqualTo("sanitize.clean_html('data', ['span'])");

    // Invalid optional safe tags.
    try {
      PyExpr unsupported = new PyExpr("'unsupported'", Integer.MAX_VALUE);
      cleanHtml.applyForPySrc(data, ImmutableList.of(unsupported));
      fail("Non-whitelisted tag allowed to be sent as a safe-tag to 'clean_html'.");
    } catch (IllegalArgumentException e) {
      // Test passes.
    }

    try {
      cleanHtml.applyForPySrc(data, ImmutableList.of(new PyExpr("'li, ul'", Integer.MAX_VALUE)));
      fail("Invalid format allowed to be used as a safe-tag in 'clean_html'");
    } catch (IllegalArgumentException e) {
      // Test passes.
    }

    // Invalid parameter syntax.
    try {
      cleanHtml.applyForPySrc(
          data, ImmutableList.of(new PyExpr("$myExtraSafeTags", Integer.MAX_VALUE)));
      fail("Non-String allowed to be used as a safe-tag in 'clean_html'");
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "The cleanHtml directive expects arguments to be tag name string "
                  + "literals, such as 'span'. Encountered: $myExtraSafeTags");
    }
  }

  private SanitizedContent sanitizedHtml(String s) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(s, ContentKind.HTML);
  }
}
