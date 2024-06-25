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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;
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
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.template.soy.internal.base.UnescapeUtils;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** A chunk of sanitized content of a known kind, e.g. the output of an HTML sanitizer. */
@ParametersAreNonnullByDefault
@Immutable
@DoNotMock("Use SanitizedContents.emptyString or UnsafeSanitizedContentOrdainer.ordainAsSafe")
public abstract class SanitizedContent extends SoyAbstractValue {

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
    return kind == ContentKind.ATTRIBUTES
        ? new Attributes(content, dir)
        : new Impl(content, kind, dir);
  }

  /** Creates a SanitizedContent object with default direction. */
  static SanitizedContent create(String content, ContentKind kind) {
    return create(content, kind, kind.getDefaultDir());
  }

  /** Creates a SanitizedContent from a command buffer. */
  static SanitizedContent create(
      LoggingAdvisingAppendable.CommandBuffer commandBuffer, ContentKind kind, @Nullable Dir dir) {
    if (kind == ContentKind.HTML) {
      return new BufferedImpl(commandBuffer, kind, dir);
    }
    if (kind == ContentKind.ATTRIBUTES) {
      return new BufferedAttributes(commandBuffer, dir);
    }
    throw new IllegalArgumentException("Only kind ATTRIBUTES and HTML are supported, got: " + kind);
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

  /**
   * Package private constructor to limit subclasses to this file. This is important to ensure that
   * all implementations of this class are fully vetted by security.
   */
  private SanitizedContent(ContentKind contentKind, @Nullable Dir contentDir) {
    checkArgument(
        contentKind != ContentKind.TEXT,
        "Use plain strings instead SanitizedContent with kind of TEXT");
    this.contentKind = Preconditions.checkNotNull(contentKind);
    this.contentDir = contentDir;
  }

  /** Returns a string of valid content with kind {@link #getContentKind}. */
  public abstract String getContent();

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
    return true;
  }

  @Override
  public boolean isTruthyNonEmpty() {
    return getContent().length() > 0;
  }

  @Override
  public boolean hasContent() {
    return getContent().length() > 0;
  }

  @Override
  public String coerceToString() {
    return toString();
  }

  @Override
  public String toString() {
    return getContent();
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

  @Override
  public SoyValue checkNullishSanitizedContent(ContentKind contentKind) {
    if (this.contentKind == ContentKind.TRUSTED_RESOURCE_URI && contentKind == ContentKind.URI) {
      // This should be allowed.
      return this;
    }

    if (contentKind == ContentKind.TRUSTED_RESOURCE_URI && this.contentKind == ContentKind.URI) {
      // This probably shouldn't be allowed but there are templates in the wild that depend on this.
      return this;
    }

    if (contentKind != this.contentKind) {
      throw new ClassCastException(this.contentKind + " cannot be cast to " + contentKind);
    }

    return this;
  }

  @Override
  public boolean isSanitizedContentKind(ContentKind contentKind) {
    if (this.contentKind == ContentKind.TRUSTED_RESOURCE_URI && contentKind == ContentKind.URI) {
      return true;
    }

    return contentKind == this.contentKind;
  }

  /** A single attribute value. */
  @AutoValue
  @Immutable
  public abstract static class AttributeValue {
    private static final AttributeValue NONE = new AutoValue_SanitizedContent_AttributeValue(null);

    /**
     * Returns an attribute value that represents no value, useful for bare attributes like {@code
     * disabled}.
     */
    public static AttributeValue none() {
      return NONE;
    }

    /** Creates an attribute value from a string that has already been entity escaped. */
    public static AttributeValue createFromEscapedValue(String escapedValue) {
      // Much like the rest of Soy we are as permissive as the browsers when it comes to attribute
      // values.  While technically everything must be entity escaped, only quotation marks really
      // matter
      checkArgument(escapedValue.indexOf('"') == -1, "value contains unescped characters");
      return createFromEscapedValueUnchecked(escapedValue);
    }

    /** Creates an attribute value from a string that has not been entity escaped. */
    public static AttributeValue createFromUnescapedValue(String unescaped) {
      String escapedValue = HtmlEscapers.htmlEscaper().escape(unescaped);
      var v = createFromEscapedValueUnchecked(escapedValue);
      v.unescapedValue = unescaped;
      return v;
    }

    private static AttributeValue createFromEscapedValueUnchecked(String escapedValue) {
      return new AutoValue_SanitizedContent_AttributeValue(escapedValue);
    }

    @LazyInit private String unescapedValue;

    @Nullable
    public abstract String escapedValue();

    @Nullable
    public String unescapedValue() {
      var unescapedValue = this.unescapedValue;
      if (unescapedValue == null) {
        if (escapedValue() == null) {
          return null;
        }
        unescapedValue = this.unescapedValue = UnescapeUtils.unescapeHtml(escapedValue());
      }
      return unescapedValue;
    }

    AttributeValue() {} // prevent instantiation outside of this package
  }

  /**
   * Returns the contents of a SanitizedContent of kind ATTRIBUTES as a map of attribute name to
   * attribute value.
   *
   * <p>All quotation marks have been remove and the names
   */
  public ImmutableMap<String, AttributeValue> getAsAttributesMap() {
    throw new IllegalStateException(
        "getAsAttributesMap() is only valid for SanitizedContent of kind  ATTRIBUTES, this is "
            + contentKind);
  }

  static final class Attributes extends SanitizedContent {

    // At least one of these is always non-null and each can be derived from the other.
    @LazyInit @Nullable private String content;
    @LazyInit @Nullable private ImmutableMap<String, AttributeValue> attributes;

    Attributes(String content, @Nullable Dir contentDir) {
      super(ContentKind.ATTRIBUTES, contentDir);
      this.content = content;
    }

    Attributes(Map<String, AttributeValue> content) {
      super(ContentKind.ATTRIBUTES, ContentKind.ATTRIBUTES.getDefaultDir());
      var attributes = ImmutableMap.copyOf(content);
      attributes.forEach(
          (key, value) -> {
            if (key.isEmpty() || !key.equals(Ascii.toLowerCase(key))) {
              throw new IllegalArgumentException(
                  "attribute names must be lowercase and non-empty:" + key);
            }
          });
      this.attributes = attributes;
    }

    @Override
    public String getContent() {
      var content = this.content;
      if (content == null) {
        StringBuilder sb = new StringBuilder();
        try {
          writeAttributesToAppendable(sb);
        } catch (IOException e) {
          throw new AssertionError(e); // impossible
        }
        content = this.content = sb.toString();
      }
      return content;
    }

    @Override
    public void render(LoggingAdvisingAppendable appendable) throws IOException {
      appendable = appendable.setKindAndDirectionality(getContentKind(), getContentDirection());
      var content = this.content;
      if (content == null) {
        writeAttributesToAppendable(appendable);
      } else {
        appendable.append(content);
      }
    }

    private void writeAttributesToAppendable(Appendable appendable) throws IOException {
      boolean first = true;
      for (var entry : attributes.entrySet()) {
        if (!first) {
          appendable.append(' ');
        }
        first = false;
        var key = entry.getKey();
        var escapedValue = entry.getValue().escapedValue();
        appendable.append(key);
        if (escapedValue != null) {
          appendable.append("=\"").append(escapedValue).append('"');
        }
      }
    }

    @Override
    public ImmutableMap<String, AttributeValue> getAsAttributesMap() {
      var attributes = this.attributes;
      if (attributes == null) {
        attributes = this.attributes = parseAttributes(this.content);
      }
      return attributes;
    }
  }

  private static final CharMatcher NOT_WHITESPACE = CharMatcher.whitespace().negate();

  private static int consume(CharMatcher endMatcher, String content, int position) {
    int length = content.length();
    for (; position < length; position++) {
      if (endMatcher.matches(content.charAt(position))) {
        break;
      }
    }
    return position;
  }

  /** Returns the position of the first non-whitespace character, or length */
  private static int consumeWhitespace(String content, int position) {
    return consume(NOT_WHITESPACE, content, position);
  }

  private static final CharMatcher WHITESPACE_OR_EQUALS =
      CharMatcher.whitespace().or(CharMatcher.is('=')).precomputed();

  private static int consumeAttributeName(String content, int position) {
    return consume(WHITESPACE_OR_EQUALS, content, position);
  }

  private static int consumeUnquotedAttributeValue(String content, int position) {
    return consume(CharMatcher.whitespace(), content, position);
  }

  /**
   * Returns a map of attributes from the given content.
   *
   * <p>This parser does not validate, the content provided here was either produced by a trusted
   * producer (like Soy), a safe factory function, or was 'ordained' as safe by the caller. So we
   * assume that attribute names and values are well formed and will only perform the validation
   * that is necessary to fulfill our contract.
   *
   * @throws IllegalArgumentException if attribute values are not well formed (quotes are not
   *     balanced, a value is missing after an equals sign, etc).
   */
  @VisibleForTesting
  static ImmutableMap<String, AttributeValue> parseAttributes(String content) {
    var attributes = ImmutableMap.<String, AttributeValue>builder();
    final var length = content.length();
    int position = consumeWhitespace(content, 0);
    while (position < length) {
      var end = consumeAttributeName(content, position);
      // Canonically attribute names are case insensitive (in ascii), and by convention lowercase
      // is used.
      var name = Ascii.toLowerCase(content.substring(position, end));
      if (name.isEmpty()) {
        throw new IllegalArgumentException("Empty attribute name in " + content);
      }
      // end is pointing at whitespace or an equals sign (or the end of the string).
      if (end == length) {
        attributes.put(name, AttributeValue.none());
        break;
      }
      // The most common case is that the equals sign follows the attribute name immediately
      if (content.charAt(end) != '=') {
        end = consumeWhitespace(content, end);
        if (end == length || content.charAt(end) != '=') {
          position = end;
          attributes.put(name, AttributeValue.none());
          continue;
        }
      }
      position = end + 1; // consume the equals sign
      position = consumeWhitespace(content, position);
      if (position == length) {
        throw new IllegalArgumentException(
            "missing attribute value for " + name + " in " + content);
      }
      char initial = content.charAt(position);
      if (initial == '"' || initial == '\'') {
        position++; // skip the leading quotation mark
        end = content.indexOf(initial, position);
        if (end == -1) {
          throw new IllegalArgumentException("Unbalanced quotes in attribute value");
        }
        String quotedValue = content.substring(position, end);
        if (initial == '\'') {
          // In a single quoted attribute value, a double quote may exist which will corrupt the
          // output when/if the value is re-encoded as a double quoted attribute value.
          // This is technically out of spec, but we are permissive.
          quotedValue = quotedValue.replace("\"", "&quot;");
        }
        attributes.put(name, AttributeValue.createFromEscapedValueUnchecked(quotedValue));
        position = end + 1; // ignore the trailing quotation mark
      } else {
        end = consumeUnquotedAttributeValue(content, position);
        attributes.put(
            name, AttributeValue.createFromEscapedValueUnchecked(content.substring(position, end)));
        position = end + 1; // we found the end or some whitespace, skip it.
      }
      position = consumeWhitespace(content, position);
    }
    return attributes.buildKeepingLast();
  }

  private static final class Impl extends SanitizedContent {
    private final String content;

    Impl(String content, ContentKind contentKind, @Nullable Dir contentDir) {
      super(contentKind, contentDir);
      this.content = content;
    }

    @Override
    public void render(LoggingAdvisingAppendable appendable) throws IOException {
      appendable.setKindAndDirectionality(getContentKind(), getContentDirection()).append(content);
    }

    @Override
    public String getContent() {
      return content;
    }
  }

  private static class BufferedImpl extends SanitizedContent {
    @LazyInit String content;
    private final LoggingAdvisingAppendable.CommandBuffer commandBuffer;

    BufferedImpl(
        LoggingAdvisingAppendable.CommandBuffer commandBuffer,
        ContentKind contentKind,
        @Nullable Dir contentDir) {
      super(contentKind, contentDir);
      this.commandBuffer = commandBuffer;
    }

    @Override
    public String getContent() {
      var content = this.content;
      if (content == null) {
        content = commandBuffer.toString();
        this.content = content;
      }
      return content;
    }

    @Override
    public void render(LoggingAdvisingAppendable appendable) throws IOException {
      commandBuffer.replayOn(
          appendable.setKindAndDirectionality(getContentKind(), getContentDirection()));
    }
  }

  private static final class BufferedAttributes extends BufferedImpl {
    @LazyInit @Nullable private ImmutableMap<String, AttributeValue> attributes;

    BufferedAttributes(LoggingAdvisingAppendable.CommandBuffer commandBuffer, @Nullable Dir dir) {
      super(commandBuffer, ContentKind.ATTRIBUTES, dir);
    }

    @Override
    public ImmutableMap<String, AttributeValue> getAsAttributesMap() {
      var attributes = this.attributes;
      if (attributes == null) {
        // TODO(b/288958830): create a fast path for parsing attributes from a buffer.
        attributes = this.attributes = parseAttributes(this.getContent());
      }
      return attributes;
    }
  }
}
