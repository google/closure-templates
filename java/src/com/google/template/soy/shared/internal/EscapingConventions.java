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

package com.google.template.soy.shared.internal;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.escape.Escaper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Definitions of escaping functions that behave consistently in JavaScript and Java that implement
 * the escaping directives as in <code>{print $x <b>|escapeJsString</b>}</code>.
 *
 * <p>An escaping convention is defined in terms of
 *
 * <ol>
 *   <li>An optional filter predicate that all valid inputs must match.
 *   <li>An optional function name from the closure JavaScript library that already implements the
 *       escaping convention.
 *   <li>A required mapping from characters to escaping strings.
 * </ol>
 *
 * <p>Escaping functions are exposed as {@link Escaper}s in Java and via a JavaScript code
 * generating ant task for JavaScript.
 */
@ParametersAreNonnullByDefault
public final class EscapingConventions {

  // Below we take advantage of lazy class loading to avoid doing the work of initializing maps
  // or loading code for escaping conventions never used by the Java runtime.
  // We first define a base class that collects the information above, and that allows enumeration
  // over escaped characters.
  // Each escaping convention is its own public interface to java code, and the JavaScript code
  // generator uses a public accessor that ties them all together.

  /** The list of potential languages which are used by the escapers. */
  public enum EscapingLanguage {
    JAVASCRIPT,
    PYTHON
  }

  /**
   * A mapping from a plain text character to the escaped text in the target language. We define a
   * character below as a code unit, not a codepoint as none of the target languages treat
   * supplementary codepoints as special.
   */
  public static final class Escape implements Comparable<Escape> {
    private final char plainText;
    private final String escaped;

    public Escape(char plainText, String escaped) {
      this.plainText = plainText;
      this.escaped = escaped;
    }

    /** A character in the input language. */
    public char getPlainText() {
      return plainText;
    }

    /**
     * A string in the output language that corresponds to {@link #getPlainText} in the input
     * language.
     */
    public String getEscaped() {
      return escaped;
    }

    @Override
    public int compareTo(Escape b) {
      return this.plainText - b.plainText;
    }
  }

  /**
   * A transformation on strings that preserves some correctness or safety properties. Subclasses
   * come in three varieties:
   *
   * <dl>
   *   <dt>Escaper
   *   <dd>A mapping from strings in an input language to strings in an output language that
   *       preserves the content. E.g. the plain text string {@code 1 < 2} can be escaped to the
   *       equivalent HTML string {@code 1 &lt; 2}.
   *   <dt>Normalizer
   *   <dd>A mapping from strings in a language to equivalent strings in the same language but that
   *       can be more easily embedded in another language. E.g. the URI {@code
   *       http://www.google.com/search?q=O'Reilly} is equivalent to {@code
   *       http://www.google.com/search?q=O%27Reilly} but the latter can be safely embedded in a
   *       single quoted HTML attribute.
   *   <dt>Filter
   *   <dd>A mapping from strings in a language to the same value or to an innocuous value. E.g. the
   *       string {@code h1} might pass an html identifier filter but the string {@code
   *       ><script>alert('evil')</script>} should not and could be replaced by an innocuous value
   *       like {@code zzz}.
   * </dl>
   */
  public abstract static class CrossLanguageStringXform {
    private static final String[] EMPTY_ASCII_ESCAPES_ARRAY = new String[0x80];
    private final String directiveName;
    @Nullable private final Pattern valueFilter;
    private final ImmutableList<Escape> escapes;

    /**
     * A dense mapping mirroring escapes. I.e. for each element of {@link #escapes} {@code e} such
     * that {@code e.plainText < 0x80}, {@code escapesByCodeUnit[e.plainText] == e.escaped}.
     */
    protected final String[] escapesByCodeUnit;

    /** Keys in a sparse mapping for the non ASCII {@link #escapes}. */
    @Nullable protected final char[] nonAsciiCodeUnits;

    /** Values in a sparse mapping corresponding to {@link #nonAsciiCodeUnits}. */
    @Nullable protected final String[] nonAsciiEscapes;

    protected CrossLanguageStringXform() {
      this(null);
    }

    /**
     * @param valueFilter {@code null} if the directive accepts all strings as inputs. Otherwise a
     *     regular expression that accepts only strings that can be escaped by this directive.
     * @param nonAsciiPrefix An escaping prefix in {@code "%", "\\u", "\\"} which specifies how to
     *     escape non-ASCII code units not in the sparse mapping. If null, then non-ASCII code units
     *     outside the sparse map can appear unescaped.
     */
    protected CrossLanguageStringXform(@Nullable Pattern valueFilter) {
      String simpleName = getClass().getSimpleName();
      // EscapeHtml -> |escapeHtml
      this.directiveName =
          ("|" + Ascii.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1));

      this.valueFilter = valueFilter;
      this.escapes = defineEscapes();

      // Now create the maps used by the escape methods.  The below depends on defineEscapes()
      // returning sorted escapes.  EscapeListBuilder.build() sorts its escapes.
      int numEscapes = escapes.size();
      int numAsciiEscapes = escapes.size();
      while (numAsciiEscapes > 0 && escapes.get(numAsciiEscapes - 1).plainText >= 0x80) {
        --numAsciiEscapes;
      }
      // Create the dense ASCII map.
      if (numAsciiEscapes != 0) {
        escapesByCodeUnit = new String[0x80];
        for (Escape escape : escapes.subList(0, numAsciiEscapes)) {
          escapesByCodeUnit[escape.plainText] = escape.escaped;
        }
      } else {
        escapesByCodeUnit = EMPTY_ASCII_ESCAPES_ARRAY; // save a little memory by reusing this.
      }
      // Create the sparse non-ASCII map.
      if (numEscapes != numAsciiEscapes) {
        int numNonAsciiEscapes = numEscapes - numAsciiEscapes;
        nonAsciiCodeUnits = new char[numNonAsciiEscapes];
        nonAsciiEscapes = new String[numNonAsciiEscapes];
        for (int i = 0; i < numNonAsciiEscapes; ++i) {
          Escape esc = escapes.get(numAsciiEscapes + i);
          nonAsciiCodeUnits[i] = esc.plainText;
          nonAsciiEscapes[i] = esc.escaped;
        }
      } else {
        nonAsciiCodeUnits = null;
        nonAsciiEscapes = null;
      }
    }

    /** Returns the escapes used for this escaper. */
    protected abstract ImmutableList<Escape> defineEscapes();

    /**
     * The name of the directive associated with this escaping function.
     *
     * @return E.g. {@code |escapeHtml}
     */
    public String getDirectiveName() {
      return directiveName;
    }

    /**
     * An escaping prefix in {@code "%", "\\u", "\\"} which specifies how to escape non-ASCII code
     * units not in the sparse mapping. If null, then non-ASCII code units outside the sparse map
     * can appear unescaped.
     */
    @Nullable
    public String getNonAsciiPrefix() {
      return null;
    }

    /**
     * Null if the escaper accepts all strings as inputs, or otherwise a regular expression that
     * accepts only strings that can be escaped by this escaper.
     */
    @Nullable
    public final Pattern getValueFilter() {
      return valueFilter;
    }

    /** The escapes need to translate the input language to the output language. */
    public final ImmutableList<Escape> getEscapes() {
      return escapes;
    }

    /**
     * The names of existing language builtins or available library functions (such as Google
     * Closure) that implement the escaping convention.
     *
     * @param language The language being escaped.
     * @return {@code null} if there is no such function.
     */
    public List<String> getLangFunctionNames(EscapingLanguage language) {
      return ImmutableList.of();
    }

    /** Returns an innocuous string in this context that can be used when filtering. */
    public String getInnocuousOutput() {
      return INNOCUOUS_OUTPUT;
    }

    /**
     * Escapes the given string.
     *
     * <p>If the string is already escaped, this method will return the original string.
     */
    public String escape(String string) {
      var escapesByCodeUnit = this.escapesByCodeUnit;
      var nonAsciiCodeUnits = this.nonAsciiCodeUnits;
      for (int i = 0, n = string.length(); i < n; ++i) {
        char c = string.charAt(i);
        if (c < 0x80) { // Use the dense map.
          String esc = escapesByCodeUnit[c];
          if (esc != null) {
            return escapeOntoStringBuilderStartingAt(string, i);
          }
        } else { // Use the sparse map.
          int index = nonAsciiCodeUnits != null ? Arrays.binarySearch(nonAsciiCodeUnits, c) : -1;
          if (index >= 0) {
            return escapeOntoStringBuilderStartingAt(string, i);
          }
        }
      }
      return string;
    }

    /**
     * Escapes all the bytes written to the returned appendable with this strategy.
     *
     * <p>This is guaranteed to not do any buffering, each {@link Appendable#append} operation will
     * directly pass through into a series of {@code append} operations on the delegate.
     *
     * @deprecated Use {@link #escapeOnto(CharSequence, Appendable)} instead.
     */
    @Deprecated
    public final Appendable escape(Appendable out) {
      return new Appendable() {
        @CanIgnoreReturnValue
        @Override
        public Appendable append(CharSequence csq) throws IOException {
          escapeOnto(csq, out, 0, csq.length());
          return this;
        }

        @CanIgnoreReturnValue
        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
          escapeOnto(csq, out, start, end);
          return this;
        }

        @CanIgnoreReturnValue
        @Override
        public Appendable append(char c) throws IOException {
          escapeOnto(c, out);
          return this;
        }
      };
    }

    /** Helper to escape the string starting from a given point. */
    final String escapeOntoStringBuilderStartingAt(String string, int start) {
      int length = string.length();
      try {
        return escapeOnto(
                string, new StringBuilder(length + 32).append(string, 0, start), start, length)
            .toString();
      } catch (IOException e) {
        throw new AssertionError(e); // impossible
      }
    }

    @CanIgnoreReturnValue
    public Appendable escapeOnto(char c, Appendable out) throws IOException {
      if (c < 0x80) { // Use the dense map.
        String esc = escapesByCodeUnit[c];
        if (esc != null) {
          return out.append(esc);
        }
      } else {
        int index = Arrays.binarySearch(nonAsciiCodeUnits, c);
        if (index >= 0) { // Found in the sparse map.
          return out.append(nonAsciiEscapes[index]);
        }
      }
      return out.append(c);
    }

    @CanIgnoreReturnValue
    public final Appendable escapeOnto(CharSequence s, Appendable out) throws IOException {
      return escapeOnto(s, out, 0, s.length());
    }

    /**
     * Escapes the given range of the given sequence onto the given buffer iff it contains
     * characters that need to be escaped.
     *
     * @return null if no output buffer was passed in, and s contains no characters that need
     *     escaping. Otherwise out, or a StringBuilder if one needed to be allocated.
     */
    @CanIgnoreReturnValue
    public Appendable escapeOnto(CharSequence s, Appendable out, int start, int end)
        throws IOException {
      int pos = start;
      var escapesByCodeUnit = this.escapesByCodeUnit;
      var nonAsciiCodeUnits = this.nonAsciiCodeUnits;
      for (int i = start; i < end; ++i) {
        char c = s.charAt(i);
        if (c < 0x80) { // Use the dense map.
          String esc = escapesByCodeUnit[c];
          if (esc != null) {
            out.append(s, pos, i).append(esc);
            pos = i + 1;
          }
        } else { // Use the sparse map.
          int index = Arrays.binarySearch(nonAsciiCodeUnits, c);
          if (index >= 0) {
            out.append(s, pos, i).append(nonAsciiEscapes[index]);
            pos = i + 1;
          }
        }
      }
      return out.append(s, pos, end);
    }
  }

  private abstract static class AsciiOnlyCrossLanguageStringXform extends CrossLanguageStringXform {
    AsciiOnlyCrossLanguageStringXform() {
      this(null);
    }

    AsciiOnlyCrossLanguageStringXform(@Nullable Pattern valueFilter) {
      super(valueFilter);
      checkState(nonAsciiCodeUnits == null);
    }

    @Override
    public final String escape(String string) {
      for (int i = 0, n = string.length(); i < n; ++i) {
        char c = string.charAt(i);
        if (c < 0x80 && escapesByCodeUnit[c] != null) {
          return escapeOntoStringBuilderStartingAt(string, i);
        }
      }
      return string;
    }

    @Override
    @CanIgnoreReturnValue
    public final Appendable escapeOnto(char c, Appendable out) throws IOException {
      String escape;
      if (c < 0x80 && (escape = escapesByCodeUnit[c]) != null) {
        return out.append(escape);
      }
      return out.append(c);
    }

    @Override
    @CanIgnoreReturnValue
    public final Appendable escapeOnto(CharSequence s, Appendable out, int start, int end)
        throws IOException {
      int pos = start;
      var localEscapes = this.escapesByCodeUnit;
      for (int i = start; i < end; ++i) {
        char c = s.charAt(i);
        String escape;
        if (c < 0x80 && (escape = localEscapes[c]) != null) {
          out.append(s, pos, i).append(escape);
          pos = i + 1;
        }
      }
      return out.append(s, pos, end);
    }
  }

  /** A builder for lists of escapes. */
  private abstract static class EscapeListBuilder {
    private final List<Escape> escapes = Lists.newArrayList();

    /**
     * Computes the numeric escape in the output language for the given codepoint in the input
     * language. E.g. in C, the numeric escape for space is {@code \x20}.
     */
    abstract String getNumericEscapeFor(char plainText);

    /** Adds an escape for the given code unit in the input language to the given escaped text. */
    @CanIgnoreReturnValue
    final EscapeListBuilder escape(char plainText, String escaped) {
      escapes.add(new Escape(plainText, escaped));
      return this;
    }

    /**
     * Adds an escape for the given code unit in the input language using the numeric escaping
     * scheme.
     */
    @CanIgnoreReturnValue
    final EscapeListBuilder escape(char plainText) {
      escapes.add(new Escape(plainText, getNumericEscapeFor(plainText)));
      return this;
    }

    /** Adds a numeric escape for each code unit in the input string. */
    @CanIgnoreReturnValue
    final EscapeListBuilder escapeAll(String plainTextCodeUnits) {
      int numCodeUnits = plainTextCodeUnits.length();
      for (int i = 0; i < numCodeUnits; ++i) {
        escape(plainTextCodeUnits.charAt(i));
      }
      return this;
    }

    /** Adds numeric escapes for each code unit in the given range not in the exclusion set. */
    @CanIgnoreReturnValue
    final EscapeListBuilder escapeAllInRangeExcept(
        int startInclusive, int endExclusive, char... notEscaped) {
      notEscaped = notEscaped.clone();
      Arrays.sort(notEscaped);
      int k = 0;
      int numNotEscaped = notEscaped.length;
      for (int i = startInclusive; i < endExclusive; ++i) {
        while (k < numNotEscaped && notEscaped[k] < i) {
          ++k;
        }
        if (k < numNotEscaped && notEscaped[k] == i) {
          continue;
        }
        escape((char) i);
      }
      return this;
    }

    /** The list of all escapes defined thus far. */
    final ImmutableList<Escape> build() {
      Collections.sort(escapes);
      return ImmutableList.copyOf(escapes);
    }
  }

  /** Escapes using HTML/XML numeric entities : {@code 'A' -> "&#65;"}. */
  private static final class HtmlEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      return "&#" + ((int) plainText) + ";";
    }
  }

  // Implementations of particular escapers.
  // These names follow the convention defined in Escaper's constructor above where
  //    class EscapeFoo
  // is the concrete definition for
  //    |escapeFoo
  // Each also provides a singleton INSTANCE member.

  /** Implements the {@code |escapeHtml} directive. */
  public static final class EscapeHtml extends AsciiOnlyCrossLanguageStringXform {
    /** Implements the {@code |escapeHtml} directive. */
    public static final EscapeHtml INSTANCE = new EscapeHtml();

    private EscapeHtml() {
      super(/* valueFilter= */ null);
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new HtmlEscapeListBuilder()
          .escape('&', "&amp;")
          .escape('<', "&lt;")
          .escape('>', "&gt;")
          .escape('"', "&quot;")
          // It escapes ' to &#39; instead of &apos; which is not standardized in XML.
          .escapeAll("\0'")
          .build();
    }

    @Override
    public List<String> getLangFunctionNames(EscapingLanguage language) {
      if (language == EscapingLanguage.JAVASCRIPT) {
        return ImmutableList.of("goog.string.htmlEscape");
      }
      return super.getLangFunctionNames(language);
    }
  }

  /**
   * A directive that encodes any HTML special characters that can appear in RCDATA unescaped but
   * that can be escaped without changing semantics. From <a
   * href="http://www.w3.org/TR/html5/tokenization.html#rcdata-state">HTML 5</a>:
   *
   * <blockquote>
   *
   * <h4>8.2.4.3 RCDATA state</h4>
   *
   * Consume the next input character:
   *
   * <ul>
   *   <li>U+0026 AMPERSAND (&) <br>
   *       Switch to the character reference in RCDATA state.
   *   <li>U+003C LESS-THAN SIGN (<) <br>
   *       Switch to the RCDATA less-than sign state.
   *   <li>EOF <br>
   *       Emit an end-of-file token.
   *   <li>Anything else <br>
   *       Emit the current input character as a character token.
   * </ul>
   *
   * </blockquote>
   *
   * So all HTML special characters can be escaped, except ampersand, since escaping that would lead
   * to overescaping of legitimate HTML entities.
   */
  public static final class NormalizeHtml extends AsciiOnlyCrossLanguageStringXform {
    /** Implements the {@code |normalizeHtml} directive. */
    public static final NormalizeHtml INSTANCE = new NormalizeHtml();

    private NormalizeHtml() {}

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      ImmutableList.Builder<Escape> escapes = ImmutableList.builder();
      for (Escape esc : EscapeHtml.INSTANCE.getEscapes()) {
        if (esc.plainText != '&') {
          escapes.add(esc);
        }
      }
      return escapes.build();
    }
  }

  /**
   * Implements the {@code |escapeHtmlNospace} directive which allows arbitrary content to be
   * included in the value of an unquoted HTML attribute.
   */
  public static final class EscapeHtmlNospace extends CrossLanguageStringXform {
    /** Implements the {@code |escapeHtmlNospace} directive. */
    public static final EscapeHtmlNospace INSTANCE = new EscapeHtmlNospace();

    private EscapeHtmlNospace() {}

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new HtmlEscapeListBuilder()
          .escape('&', "&amp;")
          .escape('<', "&lt;")
          .escape('>', "&gt;")
          .escape('"', "&quot;")
          // The below list of characters are all those that need to be encode to prevent unquoted
          // value splitting.
          //
          // From the XML spec,
          //   [3]   S   ::=   (#x20 | #x9 | #xD | #xA)+
          // From section 2.4.1 of the HTML5 draft,
          //   The space characters, for the purposes of this specification, are
          //   U+0020 SPACE, U+0009 CHARACTER TABULATION (tab), U+000A LINE FEED (LF),
          //   U+000C FORM FEED (FF), and U+000D CARRIAGE RETURN (CR).
          //   The White_Space characters are those that have the Unicode property
          //   "White_Space" in the Unicode PropList.txt data file.
          // From XML processing notes:
          //   [XML1.1] also normalizes NEL (U+0085) and U+2028 LINE SEPARATOR, but
          //   U+2029 PARAGRAPH SEPARATOR is not treated that way.
          // Those newline characters are described at
          // http://unicode.org/reports/tr13/tr13-9.html
          //
          // Empirically, we need to quote
          //   U+0009 - U+000d, U+0020, double quote, single quote, '>', and back quote.
          // based on running
          //   <body>
          //   <div id=d></div>
          //   <script>
          //   var d = document.getElementById('d');
          //
          //   for (var i = 0x0; i <= 0xffff; ++i) {
          //     var unsafe = false;
          //
          //     var ch = String.fromCharCode(i);
          //
          //     d.innerHTML = '<input title=foo' + ch + 'checked>';
          //     var inp = d.getElementsByTagName('INPUT')[0];
          //     if (inp && (inp.getAttribute('title') === 'foo' || inp.checked)) {
          //       unsafe = true;
          //     } else {  // Try it as a quoting character.
          //       d.innerHTML = '<input title=' + ch + 'foo' + ch + 'checked>';
          //       inp = d.getElementsByTagName('INPUT')[0];
          //       unsafe = !!(inp && (inp.getAttribute('title') === 'foo' || inp.checked));
          //     }
          //     if (unsafe) {
          //       var fourhex = i.toString(16);
          //       fourhex = "0000".substring(fourhex.length) + fourhex;
          //       document.write('\\u' + fourhex + '<br>');
          //     }
          //   }
          //   </script>
          // in a variety of browsers.
          //
          // We supplement that set with the quotes and equal sign which have special
          // meanings in attributes, and with the XML normalized spaces.
          .escapeAll("\u0000\u0009\n\u000B\u000C\r '-/=`\u0085\u00a0\u2028\u2029")
          .build();
    }
  }

  /**
   * A directive that encodes any HTML special characters and unquoted attribute terminators that
   * can appear in RCDATA unescaped but that can be escaped without changing semantics.
   */
  public static final class NormalizeHtmlNospace extends CrossLanguageStringXform {
    /** Implements the {@code |normalizeHtml} directive. */
    public static final NormalizeHtmlNospace INSTANCE = new NormalizeHtmlNospace();

    private NormalizeHtmlNospace() {}

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      ImmutableList.Builder<Escape> escapes = ImmutableList.builder();
      for (Escape esc : EscapeHtmlNospace.INSTANCE.getEscapes()) {
        if (esc.plainText != '&') {
          escapes.add(esc);
        }
      }
      return escapes.build();
    }
  }

  /** Escapes using hex escapes since octal are non-standard. 'A' -> "\\x41" */
  private static final class JsEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      return String.format(plainText < 0x100 ? "\\x%02x" : "\\u%04x", (int) plainText);
    }
  }

  /**
   * Implements the {@code |escapeJsString} directive which allows arbitrary content to be included
   * inside a quoted JavaScript string.
   */
  public static final class EscapeJsString extends CrossLanguageStringXform {
    /** Implements the {@code |escapeJsString} directive. */
    public static final EscapeJsString INSTANCE = new EscapeJsString();

    private EscapeJsString() { // TODO(msamuel): Maybe use goog.string.quote

      // Ensure that the hardcoded escape function covers the set of escapes exactly.
      for (var escape : defineEscapes()) {
        var actual = escapeChar(escape.plainText);
        checkState(
            escape.escaped.equals(actual),
            "expected '%s' (\\x%s) to escape to '%s', got '%s'",
            escape.plainText,
            Integer.toString(escape.plainText, 16),
            escape.escaped,
            actual);
      }
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new JsEscapeListBuilder()
          // Some control characters.
          .escape('\u0000')
          .escape('\b') // \\b means word-break inside RegExps.
          .escape('\t', "\\t")
          .escape('\n', "\\n")
          .escape('\u000b') // \\v not consistently supported on IE.
          .escape('\f', "\\f")
          .escape('\r', "\\r")
          .escape('\\', "\\\\")
          // Quoting characters.  / is also instrumental in </script>.
          .escape('"')
          .escape('\'')
          .escape('/', "\\/")
          .escapeAll("\u2028\u2029") // JavaScript newlines
          .escape('\u0085') // A JavaScript newline according to at least one draft spec.
          // HTML special characters.  Note, that this provides added protection against problems
          // with </script> <![CDATA[, ]]>, <!--, -->, etc.
          .escapeAll("<>&=")
          // Characters used in Angular start and end symbols. In AngularJS applications, the text
          // between start and end symbols (usually {{}}, [[]], [{}] or {[]}) is interpreted as an
          // Angular expression. Escaping these in JS strings is desirable for two main reasons:
          // - if the start symbol shows up in an on* attribute, AngularJS throws an exception
          //   (saying that it cannot do interpolation in on* attributes) and the application breaks
          // - ng-init is treated similarly to on* attributes by Soy to make it possible to safely
          //   pass values from Soy to Angular. It is possible because AngularJS expects the ng-init
          //   attribute to contain an Angular expression, and the Angular expression syntax is a
          //   subset of the JS syntax. Independently of treating the whole attribute value as an
          //   Angular expression, it will still look for interpolation symbols inside it, so not
          //   escaping these characters would make it possible to introduce an Angular injection
          //   there, which we want to avoid. For example:
          //   <div ng-init="x='{$x}'">  -->  <div ng-init="x='this is executed: [[1+2]]'">
          //   Hex escaping the common start and end symbols prevents this, since the interpolation
          //   looks for the start and end symbols on the HTML attribute level, and doesn't parse JS
          //   escapes.
          .escapeAll("{}[]")
          .build();
    }

    @Override
    public final String escape(String string) {
      for (int i = 0, n = string.length(); i < n; ++i) {
        var escaped = escapeChar(string.charAt(i));
        if (escaped != null) {
          return escapeOntoStringBuilderStartingAt(string, i);
        }
      }
      return string;
    }

    @Override
    @CanIgnoreReturnValue
    public final Appendable escapeOnto(char c, Appendable out) throws IOException {
      String escape = escapeChar(c);
      if (escape != null) {
        return out.append(escape);
      }
      return out.append(c);
    }

    @Override
    @CanIgnoreReturnValue
    public final Appendable escapeOnto(CharSequence s, Appendable out, int start, int end)
        throws IOException {
      int pos = start;

      for (int i = start; i < end; ++i) {
        char c = s.charAt(i);
        String escape = escapeChar(c);
        if (escape != null) {
          out.append(s, pos, i).append(escape);
          pos = i + 1;
        }
      }
      return out.append(s, pos, end);
    }

    @Nullable
    private String escapeChar(char c) {
      switch (c) {
        case '\u0000':
          return "\\x00";
        case '\b':
          return "\\x08";
        case '\t':
          return "\\t";
        case '\n':
          return "\\n";
        case '\u000b':
          return "\\x0b";
        case '\f':
          return "\\f";
        case '\r':
          return "\\r";
        case '\\':
          return "\\\\";
        case '"':
          return "\\x22";
        case '\'':
          return "\\x27";
        case '/':
          return "\\/";
        case '&':
          return "\\x26";
        case '<':
          return "\\x3c";
        case '=':
          return "\\x3d";
        case '>':
          return "\\x3e";
        case '[':
          return "\\x5b";
        case ']':
          return "\\x5d";
        case '{':
          return "\\x7b";
        case '}':
          return "\\x7d";
        case '\u0085':
          return "\\x85";
        case '\u2028':
          return "\\u2028";
        case '\u2029':
          return "\\u2029";
        default:
          return null;
      }
    }
  }

  /**
   * Implements the {@code |escapeJsRegex} directive which allows arbitrary content to be included
   * inside a JavaScript regular expression.
   */
  public static final class EscapeJsRegex extends CrossLanguageStringXform {
    /** Implements the {@code |escapeJsRegex} directive. */
    public static final EscapeJsRegex INSTANCE = new EscapeJsRegex();

    private EscapeJsRegex() {
      // TODO(msamuel): maybe use goog.string.regExpEscape after fixing it to escape
      // [\r\n\u2028\u2029]
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new JsEscapeListBuilder()
          // Some control characters.
          .escape('\u0000')
          .escape('\b') // \\b means word-break inside RegExps.
          .escape('\t', "\\t")
          .escape('\n', "\\n")
          .escape('\u000b') // \\v not consistently supported on IE.
          .escape('\f', "\\f")
          .escape('\r', "\\r")
          .escape('\\', "\\\\") // Escape prefix
          .escapeAll("\u2028\u2029") // JavaScript newlines
          .escape('\u0085') // A JavaScript newline according to at least one draft spec.
          // Quoting characters.  / is also instrumental in </script>.
          .escape('"')
          .escape('\'')
          .escape('/', "\\/")
          // HTML special characters.  Note, that this provides added protection against problems
          // with </script> <![CDATA[, ]]>, <!--, -->, etc.
          .escapeAll("<>&=")
          // Special in regular expressions.  / is also special, but is escaped above.
          .escapeAll("$()*+-.:?[]^{|},")
          .build();
    }
  }

  /**
   * Escapes using CSS hex escapes with a space at the end in case a hex digit is the next character
   * : {@code 'A' => "\41 "}
   */
  private static final class CssEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      return String.format("\\%x ", (int) plainText);
    }
  }

  /**
   * Implements the {@code |escapeCssString} directive which allows arbitrary content to be included
   * in a CSS quoted string or identifier.
   */
  public static final class EscapeCssString extends CrossLanguageStringXform {
    /** Implements the {@code |escapeCssString} directive. */
    public static final EscapeCssString INSTANCE = new EscapeCssString();

    private EscapeCssString() {}

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new CssEscapeListBuilder()
          // Escape newlines and similar control characters, quotes, HTML special characters, and
          // CSS punctuation that might cause CSS error recovery code to restart parsing in the
          // middle of a string.
          // Semicolons, close curlies, and @ (which precedes top-level directives like @media),
          // and slashes in comment delimiters are all good places for CSS error recovery code to
          // skip to.
          // Quotes and parentheses are used as string and URL delimiters.
          // Angle brackets and slashes appear in escaping text spans allowed in HTML5 <style>
          // that might affect the parsing of subsequent content, and < appears in
          // </style> which could prematurely close a style element.
          // Newlines are disallowed in strings, so not escaping them can trigger CSS error
          // recovery.
          .escapeAll("\u0000\b\t\n\u000b\f\r\u0085\u00a0\u2028\u2029\"'\\<>&{};:()@/=*")
          .build();
    }
  }

  /** Escapes using URI percent encoding : {@code 'A' => "%41"} */
  private static final class UriEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      // URI encoding is different from the other escaping schemes.
      // The others are transformations on strings of UTF-16 code units, but URIs are composed of
      // strings of bytes.  We assume UTF-8 as the standard way to convert between bytes and code
      // units below.
      byte[] bytes = Character.toString(plainText).getBytes(UTF_8);
      int numBytes = bytes.length;
      StringBuilder sb = new StringBuilder(numBytes * 3);
      for (byte aByte : bytes) {
        // Use uppercase escapes for consistency with CharEscapers.uriEscaper().
        sb.append(String.format("%%%02X", aByte));
      }
      return sb.toString();
    }
  }

  /**
   * Implements the {@code |normalizeUri} directive which allows arbitrary content to be included in
   * a URI regardless of the string delimiters of the surrounding language. This normalizes, but
   * does not escape, so it does not affect URI special characters, but instead escapes HTML, CSS,
   * and JS delimiters.
   */
  public static final class NormalizeUri extends CrossLanguageStringXform {
    /** Implements the {@code |normalizeUri} directive. */
    public static final NormalizeUri INSTANCE = new NormalizeUri();

    private NormalizeUri() {}

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new UriEscapeListBuilder()
          // Escape all ASCII control characters.
          .escapeAll("\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007")
          .escapeAll("\u0008\u0009\n\u000B\u000C\r\u000E\u000F")
          .escapeAll("\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017")
          .escapeAll("\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F")
          .escape('\u007f')
          // Escape non-special URI characters that might prematurely close an unquoted CSS URI or
          // HTML attribute.
          // Parentheses and single quote are technically sub-delims, but not in HTTP or HTTPS,
          // only appearing in the obsolete mark rule in section D.2. of RFC 3986.
          // It is important to encode parentheses to prevent CSS URIs from being broken as in:
          //      background: {lb} background-image: url( /foo/{print $x}.png ) {rb}
          // It is important to encode both quote characters to prevent broken CSS URIs and HTML
          // attributes as in:
          //      background: {lb} background-image: url('/foo/{print $x}.png') {rb}
          // and
          //      <img src="/foo/{print $x}.png">
          .escapeAll(" (){}\"'\\<>")
          // More spaces and newlines.
          .escapeAll("\u0085\u00A0\u2028\u2029")
          // Make sure that full-width versions of reserved characters are escaped.
          // Some user-agents treat full-width characters in URIs entered in the URL bar the same
          // as the ASCII version so that URLs copied and pasted from written Chinese work.
          // Each Latin printable character has a full-width equivalent in the U+FF00 code plane,
          // e.g. the full-width colon is \uFF1A.
          // http://www.cisco.com/en/US/products/products_security_response09186a008083f82e.html
          // says that it is possible to route malicious URLs through intervening layers to the
          // browser by using the full-width equivalents of special characters.
          .escapeAll(toFullWidth(":/?#[]@!$&'()*+,;="))
          .build();
    }
  }

  /** Like {@link NormalizeUri} but filters out dangerous protocols. */
  public static final class FilterNormalizeUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterNormalizeUri} directive. */
    public static final FilterNormalizeUri INSTANCE = new FilterNormalizeUri();

    private FilterNormalizeUri() {
      // Disallows the javascript: protocol.
      // The below passes if there is
      // (1) Either an explicit protocol that is not 'javascript' is provided. The protocol must
      //     only contain characters allowed by the URL specification, namely ASCII alphanumerics,
      //     U+002B (+), U+002D (-), or U+002E (.).
      // (2) or no protocol.  A protocol must be followed by a colon.  The below allows that by
      //     allowing colons only after one of the characters [/?#].
      //     A colon after a hash (#) must be in the fragment.
      //     Otherwise, a colon after a (?) must be in a query.
      //     Otherwise, a colon after a single solidus (/) must be in a path.
      //     Otherwise, a colon after a double solidus (//) must be in the authority (before port).
      //
      // Finally, the pattern disallows &, used in HTML entity declarations before one of the
      // characters in [/?#].
      // This disallows HTML entities used in the protocol name, which should never happen,
      // e.g. "h&#116;tp" for "http".
      // It also disallows HTML entities in the first path part of a relative path,
      // e.g. "foo&lt;bar/baz".  Our existing escaping functions should not produce that.
      // More importantly, it disallows masking of a colon, e.g. "javascript&#58;...".
      super(
          // Pattern for the scheme: https://url.spec.whatwg.org/#scheme-state
          // Must have an alphanumeric (or +-.) scheme that is not javascript, or no scheme. Case
          // insensitive.
          /* valueFilter= */ Pattern.compile(
              "^(?!javascript:)(?:[a-z0-9+.-]+:|[^&:/?#]*(?:[/?#]|\\z))",
              Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return NormalizeUri.INSTANCE.defineEscapes();
    }

    @Override
    public String getInnocuousOutput() {
      return "about:invalid#" + INNOCUOUS_OUTPUT;
    }
  }

  /**
   * Like {@link FilterNormalizeUri}, but also accepts {@code data:} and {@code blob:} URIs, since
   * image sources don't execute script in the same origin as the page (although image handling
   * 0-days are available from time to time, but a templating language can't realistically try to
   * protect against such a thing).
   *
   * <p>Only intended to be used with images; for videos and audio we expect some sort of further
   * review since they can more easily be used for social engineering. Video and audio still accept
   * http/https because remote video and audio can still be protected against via CSP, but data URIs
   * don't have self-evident provenance.
   */
  public static final class FilterNormalizeMediaUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterNormalizeMediaUri} directive. */
    public static final FilterNormalizeMediaUri INSTANCE = new FilterNormalizeMediaUri();

    private FilterNormalizeMediaUri() {
      // For image URIs, we use a relatively permissive filter. We accept:
      // - http and https URLs
      // - data URLs of supported types
      // We don't worry about sequences of "/../" here, because path traversal isn't a worry for
      // images, and detecting /../ sequences would add unnecessary complexity here.
      super(
          /* valueFilter= */ Pattern.compile(
              // Allow relative URIs.
              "^[^&:/?#]*(?:[/?#]|\\z)"
                  // Allow http, https and ftp URIs.
                  + "|^https?:"
                  + "|^ftp:"
                  // Allow image data URIs. Ignore the subtype because browsers ignore them anyways.
                  // In fact, most browsers happily accept text/html or a completely empty MIME, but
                  // it doesn't hurt to verify that it at least looks vaguely correct.
                  + "|^data:image/[a-z0-9+-]+"
                  + ";base64,[a-z0-9+/]+=*\\z"
                  // Blob URIs -- while there's no saying what's in them, (a) they are created on
                  // the same origin, and (b) no worse than loading a random http/https link.
                  + "|^blob:",
              Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return NormalizeUri.INSTANCE.defineEscapes();
    }

    @Override
    public String getInnocuousOutput() {
      // NOTE: about:invalid is registered in http://www.w3.org/TR/css3-values/#about-invalid :
      // "The about:invalid URI references a non-existent document with a generic error condition.
      // It can be used when a URI is necessary, but the default value shouldn't be resolveable as
      // any type of document."
      return "about:invalid#" + INNOCUOUS_OUTPUT;
    }
  }

  /**
   * Accepts only data URI's that contain an image.
   *
   * <p>Developers use this simultaneously to allow data URI's, but also to ensure that the image
   * tag won't initiate any HTTP requests.
   *
   * <p>NOTE: We may consider deprecating this now that img/data URIs are allowed by default, since
   * it's unlikely too many projects need a mechanism to double-check that images are only loaded
   * from data URIs; anyone else that does can simply scan the URL and fail if it detects
   * http/https.
   */
  public static final class FilterImageDataUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterImageDataUri} directive. */
    public static final FilterImageDataUri INSTANCE = new FilterImageDataUri();

    private FilterImageDataUri() {
      super(
          /* valueFilter= */ Pattern.compile(
              "^data:image/(?:bmp|gif|jpe?g|png|tiff|webp|x-icon);base64,[a-z0-9+/]+=*\\z",
              Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      // No normalization or escaping necessary -- the filter is limited to a strict subset that
      // doesn't involve html stop-chars.
      return ImmutableList.of();
    }

    @Override
    public String getInnocuousOutput() {
      // Return something that is both clearly an image, but clearly invalid. We don't want the
      // browser to fetch anything. We also don't necessarily want a transparent gif, since it
      // doesn't alert developers to an issue. And finally, by not starting with GIF89a, we ensure
      // the browser doesn't attempt to actually decode it and crash.
      return "data:image/gif;base64,zSoyz";
    }
  }

  /**
   * Accepts only sip URIs but does not verify complete correctness.
   *
   * <p>The RFC for sip: https://tools.ietf.org/html/rfc3261
   *
   * <p>The RFC for URIs: https://tools.ietf.org/html/rfc3986
   */
  public static final class FilterSipUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterSipUri} directive. */
    public static final FilterSipUri INSTANCE = new FilterSipUri();

    private FilterSipUri() {
      super(
          /* valueFilter= */ Pattern.compile(
              "^sip:[0-9a-z;=\\-+._!~*' /():&$#?@,]+\\z", Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.of();
    }

    @Override
    public String getInnocuousOutput() {
      // NOTE: about:invalid is registered in http://www.w3.org/TR/css3-values/#about-invalid :
      // "The about:invalid URI references a non-existent document with a generic error condition.
      // It can be used when a URI is necessary, but the default value shouldn't be resolveable as
      // any type of document."
      return "about:invalid#" + INNOCUOUS_OUTPUT;
    }
  }

  /**
   * Accepts only sms URIs but does not verify complete correctness. The regular expression will
   * allow spaces in the phone number this is not part of the RFC but supported by browsers.
   *
   * <p>The RFC for the sms: https://tools.ietf.org/html/rfc5724
   *
   * <p>The RFC for URIs: https://tools.ietf.org/html/rfc3986
   */
  public static final class FilterSmsUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterSmsUri} directive. */
    public static final FilterSmsUri INSTANCE = new FilterSmsUri();

    private FilterSmsUri() {
      super(
          /* valueFilter= */ Pattern.compile(
              "^sms:[0-9a-z;=\\-+._!~*' /():&$#?@,]+\\z", Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.of();
    }

    @Override
    public String getInnocuousOutput() {
      // NOTE: about:invalid is registered in http://www.w3.org/TR/css3-values/#about-invalid :
      // "The about:invalid URI references a non-existent document with a generic error condition.
      // It can be used when a URI is necessary, but the default value shouldn't be resolveable as
      // any type of document."
      return "about:invalid#" + INNOCUOUS_OUTPUT;
    }
  }

  /**
   * Accepts only tel URIs but does not verify complete correctness.
   *
   * <p>The RFC for the tel: URI https://tools.ietf.org/html/rfc3966
   */
  public static final class FilterTelUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterTelUri} directive. */
    public static final FilterTelUri INSTANCE = new FilterTelUri();

    private FilterTelUri() {
      super(
          /* valueFilter= */ Pattern.compile(
              "^tel:(?:[0-9a-z;=\\-+._!~*' /():&$#?@,]"
                  // Percent encoded '#'
                  + "|%23"
                  // Percent encoded ','
                  + "|%2C"
                  // Percent encoded ';'
                  + "|%3B"
                  + ")+\\z",
              Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.of();
    }

    @Override
    public String getInnocuousOutput() {
      // NOTE: about:invalid is registered in http://www.w3.org/TR/css3-values/#about-invalid :
      // "The about:invalid URI references a non-existent document with a generic error condition.
      // It can be used when a URI is necessary, but the default value shouldn't be resolveable as
      // any type of document."
      return "about:invalid#" + INNOCUOUS_OUTPUT;
    }
  }

  /**
   * Implements the {@code |escapeUri} directive which allows arbitrary content to be included in a
   * URI regardless of the string delimiters of the surrounding language.
   */
  public static final class EscapeUri extends CrossLanguageStringXform {
    /** Implements the {@code |escapeUri} directive. */
    public static final EscapeUri INSTANCE = new EscapeUri();

    private EscapeUri() {
      checkState(this.nonAsciiCodeUnits == null);
    }

    @Override
    public String getNonAsciiPrefix() {
      return "%";
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      // From Appendix A of RFC 3986
      // unreserved := ALPHA / DIGIT / "-" / "." / "_" / "~"
      String unreservedChars = "-.";
      for (char c = '0'; c <= '9'; c++) {
        unreservedChars += c;
      }
      for (char c = 'A'; c <= 'Z'; c++) {
        unreservedChars += c;
      }
      unreservedChars += '_';
      for (char c = 'a'; c <= 'z'; c++) {
        unreservedChars += c;
      }
      unreservedChars += '~';
      return new UriEscapeListBuilder()
          .escapeAllInRangeExcept(0, 0x80, unreservedChars.toCharArray())
          // All non-ASCII codepoints escaped per the constructor above.
          .build();
    }

    @Override
    public List<String> getLangFunctionNames(EscapingLanguage language) {
      if (language == EscapingLanguage.JAVASCRIPT) {
        return ImmutableList.of("goog.string.urlEncode", "encodeURIComponent");
      } else if (language == EscapingLanguage.PYTHON) {
        return ImmutableList.of("quote");
      }
      return super.getLangFunctionNames(language);
    }

    @Override
    public final String escape(String string) {
      var escapesByCodeUnit = this.escapesByCodeUnit;
      for (int i = 0, n = string.length(); i < n; ++i) {
        char c = string.charAt(i);
        if (c < 0x80) { // Use the dense map.
          String esc = escapesByCodeUnit[c];
          if (esc != null) {
            return escapeOntoStringBuilderStartingAt(string, i);
          }
        } else {
          return escapeOntoStringBuilderStartingAt(string, i);
        }
      }
      return string;
    }

    @Override
    @CanIgnoreReturnValue
    public final Appendable escapeOnto(char c, Appendable out) throws IOException {
      if (c < 0x80) { // Use the dense map.
        String esc = escapesByCodeUnit[c];
        if (esc != null) {
          return out.append(esc);
        }
      } else {
        escapeUsingPercent(c, out);
        return out;
      }
      return out.append(c);
    }

    @Override
    @CanIgnoreReturnValue
    public final Appendable escapeOnto(CharSequence s, Appendable out, int start, int end)
        throws IOException {
      int pos = start;
      var escapesByCodeUnit = this.escapesByCodeUnit;
      for (int i = start; i < end; ++i) {
        char c = s.charAt(i);
        if (c < 0x80) { // Use the dense map.
          String esc = escapesByCodeUnit[c];
          if (esc != null) {
            out.append(s, pos, i).append(esc);
            pos = i + 1;
          }
        } else { // use the prefix
          out.append(s, pos, i);
          escapeUsingPercent(c, out);
          pos = i + 1;
        }
      }
      return out.append(s, pos, end);
    }

    /**
     * Appends a percent-encoded version of the given code unit to the given appendable.
     *
     * @param c A code unit greater than or equal to 0x80.
     * @param out written to.
     */
    private void escapeUsingPercent(char c, Appendable out) throws IOException {
      // Use larger appends to reduce overhead.  These strings are trivial for the runtime to
      // construct.
      // Use a UTF-8
      int trailing = (c & 0x3f) | 0x80;
      if (c < 0x800) {
        int leading = ((c >>> 6) & 0x1f) | 0xc0;
        out.append(
            "%"
                + HEX_DIGITS[leading >>> 4]
                + HEX_DIGITS[leading & 0xf]
                + '%'
                + HEX_DIGITS[trailing >>> 4]
                + HEX_DIGITS[trailing & 0xf]);
      } else {
        int upper = ((c >>> 12) & 0xf) | 0xe0;
        int middle = ((c >>> 6) & 0x3f) | 0x80;
        out.append(
            "%"
                + HEX_DIGITS[upper >>> 4]
                + HEX_DIGITS[upper & 0xf]
                + '%'
                + HEX_DIGITS[middle >>> 4]
                + HEX_DIGITS[middle & 0xf]
                + '%'
                + HEX_DIGITS[trailing >>> 4]
                + HEX_DIGITS[trailing & 0xf]);
      }
    }

    private static final char[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
    };
  }

  /**
   * Implements the {@code |filterHtmlAttributes} directive which filters out identifiers that can't
   * appear as part of an HTML tag or attribute name.
   */
  public static final class FilterHtmlAttributes extends CrossLanguageStringXform {
    /** Implements the {@code |filterHtmlAttributes} directive. */
    public static final FilterHtmlAttributes INSTANCE = new FilterHtmlAttributes();

    private FilterHtmlAttributes() {
      super(
          /* valueFilter= */ Pattern.compile(
              "^"
                  // Disallow on* and src* attribute names.
                  + "(?!on|src|"
                  // Disallow specific other attribute names.
                  + "(?:action|archive|background|cite|classid|codebase|content|data|dsync|href"
                  + "|http-equiv|longdesc|style|usemap)\\s*$)"
                  + "(?:"
                  // Must match letters
                  + "[a-z0-9_$:-]*"
                  // Match until the end.
                  + ")\\z",
              Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.of();
    }
  }

  /**
   * Implements the {@code |filterHtmlElementName} directive which filters out identifiers that
   * can't appear as part of an HTML tag or attribute name.
   */
  public static final class FilterHtmlElementName extends CrossLanguageStringXform {
    /** Implements the {@code |filterHtmlElementName} directive. */
    public static final FilterHtmlElementName INSTANCE = new FilterHtmlElementName();

    private FilterHtmlElementName() {
      super(
          /* valueFilter= */ Pattern.compile(
              "^"
                  // Disallow special element names.
                  + "(?!base|iframe|link|noframes|noscript|object|script|style|textarea|title|xmp)"
                  + "[a-z0-9_$:-]*\\z",
              Pattern.CASE_INSENSITIVE));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.of();
    }
  }

  /**
   * Implements the {@code |filterCspNonceValue} directive
   *
   * <p>This only allows alphanumeric, plus, slash, undescore, dash and equals (in suffix position).
   * So importantly it shouldn't be used in any programming-languagey context, such as:
   *
   * <ul>
   *   <li>JavaScript outside a string
   *   <li>CSS outside a string
   *   <li>tag names, attribute names ("attributes" context)
   * </ul>
   *
   * <p>It is allowed in:
   *
   * <ul>
   *   <li>HTML pcdata, rcdata, attribute values, even nospace
   *   <li>CSS and JS strings
   *   <li>HTML, JS, CSS comments
   * </ul>
   *
   * <p>And in practice, it is only used in:
   *
   * <ul>
   *   <li>HTML attribute values
   * </ul>
   *
   * <p>See also https://www.w3.org/TR/CSP3/#grammardef-base64-value
   */
  public static final class FilterCspNonceValue extends CrossLanguageStringXform {
    public static final FilterCspNonceValue INSTANCE = new FilterCspNonceValue();

    private FilterCspNonceValue() {
      super(/* valueFilter= */ Pattern.compile("^[a-zA-Z0-9+/_-]+={0,2}$"));
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.of();
    }

    @Override
    public String getInnocuousOutput() {
      return INNOCUOUS_OUTPUT;
    }
  }

  /** An accessor for all string transforms defined above. */
  public static Iterable<CrossLanguageStringXform> getAllEscapers() {
    // This list is hard coded but is checked by unittests for the contextual auto-escaper.
    return ImmutableList.of(
        EscapeHtml.INSTANCE,
        NormalizeHtml.INSTANCE,
        EscapeHtmlNospace.INSTANCE,
        NormalizeHtmlNospace.INSTANCE,
        EscapeJsString.INSTANCE,
        EscapeJsRegex.INSTANCE,
        EscapeCssString.INSTANCE,
        EscapeUri.INSTANCE,
        NormalizeUri.INSTANCE,
        FilterNormalizeUri.INSTANCE,
        FilterNormalizeMediaUri.INSTANCE,
        FilterImageDataUri.INSTANCE,
        FilterSipUri.INSTANCE,
        FilterSmsUri.INSTANCE,
        FilterTelUri.INSTANCE,
        FilterHtmlAttributes.INSTANCE,
        FilterHtmlElementName.INSTANCE,
        FilterCspNonceValue.INSTANCE);
  }

  /**
   * A string, used as the result of a filter when the filter pattern does not match the input, that
   * is not a substring of any keyword or well-known identifier in HTML, JS, or CSS and that is a
   * valid identifier part in all those languages, and which cannot terminate a string, comment, or
   * other bracketed section.
   *
   * <p>This string is also longer than necessary so that developers can use grep when it starts
   * showing up in their output.
   *
   * <p>If grep directed you here, then one of your Soy templates is using a filter directive that
   * is receiving a potentially unsafe input. Run your app in debug mode and you should get the name
   * of the directive and the input deemed unsafe.
   */
  public static final String INNOCUOUS_OUTPUT = "zSoyz";

  private static final String HTML_TAG_FIRST_TOKEN_STR = "(?:!|/?([a-zA-Z][a-zA-Z0-9:\\-]*))";

  /**
   * Loose matcher for HTML tags, DOCTYPEs, and HTML comments. This will reliably find HTML tags
   * (though not CDATA tags and not XML tags whose name or namespace starts with a non-latin
   * character), and will do a good job with DOCTYPES (though will have trouble with complex
   * doctypes that define their own entities) and does a decent job with simple HTML comments.
   *
   * <p>This should be good enough since HTML sanitizers do not typically output comments, or CDATA,
   * or RCDATA content.
   *
   * <p>The tag name, if any is in group 1.
   *
   * @deprecated Previously used in Java and JS but now only shared with Python. Replaced with
   *     custom loop that avoids performance cliffs.
   */
  @Deprecated
  public static final Pattern HTML_TAG_CONTENT =
      Pattern.compile(
          // Matches a left angle bracket followed by either
          // (1) a "!" which indicates a doctype or comment, or
          // (2) an optional solidus (/, indicating an end tag) and an HTML tag name.
          // followed by any number of quoted strings (found in tags and doctypes) or other content
          // terminated by a right angle bracket.
          "<" + HTML_TAG_FIRST_TOKEN_STR + "(?:[^>'\"]|\"[^\"]*\"|'[^']*')*>");

  /**
   * Used by custom loops in Java and JS. Matches the beginning of an HTML comment or element,
   * assuming that the previous character is a '<'. e.g. matches "!--" (comment), "b" (open tag),
   * "/b" (close tag). Must be used with {@link java.util.regex.Matcher#region}.
   */
  public static final Pattern HTML_TAG_FIRST_TOKEN =
      Pattern.compile("^" + HTML_TAG_FIRST_TOKEN_STR);

  /**
   * Convert an ASCII string to full-width. Full-width characters are in Unicode page U+FFxx and are
   * used to allow ASCII characters to be embedded in written Chinese without breaking alignment --
   * so a sinograph which occupies two columns can line up properly with a Latin letter or symbol
   * which normally occupies only one column.
   *
   * <p>See <a
   * href="http://en.wikipedia.org/wiki/Duplicate_characters_in_Unicode#CJK_fullwidth_forms">CJK
   * fullwidth forms</a> and <a href="unicode.org/charts/PDF/UFF00.pdf">unicode.org</a>.
   */
  private static String toFullWidth(String ascii) {
    int numChars = ascii.length();
    StringBuilder sb = new StringBuilder(ascii);
    for (int i = 0; i < numChars; ++i) {
      char ch = ascii.charAt(i);
      if (ch < 0x80) {
        sb.setCharAt(i, (char) (ch + 0xff00 - 0x20));
      }
    }
    return sb.toString();
  }

  private EscapingConventions() {}
}
