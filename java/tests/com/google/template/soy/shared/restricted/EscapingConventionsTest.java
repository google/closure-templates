/*
 * Copyright 2010 Google Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.AssertionFailedError;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ScriptableObject;

/**
 * Make sure that the escapers preserve containment consistently in both Java and JavaScript.
 *
 * <p>
 */
@RunWith(JUnit4.class)
public class EscapingConventionsTest {
  @Rule public final TestName testName = new TestName();

  @Test
  public void testAllEscapersIterated() {
    // Make sure that all Escapers are present in getAllEscapers().
    Set<String> actual = Sets.newLinkedHashSet();
    Set<String> expected = Sets.newLinkedHashSet();
    for (EscapingConventions.CrossLanguageStringXform directive :
        EscapingConventions.getAllEscapers()) {
      expected.add(directive.getClass().getSimpleName());
    }
    for (Class<?> clazz : EscapingConventions.class.getClasses()) {
      if (EscapingConventions.CrossLanguageStringXform.class.isAssignableFrom(clazz)
          && !Modifier.isAbstract(clazz.getModifiers())) {
        actual.add(clazz.getSimpleName());
      }
    }
    assertEquals(expected, actual);
  }

  @Test
  public void testJavaScriptStringDirective() throws Exception {
    assertEscaping(
        // The {$s} in the below is replaced with a bunch of malicious strings.
        "var x = '{$s}', y = \"{$s}\";\n/* foo */",
        // But the untrusted strings are defanged by the escape directive.
        "escapeJsString",
        // A lexer is applied to the below.
        JS_LEXER,
        // And we should get these tokens back, but with any possible value for the nulls, since
        // the actual escaped strings depend on the untrusted string.
        "var",
        " ",
        "x",
        " ",
        "=",
        " ",
        null,
        ",",
        " ",
        "y",
        " ",
        "=",
        " ",
        null,
        ";",
        "\n",
        "/* foo */");
  }

  @Test
  public void testJavaRegexStringDirective() throws Exception {
    assertEscaping(
        "var x = /foo-{$s}/; x.test('foo-bar');",
        "escapeJsRegex",
        JS_LEXER,
        "var",
        " ",
        "x",
        " ",
        "=",
        " ",
        null,
        ";",
        " ",
        "x",
        ".",
        "test",
        "(",
        "'foo-bar'",
        ");");
  }

  @Test
  public void testHtmlDirective() throws Exception {
    assertEscaping(
        "<div><!-- {$s} --></div>", "escapeHtml", HTML_LEXER, "<div", ">", null, "</div", ">");
  }

  @Test
  public void testHtmlRcdataDirective() throws Exception {
    assertEscaping(
        "<textarea>'{$s}'</textarea>",
        "escapeHtmlRcdata",
        HTML_LEXER,
        "<textarea",
        ">",
        null,
        "</textarea",
        ">");
  }

  @Test
  public void testHtmlAttributeDirective() throws Exception {
    assertEscaping(
        "<div title=\"{$s}\" class='{$s}'>",
        "escapeHtmlAttribute",
        HTML_LEXER,
        "<div",
        " ",
        "title=",
        null,
        " ",
        "class=",
        null,
        ">");
  }

  @Test
  public void testHtmlAttributeNospaceDirective() throws Exception {
    assertEscaping(
        "<div title=\"{$s}\" class='{$s}' id=x{$s}>",
        "escapeHtmlAttributeNospace",
        HTML_LEXER,
        "<div",
        " ",
        "title=",
        null,
        " ",
        "class=",
        null,
        " ",
        "id=",
        null,
        ">");
  }

  @Test
  public void testFilterHtmlElementNameDirective() throws Exception {
    assertEscaping(
        "<h{$s} id=foo onclick='foo()'>",
        "filterHtmlElementName",
        HTML_LEXER,
        (String) null,
        " ",
        "id=",
        "foo",
        " ",
        null,
        "'foo()'",
        ">");
  }

  @Test
  public void testFilterHtmlAttributeDirective() throws Exception {
    assertEscaping(
        "<h1 id=foo on{$s}='foo()'>",
        "filterHtmlAttributes",
        HTML_LEXER,
        "<h1",
        " ",
        "id=",
        "foo",
        " ",
        null,
        "'foo()'",
        ">");
  }

  @Test
  public void testCssDirective() throws Exception {
    assertEscaping(
        "div { font-family: \"{$s}\", '{$s}';\n"
            + "  background-image: url('{$s}'); border-image: url(\"${s}\") }",
        "escapeCssString",
        CSS_LEXER,
        "div",
        " ",
        "{",
        " ",
        "font-family",
        ":",
        " ",
        null,
        ",",
        " ",
        null,
        ";",
        "\n  ",
        "background-image",
        ":",
        " ",
        null,
        ";",
        " ",
        "border-image",
        ":",
        " ",
        null,
        " ",
        "}");
  }

  @Test
  public void testCssValueDirective() throws Exception {
    assertEscaping(
        "div#id-{$s}.class-{$s} { color: red; border-color: #33f; margin: 0 -2px 4.5 .25in }",
        "filterCssValue",
        CSS_LEXER,
        UNTRUSTED_VALUES,
        "div",
        null,
        null,
        " ",
        "{",
        " ",
        "color",
        ":",
        " ",
        "red",
        ";",
        " ",
        "border-color",
        ":",
        " ",
        "#33f",
        ";",
        " ",
        "margin",
        ":",
        " ",
        "0",
        " ",
        "-2px",
        " ",
        "4.5",
        " ",
        ".25in",
        " ",
        "}");
  }

  @Test
  public void testUriDirective() throws Exception {
    assertEscaping(
        "http://foo{$s}/bar{$s}?foo={$s}&{$s}=bar#{$s}1",
        "escapeUri",
        URI_LEXER,
        "http",
        "://",
        null,
        "/",
        null,
        "?",
        "foo",
        "=",
        null,
        "&",
        null,
        "=",
        "bar",
        "#",
        null);

    // Test containment in HTML.
    assertEscaping(
        "<a href={$s}.html><a href='{$s}.html'><a href=\"{$s}.html\">",
        "escapeUri",
        HTML_LEXER,
        "<a",
        " ",
        "href=",
        null,
        ">",
        "<a",
        " ",
        "href=",
        null,
        ">",
        "<a",
        " ",
        "href=",
        null,
        ">");

    // Test containment in CSS.
    assertEscaping(
        "border-image: url({$s}) url('{$s}') url(\"{$s}\");",
        "escapeUri",
        CSS_LEXER,
        "border-image",
        ":",
        " ",
        null,
        " ",
        null,
        " ",
        null,
        ";");
  }

  @Test
  public void testNormUriDirective() throws Exception {
    assertEscaping(
        "{$s}?foo=bar#s={$s}",
        "normalizeUri",
        URI_LEXER,
        ImmutableList.of("http://www.google.com/O'Leary"),
        "http",
        "://",
        "www.google.com",
        "/",
        "O%27Leary",
        "?",
        "foo",
        "=",
        "bar",
        "#",
        "s=http://www.google.com/O%27Leary");
  }

  @Test
  public void testTestFramework() throws Exception {
    // Make sure that a lexer can fail.

    // Using |escapeHtml on an unquoted attribute is not allowed.
    try {
      assertEscaping(
          "<div title={$s}>",
          "escapeHtml",
          HTML_LEXER,
          ImmutableList.of("foo onclick=alert(42)"),
          "<div",
          " ",
          "title=",
          null,
          ">");
    } catch (AssertionError err) {
      return;
    }
    fail("Expected failure.");
  }

  @Test
  public void testEscaperInterface() throws Exception {
    // Test the escape method.
    assertEquals("Hello", EscapingConventions.EscapeUri.INSTANCE.escape("Hello"));
    assertEquals(
        "%0Aletters%C2%85%E1%88%B4%E2%80%A8",
        EscapingConventions.EscapeUri.INSTANCE.escape("\nletters\u0085\u1234\u2028"));

    StringBuilder sb;

    // And the Appendable version.
    sb = new StringBuilder();
    EscapingConventions.EscapeUri.INSTANCE
        .escape(sb)
        .append("Hello")
        .append("\nletters\u0085\u1234\u2028");
    assertEquals("Hello%0Aletters%C2%85%E1%88%B4%E2%80%A8", sb.toString());

    // And the Appendable substring version.
    sb = new StringBuilder();
    EscapingConventions.EscapeUri.INSTANCE
        .escape(sb)
        .append("--Hello--", 2, 7)
        .append("--\nletters\u0085\u1234\u2028--", 2, 13);
    assertEquals("Hello%0Aletters%C2%85%E1%88%B4%E2%80%A8", sb.toString());

    // And the Appendable char version.
    sb = new StringBuilder();
    EscapingConventions.EscapeUri.INSTANCE
        .escape(sb)
        .append('H')
        .append('i')
        .append('\n')
        .append('\u0085')
        .append('\u1234');
    assertEquals("Hi%0A%C2%85%E1%88%B4", sb.toString());
  }

  private static final String SUBSTITUTION_POINT = "{$s}";

  /**
   * Create a lexer used by unittests to check that maliciously injected values can't violate the
   * boundaries of string literals, comments, identifiers, etc. in template code.
   */
  private static Function<String, List<String>> makeLexer(final String... regexParts) {
    return new Function<String, List<String>>() {
      @Override
      public List<String> apply(String src) {
        ImmutableList.Builder<String> tokens = ImmutableList.builder();
        Pattern token = Pattern.compile(Joiner.on("").join(regexParts), Pattern.DOTALL);
        while (src.length() != 0) {
          Matcher m = token.matcher(src);
          if (m.find()) {
            tokens.add(m.group());
            src = src.substring(m.end());
          } else {
            throw new IllegalArgumentException("Cannot lex `" + src + "`");
          }
        }
        return tokens.build();
      }
    };
  }

  /** Glosses over regexular expression, number, and punctuation boundaries. */
  private static final Function<String, List<String>> JS_LEXER =
      makeLexer(
          "^(?:",
          // A double quoted string not containing a newline.
          "\"(?:[^\\\\\"\r\n\u2028\u2029]|\\\\.)*\"|",
          // A single quoted string not containing a newline.
          "'(?:[^\\\\\'\r\n\u2028\u2029]|\\\\.)*'|",
          // A C style block comment.
          "/\\*.*?\\*/|",
          // A C++ style line comment.
          "//[^\r\n\u2028\u2029]*|",
          // Space.
          "\\s+|",
          // A run of word characters or numbers.
          "\\w+|", // A simplification of numbers.
          // A run of punctuation.
          "[^\\s\"\'/\\w]+|",
          // A division operator.
          "/=?(?![\\S])|", // A simplification of div ops vs regexs.
          // A regular expression literal.
          "/(?:",
          // Regular expression character other than an escape or charset.
          "[^\r\n\u2028\u2029\\\\/\\[]|",
          // An escape sequence.
          "\\\\[^\r\n\u2028\u2029]|",
          // A charset.
          "\\[",
          "(?:",
          // A charset member.
          "[^\\]\r\b\u2028\u2029\\\\]|",
          // An escape.
          "\\\\[^\r\n\u2028\u2029]",
          ")*",
          "\\]",
          ")*/",
          ")");

  private static final Function<String, List<String>> HTML_LEXER =
      makeLexer(
          "^(?:",
          // Beginning of a tag including its name.
          "</?[\\w:-]+|",
          // An HTML style comment.
          "<!--[^<>\"']*-->|",
          // Spaces.
          "\\s+|",
          // End of a tag.
          "/?>|",
          // An attribute name and equal sign.
          "[\\w:-]+=|",
          // A double quoted attribute value.
          "\"[^\"<>]*\"|",
          // A single quoted attribute value.
          "\'[^\'<>]*\'|",
          // An IE back quoted attribute value.
          "`[^`]*`|",
          // Raw HTML text (excl. quotes), or unquoted attribute value.
          "(?:[^\\s<>\"'`=]|[\\w:-](?!=))+",
          ")");

  private static final Function<String, List<String>> CSS_LEXER =
      makeLexer(
          "^(?i:", // CSS is case insensitive
          // Escaping text span start or end.  Allowed in CSS.
          "<!--|",
          "-->|",
          // A double quoted string.
          "\"(?:[^\\\\\"\r\n\f]|\\\\.)*\"|",
          // A single quoted string.
          "'(?:[^\\\\\'\r\n\f]|\\\\.)*'|",
          // An identifier  (other than url), hash color literal, or quantity
          "(?!url\\b)[.#@!]?(?:[\\w-]|\\.[0-9]|\\\\[0-9a-f]+[ \t\r\n\f]?)+%?|",
          // A C style comments.  Line comments are non-standard in CSS.
          "/\\*.*?\\*+/|",
          // Punctuation.
          "[:{}();,~]|",
          // A url literal.
          "url\\(\\s*(?:",
          // Double quoted.
          "\"(?:[^\\\\\"\r\n\f]|\\\\.)*\"|",
          // Single quoted.
          "'(?:[^\\\\\'\r\n\f]|\\\\.)*'|",
          // Unquoted.
          "(?:[!#$%&*-\\[\\]-~\u0080-\uffff]|\\\\[0-9a-f]+[ \t\r\n\f]?)*",
          ")\\s*\\)|",
          // Space.
          "\\s+",
          ")");

  /** Lexes URIs returning each of the parts defined in RFC3986 for hierarchical URIs separately. */
  private static final Function<String, List<String>> URI_LEXER =
      new Function<String, List<String>>() {

        @Override
        public List<String> apply(String s) {
          Matcher m =
              Pattern.compile( // Pattern from RFC 3986 Appendix B.
                      "^(?:([^:/?#]+):)?(?://([^/?#]*))?([^?#]*)(?:\\?([^#]*))?(?:#(.*))?",
                      Pattern.DOTALL)
                  .matcher(s);
          assertTrue(m.find());
          String scheme = m.group(1);
          String authority = m.group(2);
          String path = m.group(3);
          String query = m.group(4);
          String fragment = m.group(5);
          ImmutableList.Builder<String> out = ImmutableList.builder();
          if (scheme != null) {
            out.add(scheme);
            out.add("://");
          }
          if (authority != null) {
            out.add(authority);
          }
          if (path != null) {
            int pos = 0;
            int queryLen = path.length();
            for (int i = 0; i < queryLen; ++i) {
              if (path.charAt(i) == '/') {
                if (pos != i) {
                  out.add(path.substring(pos, i));
                }
                pos = i + 1;
                out.add(path.substring(i, i + 1));
              }
            }
            if (pos != queryLen) {
              out.add(path.substring(pos));
            }
          }
          if (query != null) {
            out.add("?");
            int pos = 0;
            int queryLen = query.length();
            for (int i = 0; i < queryLen; ++i) {
              if (query.charAt(i) == '&' || query.charAt(i) == '=') {
                out.add(query.substring(pos, i));
                pos = i + 1;
                out.add(query.substring(i, i + 1));
              }
            }
            out.add(query.substring(pos));
          }
          if (fragment != null) {
            out.add("#");
            out.add(fragment);
          }
          return out.build();
        }
      };

  /** Problematic strings to escape that should stress token boundaries. */
  private static final ImmutableList<String> UNTRUSTED_VALUES =
      ImmutableList.of(
          "",
          "foo",
          "Foo",
          "foo-BAR",
          "h1",
          // Some HTML boundaries.
          "123",
          "<script>",
          "</script>",
          "<!--",
          "-->",
          "<\0script",
          "<![CDATA[",
          "]]>",
          "<div>",
          ">",
          " />",
          // Some newlines
          "\n",
          "\r\n",
          "\r",
          "\f",
          "\b",
          "\u2028",
          "\u2029",
          // String and attribute boundaries and problem characters.
          "\"",
          "'",
          "`",
          "\\",
          "/i, ",
          // JS and CSS comment boundaries
          "/*",
          "*/",
          "//",
          // Unquoted attribute boundaries.
          " ",
          "\u00A0",
          // More
          "\"'`/*\\*/\r\n<!-</ScRipt</style <-->",
          ":/?=&#();@././../",
          "'' onclick=alert(1337)",
          ") expression(alert(1337)");

  /**
   * For the named directive, check that containment holds, by doing simple template substitution in
   * each of the Java and JavaScript modes, and then lexing the result in a way that would expose
   * differences in string, comment, and tag boundaries..
   *
   * @param templateText Text in the escaping directive's output language that contains the {@link
   *     #SUBSTITUTION_POINT substitution point}.
   * @param directiveName The name of the escape directive to test in both Java and JavaScript.
   * @param lexer Used to lex the result of running the escaping directive.
   * @param expectedTokens The expected tokens from lexing templateText after replacing the
   *     substitution point with dynamic content escaped using directive. If a value is null, then
   *     it will match any token.
   */
  private void assertEscaping(
      String templateText,
      String directiveName,
      Function<String, List<String>> lexer,
      String... expectedTokens)
      throws Exception {
    assertEscaping(templateText, directiveName, lexer, UNTRUSTED_VALUES, expectedTokens);
  }

  /**
   * For the named directive, check that containment holds, by doing simple template substitution in
   * each of the Java and JavaScript modes, and then lexing the result in a way that would expose
   * differences in string, comment, and tag boundaries..
   *
   * @param templateText Text in the escaping directive's output language that contains the {@link
   *     #SUBSTITUTION_POINT substitution point}.
   * @param directiveName The name of the escape directive to test in both Java and JavaScript.
   * @param lexer Used to lex the result of running the escaping directive.
   * @param expectedTokens The expected tokens from lexing templateText after replacing the
   *     substitution point with dynamic content escaped using directive. If a value is null, then
   *     it will match any token.
   */
  private void assertEscaping(
      String templateText,
      String directiveName,
      Function<String, List<String>> lexer,
      Iterable<String> untrustedValues,
      String... expectedTokens)
      throws Exception {
    assertTrue(templateText, templateText.contains(SUBSTITUTION_POINT));
    assertTrue(untrustedValues.iterator().hasNext()); // not empty
    checkEscaping(
        templateText,
        applyDirectiveClosure(directiveName, untrustedValues),
        directiveName + ":javascript",
        lexer,
        Arrays.asList(expectedTokens));
  }

  /**
   * Apply the named directive to the given strings by loading {@code soyutils_usegoog.js} into
   * Rhino.
   *
   * @return Even elements are the raw strings, and odd elements are the corresponding escaped
   *     versions.
   */
  private List<String> applyDirectiveClosure(String directiveName, Iterable<String> toEscape)
      throws Exception {
    return applyDirectiveInRhino(directiveName, toEscape, getSoyUtilsUseGoogPath());
  }

  private List<String> applyDirectiveInRhino(
      String directiveName, Iterable<String> toEscape, String soyUtilsPath) throws Exception {
    List<String> output = Lists.newArrayList();
    Context context = new ContextFactory().enterContext();
    context.setOptimizationLevel(-1); // Only running once.
    ScriptableObject globalScope = context.initStandardObjects();
    globalScope.defineProperty(
        "navigator", Context.javaToJS(new Navigator(), globalScope), ScriptableObject.DONTENUM);

    Reader soyutils = new InputStreamReader(new FileInputStream(soyUtilsPath), UTF_8);
    try {
      String basename = soyUtilsPath.substring(soyUtilsPath.lastIndexOf('/') + 1);
      context.evaluateReader(globalScope, soyutils, basename, 1, null);
    } finally {
      soyutils.close();
    }

    globalScope.defineProperty(
        "test_toEscape", ImmutableList.copyOf(toEscape), ScriptableObject.DONTENUM);
    globalScope.defineProperty("test_output", output, ScriptableObject.DONTENUM);

    context.evaluateString(
        globalScope,
        Joiner.on('\n')
            .join(
                "(function () {",
                "  if (typeof goog !== 'undefined') {",
                // Make sure we get the innocuous value from filters and not an exception.
                "    goog.asserts.ENABLE_ASSERTS = goog.DEBUG = false;",
                "  }",
                "  for (var i = 0, n = test_toEscape.size(); i < n; ++i) {",
                "    var raw = String(test_toEscape.get(i));",
                "    var escaped = String(soy.$$" + directiveName + "(raw));",
                "    test_output.add(raw);",
                "    test_output.add(escaped);",
                "  }",
                "})()"),
        getClass() + ":" + testName.getMethodName(), // File name for JS traces.
        1,
        null);

    return output;
  }

  /**
   * Does some simple template substitution, and checks that string, comment, tag, and other token
   * boundaries do not differ based on the string that was escaped.
   */
  private static void checkEscaping(
      String templateText,
      List<String> strings,
      String directiveVersion,
      Function<String, List<String>> lexer,
      List<String> expectedTokens) {
    int numStrings = strings.size();
    assertTrue(directiveVersion, numStrings != 0);
    for (int i = 0; i < numStrings; i += 2) {
      String unescaped = strings.get(i);
      String escaped = strings.get(i + 1);
      String outputCode = templateText.replace(SUBSTITUTION_POINT, escaped);
      try {
        List<String> tokens = lexer.apply(outputCode);
        int minLen = Math.min(expectedTokens.size(), tokens.size());
        for (int j = 0; j < minLen; ++j) {
          String expected = expectedTokens.get(j);
          String actual = tokens.get(j);
          if (expected != null && !expected.equals(actual)) {
            fail(
                "Bad escaping `"
                    + outputCode
                    + "` of `"
                    + unescaped
                    + "` for "
                    + directiveVersion
                    + ".  Expected `"
                    + expected
                    + "` but got `"
                    + actual
                    + "`");
          }
        }
        if (expectedTokens.size() != minLen) {
          fail("Missing tokens " + expectedTokens.subList(minLen, expectedTokens.size()));
        } else if (tokens.size() != minLen) {
          fail("Extra tokens " + tokens.subList(minLen, tokens.size()));
        }
      } catch (AssertionFailedError err) {
        throw err;
      } catch (Exception ex) {
        AssertionFailedError err =
            new AssertionFailedError(
                "Failed to escape `"
                    + unescaped
                    + "` with "
                    + directiveVersion
                    + ", got `"
                    + outputCode
                    + "`");
        err.initCause(ex);
        throw err;
      }
    }
  }

  /** So we can run soyutils in Rhino. */
  public static final class Navigator {
    public final String userAgent = "testzilla";
  }

  private static String getSoyUtilsUseGoogPath() {
    return "testdata/javascript/soy_usegoog_lib.js";
  }
}
