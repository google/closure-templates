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


import com.google.common.annotations.GwtCompatible;
import com.google.common.io.BaseEncoding;
import com.google.common.net.UrlEscapers;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Protocol conversions and factory methods for {@link SafeUrl}. */
@CheckReturnValue
@GwtCompatible(emulated = true)
public final class SafeUrls {

  // Don't forget to update the public docs when updating this set.
  private static final Set<String> DEFAULT_SAFE_SCHEMES =
      createUnmodifiableSet("http", "https", "mailto", "ftp");

  private static final String DATA_SCHEME = "data";

  private static final Set<String> SAFE_DATA_MIME_TYPES =
      createUnmodifiableSet(
          // Audio
          "audio/3gpp2",
          "audio/3gpp",
          "audio/aac",
          "audio/midi",
          "audio/mp3",
          "audio/mp4",
          "audio/mpeg",
          "audio/oga",
          "audio/ogg",
          "audio/opus",
          "audio/x-m4a",
          "audio/x-matroska",
          "audio/x-wav",
          "audio/wav",
          "audio/webm",
          // Image
          "image/bmp",
          "image/gif",
          "image/jpeg",
          "image/jpg",
          "image/png",
          "image/tiff",
          "image/webp",
          "image/x-icon",
          // Video
          "video/mpeg",
          "video/mp4",
          "video/ogg",
          "video/webm",
          "video/x-matroska");

  private static final String DATA_BASE64_PREFIX = ";base64,";

  private static final Set<CustomSafeUrlScheme> EMPTY_CUSTOM_SCHEMES = Collections.emptySet();

  private SafeUrls() {}

  private static final Set<String> createUnmodifiableSet(String... schemes) {
    HashSet<String> set = new HashSet<String>();
    for (String scheme : schemes) {
      set.add(scheme);
    }
    return Collections.unmodifiableSet(set);
  }

  /**
   * Deserializes a SafeUrlProto into a SafeUrl instance.
   *
   * <p>Protocol-message forms are intended to be opaque. The fields of the protocol message should
   * be considered encapsulated and are not intended for direct inspection or manipulation. Protocol
   * message forms of this type should be produced by {@link #toProto(SafeUrl)} or its equivalent in
   * other implementation languages.
   *
   * <p><b>Important:</b> It is unsafe to invoke this method on a protocol message that has been
   * received from an entity outside the application's trust domain. Data coming from the browser is
   * outside the application's trust domain.
   */
  public static SafeUrl fromProto(SafeUrlProto proto) {
    return create(proto.getPrivateDoNotAccessOrElseSafeUrlWrappedValue());
  }

  /**
   * Serializes a SafeUrl into its opaque protocol message representation.
   *
   * <p>Protocol message forms of this type are intended to be opaque. The fields of the returned
   * protocol message should be considered encapsulated and are not intended for direct inspection
   * or manipulation. Protocol messages can be converted back into a SafeUrl using {@link
   * #fromProto(SafeUrlProto)}.
   */
  public static SafeUrlProto toProto(SafeUrl url) {
    return SafeUrlProto.newBuilder()
        .setPrivateDoNotAccessOrElseSafeUrlWrappedValue(url.getSafeUrlString())
        .build();
  }

  /**
   * Creates a SafeUrl from the given compile-time constant string {@code url}.
   *
   * <p>No runtime validation or sanitization is performed on {@code url}; being under application
   * control, it is simply assumed to comply with the SafeUrl contract.
   */
  public static SafeUrl fromConstant(@CompileTimeConstant final String url) {
    return create(url);
  }

  /**
   * Creates a SafeUrl object from the given {@code url}, validating that the input string matches a
   * pattern of commonly used safe URLs. If {@code url} fails validation, this method returns a
   * SafeUrl, {@link SafeUrl#INNOCUOUS}, which contains an innocuous string, {@link
   * SafeUrl#INNOCUOUS_STRING}.
   *
   * <p>Specifically, {@code url} may be a URL with any of the default safe schemes (http, https,
   * ftp, mailto), or a relative URL (i.e., a URL without a scheme; specifically, a scheme-relative,
   * absolute-path-relative, or path-relative URL).
   *
   * <p>Note that calling this method is useless when applied on URLs used for loading resources
   * such as JavaScript or CSS. It will allow URLs such as https://attacker-controlled/hack.js. You
   * need to pass such URLs as {@link TrustedResourceUrl}, there's no sanitizer for them.
   *
   * @see http://url.spec.whatwg.org/#concept-relative-url
   */
  public static SafeUrl sanitize(String url) {
    return sanitize(url, SafeUrl.INNOCUOUS);
  }

  /**
   * Variant of {@link #sanitize(String)} using the provided innocuous URL rather than the default
   * {@link SafeUrl#INNOCUOUS}.
   *
   * <p>This allows to return a custom value to signal that an unsafe URL was sanitized, for
   * instance {@code sanitize(someUrl, SafeUrls.fromConstant("javascript:void(0);");}.
   */
  public static SafeUrl sanitize(String url, SafeUrl innocuous) {
    return sanitize(url, EMPTY_CUSTOM_SCHEMES, innocuous);
  }

  /**
   * Creates a SafeUrl object from the given {@code url}, validating that the input string matches a
   * pattern of commonly used safe URLs. If {@code url} fails validation, this method returns a
   * SafeUrl, {@link SafeUrl#INNOCUOUS}, which contains an innocuous string, {@link
   * SafeUrl#INNOCUOUS_STRING}.
   *
   * <p>{@code url} is sanitized as in {@link #sanitize(String)}, additionally permitting the custom
   * schemes listed in {@code extraAllowedSchemes}.
   */
  public static SafeUrl sanitize(String url, Set<CustomSafeUrlScheme> extraAllowedSchemes) {
    return sanitize(url, extraAllowedSchemes, SafeUrl.INNOCUOUS);
  }

  /**
   * Variant of {@link #sanitize(String, SafeUrl)} that additionally permits the custom schemes
   * listed in {@code extraAllowedSchemes}.
   */
  public static SafeUrl sanitize(
      String url, Set<CustomSafeUrlScheme> extraAllowedSchemes, SafeUrl innocuous) {
    if (!isSafeUrl(url, extraAllowedSchemes)) {
      return innocuous;
    }
    return create(url);
  }

  /**
   * Sanitizes the given {@code url}, validating that the input string matches a pattern of commonly
   * used safe URLs. If {@code url} fails validation, this method returns {@code
   * about:invalid#identifier}, with the given {@code identifier}. The identifier allows users to
   * trace a sanitized value to the library that performed the sanitization and hence should be a
   * unique string like "zLibraryNamez".
   *
   * <p>Specifically, {@code url} may be a URL with any of the default safe schemes (http, https,
   * ftp, mailto), or a relative URL (i.e., a URL without a scheme; specifically, a scheme-relative,
   * absolute-path-relative, or path-relative URL).
   *
   * @see http://url.spec.whatwg.org/#concept-relative-url
   */
  public static String sanitizeAsString(String url, @CompileTimeConstant final String identifier) {
    if (!isSafeUrl(url, EMPTY_CUSTOM_SCHEMES)) {
      return "about:invalid#" + identifier;
    }
    return url;
  }

  /**
   * Creates a {@code data:text/html} URL whose content is populated from the given {@code SafeHtml}
   * object.
   *
   * <p>The resulting {@code data}-scheme URL's content is UTF-8-encoded, but the encoding of
   * non-ASCII characters is done using the standard %xx hex encoding.
   *
   * @see http://tools.ietf.org/html/rfc2397
   * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/data_URIs
   */
  public static SafeUrl createHtmlDataUrl(SafeHtml html) {
    // Use urlPathSegmentEscaper because all other Escapers convert spaces to "+" instead of "%20",
    // which are rendered as normal "+"s in the browser instead of being rendered as spaces.
    String dataUrl =
        "data:text/html;charset=UTF-8,"
            + UrlEscapers.urlPathSegmentEscaper().escape(html.getSafeHtmlString());
    return create(dataUrl);
  }

  /**
   * Creates a {@code data:text/html} URL whose content is populated from the given {@code SafeHtml}
   * object.
   *
   * <p>The resulting {@code data}-scheme URL's content is UTF-8-encoded, and further encoded using
   * base-64 transfer encoding.
   *
   * @see http://tools.ietf.org/html/rfc2397
   * @see https://developer.mozilla.org/en-US/docs/Web/HTTP/data_URIs
   */
  public static SafeUrl createHtmlDataUrlBase64(SafeHtml html) {
    try {
      String dataUrl =
          "data:text/html;charset=UTF-8;base64,"
              + BaseEncoding.base64().encode(html.getSafeHtmlString().getBytes("UTF-8"));
      return create(dataUrl);
    } catch (UnsupportedEncodingException e) {
      // Should never happen.  We use getBytes(String) instead of getBytes(CharSet) because
      // there's no java.nio.charset.StandardCharsets in older Android SDKs.
      throw new RuntimeException(e);
    }
  }

  /**
   * Matches a subset of URLs that will not cause script execution if used in URL context within a
   * HTML document. Specifically, this method returns true if the {@code url}:
   *
   * <ul>
   *   <li>Starts with a default safe protocol (http, https, ftp, mailto) or one of the schemes
   *       specified in {@code extraAllowedSchemes}.
   *   <li>Contains no protocol. A protocol must be followed by a colon and colons are allowed only
   *       after one of the characters [/?#]. A colon after a hash (#) must be in the fragment.
   *       Otherwise, a colon after a (?) must be in a query. Otherwise, a colon after a single
   *       solidus (/) must be in a path. Otherwise, a colon after a double solidus (*) must be in
   *       the authority (before port).
   * </ul>
   *
   * <p>We don't use a regex so that we don't need to depend on GWT, which does not support Java's
   * Pattern and requires using its RegExp class.
   */
  private static boolean isSafeUrl(String url, Set<CustomSafeUrlScheme> extraAllowedSchemes) {
    String lowerCased = url.toLowerCase();

    // If some Unicode character lower cases to something that ends up matching these ASCII ones,
    // it's harmless.
    for (String scheme : DEFAULT_SAFE_SCHEMES) {
      if (lowerCased.startsWith(scheme + ":")) {
        return true;
      }
    }

    // Some data URLs are harmless, check if this is one of those.
    if (lowerCased.startsWith(DATA_SCHEME + ":")) {
      return isSafeDataUrl(url);
    }

    for (CustomSafeUrlScheme scheme : extraAllowedSchemes) {
      /**
       * For "-" in a custom URL scheme, it's not possible to write a proto enum with "-" in the
       * field name. In proto, it has to be "_". But we can safely convert all "_" in the proto name
       * to "-", since according to the URL Living Standard, a URL-scheme string must be one ASCII
       * alpha, followed by zero or more of ASCII alphanumeric, "+", "-", and ".".
       *
       * @see https://url.spec.whatwg.org/#url-syntax
       */
      if (lowerCased.startsWith(scheme.name().toLowerCase().replace('_', '-') + ":")) {
        return true;
      }
    }

    for (int i = 0; i < url.length(); i++) {
      switch (url.charAt(i)) {
        case '/':
        case '?':
        case '#':
          // After this the string can end or contain anything else, it won't be interpreted
          // as the scheme.
          return true;
        case ':':
          // This character is not allowed before seeing one of the above characters.
          return false;
        default:
          // Other characters ok.
          continue;
      }
    }
    return true;
  }

  /**
   * Check if the provided URL is a safe data: URL.
   *
   * <p>This function is implemented without regexes to make it compatible with GWT, which does not
   * support the Pattern API and requires to use their RegExp class instead.
   */
  private static boolean isSafeDataUrl(String url) {
    String lowerCased = url.toLowerCase();
    int pos = 0;

    // Make sure that the string starts with "data:" and contains at least one more character.
    if (!lowerCased.startsWith(DATA_SCHEME + ":")) {
      return false;
    }
    pos += DATA_SCHEME.length() + 1;

    if (pos >= lowerCased.length()) {
      return false;
    }

    // Read the MIME type, which comes after the data: scheme and check if it's allowed.
    int mimeStartPos = pos;
    for (; pos < lowerCased.length(); ++pos) {
      char c = lowerCased.charAt(pos);
      if (c == ';' || c == ',') {
        break;
      }
    }

    if (!SAFE_DATA_MIME_TYPES.contains(lowerCased.substring(mimeStartPos, pos))) {
      return false;
    }

    // At this point there must be at least the base64 prefix and some data after it.
    // We assume that data is always encoded as base64, because there are no textual
    // mime-types in our allowlist.
    // If that were to change, we would need to allow non-base64 bodies, and/or allow
    // a base64 body along with an explicit charset.
    if (!lowerCased.startsWith(DATA_BASE64_PREFIX, pos)) {
      return false;
    }
    pos += DATA_BASE64_PREFIX.length();

    // This has the effect of disallowing empty bodies which is fine for the set
    // of allowed mime-types.
    if (pos >= lowerCased.length()) {
      return false;
    }

    // Check that the data is encoded using the base64 alphabet, [a-zA-Z0-9+/]
    for (; pos < lowerCased.length(); ++pos) {
      char c = lowerCased.charAt(pos);
      if (c == '=') {
        break;
      }
      // We only check [a-z] since the string has been lowercased.
      if (!('a' <= c && c <= 'z') && !('0' <= c && c <= '9') && c != '+' && c != '/') {
        return false;
      }
    }

    // The trailing part of the URL may only contain base64 padding characters.
    for (; pos < lowerCased.length(); ++pos) {
      if (lowerCased.charAt(pos) != '=') {
        return false;
      }
    }

    // If no check failed, this data: URL is safe.
    return true;
  }

  /**
   * Creates a SafeUrl by doing an unchecked conversion from the given {@code url}. Also called from
   * SafeUrlBuilder.
   */
  static SafeUrl create(String url) {
    return new SafeUrl(url);
  }
}
