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

package com.google.template.soy.data;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Preconditions;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeScripts;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeStyleSheets;
import com.google.common.html.types.SafeStyles;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.SafeUrls;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.common.html.types.TrustedResourceUrls;
import com.google.common.html.types.UncheckedConversions;
import com.google.errorprone.annotations.DoNotMock;
import com.google.errorprone.annotations.Immutable;
import java.io.IOException;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** A chunk of sanitized content of a known kind, e.g. the output of an HTML sanitizer. */
@ParametersAreNonnullByDefault
@Immutable
@DoNotMock("Use SanitizedContents.emptyString or UnsafeSanitizedContentOrdainer.ordainAsSafe")
public class SanitizedContent extends SoyData {

  /**
   * Creates a SanitizedContent object.
   *
   * <p>Package-private. Ideally, if one is available, you should use an existing serializer,
   * sanitizer, verifier, or extractor that returns SanitizedContent objects. Or, you can use
   * UnsafeSanitizedContentOrdainer in this package, to make it clear that creating these objects
   * from arbitrary content is risky unless you absolutely know the input is safe. See the comments
   * in UnsafeSanitizedContentOrdainer for more recommendations.
   *
   * @param content A string of valid content with the given content kind.
   * @param kind Describes the kind of string that content is.
   * @param dir The content's direction; null if unknown and thus to be estimated when necessary.
   */
  static SanitizedContent create(String content, ContentKind kind, @Nullable Dir dir) {
      return new SanitizedContent(content, kind, dir);
  }

  /** Creates a SanitizedContent object with default direction. */
  static SanitizedContent create(String content, ContentKind kind) {
    return create(content, kind, kind.getDefaultDir());
  }

  /** A kind of textual content. */
  public enum ContentKind {
    // NOTE: internally in the compiler we use a parallel enum SanitizedContentKind.  That should
    // be preferred for all compiler usecases and this should only be used for public interfaces.

    /**
     * A snippet of HTML that does not start or end inside a tag, comment, entity, or DOCTYPE; and
     * that does not contain any executable code (JS, {@code <object>}s, etc.) from a different
     * trust domain.
     */
    HTML,

    /**
     * Executable Javascript code or expression, safe for insertion in a script-tag or event handler
     * context, known to be free of any attacker-controlled scripts. This can either be
     * side-effect-free Javascript (such as JSON) or Javascript that entirely under Google's
     * control.
     */
    JS,

    /** A properly encoded portion of a URI. */
    URI,

    /** Resource URIs used in script sources, stylesheets, etc which are not in attacker control. */
    TRUSTED_RESOURCE_URI,

    /** An attribute name and value, such as {@code dir="ltr"}. */
    ATTRIBUTES,

    // TODO(gboyer): Consider separating rules, properties, declarations, and
    // values into separate types, but for simplicity, we'll treat explicitly
    // blessed SanitizedContent as allowed in all of these contexts.
    // TODO(user): Also consider splitting CSS into CSS and CSS_SHEET (corresponding to
    // SafeStyle and SafeStyleSheet)
    /** A CSS3 declaration, property, value or group of semicolon separated declarations. */
    CSS,

    /**
     * Unsanitized plain-text content.
     *
     * <p>This is effectively the "null" entry of this enum, and is sometimes used to explicitly
     * mark content that should never be used unescaped. Since any string is safe to use as text,
     * being of ContentKind.TEXT makes no guarantees about its safety in any other context such as
     * HTML.
     *
     * <p>In the soy type system, {@code TEXT} is equivalent to the string type.
     *
     * @deprecated There is no need to use this enum value any more. If you are rendering, you can
     *     call the {@code renderText} method on {@code SoySauce} or {@code SoyTofu} to render a
     *     template as text. If you are constructing SanitizedContent objects to pass into Soy for
     *     rendering (or returning them from soy functions), then you should just pass {@link
     *     String} objects instead. This is being removed in order to simplify the API for
     *     SanitizedContent and make the types clearer.
     */
    @Deprecated
    TEXT;

    /*
     * Returns the default direction for this content kind: LTR for JS, URI, ATTRIBUTES, CSS, and
     * TRUSTED_RESOURCE_URI content, and otherwise unknown (null).
     */
    @Nullable
    public Dir getDefaultDir() {
      switch (this) {
        case JS:
        case URI:
        case ATTRIBUTES:
        case CSS:
        case TRUSTED_RESOURCE_URI:
          return Dir.LTR;
        case HTML:
        case TEXT:
          return null;
      }
      throw new AssertionError(this);
    }
  }

  private final ContentKind contentKind;
  private final Dir contentDir;
  private final String content;

  /**
   * Package private constructor to limit subclasses to this file. This is important to ensure that
   * all implementations of this class are fully vetted by security.
   */
  SanitizedContent(String content, ContentKind contentKind, @Nullable Dir contentDir) {
    checkArgument(
        contentKind != ContentKind.TEXT,
        "Use plain strings instead SanitizedContent with kind of TEXT");
    this.content = content;
    this.contentKind = contentKind;
    this.contentDir = contentDir;
  }

  /** Returns a string of valid content with kind {@link #getContentKind}. */
  public String getContent() {
    return content;
  }

  /** Returns the kind of content. */
  public final ContentKind getContentKind() {
    return contentKind;
  }

  /**
   * Returns the content's direction; null indicates that the direction is unknown, and is to be
   * estimated when necessary.
   */
  @Nullable
  public Dir getContentDirection() {
    return contentDir;
  }

  @Override
  public boolean coerceToBoolean() {
    return getContent().length() > 0; // Consistent with StringData
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public String toString() {
    return getContent();
  }

  @Override
  public void render(LoggingAdvisingAppendable appendable) throws IOException {
    appendable
        .setSanitizedContentKind(getContentKind())
        .setSanitizedContentDirectionality(getContentDirection())
        .append(content);
  }

  /**
   * Returns the string value.
   *
   * <p>In contexts where a string value is required, SanitizedContent is permitted.
   */
  @Override
  public String stringValue() {
    return getContent();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    // TODO(user): js uses reference equality, this uses content comparison
    return other instanceof SanitizedContent
        && this.contentKind == ((SanitizedContent) other).contentKind
        && this.contentDir == ((SanitizedContent) other).contentDir
        && this.getContent().equals(((SanitizedContent) other).getContent());
  }

  @Override
  public int hashCode() {
    return getContent().hashCode() + 31 * contentKind.hashCode();
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind HTML into a {@link SafeHtml}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#HTML}.
   */
  public SafeHtml toSafeHtml() {
    Preconditions.checkState(
        getContentKind() == ContentKind.HTML,
        "toSafeHtml() only valid for SanitizedContent of kind HTML, is: %s",
        getContentKind());
    return UncheckedConversions.safeHtmlFromStringKnownToSatisfyTypeContract(getContent());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind HTML into a {@link SafeHtmlProto}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#HTML}.
   */
  public SafeHtmlProto toSafeHtmlProto() {
    return SafeHtmls.toProto(toSafeHtml());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind JS into a {@link SafeScript}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#JS}.
   */
  public SafeScript toSafeScript() {
    Preconditions.checkState(
        getContentKind() == ContentKind.JS,
        "toSafeScript() only valid for SanitizedContent of kind JS, is: %s",
        getContentKind());
    return UncheckedConversions.safeScriptFromStringKnownToSatisfyTypeContract(getContent());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind JS into a {@link SafeScriptProto}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#JS}.
   */
  public SafeScriptProto toSafeScriptProto() {
    return SafeScripts.toProto(toSafeScript());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind CSS into a {@link SafeStyle}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#CSS}.
   */
  public SafeStyle toSafeStyle() {
    Preconditions.checkState(
        getContentKind() == ContentKind.CSS,
        "toSafeStyle() only valid for SanitizedContent of kind CSS, is: %s",
        getContentKind());

    // Sanity check: Try to prevent accidental misuse when this is a full stylesheet rather than a
    // declaration list.
    // The error may trigger incorrectly if the content contains curly brackets inside comments or
    // quoted strings.
    //
    // This is a best-effort attempt to preserve SafeStyle's semantical guarantees.
    Preconditions.checkState(
        !getContent().contains("{"),
        "Calling toSafeStyle() with content that doesn't look like CSS declarations. "
            + "Consider using toSafeStyleSheet().");

    return UncheckedConversions.safeStyleFromStringKnownToSatisfyTypeContract(getContent());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind CSS into a {@link SafeStyleProto}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#CSS}.
   */
  public SafeStyleProto toSafeStyleProto() {
    return SafeStyles.toProto(toSafeStyle());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind CSS into a {@link SafeStyleSheet}.
   *
   * <p>To ensure correct behavior and usage, the SanitizedContent object should fulfill the
   * contract of SafeStyleSheet - the CSS content should represent the top-level content of a style
   * element within HTML.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#CSS}.
   */
  public SafeStyleSheet toSafeStyleSheet() {
    Preconditions.checkState(
        getContentKind() == ContentKind.CSS,
        "toSafeStyleSheet() only valid for SanitizedContent of kind CSS, is: %s",
        getContentKind());

    // Sanity check: Try to prevent accidental misuse when this is not really a stylesheet but
    // instead just a declaration list (i.e. a SafeStyle). This does fail to accept a stylesheet
    // that is only a comment or only @imports; if you have a legitimate reason for this, it would
    // be fine to make this more sophisticated, but in practice it's unlikely and keeping this check
    // simple helps ensure it is fast. Note that this isn't a true security boundary, but a
    // best-effort attempt to preserve SafeStyleSheet's semantical guarantees.
    Preconditions.checkState(
        getContent().isEmpty() || getContent().indexOf('{') > 0,
        "Calling toSafeStyleSheet() with content that doesn't look like a stylesheet");

    return UncheckedConversions.safeStyleSheetFromStringKnownToSatisfyTypeContract(getContent());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind CSS into a {@link SafeStyleSheetProto}.
   *
   * <p>To ensure correct behavior and usage, the SanitizedContent object should fulfill the
   * contract of SafeStyleSheet - the CSS content should represent the top-level content of a style
   * element within HTML.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#CSS}.
   */
  public SafeStyleSheetProto toSafeStyleSheetProto() {
    return SafeStyleSheets.toProto(toSafeStyleSheet());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind URI into a {@link SafeUrl}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#URI}.
   */
  public SafeUrl toSafeUrl() {
    Preconditions.checkState(
        getContentKind() == ContentKind.URI,
        "toSafeUrl() only valid for SanitizedContent of kind URI, is: %s",
        getContentKind());
    return UncheckedConversions.safeUrlFromStringKnownToSatisfyTypeContract(getContent());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind URI into a {@link SafeUrlProto}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#URI}.
   */
  public SafeUrlProto toSafeUrlProto() {
    return SafeUrls.toProto(toSafeUrl());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind TRUSTED_RESOURCE_URI into a {@link
   * TrustedResourceUrl}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#TRUSTED_RESOURCE_URI}.
   */
  public TrustedResourceUrl toTrustedResourceUrl() {
    Preconditions.checkState(
        getContentKind() == ContentKind.TRUSTED_RESOURCE_URI,
        "toTrustedResourceUrl() only valid for SanitizedContent of kind TRUSTED_RESOURCE_URI, "
            + "is: %s",
        getContentKind());
    return UncheckedConversions.trustedResourceUrlFromStringKnownToSatisfyTypeContract(
        getContent());
  }

  /**
   * Converts a Soy {@link SanitizedContent} of kind TRUSTED_RESOURCE_URI into a {@link
   * TrustedResourceUrlProto}.
   *
   * @throws IllegalStateException if this SanitizedContent's content kind is not {@link
   *     ContentKind#TRUSTED_RESOURCE_URI}.
   */
  public TrustedResourceUrlProto toTrustedResourceUrlProto() {
    return TrustedResourceUrls.toProto(toTrustedResourceUrl());
  }

}
