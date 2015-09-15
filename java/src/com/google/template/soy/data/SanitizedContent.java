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

import com.google.template.soy.data.internal.RenderableThunk;
import com.google.template.soy.data.restricted.SoyString;

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
public abstract class SanitizedContent extends SoyData implements SoyString {
  /**
   * Creates a SanitizedContent object.
   *
   * <p>Package-private. Ideally, if one is available, you should use an existing serializer,
   * sanitizer, verifier, or extractor that returns SanitizedContent objects. Or, you can use
   * UnsafeSanitizedContentOrdainer in this package, to make it clear that creating these objects
   * from arbitrary content is risky unless you absolutely know the input is safe. See the
   * comments in UnsafeSanitizedContentOrdainer for more recommendations.
   *
   * @param content A string of valid content with the given content kind.
   * @param kind Describes the kind of string that content is.
   * @param dir The content's direction; null if unknown and thus to be estimated when
   *     necessary.
   */
  static SanitizedContent create(String content, ContentKind kind, @Nullable Dir dir) {
    return new ConstantContent(content, kind, dir);
  }

  /**
   * Creates a lazy SanitizedContent object.
   *
   * <p>Package-private. This is meant exclusively for use by the rendering infrastructure
   *
   * @param thunk A lazy thunk that renders the valid content.
   * @param kind Describes the kind of string that content is.
   * @param dir The content's direction; null if unknown and thus to be estimated when
   *     necessary.
   */
  static SanitizedContent createLazy(RenderableThunk thunk, ContentKind kind, @Nullable Dir dir) {
    return new LazyContent(thunk, kind, dir);
  }

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

    /** Resource URIs used in scrips sources, stylesheets, etc which are not in attacker control. */
    TRUSTED_RESOURCE_URI,

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
     *
     * <p>In the soy type system, {@code TEXT} is equivalent to the string type.
     */
    TEXT
    ;

  }

  private final ContentKind contentKind;
  private final Dir contentDir;


  /**
   * Private constructor to limit subclasses to this file.  This is important to ensure that all
   * implementations of this class are fully vetted by security.
   */
  private SanitizedContent(ContentKind contentKind, @Nullable Dir contentDir) {
    this.contentKind = contentKind;
    this.contentDir = contentDir;
  }


  /**
   * Returns a string of valid content with kind {@link #getContentKind}.
   */
  public abstract String getContent();


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
    return getContent().length() != 0;  // Consistent with StringData
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
    return other instanceof SanitizedContent
        && this.contentKind == ((SanitizedContent) other).contentKind
        && this.contentDir == ((SanitizedContent) other).contentDir
        && this.getContent().equals(((SanitizedContent) other).getContent());
  }


  @Override
  public int hashCode() {
    return getContent().hashCode() + 31 * contentKind.hashCode();
  }

  private static final class ConstantContent extends SanitizedContent {
    final String content;

    ConstantContent(String content, ContentKind contentKind, @Nullable Dir contentDir) {
      super(contentKind, contentDir);
      this.content = content;
    }

    @Override
    public void render(Appendable appendable) throws IOException {
      appendable.append(content);
    }

    @Override
    public String getContent() {
      return content;
    }
  }

  private static final class LazyContent extends SanitizedContent {
    // N.B. This is nearly identical to StringData.LazyString.  When changing this you
    // probably need to change that also.

    final RenderableThunk thunk;

    LazyContent(RenderableThunk thunk, ContentKind contentKind, @Nullable Dir contentDir) {
      super(contentKind, contentDir);
      this.thunk = thunk;
    }

    @Override
    public void render(Appendable appendable) throws IOException {
      thunk.render(appendable);
    }

    @Override
    public String getContent() {
      return thunk.renderAsString();
    }
  }
}
