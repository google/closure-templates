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

import static com.google.common.html.types.BuilderUtils.coerceToInterchangeValid;
import static com.google.common.html.types.BuilderUtils.escapeHtmlInternal;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.CompileTimeConstant;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

/** Protocol conversions, builders and factory methods for {@link SafeHtml}. */
@CheckReturnValue
@GwtCompatible(emulated = true)
public final class SafeHtmls {

  /** Creates SafeHtml element with content (it will be escaped) and no attributes. */
  public static SafeHtml createElement(
      @CompileTimeConstant final String elementName, String content) {
    return new SafeHtmlBuilder(elementName).escapeAndAppendContent(content).build();
  }

  /** Creates SafeHtml element with content and no attributes. */
  public static SafeHtml createElement(
      @CompileTimeConstant final String elementName, Iterable<SafeHtml> htmls) {
    return new SafeHtmlBuilder(elementName).appendContent(htmls).build();
  }

  /** Creates SafeHtml element with content and no attributes. */
  public static SafeHtml createElement(
      @CompileTimeConstant final String elementName, SafeHtml... htmls) {
    SafeHtmlBuilder builder = new SafeHtmlBuilder(elementName);
    if (htmls.length != 0) {
      builder.appendContent(htmls);
    }
    return builder.build();
  }

  /**
   * Creates a SafeHtml from the given compile-time constant {@code resourceName}. The resource will
   * be loaded using {@link Resources#getResource(String)} and treated as UTF-8.
   *
   * <p>This performs ZERO VALIDATION of the data. We assume that resources should be safe because
   * they are part of the binary, and therefore not attacker controlled.
   */
  @GwtIncompatible("Resources")
  public static SafeHtml fromResource(@CompileTimeConstant final String resourceName)
      throws IOException {
    return create(
        Resources.toString(Resources.getResource(resourceName), Charset.forName("UTF-8")));
  }

  /**
   * Creates a SafeHtml from the given compile-time constant {@code resourceName}. The resource will
   * be loaded using {@link Resources#getResource(Class, String)} and treated as UTF-8.
   *
   * <p>This performs ZERO VALIDATION of the data. We assume that resources should be safe because
   * they are part of the binary, and therefore not attacker controlled.
   *
   * @param contextClass Class relative to which to load the resource.
   */
  @GwtIncompatible("Resources")
  public static SafeHtml fromResource(
      Class<?> contextClass, @CompileTimeConstant final String resourceName) throws IOException {
    return create(
        Resources.toString(
            Resources.getResource(contextClass, resourceName), Charset.forName("UTF-8")));
  }

  /**
   * Deserializes a SafeHtmlProto into a SafeHtml instance.
   *
   * <p>Protocol-message forms are intended to be opaque. The fields of the protocol message should
   * be considered encapsulated and are not intended for direct inspection or manipulation. Protocol
   * message forms of this type should be produced by {@link #toProto(SafeHtml)} or its equivalent
   * in other implementation languages.
   *
   * <p><b>Important:</b> It is unsafe to invoke this method on a protocol message that has been
   * received from an entity outside the application's trust domain. Data coming from the browser is
   * outside the application's trust domain.
   */
  public static SafeHtml fromProto(SafeHtmlProto proto) {
    return create(proto.getPrivateDoNotAccessOrElseSafeHtmlWrappedValue());
  }

  /** Wraps a SafeScript inside a &lt;script type="text/javascript"&gt; tag. */
  public static SafeHtml fromScript(SafeScript script) {
    return create("<script type=\"text/javascript\">" + script.getSafeScriptString() + "</script>");
  }

  /** Wraps a SafeScript inside a &lt;script type="application/ld+json"&gt; tag. */
  public static SafeHtml fromScriptForTypeApplicationLdJson(SafeScript script) {
    return create(
        "<script type=\"application/ld+json\">" + script.getSafeScriptString() + "</script>");
  }

  /**
   * Wraps a SafeScript inside a &lt;script type="text/javascript"&gt; tag. The tag has a nonce
   * attribute populated from the provided CSP nonce value.
   */
  public static SafeHtml fromScriptWithCspNonce(SafeScript script, String cspNonce) {
    return create(
        "<script type=\"text/javascript\" nonce=\""
            + htmlEscapeInternal(cspNonce)
            + "\">"
            + script.getSafeScriptString()
            + "</script>");
  }

  /**
   * Creates a &lt;script type="text/javascript" src="<i>url</i>"&gt;&lt;script&gt; where the {@code
   * src} attribute points to the given {@code trustedResourceUrl}.
   */
  public static SafeHtml fromScriptUrl(TrustedResourceUrl trustedResourceUrl) {
    String escapedUrl = htmlEscapeInternal(trustedResourceUrl.getTrustedResourceUrlString());
    return create("<script type=\"text/javascript\" src=\"" + escapedUrl + "\"></script>");
  }

  /**
   * Creates a &lt;script defer type="text/javascript" src="<i>url</i>"&gt;&lt;script&gt; where the
   * {@code src} attribute points to the given {@code trustedResourceUrl}.
   */
  public static SafeHtml fromScriptUrlDeferred(TrustedResourceUrl trustedResourceUrl) {
    String escapedUrl = htmlEscapeInternal(trustedResourceUrl.getTrustedResourceUrlString());
    return create("<script defer type=\"text/javascript\" src=\"" + escapedUrl + "\"></script>");
  }

  /**
   * Creates a &lt;script type="text/javascript" src="<i>url</i>"&gt;&lt;script&gt; where the {@code
   * src} attribute points to the given {@code trustedResourceUrl}. The tag has a nonce attribute
   * populated from the provided CSP nonce value.
   */
  public static SafeHtml fromScriptUrlWithCspNonce(
      TrustedResourceUrl trustedResourceUrl, String cspNonce) {
    String escapedUrl = htmlEscapeInternal(trustedResourceUrl.getTrustedResourceUrlString());
    return create(
        "<script type=\"text/javascript\" nonce=\""
            + htmlEscapeInternal(cspNonce)
            + "\" src=\""
            + escapedUrl
            + "\"></script>");
  }

  /**
   * Creates a &lt;script defer type="text/javascript" src="<i>url</i>"&gt;&lt;script&gt; where the
   * {@code src} attribute points to the given {@code trustedResourceUrl}. The tag has a nonce
   * attribute populated from the provided CSP nonce value.
   */
  public static SafeHtml fromScriptUrlWithCspNonceDeferred(
      TrustedResourceUrl trustedResourceUrl, String cspNonce) {
    String escapedUrl = htmlEscapeInternal(trustedResourceUrl.getTrustedResourceUrlString());
    return create(
        "<script defer type=\"text/javascript\" nonce=\""
            + htmlEscapeInternal(cspNonce)
            + "\" src=\""
            + escapedUrl
            + "\"></script>");
  }

  /** Wraps a SafeStyleSheet inside a &lt;style type="text/css"&gt; tag. */
  public static SafeHtml fromStyleSheet(SafeStyleSheet safeStyleSheet) {
    Preconditions.checkArgument(!safeStyleSheet.getSafeStyleSheetString().contains("<"));
    return create(
        "<style type=\"text/css\">" + safeStyleSheet.getSafeStyleSheetString() + "</style>");
  }

  /**
   * Creates a &lt;link rel="stylesheet" href="<i>url</i>"&gt; where the {@code href} attribute
   * points to the given {@code trustedResourceUrl}.
   */
  public static SafeHtml fromStyleUrl(TrustedResourceUrl trustedResourceUrl) {
    String escapedUrl = htmlEscapeInternal(trustedResourceUrl.getTrustedResourceUrlString());
    return create("<link rel=\"stylesheet\" href=\"" + escapedUrl + "\">");
  }

  /**
   * Serializes a SafeHtml into its opaque protocol message representation.
   *
   * <p>Protocol message forms of this type are intended to be opaque. The fields of the returned
   * protocol message should be considered encapsulated and are not intended for direct inspection
   * or manipulation. Protocol messages can be converted back into a SafeHtml using {@link
   * #fromProto(SafeHtmlProto)}.
   */
  public static SafeHtmlProto toProto(SafeHtml safeHtml) {
    return SafeHtmlProto.newBuilder()
        .setPrivateDoNotAccessOrElseSafeHtmlWrappedValue(safeHtml.getSafeHtmlString())
        .build();
  }

  /** Converts, by HTML-escaping, an arbitrary string into a contract-compliant {@link SafeHtml}. */
  public static SafeHtml htmlEscape(String text) {
    return create(htmlEscapeInternal(text));
  }

  /** Returns HTML-escaped text as a SafeHtml object, with newlines changed to {@code <br>}. */
  public static SafeHtml htmlEscapePreservingNewlines(String text) {
    return create(htmlEscapeInternal(text).replaceAll("\r?\n|\r", "<br>"));
  }

  /** Returns HTML-escaped text as a SafeHtml object, with newlines changed to {@code <br>}. */
  public static SafeHtml htmlEscapePreservingWhitespace(String text) {
    String html =
        htmlEscapeInternal(text)
            // Leading space is converted into a non-breaking space, and spaces following whitespace
            // are converted into non-breaking spaces. This must happen first, to ensure we preserve
            // spaces after newlines.
            .replaceAll("(^|[\r\n\t ]) ", "$1&#160;")
            .replaceAll("\r?\n|\r", "<br>")
            .replaceAll("(\t+)", "<span style=\"white-space:pre\">$1</span>");
    return create(html);
  }

  /**
   * Converts an arbitrary string into an HTML comment by HTML-escaping the contents and embedding
   * the result between HTML comment markers.
   *
   * <p>Escaping is needed because Internet Explorer supports conditional comments and so may render
   * HTML markup within comments.
   */
  public static SafeHtml comment(String text) {
    return create("<!--" + htmlEscapeInternal(text) + "-->");
  }

  /** The SafeHtml for the HTML5 doctype. */
  public static SafeHtml html5Doctype() {
    return create("<!DOCTYPE html>");
  }

  /**
   * Creates a new SafeHtml which contains, in order, the string representations of the given {@code
   * htmls}.
   */
  public static SafeHtml concat(SafeHtml... htmls) {
    return concat(Arrays.asList(htmls));
  }

  /**
   * Creates a new SafeHtml which contains, in order, the string representations of the given {@code
   * htmls}.
   */
  public static SafeHtml concat(Iterable<SafeHtml> htmls) {
    int concatLength = 0;
    for (SafeHtml html : htmls) {
      concatLength += html.getSafeHtmlString().length();
    }

    StringBuilder result = new StringBuilder(concatLength);
    for (SafeHtml html : htmls) {
      result.append(html.getSafeHtmlString());
    }
    return create(result.toString());
  }

  // Default visibility for use by SafeHtmlBuilder.
  static SafeHtml create(String html) {
    return new SafeHtml(html);
  }

  private static String htmlEscapeInternal(String text) {
    return escapeHtmlInternal(coerceToInterchangeValid(text));
  }

  private SafeHtmls() {}
}
