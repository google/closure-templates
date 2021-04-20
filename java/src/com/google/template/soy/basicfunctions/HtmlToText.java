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
import static java.util.Arrays.stream;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.internal.base.UnescapeUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Converts HTML to plain text by removing tags, normalizing spaces and converting entities. */
public final class HtmlToText {
  // LINT.IfChange
  private static final Pattern TAG =
      Pattern.compile(
          "<(?:!--.*?--|(?:!|(/?[a-zA-Z][\\w:-]*))(?:[^>'\"]*|\"[^\"]*\"|'[^']*')*)>|\\z");

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
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private static boolean endsWithNewline(StringBuffer builder) {
    return builder.length() == 0 || builder.charAt(builder.length() - 1) == '\n';
  }

  private static boolean emptyOrEndsWithSpace(StringBuffer builder) {
    return builder.length() == 0
        || CharMatcher.whitespace().matches(builder.charAt(builder.length() - 1));
  }

  private static boolean matchesTag(String tag, ImmutableSet<String> tagSet) {
    return tagSet.contains(tag);
  }

  private static boolean matchesTag(String tag, String tagSet) {
    return tagSet.equals(tag);
  }

  public static String convert(SoyValue value) {
    if (value == null || value instanceof NullData) {
      return "";
    }
    if (!(value instanceof SanitizedContent)) {
      return value.stringValue();
    }
    Preconditions.checkArgument(((SanitizedContent) value).getContentKind() == ContentKind.HTML);
    String html = value.stringValue();
    StringBuffer text = new StringBuffer(html.length()); // guaranteed to be no bigger than this
    int start = 0;
    String removingUntil = "";
    String wsPreservingUntil = "";
    Matcher wsMatcher = null;

    Matcher matcher = TAG.matcher(html);
    while (matcher.find()) {
      int offset = matcher.start();
      String tag = matcher.group(1);
      String lowerCaseTag = tag != null ? Ascii.toLowerCase(tag) : null;
      if (removingUntil.isEmpty()) {
        String chunk = html.substring(start, offset);
        chunk = UnescapeUtils.unescapeHtml(chunk);
        if (wsPreservingUntil.isEmpty()) {
          // collapse internal whitespace sequences to a single space
          // perform this loop inline so we can append directly to text instead of allocating
          // intermediate strings which is how Matcher.replaceAll works
          if (wsMatcher == null) {
            wsMatcher = WHITESPACE.matcher(chunk);
          } else {
            // reuse matchers, this saves a lot of allocations.
            wsMatcher.reset(chunk);
          }
          while (wsMatcher.find()) {
            // if the current builder ends with space and we see ws at the beginning of this chunk
            // just skip it
            if (wsMatcher.start() == 0 && emptyOrEndsWithSpace(text)) {
              wsMatcher.appendReplacement(text, "");
            } else {
              wsMatcher.appendReplacement(text, " ");
            }
          }
          wsMatcher.appendTail(text);
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
            if (matchesTag(lowerCaseTag, WS_PRESERVING_TAGS)) {
              wsPreservingUntil = '/' + lowerCaseTag;
            } else if (lowerCaseTag.equals(wsPreservingUntil)) {
              wsPreservingUntil = "";
            }
          } else if (matchesTag(lowerCaseTag, TAB_TAGS)) {
            text.append('\t');
          }
        }
      } else if (removingUntil.equals(lowerCaseTag)) {
        removingUntil = "";
      }
      start = matcher.end();
    }
    return text.toString();
  }
  // LINT.ThenChange(
  //     ../../../../../../../../../javascript/template/soy/soyutils_usegoog.js:htmlToText,
  //     ../../../../../../python/runtime/sanitize.py:htmlToText)

  private HtmlToText() {}
}
