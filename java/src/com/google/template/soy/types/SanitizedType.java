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

package com.google.template.soy.types;

import com.google.common.base.Ascii;
import com.google.template.soy.base.internal.SanitizedContentKind;
import com.google.template.soy.soytree.SoyTypeP;

/**
 * Implementation of types for sanitized strings, that is strings that are produced by templates
 * having a "kind" attribute. All of these types may be implicitly coerced into strings.
 *
 */
public abstract class SanitizedType extends PrimitiveType {

  /** Returns the content kind for this type. Guaranteed to be non-null and also not TEXT. */
  public abstract SanitizedContentKind getContentKind();

  @Override
  public String toString() {
    return Ascii.toLowerCase(getContentKind().toString());
  }

  /**
   * Given a content kind, return the corresponding soy type.
   *
   * <p>For {@link SanitizedContentKind#TEXT} this returns {@link StringType}, for all other types
   * it is a {@link SanitizedType}.
   */
  public static SoyType getTypeForContentKind(SanitizedContentKind contentKind) {
    switch (contentKind) {
      case ATTRIBUTES:
        return AttributesType.getInstance();

      case CSS:
        return StyleType.getInstance();

      case HTML_ELEMENT:
        return ElementType.getInstance();

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
    }
    throw new AssertionError(contentKind);
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
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.HTML;
    }

    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.HTML);
    }

    /** Return the single instance of this type. */
    public static HtmlType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "html<?>". */
  public static final class ElementType extends SanitizedType {

    private static final ElementType INSTANCE = new ElementType();

    // Not constructible - use getInstance().
    private ElementType() {}

    @Override
    public Kind getKind() {
      return Kind.ELEMENT;
    }

    @Override
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.HTML_ELEMENT;
    }

    /** TODO(tomnguyen) Change this to ELEMENT */
    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.HTML);
    }

    /** Return the single instance of this type. */
    public static ElementType getInstance() {
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
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.ATTRIBUTES;
    }

    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.ATTRIBUTES);
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
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.URI;
    }

    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.URI);
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
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.TRUSTED_RESOURCE_URI;
    }

    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.TRUSTED_RESOURCE_URI);
    }

    /** Return the single instance of this type. */
    public static TrustedResourceUriType getInstance() {
      return INSTANCE;
    }
  }

  /** Type produced by templates whose kind is "style". */
  public static final class StyleType extends SanitizedType {

    private static final StyleType INSTANCE = new StyleType();

    // Not constructible - use getInstance().
    private StyleType() {}

    @Override
    public Kind getKind() {
      return Kind.CSS;
    }

    @Override
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.CSS;
    }

    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.CSS);
    }

    /** Return the single instance of this type. */
    public static StyleType getInstance() {
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
    public SanitizedContentKind getContentKind() {
      return SanitizedContentKind.JS;
    }

    @Override
    void doToProto(SoyTypeP.Builder builder) {
      builder.setPrimitive(SoyTypeP.PrimitiveTypeP.JS);
    }

    /** Return the single instance of this type. */
    public static JsType getInstance() {
      return INSTANCE;
    }
  }
}
