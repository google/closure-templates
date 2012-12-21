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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.template.soy.internal.base.Escaper;

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
 * <p>
 * An escaping convention is defined in terms of
 * <ol>
 *   <li>An optional filter predicate that all valid inputs must match.
 *   <li>An optional function name from the closure JavaScript library that
 *       already implements the escaping convention.
 *   <li>A required mapping from characters to escaping strings.
 * </ol>
 *
 * <p>
 * Escaping functions are exposed as {@link Escaper}s in Java and via a JavaScript code
 * generating ant task for JavaScript.
 *
 * @author Mike Samuel
 */
@ParametersAreNonnullByDefault
public final class EscapingConventions {


  // Below we take advantage of lazy class loading to avoid doing the work of initializing maps
  // or loading code for escaping conventions never used by the Java runtime.
  // We first define a base class that collects the information above, and that allows enumeration
  // over escaped characters.
  // Each escaping convention is its own public interface to java code, and the JavaScript code
  // generator uses a public accessor that ties them all together.


  /**
   * A mapping from a plain text character to the escaped text in the target language.
   * We define a character below as a code unit, not a codepoint as none of the target languages
   * treat supplementary codepoints as special.
   */
  public static final class Escape implements Comparable<Escape> {
    private final char plainText;
    private final String escaped;

    public Escape(char plainText, String escaped) {
      this.plainText = plainText;
      this.escaped = escaped;
    }

    /**
     * A character in the input language.
     */
    public char getPlainText() {
      return plainText;
    }

    /**
     * A string in the output language that corresponds to {@link #getPlainText}
     * in the input language.
     */
    public String getEscaped() {
      return escaped;
    }

    @Override public int compareTo(Escape b) {
      return this.plainText - b.plainText;
    }
  }


  /**
   * A transformation on strings that preserves some correctness or safety properties.
   * Subclasses come in three varieties:
   * <dl>
   *   <dt>Escaper</dt>
   *     <dd>A mapping from strings in an input language to strings in an output language that
   *     preserves the content.
   *     E.g. the plain text string {@code 1 < 2} can be escaped to the equivalent HTML string
   *     {@code 1 &lt; 2}.</dd>
   *   <dt>Normalizer</dt>
   *     <dd>A mapping from strings in a language to equivalent strings in the same language but
   *     that can be more easily embedded in another language.
   *     E.g. the URI {@code http://www.google.com/search?q=O'Reilly} is equivalent to
   *     {@code http://www.google.com/search?q=O%27Reilly} but the latter can be safely
   *     embedded in a single quoted HTML attribute.</dd>
   *   <dt>Filter</dt>
   *     <dd>A mapping from strings in a language to the same value or to an innocuous value.
   *     E.g. the string {@code h1} might pass an html identifier filter but the string
   *     {@code ><script>alert('evil')</script>} should not and could be replaced by an innocuous
   *     value like {@code zzz}.</dd>
   * </dl>
   */
  public static abstract class CrossLanguageStringXform implements Escaper {
    private final String directiveName;
    private final @Nullable Pattern valueFilter;
    private final @Nullable List<String> jsNames;
    private final ImmutableList<Escape> escapes;
    /**
     * A dense mapping mirroring escapes.
     * I.e. for each element of {@link #escapes} {@code e} such that {@code e.plainText < 0x80},
     * {@code escapesByCodeUnit[e.plainText] == e.escaped}.
     */
    private final String[] escapesByCodeUnit;
    /** Keys in a sparse mapping for the non ASCII {@link #escapes}. */
    private final char[] nonAsciiCodeUnits;
    /** Values in a sparse mapping corresponding to {@link #nonAsciiCodeUnits}. */
    private final String[] nonAsciiEscapes;
    /** @see #getNonAsciiPrefix */
    private final @Nullable String nonAsciiPrefix;

    /**
     * @param valueFilter {@code null} if the directive accepts all strings as inputs.  Otherwise
     *     a regular expression that accepts only strings that can be escaped by this directive.
     * @param jsNames The names of existing JavaScript builtins or Google Closure functions, if
     *     any exist, that implements this escaping convention.
     * @param nonAsciiPrefix An escaping prefix in {@code "%", "\\u", "\\"} which specifies how to
     *     escape non-ASCII code units not in the sparse mapping.
     *     If null, then non-ASCII code units outside the sparse map can appear unescaped.
     */
    protected CrossLanguageStringXform(
        @Nullable Pattern valueFilter, List<? extends String> jsNames,
        @Nullable String nonAsciiPrefix) {
      String simpleName = getClass().getSimpleName();
      // EscapeHtml -> |escapeHtml
      this.directiveName = ("|" + Character.toLowerCase(simpleName.charAt(0)) +
                            simpleName.substring(1));

      this.valueFilter = valueFilter;
      this.jsNames = ImmutableList.<String>copyOf(jsNames);
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
        escapesByCodeUnit = new String[escapes.get(numAsciiEscapes - 1).plainText + 1];
        for (Escape escape : escapes.subList(0, numAsciiEscapes)) {
          escapesByCodeUnit[escape.plainText] = escape.escaped;
        }
      } else {
        escapesByCodeUnit = new String[0];
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
        nonAsciiCodeUnits = new char[0];
        nonAsciiEscapes = new String[0];
      }

      // The fallback mode if neither the ASCII nor non-ASCII escaping maps contain a mapping.
      this.nonAsciiPrefix = nonAsciiPrefix;
    }


    /**
     * Returns the escapes used for this escaper.
     */
    protected abstract ImmutableList<Escape> defineEscapes();


    /**
     * The name of the directive associated with this escaping function.
     * @return E.g. {@code |escapeHtml}
     */
    public String getDirectiveName() {
      return directiveName;
    }

    /**
     * An escaping prefix in {@code "%", "\\u", "\\"} which specifies how to escape non-ASCII code
     * units not in the sparse mapping.
     * If null, then non-ASCII code units outside the sparse map can appear unescaped.
     */
    public final @Nullable String getNonAsciiPrefix() {
      return nonAsciiPrefix;
    }

    /**
     * Null if the escaper accepts all strings as inputs, or otherwise a regular expression
     * that accepts only strings that can be escaped by this escaper.
     */
    public final @Nullable Pattern getValueFilter() {
      return valueFilter;
    }

    /**
     * The names of existing JavaScript builtins or Google Closure functions that implement
     * the escaping convention.
     * @return {@code null} if there is no such function.
     */
    public final List<String> getJsFunctionNames() {
      return jsNames;
    }

    /**
     * The escapes need to translate the input language to the output language.
     */
    public final ImmutableList<Escape> getEscapes() {
      return escapes;
    }

    /**
     * Returns an innocuous string in this context that can be used when filtering.
     */
    public String getInnocuousOutput() {
      return INNOCUOUS_OUTPUT;
    }


    // Methods that satisfy the Escaper interface.
    @Override
    public final String escape(String string) {
      // We pass null so that we don't unnecessarily allocate (and zero) or copy char arrays.
      StringBuilder sb = maybeEscapeOnto(string, null);
      return sb != null ? sb.toString() : string;
    }

    @Override
    public final Appendable escape(final Appendable out) {
      return new Appendable() {
        @Override public Appendable append(CharSequence csq) throws IOException {
          maybeEscapeOnto(csq, out, 0, csq.length());
          return this;
        }

        @Override public Appendable append(CharSequence csq, int start, int end)
            throws IOException {
          maybeEscapeOnto(csq, out, start, end);
          return this;
        }

        @Override public Appendable append(char c) throws IOException {
          if (c < escapesByCodeUnit.length) {  // Use the dense map.
            String esc = escapesByCodeUnit[c];
            if (esc != null) {
              out.append(esc);
              return this;
            }
          } else if (c >= 0x80) {
            int index = Arrays.binarySearch(nonAsciiCodeUnits, c);
            if (index >= 0) {  // Found in the sparse map.
              out.append(nonAsciiEscapes[index]);
              return this;
            }
            if (nonAsciiPrefix != null) {  // Fallback for non-ASCII code units.
              escapeUsingPrefix(c, out);
              return this;
            }
          }
          out.append(c);
          return this;
        }
      };
    }

    /**
     * Escapes the given char sequence onto the given buffer iff it contains characters that need to
     * be escaped.
     * @return null if no output buffer was passed in, and s contains no characters that need
     *    escaping.  Otherwise out, or a StringBuilder if one needed to be allocated.
     */
    private @Nullable StringBuilder maybeEscapeOnto(CharSequence s, @Nullable StringBuilder out) {
      try {
        return (StringBuilder) maybeEscapeOnto(s, out, 0, s.length());
      } catch (IOException ex) {
        // StringBuilders should not throw IOExceptions.
        throw new AssertionError(ex);
      }
    }

    /**
     * Escapes the given range of the given sequence onto the given buffer iff it contains
     * characters that need to be escaped.
     * @return null if no output buffer was passed in, and s contains no characters that need
     *    escaping.  Otherwise out, or a StringBuilder if one needed to be allocated.
     */
    private @Nullable Appendable maybeEscapeOnto(
        CharSequence s, @Nullable Appendable out, int start, int end)
        throws IOException {
      int pos = start;
      for (int i = start; i < end; ++i) {
        char c = s.charAt(i);
        if (c < escapesByCodeUnit.length) {  // Use the dense map.
          String esc = escapesByCodeUnit[c];
          if (esc != null) {
            if (out == null) {
              // Create a new buffer if we need to escape a character in s.
              // We add 32 to the size to leave a decent amount of space for escape characters.
              out = new StringBuilder(end - start + 32);
            }
            out.append(s, pos, i).append(esc);
            pos = i + 1;
          }
        } else if (c >= 0x80) {  // Use the sparse map.
          int index = Arrays.binarySearch(nonAsciiCodeUnits, c);
          if (index >= 0) {
            if (out == null) {
              out = new StringBuilder(end - start + 32);
            }
            out.append(s, pos, i).append(nonAsciiEscapes[index]);
            pos = i + 1;
          } else if (nonAsciiPrefix != null) {  // Fallback to the prefix based escaping.
            if (out == null) {
              out = new StringBuilder(end - start + 32);
            }
            out.append(s, pos, i);
            escapeUsingPrefix(c, out);
            pos = i + 1;
          }
        }
      }
      if (out != null) {
        out.append(s, pos, end);
      }
      return out;
    }

    /**
     * Appends a hex representation of the given code unit to out preceded by the
     * {@link #nonAsciiPrefix}.
     *
     * @param c A code unit greater than or equal to 0x80.
     * @param out written to.
     */
    private void escapeUsingPrefix(char c, Appendable out) throws IOException {
      if ("%".equals(nonAsciiPrefix)) {  // Use a UTF-8
        if (c < 0x800) {
          out.append('%');
          appendHexPair(((c >>> 6) & 0x1f) | 0xc0, out);
        } else {
          out.append('%');
          appendHexPair(((c >>> 12) & 0xf) | 0xe0, out);
          out.append('%');
          appendHexPair(((c >>> 6) & 0x3f) | 0x80, out);
        }
        out.append('%');
        appendHexPair((c & 0x3f) | 0x80, out);
      } else {
        out.append(nonAsciiPrefix);
        appendHexPair((c >>> 8) & 0xff, out);
        appendHexPair(c & 0xff, out);
        if ("\\".equals(nonAsciiPrefix)) {
          // Append with a space so that CSS escape doesn't pull in any hex digits following.
          out.append(' ');
        }
      }
    }

    private static final char[] HEX_DIGITS = {
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
    };
    /**
     * Given {@code 0x20} appends {@code "20"} to the given output buffer.
     */
    private void appendHexPair(int b, Appendable out) throws IOException {
      out.append(HEX_DIGITS[b >>> 4]);
      out.append(HEX_DIGITS[b & 0xf]);
    }
  }


  /**
   * A builder for lists of escapes.
   */
  private static abstract class EscapeListBuilder {
    private final List<Escape> escapes = Lists.newArrayList();

    /**
     * Computes the numeric escape in the output language for the given codepoint in the input
     * language.
     * E.g. in C, the numeric escape for space is {@code \x20}.
     */
    abstract String getNumericEscapeFor(char plainText);

    /**
     * Adds an escape for the given code unit in the input language to the given escaped text.
     */
    final EscapeListBuilder escape(char plainText, String escaped) {
      escapes.add(new Escape(plainText, escaped));
      return this;
    }

    /**
     * Adds an escape for the given code unit in the input language using the numeric escaping
     * scheme.
     */
    final EscapeListBuilder escape(char plainText) {
      escapes.add(new Escape(plainText, getNumericEscapeFor(plainText)));
      return this;
    }

    /**
     * Adds a numeric escape for each code unit in the input string.
     */
    final EscapeListBuilder escapeAll(String plainTextCodeUnits) {
      int numCodeUnits = plainTextCodeUnits.length();
      for (int i = 0; i < numCodeUnits; ++i) {
        escape(plainTextCodeUnits.charAt(i));
      }
      return this;
    }

    /**
     * Adds numeric escapes for each code unit in the given range not in the exclusion set.
     */
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

    /**
     * The list of all escapes defined thus far.
     */
    final ImmutableList<Escape> build() {
      Collections.sort(escapes);
      return ImmutableList.copyOf(escapes);
    }
  }


  /**
   * Escapes using HTML/XML numeric entities : {@code 'A' -> "&#65;"}.
   */
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


  /**
   * Implements the {@code |escapeHtml} directive.
   */
  public static final class EscapeHtml extends CrossLanguageStringXform {
    /** Implements the {@code |escapeHtml} directive. */
    public static final EscapeHtml INSTANCE = new EscapeHtml();

    private EscapeHtml() {
      // TODO: enable goog.string.htmlEscape after it escapes single quotes.
      super(null, ImmutableList.<String>of(/*"goog.string.htmlEscape"*/), null);
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
  }


  /**
   * A directive that encodes any HTML special characters that can appear in RCDATA unescaped but
   * that can be escaped without changing semantics.
   * From <a href="http://www.w3.org/TR/html5/tokenization.html#rcdata-state">HTML 5</a>:
   * <blockquote>
   *   <h4>8.2.4.3 RCDATA state</h4>
   *   Consume the next input character:
   *   <ul>
   *     <li>U+0026 AMPERSAND (&)
   *       <br>Switch to the character reference in RCDATA state.
   *     <li>U+003C LESS-THAN SIGN (<)
   *       <br>Switch to the RCDATA less-than sign state.
   *     <li>EOF
   *       <br>Emit an end-of-file token.
   *     <li>Anything else
   *       <br>Emit the current input character as a character token.
   *   </ul>
   * </blockquote>
   * So all HTML special characters can be escaped, except ampersand, since escaping that would
   * lead to overescaping of legitimate HTML entities.
   */
  public static final class NormalizeHtml extends CrossLanguageStringXform {
    /** Implements the {@code |normalizeHtml} directive. */
    public static final NormalizeHtml INSTANCE = new NormalizeHtml();

    private NormalizeHtml() {
      super(null, ImmutableList.<String>of(), null);
    }

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
   * Implements the {@code |escapeHtmlNoSpace} directive which allows arbitrary content
   * to be included in the value of an unquoted HTML attribute.
   */
  public static final class EscapeHtmlNospace extends CrossLanguageStringXform {
    /** Implements the {@code |escapeHtmlNospace} directive. */
    public static final EscapeHtmlNospace INSTANCE = new EscapeHtmlNospace();

    private EscapeHtmlNospace() {
      super(null, ImmutableList.<String>of(), null);
    }

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
          .escapeAll("\u0000\u0009\n\u000B\u000C\r '-/=\u0060\u0085\u00a0\u2028\u2029")
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

    private NormalizeHtmlNospace() {
      super(null, ImmutableList.<String>of(), null);
    }

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


  /**
   * Escapes using hex escapes since octal are non-standard.  'A' -> "\\x41"
   */
  private static final class JsEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      return String.format(plainText < 0x100 ? "\\x%02x" : "\\u%04x", (int) plainText);
    }
  }


  /**
   * Implements the {@code |escapeJsString} directive which allows arbitrary content
   * to be included inside a quoted JavaScript string.
   */
  public static final class EscapeJsString extends CrossLanguageStringXform {
    /** Implements the {@code |escapeJsString} directive. */
    public static final EscapeJsString INSTANCE = new EscapeJsString();

    private EscapeJsString() {
      super(null, ImmutableList.<String>of(), null);  // TODO: Maybe use goog.string.quote
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new JsEscapeListBuilder()
          // Some control characters.
          .escape('\u0000')
          .escape('\b')  // \\b means word-break inside RegExps.
          .escape('\t', "\\t")
          .escape('\n', "\\n")
          .escape('\u000b')  // \\v not consistently supported on IE.
          .escape('\f', "\\f")
          .escape('\r', "\\r")
          .escape('\\', "\\\\")
          // Quoting characters.  / is also instrumental in </script>.
          .escape('"')
          .escape('\'')
          .escape('/', "\\/")
          .escapeAll("\u2028\u2029")  // JavaScript newlines
          .escape('\u0085')  // A JavaScript newline according to at least one draft spec.
          // HTML special characters.  Note, that this provides added protection against problems
          // with </script> <![CDATA[, ]]>, <!--, -->, etc.
          .escapeAll("<>&=")
          .build();
    }
  }


  /**
   * Implements the {@code |escapeJsRegex} directive which allows arbitrary content
   * to be included inside a JavaScript regular expression.
   */
  public static final class EscapeJsRegex extends CrossLanguageStringXform {
    /** Implements the {@code |escapeJsRegex} directive. */
    public static final EscapeJsRegex INSTANCE = new EscapeJsRegex();

    private EscapeJsRegex() {
      // TODO: maybe use goog.string.regExpEscape after fixing it to escape [\r\n\u2028\u2029]
      super(null, ImmutableList.<String>of(), null);
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new JsEscapeListBuilder()
          // Some control characters.
          .escape('\u0000')
          .escape('\b')  // \\b means word-break inside RegExps.
          .escape('\t', "\\t")
          .escape('\n', "\\n")
          .escape('\u000b')  // \\v not consistently supported on IE.
          .escape('\f', "\\f")
          .escape('\r', "\\r")
          .escape('\\', "\\\\")  // Escape prefix
          .escapeAll("\u2028\u2029")  // JavaScript newlines
          .escape('\u0085')  // A JavaScript newline according to at least one draft spec.
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
   * Escapes using CSS hex escapes with a space at the end in case a hex digit is the next
   * character : {@code 'A' => "\41 "}
   */
  private static final class CssEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      return String.format("\\%x ", (int) plainText);
    }
  }


  /**
   * Implements the {@code |escapeCssString} directive which allows arbitrary content to be
   * included in a CSS quoted string or identifier.
   */
  public static final class EscapeCssString extends CrossLanguageStringXform {
    /** Implements the {@code |escapeCssString} directive. */
    public static final EscapeCssString INSTANCE = new EscapeCssString();

    private EscapeCssString() {
      super(null, ImmutableList.<String>of(), null);
    }

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
          .escapeAll("\u0000\b\t\n\u000b\f\r\u0085\u00a0\u2028\u2029\"\'\\<>&{};:()@/=*")
          .build();
    }
  }


  /**
   * Implements the {@code |filterCssValue} directive which filters out strings that are not valid
   * CSS property names, keyword values, quantities, hex colors, or ID or class literals.
   */
  public static final class FilterCssValue extends CrossLanguageStringXform {
    /**
     * Matches a CSS token that can appear unquoted as part of an ID, class, font-family-name,
     * or CSS keyword value.
     */
    public static final Pattern CSS_WORD = Pattern.compile(
        // See http://www.owasp.org/index.php/XSS_(Cross_Site_Scripting)_Prevention_Cheat_Sheet
        // #RULE_.234_-_CSS_Escape_Before_Inserting_Untrusted_Data_into_HTML_Style_Property_Values
        // for an explanation of why expression and moz-binding are bad.
        "^(?!-*(?:expression|(?:moz-)?binding))(?:" +
          // A latin class name or ID, CSS identifier, hex color or unicode range.
          "[.#]?-?(?:[_a-z0-9-]+)(?:-[_a-z0-9-]+)*-?|" +
          // A quantity
          "-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[a-z]{1,2}|%)?|" +
          // The special value !important.
          "!important|" +
          // Nothing.
          "" +
        ")\\z",
        Pattern.CASE_INSENSITIVE);

    /** Implements the {@code |filterCssValue} directive. */
    public static final FilterCssValue INSTANCE = new FilterCssValue();

    private FilterCssValue() {
      super(CSS_WORD, ImmutableList.<String>of(), null);
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.<Escape>of();
    }
  }


  /**
   * Escapes using URI percent encoding : {@code 'A' => "%41"}
   */
  private static final class UriEscapeListBuilder extends EscapeListBuilder {
    @Override
    String getNumericEscapeFor(char plainText) {
      // URI encoding is different from the other escaping schemes.
      // The others are transformations on strings of UTF-16 code units, but URIs are composed of
      // strings of bytes.  We assume UTF-8 as the standard way to convert between bytes and code
      // units below.
      byte[] bytes = Character.toString(plainText).getBytes(Charsets.UTF_8);
      int numBytes = bytes.length;
      StringBuilder sb = new StringBuilder(numBytes * 3);
      for (int i = 0; i < numBytes; ++i) {
        // Use uppercase escapes for consistency with CharEscapers.uriEscaper().
        sb.append(String.format("%%%02X", bytes[i]));
      }
      return sb.toString();
    }
  }


  /**
   * Implements the {@code |normalizeUri} directive which allows arbitrary content to be included
   * in a URI regardless of the string delimiters of the the surrounding language.
   * This normalizes, but does not escape, so it does not affect URI special characters, but
   * instead escapes HTML, CSS, and JS delimiters.
   */
  public static final class NormalizeUri extends CrossLanguageStringXform {
    /** Implements the {@code |normalizeUri} directive. */
    public static final NormalizeUri INSTANCE = new NormalizeUri();

    private NormalizeUri() {
      super(null, ImmutableList.<String>of(), null);
    }

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
          .escapeAll(" (){}\"\'\\<>")
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


  /**
   * Like {@link NormalizeUri} but filters out dangerous protocols.
   */
  public static final class FilterNormalizeUri extends CrossLanguageStringXform {
    /** Implements the {@code |filterNormalizeUri} directive. */
    public static final FilterNormalizeUri INSTANCE = new FilterNormalizeUri();

    private FilterNormalizeUri() {
      // Disallows any protocol that is not in a whitelist.
      // The below passes if there is
      // (1) Either a protocol in a whitelist (http, https, mailto).  This could be expanded but
      //     talk to your friendly local security-team@ first.
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
          Pattern.compile(
              "^(?:(?:https?|mailto):|[^&:\\/?#]*(?:[\\/?#]|\\z))", Pattern.CASE_INSENSITIVE),
          ImmutableList.<String>of(), null);
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return NormalizeUri.INSTANCE.defineEscapes();
    }

    @Override
    public String getInnocuousOutput() {
      return "#" + INNOCUOUS_OUTPUT;
    }
  }


  /**
   * Implements the {@code |escapeUri} directive which allows arbitrary content to be included in a
   * URI regardless of the string delimiters of the the surrounding language.
   */
  public static final class EscapeUri extends CrossLanguageStringXform {
    /** Implements the {@code |escapeUri} directive. */
    public static final EscapeUri INSTANCE = new EscapeUri();

    private EscapeUri() {
      super(null, ImmutableList.of("goog.string.urlEncode", "encodeURIComponent"), "%");
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return new UriEscapeListBuilder()
          .escapeAllInRangeExcept(
              0, 0x80,
              // From Appendix A of RFC 3986
              // unreserved := ALPHA / DIGIT / "-" / "." / "_" / "~"
              '-', '.',
              '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
              'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
              'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
              '_',
              'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
              'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
              '~')
          // All non-ASCII codepoints escaped per the constructor above.
          .build();
    }
  }


  /**
   * Implements the {@code |filterHtmlAttributes} directive which filters out identifiers that
   * can't appear as part of an HTML tag or attribute name.
   */
  public static final class FilterHtmlAttributes extends CrossLanguageStringXform {
    /** Implements the {@code |filterHtmlAttributes} directive. */
    public static final FilterHtmlAttributes INSTANCE = new FilterHtmlAttributes();

    private FilterHtmlAttributes() {
      super(
          Pattern.compile(
              "^" +
              // Disallow special attribute names
              "(?!style|on|action|archive|background|cite|classid|codebase|data|dsync|href" +
              "|longdesc|src|usemap)" +
              "(?:" +
              // Must match letters
              "[a-z0-9_$:-]*" +
              // Match until the end.
              ")\\z",
              Pattern.CASE_INSENSITIVE),
          ImmutableList.<String>of(), null);
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.<Escape>of();
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
          Pattern.compile(
              "^" +
              // Disallow special element names.
              "(?!script|style|title|textarea|xmp|no)" +
              "[a-z0-9_$:-]*\\z",
              Pattern.CASE_INSENSITIVE),
          ImmutableList.<String>of(), null);
    }

    @Override
    protected ImmutableList<Escape> defineEscapes() {
      return ImmutableList.<Escape>of();
    }
  }


  /**
   * An accessor for all string transforms defined above.
   */
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
        FilterCssValue.INSTANCE,
        EscapeUri.INSTANCE,
        NormalizeUri.INSTANCE,
        FilterNormalizeUri.INSTANCE,
        FilterHtmlAttributes.INSTANCE,
        FilterHtmlElementName.INSTANCE
        );
  }


  /**
   * A string, used as the result of a filter when the filter pattern does not match the input, that
   * is not a substring of any keyword or well-known identifier in HTML, JS, or CSS and that is a
   * valid identifier part in all those languages, and which cannot terminate a string, comment, or
   * other bracketed section.
   * <p>
   * This string is also longer than necessary so that developers can use grep when it starts
   * showing up in their output.
   * <p>
   * If grep directed you here, then one of your Soy templates is using a filter directive that
   * is receiving a potentially unsafe input.  Run your app in debug mode and you should get the
   * name of the directive and the input deemed unsafe.
   */
  public static final String INNOCUOUS_OUTPUT = "zSoyz";


  /**
   * Loose matcher for HTML tags, DOCTYPEs, and HTML comments.
   * This will reliably find HTML tags (though not CDATA tags and not XML tags whose name or
   * namespace starts with a non-latin character), and will do a good job with DOCTYPES (though
   * will have trouble with complex doctypes that define their own entities) and does a decent job
   * with simple HTML comments.
   * <p>
   * This should be good enough since HTML sanitizers do not typically output comments, or CDATA,
   * or RCDATA content.
   * <p>
   * The tag name, if any is in group 1.
   */
  public static final Pattern HTML_TAG_CONTENT = Pattern.compile(
      // Matches a left angle bracket followed by either
      // (1) a "!" which indicates a doctype or comment, or
      // (2) an optional solidus (/, indicating an end tag) and an HTML tag name.
      // followed by any number of quoted strings (found in tags and doctypes) or other content
      // terminated by a right angle bracket.
      "<(?:!|/?([a-zA-Z][a-zA-Z0-9:\\-]*))(?:[^>'\"]|\"[^\"]*\"|'[^']*')*>");


  /**
   * Convert an ASCII string to full-width.
   * Full-width characters are in Unicode page U+FFxx and are used to allow ASCII characters to be
   * embedded in written Chinese without breaking alignment -- so a sinograph which occupies two
   * columns can line up properly with a Latin letter or symbol which normally occupies only one
   * column.
   * <p>
   * See <a href="http://en.wikipedia.org/wiki/Duplicate_characters_in_Unicode#CJK_fullwidth_forms">
   * CJK fullwidth forms</a> and <a href="unicode.org/charts/PDF/UFF00.pdf">unicode.org</a>.
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
}
