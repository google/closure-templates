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

package com.google.template.soy.parsepasses.contextautoesc;

import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;

/**
 * Ways of escaping dynamic content in a template.
 *
 * @author Mike Samuel
 */
public enum EscapingMode {

  /** Encodes HTML special characters. */
  ESCAPE_HTML(true, ContentKind.HTML),

  /** Like {@link #ESCAPE_HTML} but normalizes known safe HTML since RCDATA can't contain tags. */
  ESCAPE_HTML_RCDATA(true, null),

  /**
   * Encodes HTML special characters, including quotes, so that the value can appear as part of a
   * quoted attribute value.
   * This differs from {@link #ESCAPE_HTML} in that it strips tags from known safe HTML.
   */
  ESCAPE_HTML_ATTRIBUTE(true, null),

  /**
   * Encodes HTML special characters and spaces so that the value can appear as part of an unquoted
   * attribute.
   */
  ESCAPE_HTML_ATTRIBUTE_NOSPACE(true, null),

  /**
   * Only allow a valid identifier - letters, numbers, dashes, and underscores.
   * Throw a runtime exception otherwise.
   */
  FILTER_HTML_ELEMENT_NAME(true, null),

  /**
   * Only allow a valid identifier - letters, numbers, dashes, and underscores
   * or a subset of attribute value pairs.
   * Throw a runtime exception otherwise.
   */
  FILTER_HTML_ATTRIBUTE(true, null),

  /**
   * Encode all HTML special characters and quotes, and JS newlines as if to allow them to appear
   * literally in a JS string.
   */
  ESCAPE_JS_STRING(false, ContentKind.JS_STR_CHARS),

  /**
   * If a number or boolean, output as a JS literal.  Otherwise surround in quotes and escape.
   * Make sure all HTML and space characters are quoted.
   */
  ESCAPE_JS_VALUE(false, null),

  /**
   * Like {@link #ESCAPE_JS_STRING} but additionally escapes RegExp specials like
   * <code>.+*?$^[](){}</code>.
   */
  ESCAPE_JS_REGEX(false, null),

  /**
   * Must escape all quotes, newlines, and the close parenthesis using {@code \} followed by hex
   * followed by a space.
   */
  ESCAPE_CSS_STRING(true, null),

  /**
   * If the value is numeric, renders it as a numeric value so that <code>{$n}px</code> works as
   * expected, otherwise if it is a valid CSS identifier, outputs it without escaping, otherwise
   * surrounds in quotes and escapes like {@link #ESCAPE_CSS_STRING}.
   */
  FILTER_CSS_VALUE(false, null),

  /**
   * Percent encode all URI special characters and characters that cannot appear unescaped in a URI
   * such as spaces.  Make sure to encode pluses and parentheses.
   * This corresponds to the JavaScript function {@code encodeURIComponent}.
   */
  ESCAPE_URI(true, ContentKind.URI),

  /**
   * Percent encode non-URI characters that cannot appear unescaped in a URI such as spaces, and
   * encode characters that are not special in URIs that are special in languages that URIs are
   * embedded in such as parentheses and quotes.
   * This corresponds to the JavaScript function {@code encodeURI} but additionally encodes quotes
   * parentheses, and percent signs that are not followed by two hex digits.
   */
  NORMALIZE_URI(false, ContentKind.URI),

  /**
   * Like {@link #NORMALIZE_URI}, but filters out schemes like {@code javascript:} that load code.
   */
  FILTER_NORMALIZE_URI(false, ContentKind.URI),

  /**
   * The explicit rejection of escaping.
   */
  NO_AUTOESCAPE(false, null),
  ;

  /** The Soy <code>{print}</code> directive that specifies this escaping mode. */
  public final String directiveName;

  /** True iff the output does not contain quotes so can be embedded in HTML attribute values. */
  public final boolean isHtmlEmbeddable;

  /** The kind of content produced by the escaping directive associated with this escaping mode. */
  public final @Nullable ContentKind contentKind;


  EscapingMode(boolean escapesQuotes, @Nullable ContentKind contentKind) {
    this.directiveName = "|" + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
    this.isHtmlEmbeddable = escapesQuotes;
    this.contentKind = contentKind;
  }


  /**
   * The escaping mode corresponding to the given directive or null.
   */
  public static @Nullable EscapingMode fromDirective(String directiveName) {
    return DIRECTIVE_TO_ESCAPING_MODE.get(directiveName);
  }

  private static final Map<String, EscapingMode> DIRECTIVE_TO_ESCAPING_MODE;
  static {
    ImmutableMap.Builder<String, EscapingMode> builder = ImmutableMap.builder();
    for (EscapingMode mode : EscapingMode.values()) {
      builder.put(mode.directiveName, mode);
    }
    DIRECTIVE_TO_ESCAPING_MODE = builder.build();
  }
}
