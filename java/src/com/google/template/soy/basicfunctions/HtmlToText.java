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

import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.data.UnsanitizedString;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.internal.base.UnescapeUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts HTML to plain text by removing tags, normalizing spaces and converting entities. */
public final class HtmlToText {
  // LINT.IfChange
  private static final Pattern TAG =
      Pattern.compile(
          "<(?:!--.*?--|(?:!|(/?[a-z][\\w:-]*))(?:[^>'\"]|\"[^\"]*\"|'[^']*')*)>|\\z",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern REMOVING_TAGS =
      Pattern.compile("script|style|textarea|title", Pattern.CASE_INSENSITIVE);
  private static final Pattern WS_PRESERVING_TAGS =
      Pattern.compile("pre", Pattern.CASE_INSENSITIVE);
  private static final Pattern NEWLINE_TAGS = Pattern.compile("br", Pattern.CASE_INSENSITIVE);
  private static final Pattern BLOCK_TAGS =
      Pattern.compile(
          "/?(address|blockquote|dd|div|dl|dt|h[1-6]|hr|li|ol|p|pre|table|tr|ul)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern TAB_TAGS = Pattern.compile("td|th", Pattern.CASE_INSENSITIVE);
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern TRAILING_NON_WHITESPACE = Pattern.compile("\\S\\z");
  private static final Pattern TRAILING_NON_NEWLINE = Pattern.compile("[^\n]\\z");
  private static final Pattern LEADING_SPACE = Pattern.compile("^ ");

  public static String convert(SoyValue value) {
    if (value == null || value instanceof NullData) {
      return "";
    }
    if (!(value instanceof SanitizedContent) || value instanceof UnsanitizedString) {
      return value.stringValue();
    }
    Preconditions.checkArgument(((SanitizedContent) value).getContentKind() == ContentKind.HTML);
    String html = value.stringValue();
    StringBuilder text = new StringBuilder();
    int start = 0;
    String removingUntil = "";
    String wsPreservingUntil = "";
    Matcher matcher = TAG.matcher(html);
    while (matcher.find()) {
      int offset = matcher.start();
      String tag = matcher.group(1);
      if (removingUntil.isEmpty()) {
        String chunk = html.substring(start, offset);
        chunk = UnescapeUtils.unescapeHtml(chunk);
        if (wsPreservingUntil.isEmpty()) {
          chunk = WHITESPACE.matcher(chunk).replaceAll(" ");
          if (!TRAILING_NON_WHITESPACE.matcher(text).find()) {
            chunk = LEADING_SPACE.matcher(chunk).replaceFirst("");
          }
        }
        text.append(chunk);
        if (tag != null) {
          if (REMOVING_TAGS.matcher(tag).matches()) {
            removingUntil = '/' + tag;
          } else if (NEWLINE_TAGS.matcher(tag).matches()) {
            text.append('\n');
          } else if (BLOCK_TAGS.matcher(tag).matches()) {
            if (TRAILING_NON_NEWLINE.matcher(text).find()) {
              text.append('\n');
            }
            if (WS_PRESERVING_TAGS.matcher(tag).matches()) {
              wsPreservingUntil = '/' + tag;
            } else if (Ascii.equalsIgnoreCase(tag, wsPreservingUntil)) {
              wsPreservingUntil = "";
            }
          } else if (TAB_TAGS.matcher(tag).matches()) {
            text.append('\t');
          }
        }
      } else if (Ascii.equalsIgnoreCase(removingUntil, tag)) {
        removingUntil = "";
      }
      start = matcher.end();
    }
    return text.toString();
  }
  // LINT.ThenChange(
  //     ../../../../../../../../../javascript/template/soy/soyutils_usegoog.js:htmlToText,
  //     ../../../../../../python/runtime/sanitize.py:htmlToText)
}
