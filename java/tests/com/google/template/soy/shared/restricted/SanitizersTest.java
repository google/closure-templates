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

package com.google.template.soy.shared.restricted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.Dir;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.shared.restricted.TagWhitelist.OptionalSafeTag;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SanitizersTest {
  private static final String ASCII_CHARS;

  static {
    StringBuilder sb = new StringBuilder(0x80);
    for (int i = 0; i < 0x80; ++i) {
      sb.append((char) i);
    }
    ASCII_CHARS = sb.toString();
  }

  private static final SoyValue ASCII_CHARS_SOYDATA = StringData.forValue(ASCII_CHARS);

  /** Substrings that might change the parsing mode of scripts they are embedded in. */
  private static final String[] EMBEDDING_HAZARDS =
      new String[] {"</script", "</style", "<!--", "-->", "<![CDATA[", "]]>"};

  @Test
  public void testEscapeJsString() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals(
        "\\x00 \\x22 \\x27 \\\\ \\r \\n \\u2028 \\u2029",
        Sanitizers.escapeJsString("\u0000 \" \' \\ \r \n \u2028 \u2029"));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.escapeJsString(hazard).contains(hazard));
    }

    // Check correctness of other Latins.
    String escapedAscii =
        ("\\x00\u0001\u0002\u0003\u0004\u0005\u0006\u0007\\x08\\t\\n\\x0b\\f\\r\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + " !\\x22#$%\\x26\\x27()*+,-.\\/"
            + "0123456789:;\\x3c\\x3d\\x3e?"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ\\x5b\\\\\\x5d^_"
            + "`abcdefghijklmno"
            + "pqrstuvwxyz\\x7b|\\x7d~\u007f");
    assertEquals(escapedAscii, Sanitizers.escapeJsString(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.escapeJsString(ASCII_CHARS_SOYDATA));
  }

  @Test
  public void testEscapeJsRegExpString() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals(
        "\\x00 \\x22 \\x27 \\\\ \\/ \\r \\n \\u2028 \\u2029"
            +
            // RegExp operators.
            " \\x24\\x5e\\x2a\\x28\\x29\\x2d\\x2b\\x7b\\x7d\\x5b\\x5d\\x7c\\x3f",
        Sanitizers.escapeJsRegex("\u0000 \" \' \\ / \r \n \u2028 \u2029" + " $^*()-+{}[]|?"));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.escapeJsRegex(hazard).contains(hazard));
    }

    String escapedAscii =
        ("\\x00\u0001\u0002\u0003\u0004\u0005\u0006\u0007\\x08\\t\\n\\x0b\\f\\r\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + " !\\x22#\\x24%\\x26\\x27\\x28\\x29\\x2a\\x2b\\x2c\\x2d\\x2e\\/"
            + "0123456789\\x3a;\\x3c\\x3d\\x3e\\x3f"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ\\x5b\\\\\\x5d\\x5e_"
            + "`abcdefghijklmno"
            + "pqrstuvwxyz\\x7b\\x7c\\x7d~\u007f");
    assertEquals(escapedAscii, Sanitizers.escapeJsRegex(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.escapeJsRegex(ASCII_CHARS_SOYDATA));
  }

  @Test
  public void testEscapeJsValue() {
    assertEquals( // Adds quotes.
        "'Don\\x27t run with \\x22scissors\\x22.\\n'",
        Sanitizers.escapeJsValue("Don't run with \"scissors\".\n"));
    assertEquals( // SoyValue version does the same as String version.
        "'Don\\x27t run with \\x22scissors\\x22.\\n'",
        Sanitizers.escapeJsValue(StringData.forValue("Don't run with \"scissors\".\n")));
    assertEquals(" 4.0 ", Sanitizers.escapeJsValue(IntegerData.forValue(4)));
    assertEquals(" 4.5 ", Sanitizers.escapeJsValue(FloatData.forValue(4.5)));
    assertEquals(" true ", Sanitizers.escapeJsValue(BooleanData.TRUE));
    assertEquals(" null ", Sanitizers.escapeJsValue(NullData.INSTANCE));
    assertEquals(
        "foo() + bar",
        Sanitizers.escapeJsValue(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo() + bar", SanitizedContent.ContentKind.JS)));
    // Wrong content kind should be wrapped in a string.
    assertEquals(
        "'foo() + bar'",
        Sanitizers.escapeJsValue(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo() + bar", SanitizedContent.ContentKind.HTML)));
  }

  @Test
  public void testEscapeCssString() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals(
        "\\0  \\22  \\27  \\5c  \\a  \\c  \\d ",
        Sanitizers.escapeCssString("\u0000 \" \' \\ \n \u000c \r"));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.escapeCssString(hazard).contains(hazard));
    }

    String escapedAscii =
        ("\\0 \u0001\u0002\u0003\u0004\u0005\u0006\u0007\\8 \\9 \\a \\b \\c \\d \u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F"
            + " !\\22 #$%\\26 \\27 \\28 \\29 \\2a +,-.\\2f "
            + "0123456789\\3a \\3b \\3c \\3d \\3e ?"
            + "\\40 ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\5c ]^_"
            + "`abcdefghijklmno"
            + "pqrstuvwxyz\\7b |\\7d ~\u007f");
    assertEquals(escapedAscii, Sanitizers.escapeCssString(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.escapeCssString(ASCII_CHARS_SOYDATA));
  }

  @Test
  public void testFilterCssValue() {
    assertEquals("33px", Sanitizers.filterCssValue("33px"));
    assertEquals("33px", Sanitizers.filterCssValue(StringData.forValue("33px")));
    assertEquals("-.5em", Sanitizers.filterCssValue("-.5em"));
    assertEquals("inherit", Sanitizers.filterCssValue("inherit"));
    assertEquals("display", Sanitizers.filterCssValue("display"));
    assertEquals("none", Sanitizers.filterCssValue("none"));
    assertEquals("#id", Sanitizers.filterCssValue("#id"));
    assertEquals(".class", Sanitizers.filterCssValue(".class"));
    assertEquals("red", Sanitizers.filterCssValue("red"));
    assertEquals("#aabbcc", Sanitizers.filterCssValue("#aabbcc"));
    assertEquals(
        "0px 5px 10px  rgba(0,0,0, 0.3)",
        Sanitizers.filterCssValue("0px 5px 10px  rgba(0,0,0, 0.3)"));
    assertEquals("zSoyz", Sanitizers.filterCssValue(" "));
    assertEquals("zSoyz", Sanitizers.filterCssValue("expression"));
    assertEquals("zSoyz", Sanitizers.filterCssValue(StringData.forValue("expression")));
    assertEquals("zSoyz", Sanitizers.filterCssValue("Expression"));
    assertEquals("zSoyz", Sanitizers.filterCssValue("\\65xpression"));
    assertEquals("zSoyz", Sanitizers.filterCssValue("\\65 xpression"));
    assertEquals("zSoyz", Sanitizers.filterCssValue("-moz-binding"));
    assertEquals("zSoyz", Sanitizers.filterCssValue("</style><script>alert('foo')</script>/*"));
    assertEquals("zSoyz", Sanitizers.filterCssValue("color:expression('whatever')"));
    assertEquals(
        "color:expression('whatever')",
        Sanitizers.filterCssValue(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "color:expression('whatever')", SanitizedContent.ContentKind.CSS)));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.filterCssValue(hazard).contains(hazard));
    }
  }

  @Test
  public void testFilterHtmlAttributes() {
    assertEquals("dir", Sanitizers.filterHtmlAttributes("dir"));
    assertEquals("data-foo", Sanitizers.filterHtmlAttributes("data-foo"));
    assertEquals("hamburger", Sanitizers.filterHtmlAttributes("hamburger"));
    assertEquals("action-packed", Sanitizers.filterHtmlAttributes("action-packed"));
    assertEquals("dir", Sanitizers.filterHtmlAttributes(StringData.forValue("dir")));
    assertEquals("zSoyz", Sanitizers.filterHtmlAttributes("><script>alert('foo')</script"));
    assertEquals("zSoyz", Sanitizers.filterHtmlAttributes("style"));
    assertEquals("zSoyz", Sanitizers.filterHtmlAttributes("onclick"));
    assertEquals("zSoyz", Sanitizers.filterHtmlAttributes("href"));

    assertEquals(
        "a=1 b=2 dir=\"ltr\"",
        Sanitizers.filterHtmlAttributes(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "a=1 b=2 dir=\"ltr\"", SanitizedContent.ContentKind.ATTRIBUTES)));
    assertEquals(
        "Should append a space to parse correctly",
        "foo=\"bar\" dir=ltr ",
        Sanitizers.filterHtmlAttributes(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo=\"bar\" dir=ltr", SanitizedContent.ContentKind.ATTRIBUTES)));
    assertEquals(
        "Should append a space to parse correctly",
        "foo=\"bar\" checked ",
        Sanitizers.filterHtmlAttributes(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo=\"bar\" checked", SanitizedContent.ContentKind.ATTRIBUTES)));
    assertEquals(
        "No duplicate space should be added",
        "foo=\"bar\" checked ",
        Sanitizers.filterHtmlAttributes(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo=\"bar\" checked ", SanitizedContent.ContentKind.ATTRIBUTES)));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.filterHtmlAttributes(hazard).contains(hazard));
    }
  }

  @Test
  public void testFilterHtmlElementName() {
    assertEquals("h1", Sanitizers.filterHtmlElementName("h1"));
    assertEquals("h1", Sanitizers.filterHtmlElementName(StringData.forValue("h1")));
    assertEquals("zSoyz", Sanitizers.filterHtmlElementName("script"));
    assertEquals("zSoyz", Sanitizers.filterHtmlElementName("style"));
    assertEquals("zSoyz", Sanitizers.filterHtmlElementName("SCRIPT"));
    assertEquals("zSoyz", Sanitizers.filterHtmlElementName("><script>alert('foo')</script"));
    assertEquals("zSoyz", Sanitizers.filterHtmlElementName(StringData.forValue("<h1>")));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.filterHtmlElementName(hazard).contains(hazard));
    }
  }

  @Test
  public void testEscapeUri() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals(
        "%00%0A%0C%0D%22%23%26%27%2F%3A%3D%3F%40", Sanitizers.escapeUri("\u0000\n\f\r\"#&'/:=?@"));

    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.escapeUri(hazard).contains(hazard));
    }

    String escapedAscii =
        ("%00%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F"
            + "%10%11%12%13%14%15%16%17%18%19%1A%1B%1C%1D%1E%1F"
            + "%20%21%22%23%24%25%26%27%28%29*%2B%2C-.%2F"
            + "0123456789%3A%3B%3C%3D%3E%3F"
            + "%40ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ%5B%5C%5D%5E_"
            + "%60abcdefghijklmno"
            + "pqrstuvwxyz%7B%7C%7D%7E%7F");
    assertEquals(escapedAscii, Sanitizers.escapeUri(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.escapeUri(ASCII_CHARS_SOYDATA));
    // Test full-width.  The two characters at the right are a full-width '#' and ':'.
    assertEquals("%EF%BC%83%EF%BC%9A", Sanitizers.escapeUri("\uff03\uff1a"));
    // Test other unicode codepoints.
    assertEquals("%C2%85%E2%80%A8", Sanitizers.escapeUri("\u0085\u2028"));
    // SanitizedUris are not special in URIs. For example, in /redirect?continue={$url}, we clearly
    // don't want ampersands in the continue URL to break out of the continue param.
    assertEquals(
        "foo%20%28%2527%26%27%29",
        Sanitizers.escapeUri(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo (%27&')", SanitizedContent.ContentKind.URI)));
    // Test SanitizedContent of the wrong kind -- it should be completely escaped.
    assertEquals(
        "%2528%2529",
        Sanitizers.escapeUri(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "%28%29", SanitizedContent.ContentKind.HTML)));
  }

  @Test
  public void testNormalizeUriAndFilterNormalizeUri() {
    // This test contains an ANSI escape sequence. (\u000e). If the logger is logging to a terminal,
    // the terminal will be corrupted. As a workaround, silence logs below the WARNING level.
    Logger.getLogger(Sanitizers.class.getName()).setLevel(Level.SEVERE);
    for (String hazard : EMBEDDING_HAZARDS) {
      assertFalse(hazard, Sanitizers.normalizeUri(hazard).contains(hazard));
      assertFalse(hazard, Sanitizers.filterNormalizeUri(hazard).contains(hazard));
    }

    String escapedAscii =
        ("%00%01%02%03%04%05%06%07%08%09%0A%0B%0C%0D%0E%0F"
            + "%10%11%12%13%14%15%16%17%18%19%1A%1B%1C%1D%1E%1F"
            + "%20!%22#$%&%27%28%29*+,-./"
            + "0123456789:;%3C=%3E?"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[%5C]^_"
            + "`abcdefghijklmno"
            + "pqrstuvwxyz%7B|%7D~%7F");
    assertEquals(escapedAscii, Sanitizers.normalizeUri(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.normalizeUri(ASCII_CHARS_SOYDATA));
    assertEquals("#" + escapedAscii, Sanitizers.filterNormalizeUri("#" + ASCII_CHARS));

    // Test full-width.  The two characters at the right are a full-width '#' and ':'.
    String escapedFullWidth = "%EF%BC%83%EF%BC%9A";
    String fullWidth = "\uff03\uff1a";
    assertEquals(escapedFullWidth, Sanitizers.normalizeUri(fullWidth));
    assertEquals(escapedFullWidth, Sanitizers.filterNormalizeUri(fullWidth));

    String[] badForAllFilters =
        new String[] {
          // Test filtering of URI starts.
          "javascript:",
          "javascript:alert(1337)",
          "vbscript:alert(1337)",
          "livescript:alert(1337)",
          "data:,alert(1337)",
          "data:text/javascript,alert%281337%29",
          "file:///etc/passwd",
          // Testcases from http://ha.ckers.org/xss.html
          "JaVaScRiPt:alert(1337)",
          // Using HTML entities to obfuscate javascript:alert('XSS');
          "&#106;&#97;&#118;&#97;&#115;&#99;&#114;&#105;&#112;&#116;"
              + "&#58;&#97;&#108;&#101;&#114;&#116;&#40;&#39;&#88;&#83;&#83;&#39;&#41;",
          // Using longer HTML entities to obfuscate the same.
          "&#0000106&#0000097&#0000118&#0000097&#0000115&#0000099&#0000114&#0000105"
              + "&#0000112&#0000116&#0000058&#0000097&#0000108&#0000101&#0000114&#0000116"
              + "&#0000040&#0000039&#0000088&#0000083&#0000083&#0000039&#0000041",
          // Using hex HTML entities to obfuscate the same.
          "&#x6A&#x61&#x76&#x61&#x73&#x63&#x72&#x69&#x70&#x74"
              + "&#x3A&#x61&#x6C&#x65&#x72&#x74&#x28&#x27&#x58&#x53&#x53&#x27&#x29",
          "jav\tascript:alert('XSS');",
          "jav&#x09;ascript:alert('XSS');",
          "jav&#x0A;ascript:alert('XSS');",
          "jav&#x0D;ascript:alert('XSS');",
          "\nj\n\na\nv\na\ns\nc\nr\ni\np\nt\n:\na\nl\ne\nr\nt\n(\n1\n3\n3\n7\n)",
          "\u000e  javascript:alert('XSS');"
        };

    for (String badCase : badForAllFilters) {
      assertEquals("about:invalid#zSoyz", Sanitizers.filterNormalizeUri(badCase));
      assertInvalidMediaUri(badCase);
    }

    assertFalse(
        Sanitizers.filterNormalizeUri("javascript\uff1aalert(1337);").contains("javascript\uff1a"));

    // Tests of filtering heirarchy within uri path (/.. etc )
    assertEquals("about:invalid#zSoyz", Sanitizers.filterNormalizeUri("a/../"));
    assertEquals("about:invalid#zSoyz", Sanitizers.filterNormalizeUri("/..?"));
    assertEquals(
        "about:invalid#zSoyz", Sanitizers.filterNormalizeUri("http://bad.url.com../../s../.#.."));
    assertEquals(
        "about:invalid#zSoyz", Sanitizers.filterNormalizeUri("http://badurl.com/normal/../unsafe"));

    // Things we should accept.
    String[] goodForAllFilters =
        new String[] {
          "http://google.com/",
          "https://google.com/",
          "HTTP://google.com/",
          "?a=b&c=d",
          "?a=b:c&d=e",
          "//foo.com:80/",
          "//foo.com/",
          "/foo:bar/",
          "#a:b",
          "#",
          "/",
          "",
          "../",
          ".%2E",
          "..",
          "%2E%2E",
          "%2e%2e",
          "%2e.",
          "http://goodurl.com/.stuff/?/../.",
          "http://good.url.com../..s../.#..",
          "http://goodurl.com/normal/%2e/unsafe?",
        };

    for (String goodCase : goodForAllFilters) {
      assertEquals(goodCase, Sanitizers.filterNormalizeUri(goodCase));
      assertValidMediaUri(goodCase);
    }

    // Test normalization.
    assertEquals(
        "javascript:handleClick%28%29",
        Sanitizers.filterNormalizeUri(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "javascript:handleClick()", SanitizedContent.ContentKind.URI)));
    // Except doesn't handle HTML.
    assertEquals(
        "about:invalid#zSoyz",
        Sanitizers.filterNormalizeUri(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "javascript:handleClick()", SanitizedContent.ContentKind.HTML)));

    Logger.getLogger(Sanitizers.class.getName()).setLevel(Level.INFO);
  }

  private static void assertValidImageDataUri(String uri) {
    assertImageDataUri(uri, true);
  }

  private static void assertInvalidImageDataUri(String uri) {
    assertImageDataUri(uri, false);
  }

  private static void assertImageDataUri(String uri, boolean valid) {
    SanitizedContent result = Sanitizers.filterImageDataUri(uri);
    assertEquals(valid ? uri : "data:image/gif;base64,zSoyz", result.toString());
    assertEquals(SanitizedContent.ContentKind.URI, result.getContentKind());
  }

  private static void assertValidMediaUri(String uri) {
    assertMediaUri(uri, true);
  }

  private static void assertInvalidMediaUri(String uri) {
    assertMediaUri(uri, false);
  }

  private static void assertMediaUri(String uri, boolean valid) {
    assertEquals(valid ? uri : "about:invalid#zSoyz", Sanitizers.filterNormalizeMediaUri(uri));
  }

  @Test
  public void testFilterImageDataUri() {
    String allBase64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz/+";
    // NOTE: These are not semantically correct images, nor even correctly padded base64.  Note the
    // "=" character is only used at the end, as zero padding.
    String[] validImageDataUris =
        new String[] {
          "data:image/png;base64," + allBase64Chars,
          "data:image/png;base64," + allBase64Chars + allBase64Chars,
          "data:image/png;base64," + allBase64Chars + "==",
          "data:image/gif;base64," + allBase64Chars,
          "data:image/tiff;base64," + allBase64Chars,
          "data:image/webp;base64," + allBase64Chars,
          "data:image/bmp;base64," + allBase64Chars
        };
    // These are safe specifically in the src of img, but not in other URI contexts where they
    // could be risky.
    String[] acceptableMediaUrisButNotImageDataUris =
        new String[] {
          // Blobs, now allowed!
          "blob:http%3A//www.example.com/e00aa4f2-d86e-40ed-8b15-ad09c91b8989",
          // SVG (not dangerous in <img> but dangerous in object, etc)
          "data:image/svg+xml;base64," + allBase64Chars,
          // Wrong MIME type (image/foo)
          "data:image/blargyblarg99;base64," + allBase64Chars,
          "data:image/foo;base64," + allBase64Chars,
          // Relative and remote URLs.
          "/foo",
          "https://www.google.com",
          "https://www.google.com/foo.png"
        };
    String[] invalidMediaDataUris =
        new String[] {
          // Video URIs are not yet supported until we can resolve whether they present a social
          // engineering risk, as videos might be assumed to be page-controlled instead of
          // user-controlled.
          "data:video/mp4;base64," + allBase64Chars,
          "data:video/mp4;base64," + allBase64Chars + allBase64Chars,
          "data:video/mp4;base64," + allBase64Chars + "==",
          "data:video/ogg;base64," + allBase64Chars,
          "data:video/webm;base64," + allBase64Chars,
          "data:video/mpeg;base64," + allBase64Chars,
          // Audio isn't yet supported, until we can resolve whether they present a phishing risk.
          "data:audio/ogg;base64," + allBase64Chars,
          // Wrong protocol type (beta)
          "beta:image/foo;base64," + allBase64Chars,
          // bake64 instead of base64
          "data:image/png;bake64," + allBase64Chars,
          // Invalid chars .()
          "data:image/png;base64,ABCD.()",
          // Extra junk at beginning and end. To ensure regexp is multiline-safe.
          "\ndata:image/png;base64," + allBase64Chars,
          "xdata:image/png;base64," + allBase64Chars,
          ".data:image/png;base64," + allBase64Chars,
          "data:image/png;base64," + allBase64Chars + "\n",
          "data:image/png;base64," + allBase64Chars + "=x",
          "data:image/png;base64," + allBase64Chars + ".",
          "NOTblob:http%3A//www.example.com/e00aa4f2-d86e-40ed-8b15-ad09c91b8989",
          "\nblob:http%3A//www.example.com/e00aa4f2-d86e-40ed-8b15-ad09c91b8989",
          "NOThttps://www.google.com",
          "\nhttps://www.google.com/foo.png",
          // "=" in wrong place:
          "data:image/png;base64," + allBase64Chars + "=" + allBase64Chars,
          // Junk in MIME type.
          "data:image/png*;base64," + allBase64Chars,
        };

    for (String uri : validImageDataUris) {
      assertValidImageDataUri(uri);
      assertValidMediaUri(uri);
    }

    for (String uri : acceptableMediaUrisButNotImageDataUris) {
      assertInvalidImageDataUri(uri);
      assertValidMediaUri(uri);
    }

    for (String uri : invalidMediaDataUris) {
      assertInvalidImageDataUri(uri);
      assertInvalidMediaUri(uri);
    }
  }

  @Test
  public void testEscapeHtml() {
    String escapedAscii =
        ("&#0;\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\f\r\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + " !&quot;#$%&amp;&#39;()*+,-./"
            + "0123456789:;&lt;=&gt;?"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\]^_"
            + "`abcdefghijklmno"
            + "pqrstuvwxyz{|}~\u007f");
    assertEquals(escapedAscii, Sanitizers.escapeHtml(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.escapeHtml(ASCII_CHARS_SOYDATA));
  }

  @Test
  public void testEscapeHtmlAttributeNospace() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals(
        "&#9;&#10;&#11;&#12;&#13;&#32;&quot;&#39;&#96;&lt;&gt;&amp;",
        Sanitizers.escapeHtmlAttributeNospace("\u0009\n\u000B\u000C\r \"'\u0060<>&"));

    String escapedAscii =
        ("&#0;\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b&#9;&#10;&#11;&#12;&#13;\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + "&#32;!&quot;#$%&amp;&#39;()*+,&#45;.&#47;"
            + "0123456789:;&lt;&#61;&gt;?"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\]^_"
            + "&#96;abcdefghijklmno"
            + "pqrstuvwxyz{|}~\u007f");
    assertEquals(escapedAscii, Sanitizers.escapeHtmlAttributeNospace(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.escapeHtmlAttributeNospace(ASCII_CHARS_SOYDATA));
  }

  @Test
  public void testNormalizeHtml() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals("&quot;&#39;&lt;&gt;", Sanitizers.normalizeHtml("\"'<>"));

    String escapedAscii =
        ("&#0;\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b\t\n\u000b\u000c\r\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + " !&quot;#$%&&#39;()*+,-./"
            + "0123456789:;&lt;=&gt;?"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\]^_"
            + "`abcdefghijklmno"
            + "pqrstuvwxyz{|}~\u007f");
    assertEquals(escapedAscii, Sanitizers.normalizeHtml(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.normalizeHtml(ASCII_CHARS_SOYDATA));
  }

  @Test
  public void testNormalizeHtmlNospace() {
    // The minimal escapes.
    // Do not remove anything from this set without talking to your friendly local ise-team@.
    assertEquals(
        "&#9;&#10;&#11;&#12;&#13;&#32;&quot;&#39;&#96;&lt;&gt;",
        Sanitizers.normalizeHtmlNospace("\u0009\n\u000B\u000C\r \"'\u0060<>"));

    String escapedAscii =
        ("&#0;\u0001\u0002\u0003\u0004\u0005\u0006\u0007\b&#9;&#10;&#11;&#12;&#13;\u000e\u000f"
            + "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017"
            + "\u0018\u0019\u001a\u001b\u001c\u001d\u001e\u001f"
            + "&#32;!&quot;#$%&&#39;()*+,&#45;.&#47;"
            + "0123456789:;&lt;&#61;&gt;?"
            + "@ABCDEFGHIJKLMNO"
            + "PQRSTUVWXYZ[\\]^_"
            + "&#96;abcdefghijklmno"
            + "pqrstuvwxyz{|}~\u007f");
    assertEquals(escapedAscii, Sanitizers.normalizeHtmlNospace(ASCII_CHARS));
    assertEquals(escapedAscii, Sanitizers.normalizeHtmlNospace(ASCII_CHARS_SOYDATA));
  }

  private static final String stripHtmlTags(String html, boolean spacesOk) {
    return Sanitizers.stripHtmlTags(html, null, spacesOk);
  }

  @Test
  public void testStripHtmlTags() {
    assertEquals("", stripHtmlTags("", true));
    assertEquals("Hello, World!", stripHtmlTags("Hello, World!", true));
    assertEquals("Hello,&#32;World!", stripHtmlTags("Hello, World!", false));
    assertEquals("Hello, World!", stripHtmlTags("<b>Hello, World!</b>", true));
    assertEquals("Hello, &quot;World!&quot;", stripHtmlTags("<b>Hello, \"World!\"</b>", true));
    assertEquals("Hello,&#32;&quot;World!&quot;", stripHtmlTags("<b>Hello, \"World!\"</b>", false));
    assertEquals("42", stripHtmlTags("42", true));
    // Don't merge content around tags into an entity.
    assertEquals("&amp;amp;", stripHtmlTags("&<hr>amp;", true));
  }

  private static final TagWhitelist TEST_WHITELIST =
      new TagWhitelist("b", "br", "ul", "li", "table", "tr", "td");

  private static final String cleanHtml(String html) {
    return Sanitizers.stripHtmlTags(html, TEST_WHITELIST, true);
  }

  @Test
  public void testTagWhitelisting() {
    assertEquals("<b>Hello, World!</b>", cleanHtml("<b>Hello, World!</b>"));
    assertEquals("<b>Hello, World!</b>", cleanHtml("<b onclick='evil()'>Hello, World!</b>"));
    assertEquals("<b>Hello, <br> World!</b>", cleanHtml("<b>Hello, <br/> World!</b>"));
    // Don't add end tags for void elements.
    assertEquals("<b>Hello, <br> World!</b>", cleanHtml("<b>Hello, <br/> World!"));
    assertEquals("<b>Hello, <br> World!</b>", cleanHtml("<b>Hello, <br> World!"));
    // Missing open tag.
    assertEquals("Hello, <br> World!", cleanHtml("Hello, <br/> World!"));
    // A truncated tag is not a tag.
    assertEquals("Hello, &lt;br", cleanHtml("Hello, <br"));
    // Test boundary conditions at end of input.
    assertEquals("Hello, &lt;", cleanHtml("Hello, <"));
    assertEquals("Hello, &lt;/", cleanHtml("Hello, </"));
    assertEquals("Hello, &lt; World", cleanHtml("Hello, < World"));
    // Don't be confused by attributes that merge into the tag name.
    assertEquals("", cleanHtml("<img/onload=alert(1337)>"));
    assertEquals("foo", cleanHtml("<i/onmouseover=alert(1337)>foo</i>"));
    assertEquals("AB", cleanHtml("A<img/onload=alert(1337)>B"));
    // Don't create new tags from parts that were not originally adjacent.
    assertEquals(
        "&lt;img onload=alert(1337)", cleanHtml("<<img/onload=alert(1337)>img onload=alert(1337)"));
    // Test external layout breakers.
    // <ul><li>Foo</ul></li> would be bad since it is equivalent to
    // <ul><li>Foo</li></ul></li>
    assertEquals("<ul><li>Foo</li></ul>", cleanHtml("<ul><li>Foo</ul>"));
    // We put the close tags in the wrong place but in a way that is safe.
    assertEquals("<ul><li>1<li>2</li></li></ul>", cleanHtml("<ul><li>1<li>2</ul>"));
    assertEquals("<table><tr><td></td></tr></table>", cleanHtml("<table><tr><td>"));
    // Don't merge content around tags into an entity.
    assertEquals("&amp;amp;", cleanHtml("&<hr>amp;"));
  }

  @Test
  public void testCleanHtml() {
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<em>foo</em>", ContentKind.HTML),
        Sanitizers.cleanHtml("<em>f<object>oo</em>"));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<em>foo</em>", ContentKind.HTML),
        Sanitizers.cleanHtml(StringData.forValue("<em>f<object>oo</em>")));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<em>foo</em>", ContentKind.HTML, Dir.LTR),
        Sanitizers.cleanHtml(
            UnsafeSanitizedContentOrdainer.ordainAsSafe("<em>f<object>oo</em>", ContentKind.CSS)));

    // Input of ContentKind.HTML is left alone.
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<script>notevil()</script>", ContentKind.HTML),
        Sanitizers.cleanHtml(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "<script>notevil()</script>", ContentKind.HTML)));
  }

  @Test
  public void testCleanHtml_optionalSafeTags() {
    // No OptionalSafeTags.
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<em>ff</em>", ContentKind.HTML),
        Sanitizers.cleanHtml("<em><ul>f</ul><ol><li><span>f</span></li></ol></em>"));
    // One OptionalSafeTag.
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<em>f<span>f</span></em>", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<em><ul>f</ul><ol><li><span>f</span></li></ol></em>"),
            ImmutableSet.of(OptionalSafeTag.SPAN)));
    // All OptionalSafeTags.
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<em><ul>f</ul><ol><li><span>f</span></li></ol></em>", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<em><ul>f</ul><ol><li><span>f</span></li></ol></em>"),
            ImmutableSet.copyOf(OptionalSafeTag.values())));

    // Does not preserve <li> which are not nested in a parent <ol> or <ul>.
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("foo", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<p><li>foo</li></p>"), ImmutableSet.of(OptionalSafeTag.LI)));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("a<ul>f</ul><ol>o</ol>o", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<li>a</li><ul>f</ul><ol>o</ol><li>o</li>"),
            ImmutableSet.of(OptionalSafeTag.LI, OptionalSafeTag.OL, OptionalSafeTag.UL)));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<ul>f<ol>o</ol></ul>o", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<ul>f<ol>o</ul><li>o</li></ol>"),
            ImmutableSet.of(OptionalSafeTag.LI, OptionalSafeTag.OL, OptionalSafeTag.UL)));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<ol><li>0<ol><li>1</li></ol><li>2</li></li></ol>3", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<ol><li>0<ol><li>1</li></ol><li>2</li></ol><ul><li>3</li></ul>"),
            ImmutableSet.of(OptionalSafeTag.LI, OptionalSafeTag.OL)));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<ul>&lt;3 cookies</ul>", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<ul></ol></foo><3 cookies"),
            ImmutableSet.of(OptionalSafeTag.LI, OptionalSafeTag.OL, OptionalSafeTag.UL)));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<span>endless&lt; /span&gt;</span>", ContentKind.HTML),
        Sanitizers.cleanHtml(
            StringData.forValue("<span>endless< /span>"), ImmutableSet.of(OptionalSafeTag.SPAN)));
  }

  @Test
  public void testCleanHtml_preservesDirAttribute() {
    ImmutableSet<OptionalSafeTag> treatSpanSafe = ImmutableSet.of(OptionalSafeTag.SPAN);
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<span dir=\"ltr\">foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span dir=\"ltr\" other=\"no\">f<object>oo</span>", treatSpanSafe));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<span dir=\"rtl\">foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span dir=\'Rtl\'>f<object>oo</span>", treatSpanSafe));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe(
            "<span dir=\"rtl\">foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span DIR=\'RTL\'>f<object>oo</span>", treatSpanSafe));

    // Doesn't preserve malformed directionality.
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<span>foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span dir='ltr \"onload=alert(1337)//\"'>foo</span>", treatSpanSafe));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<span>foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span d\\u0131r=ltr>foo</span>", treatSpanSafe));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<span>foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span>foo</span dir=ltr>", treatSpanSafe));
    assertEquals(
        UnsafeSanitizedContentOrdainer.ordainAsSafe("<span>foo</span>", ContentKind.HTML),
        Sanitizers.cleanHtml("<span dir=ltr>f<object>oo</span>", treatSpanSafe));
  }

  @Test
  public void testFilterNoAutoescape() {
    // Filter out anything marked with sanitized content of kind "text" which indicates it
    // previously was constructed without any escaping.
    assertEquals(
        StringData.forValue("zSoyz"),
        Sanitizers.filterNoAutoescape(
            UnsafeSanitizedContentOrdainer.ordainAsSafe("x", SanitizedContent.ContentKind.TEXT)));
    assertEquals(
        StringData.forValue("zSoyz"),
        Sanitizers.filterNoAutoescape(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "<!@*!@(*!@(>", SanitizedContent.ContentKind.TEXT)));

    // Everything else should be let through. Hope it's safe!
    assertEquals(
        StringData.forValue("<div>test</div>"),
        Sanitizers.filterNoAutoescape(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "<div>test</div>", SanitizedContent.ContentKind.HTML)));
    assertEquals(
        StringData.forValue("foo='bar'"),
        Sanitizers.filterNoAutoescape(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                "foo='bar'", SanitizedContent.ContentKind.ATTRIBUTES)));
    assertEquals(
        StringData.forValue(".foo{color:green}"),
        Sanitizers.filterNoAutoescape(
            UnsafeSanitizedContentOrdainer.ordainAsSafe(
                ".foo{color:green}", SanitizedContent.ContentKind.CSS)));
    assertEquals(
        StringData.forValue("<div>test</div>"),
        Sanitizers.filterNoAutoescape(StringData.forValue("<div>test</div>")));
    assertEquals(StringData.forValue("null"), Sanitizers.filterNoAutoescape(NullData.INSTANCE));
    assertEquals(
        StringData.forValue("123"), Sanitizers.filterNoAutoescape(IntegerData.forValue(123)));
  }

  @Test
  public void testEmbedCssIntoHtml() {
    assertEquals("", Sanitizers.embedCssIntoHtml(""));
    assertEquals("foo", Sanitizers.embedCssIntoHtml("foo"));
    assertEquals("a[foo]>b", Sanitizers.embedCssIntoHtml("a[foo]>b"));
    assertEquals("/* <\\/style> */", Sanitizers.embedCssIntoHtml("/* </style> */"));
    assertEquals(
        "content: '<\\/STYLE >'", // Semantically equivalent
        Sanitizers.embedCssIntoHtml("content: '</STYLE >'"));
    assertEquals(
        "background: url(<\\/style/>)", // Semantically equivalent
        Sanitizers.embedCssIntoHtml("background: url(</style/>)"));
  }
}
