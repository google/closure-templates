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
package com.google.template.soy.soytree;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.base.internal.SanitizedContentKind;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Ways of escaping dynamic content in a template. This is only used by the contextautoesc package,
 * but lives in soytree since it's reference by the HtmlContext enum.
 *
 */
public enum EscapingMode {

  /** Encodes HTML special characters. */
  ESCAPE_HTML(true, SanitizedContentKind.HTML, /* internalOnly= */ true),

  /** Escapes HTML except preserves ampersands and entities. */
  NORMALIZE_HTML(true, null, /* internalOnly= */ true),

  /** Like {@link #ESCAPE_HTML} but normalizes known safe HTML since RCDATA can't contain tags. */
  ESCAPE_HTML_RCDATA(true, null, /* internalOnly= */ true),

  /**
   * Encodes HTML special characters, including quotes, so that the value can appear as part of a
   * quoted attribute value. This differs from {@link #ESCAPE_HTML} in that it strips tags from
   * known safe HTML.
   */
  ESCAPE_HTML_ATTRIBUTE(true, null, /* internalOnly= */ true),

  /** Escapes HTML but doesn't mark it as HTML embeddable to allow double escaping it. */
  ESCAPE_HTML_HTML_ATTRIBUTE(false, null, /* internalOnly= */ true),

  /**
   * Encodes HTML special characters and spaces so that the value can appear as part of an unquoted
   * attribute.
   */
  ESCAPE_HTML_ATTRIBUTE_NOSPACE(true, null, /* internalOnly= */ true),

  /** Only allow decimal and floating-point numbers. */
  FILTER_NUMBER(true, null, /* internalOnly= */ true),

  /**
   * Only allow a valid identifier - letters, numbers, dashes, and underscores. Throw a runtime
   * exception otherwise.
   */
  FILTER_HTML_ELEMENT_NAME(true, null, /* internalOnly= */ true),

  /**
   * Only allow a valid identifier - letters, numbers, dashes, and underscores or a subset of
   * attribute value pairs. Throw a runtime exception otherwise.
   */
  FILTER_HTML_ATTRIBUTES(true, null, /* internalOnly= */ true),

  /**
   * Encode all HTML special characters and quotes, and JS newlines as if to allow them to appear
   * literally in a JS string.
   */
  ESCAPE_JS_STRING(false, null, /* internalOnly= */ true),

  /**
   * If a number or boolean, output as a JS literal. Otherwise surround in quotes and escape. Make
   * sure all HTML and space characters are quoted.
   */
  ESCAPE_JS_VALUE(false, null, /* internalOnly= */ true),

  /**
   * Like {@link #ESCAPE_JS_STRING} but additionally escapes RegExp specials like <code>.+*?$^[](){}
   * </code>.
   */
  ESCAPE_JS_REGEX(false, null, /* internalOnly= */ true),

  /**
   * Must escape all quotes, newlines, and the close parenthesis using {@code \} followed by hex
   * followed by a space.
   */
  ESCAPE_CSS_STRING(true, null, /* internalOnly= */ true),

  /**
   * If the value is numeric, renders it as a numeric value so that <code>{$n}px</code> works as
   * expected, otherwise if it is a valid CSS identifier, outputs it without escaping, otherwise
   * replaces with "zSoyz" to indicate the value was rejected.
   */
  FILTER_CSS_VALUE(false, SanitizedContentKind.CSS, /* internalOnly= */ true),

  // TODO(jakubvrana): Make internal-only.
  /**
   * Percent encode all URI special characters and characters that cannot appear unescaped in a URI
   * such as spaces. Make sure to encode pluses and parentheses. This corresponds to the JavaScript
   * function {@code encodeURIComponent}.
   */
  ESCAPE_URI(true, SanitizedContentKind.URI),

  // TODO(jakubvrana): Make internal-only.
  /**
   * Percent encode non-URI characters that cannot appear unescaped in a URI such as spaces, and
   * encode characters that are not special in URIs that are special in languages that URIs are
   * embedded in such as parentheses and quotes.
   *
   * <p>This corresponds to the JavaScript function {@code encodeURI} but additionally encodes
   * quotes parentheses, and percent signs that are not followed by two hex digits.
   *
   * <p>This is not necessarily HTML embeddable because we want ampersands to get HTML-escaped.
   */
  NORMALIZE_URI(false, SanitizedContentKind.URI),

  /**
   * Like {@link #NORMALIZE_URI}, but filters out everything except relative and http/https URIs.
   */
  FILTER_NORMALIZE_URI(false, SanitizedContentKind.URI, /* internalOnly= */ true),

  /**
   * Like {@link #FILTER_NORMALIZE_URI}, but also accepts some {@code data:} URIs, since image
   * sources don't execute script in the same origin as the page. Although image decoding 0-days are
   * discovered from time to time, a templating language can't realistically try to protect against
   * such a thing.
   */
  FILTER_NORMALIZE_MEDIA_URI(false, SanitizedContentKind.URI, /* internalOnly= */ true),

  /** Like {@link #FILTER_NORMALIZE_URI}, but escapes ';'. */
  FILTER_NORMALIZE_REFRESH_URI(false, SanitizedContentKind.URI, /* internalOnly= */ true),

  /**
   * Makes sure there URIs are trusted and not input variables. Currently used only for script
   * sources.
   */
  // TODO(shwetakarwa): Change second argument when function is implemented.
  FILTER_TRUSTED_RESOURCE_URI(false, null, /* internalOnly= */ true),

  /** The explicit rejection of escaping. */
  NO_AUTOESCAPE(false, SanitizedContentKind.TEXT),

  /**
   * Outputs plain text and performs no escaping. Unlike noAutoescape, this will not fail if passed
   * SanitizedContent of kind TEXT, but this may never be used manually by the developer. This has
   * no escaping.
   */
  TEXT(false, SanitizedContentKind.TEXT, /* internalOnly= */ true);

  /** The Soy <code>{print}</code> directive that specifies this escaping mode. */
  public final String directiveName;

  /** True iff the output does not contain quotes so can be embedded in HTML attribute values. */
  public final boolean isHtmlEmbeddable;

  /** The kind of content produced by the escaping directive associated with this escaping mode. */
  @Nullable public final SanitizedContentKind contentKind;

  /** Whether this directive is only for internal use by the contextual autoescaper. */
  public final boolean isInternalOnly;

  EscapingMode(
      boolean escapesQuotes, @Nullable SanitizedContentKind contentKind, boolean internalOnly) {
    this.directiveName = "|" + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name());
    this.isHtmlEmbeddable = escapesQuotes;
    this.contentKind = contentKind;
    this.isInternalOnly = internalOnly;
  }

  EscapingMode(boolean escapesQuotes, @Nullable SanitizedContentKind contentKind) {
    this(escapesQuotes, contentKind, /* internalOnly= */ false);
  }

  /** The escaping mode corresponding to the given directive or null. */
  @Nullable
  public static EscapingMode fromDirective(String directiveName) {
    return DIRECTIVE_TO_ESCAPING_MODE.get(directiveName);
  }

  @Override
  public String toString() {
    return directiveName;
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
