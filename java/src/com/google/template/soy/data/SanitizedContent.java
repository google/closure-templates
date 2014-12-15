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


import java.io.IOException;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;


/**
 * A chunk of sanitized content of a known kind, e.g. the output of an HTML sanitizer.
 *
 */
@ParametersAreNonnullByDefault
@Immutable
public final class SanitizedContent extends SoyData {


  /**
   * A kind of textual content.
   */
  public enum ContentKind {

    /**
     * A snippet of HTML that does not start or end inside a tag, comment, entity, or DOCTYPE; and
     * that does not contain any executable code (JS, {@code <object>}s, etc.) from a different
     * trust domain.
     */
    HTML,

    /**
     * Executable Javascript code or expression, safe for insertion in a script-tag or event
     * handler context, known to be free of any attacker-controlled scripts. This can either be
     * side-effect-free Javascript (such as JSON) or Javascript that entirely under Google's
     * control.
     */
    JS,

    /** A properly encoded portion of a URI. */
    URI,

    /** An attribute name and value, such as {@code dir="ltr"}. */
    ATTRIBUTES,

    // TODO(gboyer): Consider separating rules, properties, declarations, and
    // values into separate types, but for simplicity, we'll treat explicitly
    // blessed SanitizedContent as allowed in all of these contexts.
    /** A CSS3 declaration, property, value or group of semicolon separated declarations. */
    CSS,

    /**
     * Unsanitized plain-text content.
     *
     * This is effectively the "null" entry of this enum, and is sometimes used to explicitly mark
     * content that should never be used unescaped. Since any string is safe to use as text, being
     * of ContentKind.TEXT makes no guarantees about its safety in any other context such as HTML.
     */
    TEXT
    ;

  }


  private final String content;
  private final ContentKind contentKind;
  private final Dir contentDir;


  /**
   * Creates a SanitizedContent object.
   *
   * Package-private. Ideally, if one is available, you should use an existing serializer,
   * sanitizer, verifier, or extractor that returns SanitizedContent objects. Or, you can use
   * UnsafeSanitizedContentOrdainer in this package, to make it clear that creating these objects
   * from arbitrary content is risky unless you absolutely know the input is safe. See the
   * comments in UnsafeSanitizedContentOrdainer for more recommendations.
   *
   * @param content A string of valid content with the given content kind.
   * @param contentKind Describes the kind of string that content is.
   * @param contentDir The content's direction; null if unknown and thus to be estimated when
   *     necessary.
   */
  SanitizedContent(String content, ContentKind contentKind, @Nullable Dir contentDir) {
    this.content = content;
    this.contentKind = contentKind;
    this.contentDir = contentDir;
  }


  /**
   * Returns a string of valid content with kind {@link #getContentKind}.
   */
  public String getContent() {
    return content;
  }


  /**
   * Returns the kind of content.
   */
  public ContentKind getContentKind() {
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


  @Deprecated
  @Override
  public boolean toBoolean() {
    return content.length() != 0;  // Consistent with StringData
  }

  @Override public void render(Appendable appendable) throws IOException {
    appendable.append(content);
  }

  @Override
  public String toString() {
    return content;
  }


  /**
   * Returns the string value.
   *
   * In contexts where a string value is required, SanitizedCOntent is permitted.
   */
  @Override
  public String stringValue() {
    return content;
  }


  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof SanitizedContent &&
        this.contentKind == ((SanitizedContent) other).contentKind &&
        this.contentDir == ((SanitizedContent) other).contentDir &&
        this.content.equals(((SanitizedContent) other).content);
  }


  @Override
  public int hashCode() {
    return content.hashCode() + 31 * contentKind.hashCode();
  }
}
