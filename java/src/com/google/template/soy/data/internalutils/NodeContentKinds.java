/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.data.internalutils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.data.SanitizedContent.ContentKind;

import java.util.EnumSet;
import java.util.Set;


/**
 * Utility methods for values of the {@code kind} attribute on {@code {param}} nodes.
 *
 * <p>
 * This attribute specifies the {@link ContentKind} that the content of the node will evaluate to,
 * and in turn determines the HTML context to use when contextually autoescaping the node's content
 * (see
 * {@link com.google.template.soy.parsepasses.contextautoesc.Context#forContentKind(ContentKind)}).
 *
 */
public class NodeContentKinds {

  private static final ImmutableBiMap<String, ContentKind>
      KIND_ATTRIBUTE_TO_SANITIZED_CONTENT_KIND_BI_MAP =
          ImmutableBiMap.<String, ContentKind>builder()
              .put("attributes", ContentKind.ATTRIBUTES)
              .put("css", ContentKind.CSS)
              .put("html", ContentKind.HTML)
              .put("js", ContentKind.JS)
              .put("text", ContentKind.TEXT)
              .put("uri", ContentKind.URI)
              .put("trusted_resource_uri", ContentKind.TRUSTED_RESOURCE_URI)
              .build();

  private static final ImmutableMap<ContentKind, String> KIND_TO_JS_CTOR_NAME =
      ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "soydata.SanitizedHtml")
          .put(ContentKind.ATTRIBUTES, "soydata.SanitizedHtmlAttribute")
          .put(ContentKind.JS, "soydata.SanitizedJs")
          .put(ContentKind.URI, "soydata.SanitizedUri")
          .put(ContentKind.CSS, "soydata.SanitizedCss")
          .put(ContentKind.TRUSTED_RESOURCE_URI, "soydata.SanitizedTrustedResourceUri")
          // NOTE: Text intentionally doesn't follow the convention. Note that we don't just
          // convert them to a string, because the UnsanitizedText wrapper helps prevent the
          // content from getting used elsewhere in a noAutoescape.
          .put(ContentKind.TEXT, "soydata.UnsanitizedText")
          .build();

  private static final ImmutableMap<ContentKind, String> IDOM_KIND_TO_JS_CTOR_NAME =
      ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "Function")
          .put(ContentKind.ATTRIBUTES, "Function")
          .put(ContentKind.JS, "soydata.SanitizedJs")
          .put(ContentKind.URI, "soydata.SanitizedUri")
          .put(ContentKind.CSS, "soydata.SanitizedCss")
          .put(ContentKind.TRUSTED_RESOURCE_URI, "soydata.SanitizedTrustedResourceUri")
          // NOTE: Text intentionally doesn't follow the convention. Note that we don't just
          // convert them to a string, because the UnsanitizedText wrapper helps prevent the
          // content from getting used elsewhere in a noAutoescape.
          .put(ContentKind.TEXT, "soydata.UnsanitizedText")
          .build();

  /** The Javascript sanitized ordainer functions. */
  private static final ImmutableMap<ContentKind, String> KIND_TO_JS_ORDAINER_NAME =
      ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "soydata.VERY_UNSAFE.ordainSanitizedHtml")
          .put(ContentKind.ATTRIBUTES, "soydata.VERY_UNSAFE.ordainSanitizedHtmlAttribute")
          .put(ContentKind.JS, "soydata.VERY_UNSAFE.ordainSanitizedJs")
          .put(ContentKind.URI, "soydata.VERY_UNSAFE.ordainSanitizedUri")
          .put(ContentKind.CSS, "soydata.VERY_UNSAFE.ordainSanitizedCss")
          .put(ContentKind.TRUSTED_RESOURCE_URI,
              "soydata.VERY_UNSAFE.ordainSanitizedTrustedResourceUri")
          .put(ContentKind.TEXT, "soydata.markUnsanitizedText")
          .build();

  /**
   * The specialized ordainers used for param and let blocks. These ones do not wrap if the input
   * is empty string, in order that empty strings can still be truth-tested. This is an incomplete
   * solution to the truth testing problem, but dramatically simplifies migration.
   */
  private static final ImmutableMap<ContentKind, String>
      KIND_TO_JS_ORDAINER_NAME_FOR_INTERNAL_BLOCKS = ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks")
          .put(ContentKind.ATTRIBUTES,
              "soydata.VERY_UNSAFE.$$ordainSanitizedAttributesForInternalBlocks")
          .put(ContentKind.JS, "soydata.VERY_UNSAFE.$$ordainSanitizedJsForInternalBlocks")
          .put(ContentKind.URI, "soydata.VERY_UNSAFE.$$ordainSanitizedUriForInternalBlocks")
          .put(ContentKind.CSS, "soydata.VERY_UNSAFE.$$ordainSanitizedCssForInternalBlocks")
          .put(ContentKind.TRUSTED_RESOURCE_URI,
              "soydata.VERY_UNSAFE.$$ordainSanitizedTrustedResourceUriForInternalBlocks")
          .put(ContentKind.TEXT, "soydata.$$markUnsanitizedTextForInternalBlocks")
          .build();

  /** The Python sanitized classes. */
  private static final ImmutableMap<ContentKind, String> KIND_TO_PY_SANITIZED_NAME =
      ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "sanitize.SanitizedHtml")
          .put(ContentKind.ATTRIBUTES, "sanitize.SanitizedHtmlAttribute")
          .put(ContentKind.JS, "sanitize.SanitizedJs")
          .put(ContentKind.URI, "sanitize.SanitizedUri")
          .put(ContentKind.CSS, "sanitize.SanitizedCss")
          .put(ContentKind.TRUSTED_RESOURCE_URI, "sanitize.SanitizedTrustedResourceUri")
          .put(ContentKind.TEXT, "sanitize.UnsanitizedText")
          .build();

  static {
    if (!KIND_TO_JS_CTOR_NAME.keySet().containsAll(EnumSet.allOf(ContentKind.class))) {
      throw new AssertionError("Not all ContentKind enums have a JS constructor");
    }

    if (!IDOM_KIND_TO_JS_CTOR_NAME.keySet().containsAll(EnumSet.allOf(ContentKind.class))) {
      throw new AssertionError(
          "Not all ContentKind enums have a Incremental DOM JS constructor");
    }
    // These are the content kinds that actually have a native Soy language representation.
    Set<ContentKind> soyContentKinds = KIND_ATTRIBUTE_TO_SANITIZED_CONTENT_KIND_BI_MAP.values();
    if (!KIND_TO_JS_ORDAINER_NAME.keySet().containsAll(soyContentKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a JS ordainer");
    }
    if (!KIND_TO_JS_ORDAINER_NAME_FOR_INTERNAL_BLOCKS.keySet().containsAll(soyContentKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a JS ordainer");
    }
    if (!KIND_TO_PY_SANITIZED_NAME.keySet().containsAll(soyContentKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a Python sanitizer");
    }
  }


  /**
   * Given an allowed value of the attribute, returns the corresponding {@link ContentKind}).
   */
  public static ContentKind forAttributeValue(String attributeValue) {
    return KIND_ATTRIBUTE_TO_SANITIZED_CONTENT_KIND_BI_MAP.get(attributeValue);
  }


  /**
   * Given a ContentKind, returns the attribute string.
   */
  public static String toAttributeValue(ContentKind kind) {
    return KIND_ATTRIBUTE_TO_SANITIZED_CONTENT_KIND_BI_MAP.inverse().get(kind);
  }


  /** The set of permitted values of the {@code kind} attribute. */
  public static Set<String> getAttributeValues() {
    return KIND_ATTRIBUTE_TO_SANITIZED_CONTENT_KIND_BI_MAP.keySet();
  }


  /**
   * Given a {@link ContentKind}, returns the corresponding JS SanitizedContent constructor.
   *
   * These constructors may not be directly instantiated -- instead, an "ordainer" function is
   * used. This is so that Soy developers have to jump through extra hoops and carefully think
   * about the implications of directly calling the SanitizedContent constructors.
   */
  public static String toJsSanitizedContentCtorName(
      ContentKind contentKind) {
    // soydata.SanitizedHtml types etc are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(KIND_TO_JS_CTOR_NAME.get(contentKind));
  }

  /**
   * Given a {@link ContentKind}, returns the corresponding JS SanitizedContent constructor.
   *
   * This functions similarly to {@link #toJsSanitizedContentCtorName}, but
   * replaces HTML and Attribute types with Function instead of their sanitized types since
   * in Incremental DOM, the HTML types are actually functions that are invoked.
   */
  public static String toIDOMSanitizedContentCtorName(
      ContentKind contentKind) {
    return Preconditions.checkNotNull(IDOM_KIND_TO_JS_CTOR_NAME.get(contentKind));
  }


  /**
   * Given a {@link ContentKind}, returns the corresponding JS SanitizedContent factory function.
   */
  public static String toJsSanitizedContentOrdainer(
      ContentKind contentKind) {
    // soydata.VERY_UNSAFE.ordainSanitizedHtml etc are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(KIND_TO_JS_ORDAINER_NAME.get(contentKind));
  }


  /**
   * Returns the ordainer function for param and let blocks, which behaves subtly differently than
   * the normal ordainers to ease migration.
   */
  public static String toJsSanitizedContentOrdainerForInternalBlocks(
      ContentKind contentKind) {
    // Functions are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(
        KIND_TO_JS_ORDAINER_NAME_FOR_INTERNAL_BLOCKS.get(contentKind));
  }


  /**
   * Given a {@link ContentKind}, returns the corresponding Python sanitize class.
   */
  public static String toPySanitizedContentOrdainer(ContentKind contentKind) {
    // Sanitization classes are defined in sanitize.py.
    return Preconditions.checkNotNull(KIND_TO_PY_SANITIZED_NAME.get(contentKind));
  }


  // Prevent instantiation.
  private NodeContentKinds() { }
}
