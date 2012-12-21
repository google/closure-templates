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
 * @author Christoph Kern
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
              .build();

  private static final ImmutableMap<ContentKind, String> KIND_TO_JS_TYPE_NAME =
      ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "soydata.SanitizedHtml")
          .put(ContentKind.ATTRIBUTES, "soydata.SanitizedHtmlAttribute")
          .put(ContentKind.JS, "soydata.SanitizedJs")
          .put(ContentKind.JS_STR_CHARS, "soydata.SanitizedJsStrChars")
          .put(ContentKind.URI, "soydata.SanitizedUri")
          .put(ContentKind.CSS, "soydata.SanitizedCss")
          // NOTE: Text intentionally doesn't follow the convention. Note that we don't just
          // convert them to a string, because the UnsanitizedText wrapper helps prevent the
          // content from getting used elsewhere in a noAutoescape.
          .put(ContentKind.TEXT, "soydata.UnsanitizedText")
          .build();

  private static final ImmutableMap<ContentKind, String> KIND_TO_JS_ORDAINER_NAME =
      ImmutableMap.<ContentKind, String>builder()
          .put(ContentKind.HTML, "soydata.VERY_UNSAFE.ordainSanitizedHtml")
          .put(ContentKind.ATTRIBUTES, "soydata.VERY_UNSAFE.ordainSanitizedHtmlAttribute")
          .put(ContentKind.JS, "soydata.VERY_UNSAFE.ordainSanitizedJs")
          .put(ContentKind.JS_STR_CHARS, "soydata.VERY_UNSAFE.ordainSanitizedJsStrChars")
          .put(ContentKind.URI, "soydata.VERY_UNSAFE.ordainSanitizedUri")
          .put(ContentKind.CSS, "soydata.VERY_UNSAFE.ordainSanitizedCss")
          .put(ContentKind.TEXT, "soydata.markUnsanitizedText")
          .build();

  static {
    if (!KIND_TO_JS_TYPE_NAME.keySet().containsAll(EnumSet.allOf(ContentKind.class))) {
      throw new AssertionError("Not all ContentKind enums have a JS constructor");
    }
    if (!KIND_TO_JS_ORDAINER_NAME.keySet().containsAll(EnumSet.allOf(ContentKind.class))) {
      throw new AssertionError("Not all ContentKind enums have a JS ordainer");
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
  public static String toJsSanitizedContentTypeName(
      ContentKind contentKind) {
    // soydata.SanitizedHtml types etc are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(KIND_TO_JS_TYPE_NAME.get(contentKind));
  }


  /**
   * Given a {@link ContentKind}, returns the corresponding JS SanitizedContent factory function.
   */
  public static String toJsSanitizedContentOrdainer(
      ContentKind contentKind) {
    // soydata.VERY_UNSAFE.ordainSanitizedHtml etc are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(KIND_TO_JS_ORDAINER_NAME.get(contentKind));
  }


  // Prevent instantiation.
  private NodeContentKinds() { }
}
