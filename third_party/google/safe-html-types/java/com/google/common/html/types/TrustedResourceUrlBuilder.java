/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.common.html.types;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.GwtCompatible;
import com.google.common.net.UrlEscapers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Builder for constructing {@link TrustedResourceUrl} in steps, from application-controlled
 * strings.
 *
 * @see TrustedResourceUrl
 */
@CheckReturnValue
@NotThreadSafe
@GwtCompatible(emulated = true)
public final class TrustedResourceUrlBuilder {
  private final StringBuilder url = new StringBuilder();

  /**
   * Creates a new builder, with an underlying URL set to the given compile-time constant {@code
   * string}.
   *
   * <p>This method can only be used if the TrustedResourceUrl being built starts with a
   * compile-time constant prefix of one of these forms: - https://<origin>/ - //<origin>/ - Any
   * absolute or relative path.
   */
  public TrustedResourceUrlBuilder(@CompileTimeConstant final String string) {
    checkBaseUrl(string);
    url.append(string);
  }

  /**
   * A new builder whose content is initially that of the given TrustedResourceUrl.
   *
   * @param trustedResourceUrl non-null URL that specifies an origin either explicitly, or
   *     implicitly by virtue of adopting the current origin by specifying a path.
   */
  public TrustedResourceUrlBuilder(TrustedResourceUrl trustedResourceUrl) {
    String urlString = trustedResourceUrl.getTrustedResourceUrlString();
    checkBaseUrl(urlString);
    url.append(urlString);
  }

  // Constructor for factory methods.
  private TrustedResourceUrlBuilder() {}

  /**
   * Appends the compile-time constant {@code string} to the URL being built.
   *
   * <p>No runtime validation or sanitization is performed on {@code string}; being under
   * application control, it is simply assumed comply with the TrustedResourceUrl contract.
   */
  @CanIgnoreReturnValue
  public TrustedResourceUrlBuilder append(@CompileTimeConstant final String string) {
    url.append(string);
    return this;
  }

  /**
   * URL encodes and appends {@code string} to the TrustedResourceUrl.
   *
   * <p>This method can only be used if the TrustedResourceUrl being built starts with a
   * compile-time constant prefix of one of these forms: - {@code https://<origin>/} - {@code
   * //<origin>/} - Absolute or relative path.
   *
   * <p>{@code <origin>} must contain only alphanumeric characters and "-.:[]".
   *
   * <p>All characters in {@code string} besides {@code -_.*} and alphanumeric characters will be
   * percent-encoded.
   */
  @CanIgnoreReturnValue
  public TrustedResourceUrlBuilder appendEncoded(final String string) {
    checkBaseUrl(url.toString());
    url.append(UrlEscapers.urlFormParameterEscaper().escape(string));
    return this;
  }

  /** Appends a URL encoded query parameter name and value to the TrustedResourceUrl.
   *
   * The name and value are appended to the query component according to the
   * application/x-www-form-urlencoded algorithm. If the URL does not have a query component, a "?"
   * character is appended before the tuple; if there is already a query component, a "&" character
   * is inserted. In both names and values, characters that are not alphanumeric or one of "*-._"
   * will be percent-encoded; spaces will be replaced by "+" characters.
   *
   * If the URL already contains a query string, we are assuming it contains URL encoded form data,
   * as specified in the HTML standard. We do not validate the pre-existing query string;
   * in particular, if form values are added with the obsolete ';' separator or if you have
   * non-key-value data in the query, this function might cause undesirable behaviour.
   * In that case, use a mixture of {@code appendEncoded} and {@code append} with constant values to
   * support your encoding scheme.
   *
   * We also assume that the prefix built so far is a valid URL. In particular, we assume the
   * query part of the URL starts after the first '?'. Violating these assumptions does not lead
   * to security risks, but can cause odd behaviour.
   *
   * {@see https://url.spec.whatwg.org/#urlencoded-serializing
   * {@see https://www.w3.org/TR/html52/sec-forms.html#mutate-action-url}
   */
  @CanIgnoreReturnValue
  public TrustedResourceUrlBuilder appendQueryParam(String name, String value) {
    if (url.indexOf("#") >= 0) {
      throw new IllegalStateException(
          "Cannot add query parameters after a fragment was added, URL: " + url.toString());
    }
    int qmark = url.indexOf("?");
    if (qmark < 0) {
      // No query
      url.append('?');
    } else if (qmark + 1 != url.length()) {
      // The query is non-empty, so we need a separator
      url.append('&');
    }
    url.append(UrlEscapers.urlFormParameterEscaper().escape(name));
    url.append('=');
    url.append(UrlEscapers.urlFormParameterEscaper().escape(value));
    return this;
  }

  // Modelled on javascript/closure/html/trustedresourceurl.js
  // Note that all of these prefixes can only be constructed from flags/compile-time-constants
  // because / characters get URL encoded.
  // We cannot precompile this regex because of GWT.
  private static final String BASE_URL_REGEX =
      "^((https:)?//[0-9A-Za-z.:\\[\\]-]+/" // Origin.
          + "|/[^/\\\\]" // Absolute path.
          + "|[^:/\\\\]+/" // Relative path.
          + "|[^:/\\\\]*[?#]" // Query string or fragment.
          + "|about:blank#" // about:blank with fragment.
          + ").*";

  private static void checkBaseUrl(@Nullable String baseUrl) {
    if (!checkNotNull(baseUrl).matches(BASE_URL_REGEX)) {
      throw new IllegalArgumentException(
          "TrustedResourceUrls must have a prefix that sets the scheme and "
              + "origin, e.g. \"//google.com/\" or \"/path\", got:"
              + baseUrl);
    }
  }

  /** Returns the TrustedResourceUrl built so far. */
  public TrustedResourceUrl build() {
    return TrustedResourceUrls.create(url.toString());
  }
}
