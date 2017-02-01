/*
 * Copyright 2013 Google Inc.
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

package com.google.template.soy.types.primitive;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.types.SoyType;

/**
 * Implementation of types for sanitized strings, that is strings that are produced by templates
 * having a "kind" attribute. All of these types may be implicitly coerced into strings.
 *
 */
public abstract class SanitizedType extends PrimitiveType {

  /** Returns the content kind for this type. Guaranteed to be non-null and also not TEXT. */
  public abstract ContentKind getContentKind();

  @Override
  public boolean isInstance(SoyValue value) {
    return value instanceof SanitizedContent
        && ((SanitizedContent) value).getContentKind() == getContentKind();
  }

  @Override
  public String toString() {
    return getContentKind().toString().toLowerCase();
  }

  /**
   * Given a content kind, return the corresponding soy type.
   *
   * <p>For {@link ContentKind#TEXT} this returns {@link StringType}, for all other types it is a
   * {@link SanitizedType}.
   */
  public static SoyType getTypeForContentKind(ContentKind contentKind) {
    switch (contentKind) {
      case ATTRIBUTES:
        return AttributesType.getInstance();

      case CSS:
        return CssType.getInstance();

      case HTML:
        return HtmlType.getInstance();

      case JS:
        return JsType.getInstance();

      case URI:
        return UriType.getInstance();

      case TRUSTED_RESOURCE_URI:
        return TrustedResourceUriType.getInstance();

      case TEXT:
        return StringType.getInstance();

      default:
        throw new AssertionError("unexpected content kind " + contentKind);
    }
  }

  // -----------------------------------------------------------------------------------------------
  // Concrete Sanitized Types

  /** Type produced by templates whose kind is "html". */
  public static final class HtmlType extends SanitizedType {

    private static final HtmlType INSTANCE = new HtmlType();

    // Not constructible - use getInstance().
    private HtmlType() {}

    @Override
    public Kind getKind() {
      return Kind.HTML;
    }

    @Override
    public ContentKind getContentKind() {
      return ContentKind.HTML;
    }

    /** Return the single instance of this type. */
    public static HtmlType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "attributes". */
  public static final class AttributesType extends SanitizedType {

    private static final AttributesType INSTANCE = new AttributesType();

    // Not constructible - use getInstance().
    private AttributesType() {}

    @Override
    public Kind getKind() {
      return Kind.ATTRIBUTES;
    }

    @Override
    public ContentKind getContentKind() {
      return ContentKind.ATTRIBUTES;
    }

    /** Return the single instance of this type. */
    public static AttributesType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "uri". */
  public static final class UriType extends SanitizedType {

    private static final UriType INSTANCE = new UriType();

    // Not constructible - use getInstance().
    private UriType() {}

    @Override
    public Kind getKind() {
      return Kind.URI;
    }

    @Override
    public ContentKind getContentKind() {
      return ContentKind.URI;
    }

    /** Return the single instance of this type. */
    public static UriType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "trustedResourceUri". */
  public static final class TrustedResourceUriType extends SanitizedType {

    private static final TrustedResourceUriType INSTANCE = new TrustedResourceUriType();

    // Not constructible - use getInstance().
    private TrustedResourceUriType() {}

    @Override
    public Kind getKind() {
      return Kind.TRUSTED_RESOURCE_URI;
    }

    @Override
    public ContentKind getContentKind() {
      return ContentKind.TRUSTED_RESOURCE_URI;
    }

    /** Return the single instance of this type. */
    public static TrustedResourceUriType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "css". */
  public static final class CssType extends SanitizedType {

    private static final CssType INSTANCE = new CssType();

    // Not constructible - use getInstance().
    private CssType() {}

    @Override
    public Kind getKind() {
      return Kind.CSS;
    }

    @Override
    public ContentKind getContentKind() {
      return ContentKind.CSS;
    }

    /** Return the single instance of this type. */
    public static CssType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "js". */
  public static final class JsType extends SanitizedType {

    private static final JsType INSTANCE = new JsType();

    // Not constructible - use getInstance().
    private JsType() {}

    @Override
    public Kind getKind() {
      return Kind.JS;
    }

    @Override
    public ContentKind getContentKind() {
      return ContentKind.JS;
    }

    /** Return the single instance of this type. */
    public static JsType getInstance() {
      return INSTANCE;
    }
  }
}
