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

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;


/**
 * A chunk of sanitized content of a known kind, e.g. the output of an HTML sanitizer.
 *
 * @author Mike Samuel
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

    /**
     * A sequence of code units that can appear between quotes (either single or double) in a JS
     * program without causing a parse error, and without causing any side effects.
     * <p>
     * The content should not contain unescaped quotes, newlines, or anything else that would
     * cause parsing to fail or to cause a JS parser to finish the string it is parsing inside
     * the content.
     * <p>
     * The content must also not end inside an escape sequence ; no partial octal escape sequences
     * or odd number of '{@code \}'s at the end.
     */
    JS_STR_CHARS,

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
   */
  SanitizedContent(String content, ContentKind contentKind) {
    this.content = content;
    this.contentKind = contentKind;
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


  @Override
  public boolean toBoolean() {
    return content.length() != 0;  // Consistent with StringData
  }


  @Override
  public String toString() {
    return content;
  }


  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof SanitizedContent &&
        this.contentKind == ((SanitizedContent) other).contentKind &&
        this.content.equals(((SanitizedContent) other).content);
  }


  @Override
  public int hashCode() {
    return content.hashCode() + 31 * contentKind.hashCode();
  }

}
