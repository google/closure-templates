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
    HTML_ATTRIBUTE,
    ;

  }


  private final String content;
  private final ContentKind contentKind;


  /**
   * @param content A string of valid content with the given content kind.
   * @param contentKind Describes the kind of string that content is.
   */
  public SanitizedContent(String content, ContentKind contentKind) {
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
