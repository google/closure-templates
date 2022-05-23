/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.basicfunctions;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.template.soy.shared.internal.Sanitizers.HTML5_VOID_ELEMENTS;
import static com.google.template.soy.shared.internal.Sanitizers.HTML_ATTRIBUTE_PATTERN;
import static java.util.Arrays.stream;

import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.internal.base.UnescapeUtils;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Converts HTML to plain text by removing tags, normalizing spaces and converting entities. */
public final class HtmlToText {
  // LINT.IfChange
  private static final Pattern TAG =
      Pattern.compile(
          "<(?:!--.*?--|(?:!|(/?[a-zA-Z][\\w:-]*))((?:[^>'\"]*|\"[^\"]*\"|'[^']*')*))>|\\z");

  /** Pattern for matching style attribute names and values */
  private static final Pattern STYLE_ATTRIBUTE;

  static {
    String styleString = "[^:;\t\n\r ]*";
    String whitespace = "[\t\n\r ]";

    STYLE_ATTRIBUTE =
        Pattern.compile(
            String.format(
                "%s*(%s)%s*:%s*(%s)%s*(?:;|$)",
                whitespace,
                styleString, // Group 1: Style attribute name.
                whitespace,
                whitespace,
                styleString, // Group 2: Style attribute value.
                whitespace));
  }

  private static ImmutableSet<String> createOpenTagSet(String... tags) {
    return ImmutableSet.copyOf(tags);
  }

  private static ImmutableSet<String> createOpenAndCloseTagSet(String... tags) {
    return Stream.concat(stream(tags).map(t -> "/" + t), stream(tags)).collect(toImmutableSet());
  }

  private static final ImmutableSet<String> REMOVING_TAGS =
      createOpenTagSet("script", "style", "textarea", "title");
  private static final String WS_PRESERVING_TAGS = "pre";
  private static final String NEWLINE_TAGS = "br";
  private static final ImmutableSet<String> BLOCK_TAGS =
      createOpenAndCloseTagSet(
          "address",
          "blockquote",
          "dd",
          "div",
          "dl",
          "dt",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "hr",
          "li",
          "ol",
          "p",
          "pre",
          "table",
          "tr",
          "ul");
  private static final ImmutableSet<String> TAB_TAGS = createOpenTagSet("td", "th");
  private static final ImmutableSet<String> PRESERVE_WHITESPACE_STYLES =
      ImmutableSet.of("pre", "pre-wrap", "break-spaces");
  private static final ImmutableSet<String> COLLAPSE_WHITESPACE_STYLES =
      ImmutableSet.of("normal", "nowrap");
  private static final Pattern HTML_WHITESPACE = Pattern.compile("[ \t\r\n]+");

  private static boolean endsWithNewline(StringBuffer builder) {
    return builder.length() == 0 || builder.charAt(builder.length() - 1) == '\n';
  }

  private static boolean emptyOrEndsWithWhitespace(StringBuffer builder) {
    return builder.length() == 0 || " \t\r\n".indexOf(builder.charAt(builder.length() - 1)) >= 0;
  }

  private static boolean matchesTag(String tag, ImmutableSet<String> tagSet) {
    return tagSet.contains(tag);
  }

  private static boolean matchesTag(String tag, String tagSet) {
    return tagSet.equals(tag);
  }

  private static void replaceChar(StringBuffer builder, char from, char to) {
    for (int i = 0; i < builder.length(); ++i) {
      if (builder.charAt(i) == from) {
        builder.setCharAt(i, to);
      }
    }
  }

  private HtmlToText() {}

  public static String convert(SoyValue value) {
    if (value == null || value instanceof NullData) {
      return "";
    }
    if (!(value instanceof SanitizedContent)) {
      return value.stringValue();
    }
    Preconditions.checkArgument(((SanitizedContent) value).getContentKind() == ContentKind.HTML);
    String html = value.stringValue();

    return new HtmlToTextConverter().convert(html);
  }

  private static class HtmlToTextConverter {

    Matcher whitespaceMatcher = null;
    Matcher attributeMatcher = null;
    Matcher styleMatcher = null;
    ArrayDeque<TagAndWs> preserveWhitespaceStack = new ArrayDeque<>();

    // Reuse matchers, this saves a lot of allocations.
    private void resetWhitespaceMatcher(String html) {
      if (whitespaceMatcher == null) {
        whitespaceMatcher = HTML_WHITESPACE.matcher(html);
      } else {
        whitespaceMatcher.reset(html);
      }
    }

    private void resetAttributeMatcher(String html) {
      if (attributeMatcher == null) {
        attributeMatcher = HTML_ATTRIBUTE_PATTERN.matcher(html);
      } else {
        attributeMatcher.reset(html);
      }
    }

    private void resetStyleMatcher(String html) {
      if (styleMatcher == null) {
        styleMatcher = STYLE_ATTRIBUTE.matcher(html);
      } else {
        styleMatcher.reset(html);
      }
    }

    String convert(String html) {
      StringBuffer text = new StringBuffer(html.length()); // guaranteed to be no bigger than this
      int start = 0;
      String removingUntil = "";

      Matcher matcher = TAG.matcher(html);
      while (matcher.find()) {
        int offset = matcher.start();
        String tag = matcher.group(1);
        String attrs = matcher.group(2);
        String lowerCaseTag = tag != null ? Ascii.toLowerCase(tag) : null;
        if (removingUntil.isEmpty()) {
          String chunk = html.substring(start, offset);
          chunk = UnescapeUtils.unescapeHtml(chunk);

          if (!shouldPreserveWhitespace()) {
            resetWhitespaceMatcher(chunk);
            // collapse internal whitespace sequences to a single space
            // perform this loop inline so we can append directly to text instead of allocating
            // intermediate strings which is how Matcher.replaceAll works
            while (whitespaceMatcher.find()) {
              // if the current builder ends with whitespace and we see whitespace at the beginning
              // of this chunk, just skip it.
              if (whitespaceMatcher.start() == 0 && emptyOrEndsWithWhitespace(text)) {
                whitespaceMatcher.appendReplacement(text, "");
              } else {
                whitespaceMatcher.appendReplacement(text, " ");
              }
            }
            whitespaceMatcher.appendTail(text);
          } else {
            text.append(chunk);
          }
          if (lowerCaseTag != null) {
            if (matchesTag(lowerCaseTag, REMOVING_TAGS)) {
              removingUntil = '/' + lowerCaseTag;
            } else if (matchesTag(lowerCaseTag, NEWLINE_TAGS)) {
              text.append('\n');
            } else if (matchesTag(lowerCaseTag, BLOCK_TAGS)) {
              if (!endsWithNewline(text)) {
                text.append('\n');
              }
            } else if (matchesTag(lowerCaseTag, TAB_TAGS)) {
              text.append('\t');
            }

            if (!HTML5_VOID_ELEMENTS.contains(lowerCaseTag)) {
              updatePreserveWhitespaceStack(lowerCaseTag, attrs);
            }
          }
        } else if (removingUntil.equals(lowerCaseTag)) {
          removingUntil = "";
        }
        start = matcher.end();
      }
      // replace non-breaking spaces with spaces, then return the text;
      replaceChar(text, '\u00A0', ' ');
      return text.toString();
    }

    private boolean shouldPreserveWhitespace() {
      return !preserveWhitespaceStack.isEmpty()
          && preserveWhitespaceStack.peek().preserveWhitespace();
    }

    private Optional<Boolean> getStylePreservesWhitespace(String style) {
      resetStyleMatcher(style);
      while (styleMatcher.find()) {
        String styleAttribute = styleMatcher.group(1);
        String styleAttributeValue = styleMatcher.group(2);
        if (!Strings.isNullOrEmpty(styleAttribute)
            && Ascii.equalsIgnoreCase(styleAttribute, "white-space")) {
          String whitespaceStyle =
              Strings.isNullOrEmpty(styleAttributeValue)
                  ? ""
                  : Ascii.toLowerCase(styleAttributeValue);
          if (PRESERVE_WHITESPACE_STYLES.contains(whitespaceStyle)) {
            return Optional.of(true);
          } else if (COLLAPSE_WHITESPACE_STYLES.contains(whitespaceStyle)) {
            return Optional.of(false);
          }
        }
      }

      return Optional.empty();
    }

    private Optional<Boolean> getAttributesPreserveWhitespace(String attrs) {
      if (Strings.isNullOrEmpty(attrs)) {
        return Optional.empty();
      }

      resetAttributeMatcher(attrs);
      while (attributeMatcher.find()) {
        String attributeName = attributeMatcher.group(1);
        if (!Strings.isNullOrEmpty(attributeName)
            && Ascii.equalsIgnoreCase(attributeName, "style")) {
          String style = attributeMatcher.group(2);
          if (!Strings.isNullOrEmpty(style)) {
            // Strip quotes if the attribute value was quoted.
            if (style.charAt(0) == '\'' || style.charAt(0) == '"') {
              style = style.substring(1, style.length() - 1);
            }
            return getStylePreservesWhitespace(style);
          }
          return Optional.empty();
        }
      }
      return Optional.empty();
    }

    private void updatePreserveWhitespaceStack(String lowerCaseTag, String attrs) {
      if (lowerCaseTag.charAt(0) == '/') {
        lowerCaseTag = lowerCaseTag.substring(1);
        // Pop tags until we pop one that matches the current closing tag. This means we're
        // effectively automatically closing tags that aren't explicitly closed.
        while (!preserveWhitespaceStack.isEmpty()
            && !preserveWhitespaceStack.pop().tagName().equals(lowerCaseTag)) {}
      } else if (matchesTag(lowerCaseTag, WS_PRESERVING_TAGS)) {
        preserveWhitespaceStack.push(TagAndWs.of(lowerCaseTag, true));
      } else {
        // If attribute don't specify whitespace preservation, inherit from parent tag.
        boolean preserveWhitespace =
            getAttributesPreserveWhitespace(attrs).orElseGet(this::shouldPreserveWhitespace);

        preserveWhitespaceStack.push(TagAndWs.of(lowerCaseTag, preserveWhitespace));
      }
    }
  }
  // LINT.ThenChange(
  //     ../../../../../../../../../javascript/template/soy/soyutils_usegoog.js:htmlToText,
  //     ../../../../../../python/runtime/sanitize.py:htmlToText)

  @AutoValue
  abstract static class TagAndWs {
    static TagAndWs of(String tagName, boolean preserveWhitespace) {
      return new AutoValue_HtmlToText_TagAndWs(tagName, preserveWhitespace);
    }

    abstract String tagName();

    abstract boolean preserveWhitespace();
  }
}
