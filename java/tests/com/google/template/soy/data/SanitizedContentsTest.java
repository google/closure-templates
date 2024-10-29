/*
 * Copyright 2012 Google Inc.
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

package com.google.template.soy.data;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlBuilder;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeScripts;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeStyleSheets;
import com.google.common.html.types.SafeStyles;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for SanitizedContents utility class.
 */
@RunWith(JUnit4.class)
public class SanitizedContentsTest {

  @Test
  public void testConcatCombinesHtml() throws Exception {
    String text1 = "one";
    String text2 = "two";
    String text3 = "three";

    SanitizedContent content1 = SanitizedContent.create(text1, ContentKind.HTML, null);
    SanitizedContent content2 = SanitizedContent.create(text2, ContentKind.HTML, null);
    SanitizedContent content3 = SanitizedContent.create(text3, ContentKind.HTML, null);

    assertThat(SanitizedContents.concatHtml(content1, content2, content3))
        .isEqualTo(SanitizedContent.create(text1 + text2 + text3, ContentKind.HTML, null));
  }

  @Test
  public void testConcatReturnsEmpty() throws Exception {
    assertThat(SanitizedContents.concatHtml())
        .isEqualTo(SanitizedContent.create("", ContentKind.HTML, Dir.NEUTRAL));
  }

  @Test
  public void testConcatThrowsExceptionOnDifferentNonHtml() throws Exception {
    try {
      SanitizedContents.concatHtml(
          SanitizedContents.emptyString(ContentKind.HTML),
          SanitizedContents.emptyString(ContentKind.CSS));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().isEqualTo("Can only concat HTML");
    }
  }

  @Test
  public void testConcatCombinesHtmlDir() throws Exception {
    SanitizedContent EMPTY_HTML = SanitizedContents.emptyString(ContentKind.HTML);
    SanitizedContent LTR_HTML = SanitizedContent.create(".", ContentKind.HTML, Dir.LTR);
    SanitizedContent RTL_HTML = SanitizedContent.create(".", ContentKind.HTML, Dir.RTL);
    SanitizedContent NEUTRAL_HTML = SanitizedContent.create(".", ContentKind.HTML, Dir.NEUTRAL);
    SanitizedContent UNKNOWN_DIR_HTML = SanitizedContent.create(".", ContentKind.HTML, null);

    // empty -> neutral
    assertThat(SanitizedContents.concatHtml(EMPTY_HTML).getContentDirection())
        .isEqualTo(Dir.NEUTRAL);

    // x -> x
    assertThat(SanitizedContents.concatHtml(LTR_HTML).getContentDirection()).isEqualTo(Dir.LTR);
    assertThat(SanitizedContents.concatHtml(RTL_HTML).getContentDirection()).isEqualTo(Dir.RTL);
    assertThat(SanitizedContents.concatHtml(NEUTRAL_HTML).getContentDirection())
        .isEqualTo(Dir.NEUTRAL);
    assertThat(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML).getContentDirection()).isNull();

    // x + unknown -> unknown
    assertThat(SanitizedContents.concatHtml(LTR_HTML, UNKNOWN_DIR_HTML).getContentDirection())
        .isNull();
    assertThat(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, LTR_HTML).getContentDirection())
        .isNull();
    assertThat(SanitizedContents.concatHtml(RTL_HTML, UNKNOWN_DIR_HTML).getContentDirection())
        .isNull();
    assertThat(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, RTL_HTML).getContentDirection())
        .isNull();
    assertThat(SanitizedContents.concatHtml(NEUTRAL_HTML, UNKNOWN_DIR_HTML).getContentDirection())
        .isNull();
    assertThat(SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, NEUTRAL_HTML).getContentDirection())
        .isNull();
    assertThat(
            SanitizedContents.concatHtml(UNKNOWN_DIR_HTML, UNKNOWN_DIR_HTML).getContentDirection())
        .isNull();

    // x + neutral -> x
    assertThat(SanitizedContents.concatHtml(LTR_HTML, NEUTRAL_HTML).getContentDirection())
        .isEqualTo(Dir.LTR);
    assertThat(SanitizedContents.concatHtml(NEUTRAL_HTML, LTR_HTML).getContentDirection())
        .isEqualTo(Dir.LTR);
    assertThat(SanitizedContents.concatHtml(RTL_HTML, NEUTRAL_HTML).getContentDirection())
        .isEqualTo(Dir.RTL);
    assertThat(SanitizedContents.concatHtml(NEUTRAL_HTML, RTL_HTML).getContentDirection())
        .isEqualTo(Dir.RTL);
    assertThat(SanitizedContents.concatHtml(NEUTRAL_HTML, NEUTRAL_HTML).getContentDirection())
        .isEqualTo(Dir.NEUTRAL);

    // x + x -> x
    assertThat(SanitizedContents.concatHtml(LTR_HTML, LTR_HTML).getContentDirection())
        .isEqualTo(Dir.LTR);
    assertThat(SanitizedContents.concatHtml(RTL_HTML, RTL_HTML).getContentDirection())
        .isEqualTo(Dir.RTL);

    // LTR + RTL -> unknown
    assertThat(SanitizedContents.concatHtml(LTR_HTML, RTL_HTML).getContentDirection()).isNull();
    assertThat(SanitizedContents.concatHtml(LTR_HTML, RTL_HTML).getContentDirection()).isNull();
  }

  private void assertResourceNameValid(boolean valid, String resourceName, ContentKind kind) {
    try {
      SanitizedContents.pretendValidateResource(resourceName, kind);
      assertWithMessage("No exception was thrown, but wasn't expected to be valid.")
          .that(valid)
          .isTrue();
    } catch (IllegalArgumentException e) {
      assertWithMessage("Exception was thrown, but was expected to be valid.")
          .that(valid)
          .isFalse();
    }
  }

  @Test
  public void testPretendValidateResource() {
    // Correct resources.
    assertResourceNameValid(true, "test.js", ContentKind.JS);
    assertResourceNameValid(true, "/test/foo.bar.js", ContentKind.JS);
    assertResourceNameValid(true, "test.html", ContentKind.HTML);
    assertResourceNameValid(true, "test.svg", ContentKind.HTML);
    assertResourceNameValid(true, "test.css", ContentKind.CSS);

    // Wrong resource kind.
    assertResourceNameValid(false, "test.css", ContentKind.HTML);
    assertResourceNameValid(false, "test.html", ContentKind.JS);
    assertResourceNameValid(false, "test.js", ContentKind.CSS);

    // No file extensions supported for these kinds.
    assertResourceNameValid(false, "test.attributes", ContentKind.ATTRIBUTES);

    // Missing extension entirely.
    assertResourceNameValid(false, "test", ContentKind.JS);
  }

  @Test
  public void testGetDefaultDir() {
    assertThat(ContentKind.JS.getDefaultDir()).isEqualTo(Dir.LTR);
    assertThat(ContentKind.CSS.getDefaultDir()).isEqualTo(Dir.LTR);
    assertThat(ContentKind.ATTRIBUTES.getDefaultDir()).isEqualTo(Dir.LTR);
    assertThat(ContentKind.URI.getDefaultDir()).isEqualTo(Dir.LTR);
    assertThat(ContentKind.TRUSTED_RESOURCE_URI.getDefaultDir()).isEqualTo(Dir.LTR);

    assertThat(ContentKind.TEXT.getDefaultDir()).isNull();
    assertThat(ContentKind.HTML.getDefaultDir()).isNull();
  }

  @Test
  public void testCreateWithoutDir() {
    assertThat(SanitizedContent.create("abc", ContentKind.HTML).getContentDirection()).isNull();
    assertThat(SanitizedContent.create("abc", ContentKind.JS).getContentDirection())
        .isEqualTo(Dir.LTR);
  }

  @Test
  public void testConstantUri() {
    // Passing case. We actually can't test a failing case because it won't compile.
    SanitizedContent uri = SanitizedContents.constantUri("itms://blahblah");
    assertThat(uri.getContent()).isEqualTo("itms://blahblah");
    assertThat(uri.getContentKind()).isEqualTo(ContentKind.URI);
    assertThat(uri.getContentDirection()).isEqualTo(ContentKind.URI.getDefaultDir());
  }

  @Test
  public void testNumberJs() {
    SanitizedContent js = SanitizedContents.numberJs(42);
    assertThat(js.getContent()).isEqualTo("42");
    assertThat(js.getContentKind()).isEqualTo(ContentKind.JS);

    js = SanitizedContents.numberJs(4.2);
    assertThat(js.getContent()).isEqualTo("4.2");
    assertThat(js.getContentKind()).isEqualTo(ContentKind.JS);
  }

  @Test
  public void testCssTypeConversions() {
    final String testStyleSheetContent = "div { display: none; }";
    SafeStyleSheet safeStyleSheet = SafeStyleSheets.fromConstant(testStyleSheetContent);
    CssParam styleSheetParam = CssParam.of(safeStyleSheet);
    SanitizedContent sanitizedStyleSheet = SanitizedContents.fromCss(styleSheetParam);

    assertThat(sanitizedStyleSheet.getContentKind()).isEqualTo(ContentKind.CSS);
    assertThat(sanitizedStyleSheet.getContent()).isEqualTo(testStyleSheetContent);
    assertThat(sanitizedStyleSheet.toSafeStyleSheet()).isEqualTo(safeStyleSheet);

    final String testStyleContent = "display: none;";
    SafeStyle safeStyle = SafeStyles.fromConstant(testStyleContent);
    CssParam styleParam = CssParam.of(safeStyle);
    SanitizedContent sanitizedStyle = SanitizedContents.fromCss(styleParam);

    assertThat(sanitizedStyle.getContentKind()).isEqualTo(ContentKind.CSS);
    assertThat(sanitizedStyle.getContent()).isEqualTo(testStyleContent);
    assertThat(sanitizedStyle.toSafeStyle()).isEqualTo(safeStyle);
  }

  @Test
  public void testCommonSafeHtmlTypeConversions() {
    final String helloWorldHtml = "Hello <em>World</em>";
    SafeHtml safeHtml =
        SafeHtmls.concat(
            SafeHtmls.htmlEscape("Hello "),
            new SafeHtmlBuilder("em").escapeAndAppendContent("World").build());
    SanitizedContent sanitizedHtml = SanitizedContents.fromSafeHtml(safeHtml);
    assertThat(sanitizedHtml.getContentKind()).isEqualTo(ContentKind.HTML);
    assertThat(sanitizedHtml.getContent()).isEqualTo(helloWorldHtml);
    assertThat(sanitizedHtml.toSafeHtml()).isEqualTo(safeHtml);

    // Proto conversions.
    SafeHtmlProto safeHtmlProto = sanitizedHtml.toSafeHtmlProto();
    assertThat(SafeHtmls.fromProto(safeHtmlProto)).isEqualTo(safeHtml);
    assertThat(SanitizedContents.fromSafeHtmlProto(safeHtmlProto).getContent())
        .isEqualTo(helloWorldHtml);
  }

  @Test
  public void testCommonSafeScriptTypeConversions() {
    final String testScript = "window.alert('hello');";
    SafeScript safeScript = SafeScripts.fromConstant(testScript);
    SanitizedContent sanitizedScript = SanitizedContents.fromSafeScript(safeScript);
    assertThat(sanitizedScript.getContentKind()).isEqualTo(ContentKind.JS);
    assertThat(sanitizedScript.getContent()).isEqualTo(testScript);
    assertThat(sanitizedScript.getContent()).isEqualTo(safeScript.getSafeScriptString());

    // Proto conversions.
    SafeScriptProto safeScriptProto = SafeScripts.toProto(safeScript);
    assertThat(SafeScripts.fromProto(safeScriptProto)).isEqualTo(safeScript);
    assertThat(SanitizedContents.fromSafeScriptProto(safeScriptProto).getContent())
        .isEqualTo(testScript);
  }

  @Test
  public void testCommonSafeStyleSheetTypeConversions() {
    final String testCss = "div { display: none; }";
    SafeStyleSheet safeCss = SafeStyleSheets.fromConstant(testCss);
    SanitizedContent sanitizedCss = SanitizedContents.fromSafeStyleSheet(safeCss);
    assertThat(sanitizedCss.getContentKind()).isEqualTo(ContentKind.CSS);
    assertThat(sanitizedCss.getContent()).isEqualTo(testCss);
    assertThat(sanitizedCss.toSafeStyleSheet()).isEqualTo(safeCss);

    // Proto conversions.
    SafeStyleSheetProto safeCssProto = sanitizedCss.toSafeStyleSheetProto();
    assertThat(SafeStyleSheets.fromProto(safeCssProto)).isEqualTo(safeCss);
    assertThat(SanitizedContents.fromSafeStyleSheetProto(safeCssProto).getContent())
        .isEqualTo(testCss);
  }

  @Test
  public void testCommonSafeUrlTypeConversions() {
    final String testUrl = "http://blahblah";
    SanitizedContent sanitizedConstantUri = SanitizedContents.constantUri(testUrl);
    SafeUrl safeUrl = SafeUrls.fromConstant(testUrl);

    SanitizedContent sanitizedUrl = SanitizedContents.fromSafeUrl(safeUrl);
    assertThat(sanitizedUrl.getContentKind()).isEqualTo(ContentKind.URI);
    assertThat(sanitizedUrl.getContent()).isEqualTo(testUrl);
    assertThat(sanitizedUrl).isEqualTo(sanitizedConstantUri);

    // Proto conversions.
    SafeUrlProto safeUrlProto = SafeUrls.toProto(safeUrl);
    SanitizedContent sanitizedUrlProto = SanitizedContents.fromSafeUrlProto(safeUrlProto);
    assertThat(sanitizedUrlProto.getContent()).isEqualTo(testUrl);
    assertThat(sanitizedUrlProto).isEqualTo(sanitizedConstantUri);
  }

  @Test
  public void testWrongContentKindThrows_html() {
    SanitizedContent notHtml =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not HTML", ContentKind.CSS);
    try {
      notHtml.toSafeHtml();
      fail("Should have thrown on SanitizedContent of kind other than HTML");
    } catch (IllegalStateException expected) {
    }
    try {
      notHtml.toSafeHtmlProto();
      fail("Should have thrown on SanitizedContent of kind other than HTML");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWrongContentKindThrows_script() {
    SanitizedContent notJs = UnsafeSanitizedContentOrdainer.ordainAsSafe("not JS", ContentKind.URI);
    try {
      notJs.toSafeScriptProto();
      fail("Should have thrown on SanitizedContent of kind other than JS");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWrongContentKindThrows_css() {
    SanitizedContent notCss =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not CSS", ContentKind.HTML);
    try {
      notCss.toSafeStyleProto();
      fail("Should have thrown on SanitizedContent of kind other than CSS");
    } catch (IllegalStateException expected) {
    }
    try {
      notCss.toSafeStyleSheet();
      fail("Should have thrown on SanitizedContent of kind other than CSS");
    } catch (IllegalStateException expected) {
    }
    try {
      notCss.toSafeStyleSheetProto();
      fail("Should have thrown on SanitizedContent of kind other than CSS");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testWrongContentKindThrows_uri() {
    SanitizedContent notUri =
        UnsafeSanitizedContentOrdainer.ordainAsSafe("not URI", ContentKind.HTML);
    try {
      notUri.toSafeUrlProto();
      fail("Should have thrown on SanitizedContent of kind other than URI");
    } catch (IllegalStateException expected) {
    }
    try {
      notUri.toTrustedResourceUrlProto();
      fail("Should have thrown on SanitizedContent of kind other than URI");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void testInvalidStyleSheetContentThrows() {
    for (String css :
        new String[] {
          "display: none;", "{ display: none; }", "/* a comment */", "@import url('test.css');"
        }) {
      SanitizedContent cssStyle = UnsafeSanitizedContentOrdainer.ordainAsSafe(css, ContentKind.CSS);
      try {
        cssStyle.toSafeStyleSheet();
        fail("Should have thrown on CSS SanitizedContent with invalid stylesheet:" + css);
      } catch (IllegalStateException expected) {
      }
      try {
        cssStyle.toSafeStyleSheetProto();
        fail("Should have thrown on CSS SanitizedContent with invalid stylesheet:" + css);
      } catch (IllegalStateException expected) {
      }
    }
  }

  private static Map<String, String> parseAttributes(String attributes) {
    return Maps.transformValues(
        SanitizedContent.parseAttributes(attributes),
        v -> Optional.ofNullable(v.escapedValue()).orElse(""));
  }

  @Test
  public void testParseAttributes() {
    assertThat(parseAttributes("")).isEmpty();
    assertThat(parseAttributes("   ")).isEmpty();
    assertThat(parseAttributes("a=b c=d")).isEqualTo(ImmutableMap.of("a", "b", "c", "d"));
    assertThat(parseAttributes("a='b' c=\"d\"")).isEqualTo(ImmutableMap.of("a", "b", "c", "d"));
    assertThat(parseAttributes("a c=\"d\" e"))
        .isEqualTo(ImmutableMap.of("a", "", "c", "d", "e", ""));

    // Newlines and tabs are allowed in attribute values.
    // We allow attributes to abut each other without a space, this is common in HTML though
    // technically out of spec.
    assertThat(parseAttributes("a='b\n\tx'\r\nc=\"d\"e=f\r"))
        .containsExactly("a", "b\n\tx", "c", "d", "e", "f");

    assertThat(parseAttributes("CLASS=foo")).isEqualTo(ImmutableMap.of("class", "foo"));
    assertThat(parseAttributes("A=1 a")).isEqualTo(ImmutableMap.of("a", ""));

    // test various silly amounts of whitespace
    assertThat(parseAttributes("a = b c        =d e=    'f' g"))
        .isEqualTo(ImmutableMap.of("a", "b", "c", "d", "e", "f", "g", ""));

    assertThat(parseAttributes("a='\"b'")).containsExactly("a", "&quot;b");
  }

  @Test
  public void testParseAttributes_withUppercase() {
    assertThat(parseAttributes("CLASS=foo viewBox='1 2 3 4'"))
        .isEqualTo(ImmutableMap.of("class", "foo", "viewbox", "1 2 3 4"));
  }

  @Test
  public void testParseAttributes_errors() {
    assertThrows(IllegalArgumentException.class, () -> parseAttributes("a="));
    assertThrows(IllegalArgumentException.class, () -> parseAttributes("a='"));
    assertThrows(IllegalArgumentException.class, () -> parseAttributes("a=\""));
    assertThrows(IllegalArgumentException.class, () -> parseAttributes("a=\"'"));
    assertThrows(IllegalArgumentException.class, () -> parseAttributes("a='\""));
    assertThrows(IllegalArgumentException.class, () -> parseAttributes("=foo"));
  }

  @Test
  public void testSanitizedAttributes_getAsAttributesMap() {
    SanitizedContent attributes = SanitizedContent.create("a=b", ContentKind.ATTRIBUTES);
    assertThat(attributes.getAsAttributesMap())
        .isEqualTo(
            ImmutableMap.of("a", SanitizedContent.AttributeValue.createFromEscapedValue("b")));
  }

  @Test
  public void testSanitizedHtml_getAsAttributesMap() {
    SanitizedContent attributes = SanitizedContent.create("a=b", ContentKind.HTML);
    assertThrows(IllegalStateException.class, () -> attributes.getAsAttributesMap());
  }

  @Test
  public void testAttributeValue() {
    assertThat(SanitizedContent.AttributeValue.createFromEscapedValue("foo").escapedValue())
        .isEqualTo("foo");
    assertThat(SanitizedContent.AttributeValue.createFromUnescapedValue("foo").escapedValue())
        .isEqualTo("foo");
    assertThat(SanitizedContent.AttributeValue.createFromUnescapedValue("'").escapedValue())
        .isEqualTo("&#39;");
    assertThat(SanitizedContent.AttributeValue.createFromEscapedValue("&#39;").unescapedValue())
        .isEqualTo("'");

    assertThrows(
        IllegalArgumentException.class,
        () -> SanitizedContent.AttributeValue.createFromEscapedValue("\""));
  }

  @Test
  public void testAttributes_hasContent() {
    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(ImmutableMap.of()).hasContent())
        .isFalse();
    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafeAttributes(
                    ImmutableMap.of(
                        "a", SanitizedContent.AttributeValue.createFromEscapedValue("b")))
                .hasContent())
        .isTrue();
    assertThat(UnsafeSanitizedContentOrdainer.ordainAsSafe("", ContentKind.ATTRIBUTES).hasContent())
        .isFalse();
    assertThat(
            UnsafeSanitizedContentOrdainer.ordainAsSafe("a=b", ContentKind.ATTRIBUTES).hasContent())
        .isTrue();
  }

  @Test
  public void testBufferedContents_hasContent() throws IOException {
    var buffering = LoggingAdvisingAppendable.buffering(ContentKind.HTML);
    assertThat(buffering.getAsSanitizedContent().hasContent()).isFalse();
    buffering.append("foo");
    assertThat(buffering.getAsSanitizedContent().hasContent()).isTrue();

    buffering = LoggingAdvisingAppendable.buffering(ContentKind.HTML);
    buffering.enterLoggableElement(LogStatement.create(0, null, false));
    assertThat(buffering.getAsSanitizedContent().hasContent()).isFalse();
    buffering.exitLoggableElement();
    assertThat(buffering.getAsSanitizedContent().hasContent()).isFalse();
    buffering.append("bar");
    assertThat(buffering.getAsSanitizedContent().hasContent()).isTrue();
  }
}
