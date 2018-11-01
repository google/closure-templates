/*
 * Copyright 2016 Google Inc.
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
package com.google.template.soy.soytree;

import javax.annotation.Nullable;

/**
 * The type of HTML (or non-HTML) that contains a Soy node. This is primarily used by the contextual
 * auto-escaper to add escaping directives. It's also used by incremental DOM's HTML parser to mark
 * nodes for other passes to consume.
 */
public enum HtmlContext {

  /** Outside an HTML tag, directive, or comment. (Parsed character data). */
  HTML_PCDATA(EscapingMode.ESCAPE_HTML),

  /**
   * Inside an element whose content is RCDATA where text and entities can appear but where nested
   * elements cannot. The content of {@code <title>} and {@code <textarea>} fall into this category
   * since they cannot contain nested elements in HTML.
   */
  HTML_RCDATA(EscapingMode.ESCAPE_HTML_RCDATA),

  /** Just before a tag name on an open tag. */
  HTML_BEFORE_OPEN_TAG_NAME(EscapingMode.FILTER_HTML_ELEMENT_NAME),

  /** Just before a tag name on an close tag. */
  HTML_BEFORE_CLOSE_TAG_NAME(EscapingMode.FILTER_HTML_ELEMENT_NAME),

  /**
   * Just after a tag name, e.g. in ^ in <script^> or <div^>.
   *
   * <p>Note tag names must be printed all at once since we can't otherwise easily handle <s{if
   * 1}cript{/if}>.
   */
  HTML_TAG_NAME(
      "Dynamic values are not permitted in the middle of an HTML tag name;"
          + " try adding a space before."),

  /** Before an HTML attribute or the end of a tag. */
  HTML_TAG(EscapingMode.FILTER_HTML_ATTRIBUTES),
  // TODO: Do we need to filter out names that look like JS/CSS/URI attribute names.

  /** Inside an HTML attribute name. */
  HTML_ATTRIBUTE_NAME(EscapingMode.FILTER_HTML_ATTRIBUTES),

  /** Inside an HTML comment. */
  HTML_COMMENT(EscapingMode.ESCAPE_HTML_RCDATA),

  /** Inside a normal (non-CSS, JS, HTML, META_REFRESH_CONTENT, or URI) HTML attribute value. */
  HTML_NORMAL_ATTR_VALUE(EscapingMode.ESCAPE_HTML_ATTRIBUTE),

  /** Inside an HTML attribute value containing HTML such as {@code <iframe srcdoc="">}. */
  HTML_HTML_ATTR_VALUE(EscapingMode.ESCAPE_HTML_HTML_ATTRIBUTE),

  /** Inside the content attribute of {@code <meta http-equiv="refresh">}. */
  HTML_META_REFRESH_CONTENT(EscapingMode.FILTER_NUMBER),

  /** In CSS content outside a comment, string, or URI. */
  CSS(EscapingMode.FILTER_CSS_VALUE),

  /** In CSS inside a comment. */
  CSS_COMMENT("CSS comments cannot contain dynamic values."),

  /** In CSS inside a double quoted string. */
  CSS_DQ_STRING(EscapingMode.ESCAPE_CSS_STRING),

  /** In CSS inside a single quoted string. */
  CSS_SQ_STRING(EscapingMode.ESCAPE_CSS_STRING),

  /** In CSS in a URI terminated by the first close parenthesis. */
  CSS_URI(EscapingMode.NORMALIZE_URI),

  /** In CSS in a URI terminated by the first double quote. */
  CSS_DQ_URI(EscapingMode.NORMALIZE_URI),

  /** In CSS in a URI terminated by the first single quote. */
  CSS_SQ_URI(EscapingMode.NORMALIZE_URI),

  /** In JavaScript, outside a comment, string, or Regexp literal. */
  JS(EscapingMode.ESCAPE_JS_VALUE),

  /** In JavaScript inside a line comment. */
  JS_LINE_COMMENT(EscapingMode.ESCAPE_JS_STRING),

  /** In JavaScript inside a block comment. */
  JS_BLOCK_COMMENT(EscapingMode.ESCAPE_JS_STRING),

  /** In JavaScript inside a double quoted string. */
  JS_DQ_STRING(EscapingMode.ESCAPE_JS_STRING),

  /** In JavaScript inside a single quoted string. */
  JS_SQ_STRING(EscapingMode.ESCAPE_JS_STRING),

  /** In JavaScript inside a regular expression literal. */
  JS_REGEX(EscapingMode.ESCAPE_JS_REGEX),

  /**
   * In JavaScript inside a template literal string.
   *
   * <p>See https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals
   */
  JS_TEMPLATE_LITERAL("Js template literals cannot contain dynamic values"),

  /** In a URI, which may or may not be in an HTML attribute. */
  URI(EscapingMode.NORMALIZE_URI),

  /** Plain text; no escaping. */
  TEXT(EscapingMode.TEXT);

  @Nullable private final EscapingMode escapingMode;
  @Nullable private final String errorMessage;

  /**
   * The escaping mode appropriate for dynamic content inserted at this state. Null if there is no
   * appropriate escaping convention to use as for comments or plain text which do not have escaping
   * conventions.
   */
  public EscapingMode getEscapingMode() {
    return escapingMode;
  }

  /** Error message to show when trying to print a dynamic value inside of this state. */
  public String getErrorMessage() {
    return errorMessage;
  }

  HtmlContext(EscapingMode escapingMode) {
    this.escapingMode = escapingMode;
    this.errorMessage = null;
  }

  HtmlContext(String errorMessage) {
    this.errorMessage = errorMessage;
    this.escapingMode = null;
  }
}
