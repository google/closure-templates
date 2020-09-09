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
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.template.soy.base.internal.SanitizedContentKind;
import java.util.EnumSet;
import java.util.Set;

/**
 * Utility methods for values of the {@code kind} attribute on {@code {param}} nodes.
 *
 * <p>This attribute specifies the {@link SanitizedContentKind} that the content of the node will
 * evaluate to, and in turn determines the HTML context to use when contextually autoescaping the
 * node's content (see {@link
 * com.google.template.soy.parsepasses.contextautoesc.Context#forContentKind(SanitizedContentKind)}).
 *
 * <p>TODO(lukes): delete this class, move the backend specific functionality into the backends
 *
 */
public final class NodeContentKinds {

  private static final ImmutableMap<SanitizedContentKind, String> KIND_TO_JS_CTOR_NAME =
      ImmutableMap.<SanitizedContentKind, String>builder()
          .put(SanitizedContentKind.HTML, "goog.soy.data.SanitizedHtml")
          .put(SanitizedContentKind.HTML_ELEMENT, "goog.soy.data.SanitizedHtml")
          .put(SanitizedContentKind.ATTRIBUTES, "goog.soy.data.SanitizedHtmlAttribute")
          .put(SanitizedContentKind.JS, "goog.soy.data.SanitizedJs")
          .put(SanitizedContentKind.URI, "goog.soy.data.SanitizedUri")
          .put(SanitizedContentKind.CSS, "goog.soy.data.SanitizedCss")
          .put(
              SanitizedContentKind.TRUSTED_RESOURCE_URI,
              "goog.soy.data.SanitizedTrustedResourceUri")
          .put(SanitizedContentKind.TEXT, "string")
          .build();

  /** The Javascript sanitized ordainer functions. */
  private static final ImmutableMap<SanitizedContentKind, String> KIND_TO_JS_ORDAINER_NAME =
      ImmutableMap.<SanitizedContentKind, String>builder()
          .put(SanitizedContentKind.HTML, "soydata.VERY_UNSAFE.ordainSanitizedHtml")
          .put(SanitizedContentKind.HTML_ELEMENT, "soydata.VERY_UNSAFE.ordainSanitizedHtml")
          .put(SanitizedContentKind.ATTRIBUTES, "soydata.VERY_UNSAFE.ordainSanitizedHtmlAttribute")
          .put(SanitizedContentKind.JS, "soydata.VERY_UNSAFE.ordainSanitizedJs")
          .put(SanitizedContentKind.URI, "soydata.VERY_UNSAFE.ordainSanitizedUri")
          .put(SanitizedContentKind.CSS, "soydata.VERY_UNSAFE.ordainSanitizedCss")
          .put(
              SanitizedContentKind.TRUSTED_RESOURCE_URI,
              "soydata.VERY_UNSAFE.ordainSanitizedTrustedResourceUri")
          .put(SanitizedContentKind.TEXT, "")
          .build();

  /**
   * The specialized ordainers used for param and let blocks. These ones do not wrap if the input is
   * empty string, in order that empty strings can still be truth-tested. This is an incomplete
   * solution to the truth testing problem, but dramatically simplifies migration.
   */
  private static final ImmutableMap<SanitizedContentKind, String>
      KIND_TO_JS_ORDAINER_NAME_FOR_INTERNAL_BLOCKS =
          ImmutableMap.<SanitizedContentKind, String>builder()
              .put(
                  SanitizedContentKind.HTML,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks")
              .put(
                  SanitizedContentKind.HTML_ELEMENT,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedHtmlForInternalBlocks")
              .put(
                  SanitizedContentKind.ATTRIBUTES,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedAttributesForInternalBlocks")
              .put(
                  SanitizedContentKind.JS,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedJsForInternalBlocks")
              .put(
                  SanitizedContentKind.URI,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedUriForInternalBlocks")
              .put(
                  SanitizedContentKind.CSS,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedCssForInternalBlocks")
              .put(
                  SanitizedContentKind.TRUSTED_RESOURCE_URI,
                  "soydata.VERY_UNSAFE.$$ordainSanitizedTrustedResourceUriForInternalBlocks")
              .put(SanitizedContentKind.TEXT, "")
              .build();

  /** The JavaScript method to unpack a safe proto to sanitized object. */
  private static final ImmutableMap<String, String> PROTO_TO_JS_UNPACK_FN =
      ImmutableMap.<String, String>builder()
          .put(SafeHtmlProto.getDescriptor().getFullName(), "soydata.unpackProtoToSanitizedHtml")
          .put(SafeScriptProto.getDescriptor().getFullName(), "soydata.unpackProtoToSanitizedJs")
          .put(SafeUrlProto.getDescriptor().getFullName(), "soydata.unpackProtoToSanitizedUri")
          .put(SafeStyleProto.getDescriptor().getFullName(), "soydata.unpackProtoToSanitizedCss")
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              "soydata.unpackProtoToSanitizedCss")
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              "soydata.unpackProtoToSanitizedTrustedResourceUri")
          .build();

  /** The Python sanitized classes. */
  private static final ImmutableMap<SanitizedContentKind, String> KIND_TO_PY_SANITIZED_NAME =
      ImmutableMap.<SanitizedContentKind, String>builder()
          .put(SanitizedContentKind.HTML_ELEMENT, "sanitize.SanitizedHtml")
          .put(SanitizedContentKind.HTML, "sanitize.SanitizedHtml")
          .put(SanitizedContentKind.ATTRIBUTES, "sanitize.SanitizedHtmlAttribute")
          .put(SanitizedContentKind.JS, "sanitize.SanitizedJs")
          .put(SanitizedContentKind.URI, "sanitize.SanitizedUri")
          .put(SanitizedContentKind.CSS, "sanitize.SanitizedCss")
          .put(SanitizedContentKind.TRUSTED_RESOURCE_URI, "sanitize.SanitizedTrustedResourceUri")
          .put(SanitizedContentKind.TEXT, "")
          .build();

  static {
    Set<SanitizedContentKind> allKinds = EnumSet.allOf(SanitizedContentKind.class);
    if (!KIND_TO_JS_CTOR_NAME.keySet().containsAll(allKinds)) {
      throw new AssertionError("Not all ContentKind enums have a JS constructor");
    }

    // These are the content kinds that actually have a native Soy language representation.
    if (!KIND_TO_JS_ORDAINER_NAME.keySet().containsAll(allKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a JS ordainer");
    }
    if (!KIND_TO_JS_ORDAINER_NAME_FOR_INTERNAL_BLOCKS.keySet().containsAll(allKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a JS ordainer");
    }
    if (!KIND_TO_PY_SANITIZED_NAME.keySet().containsAll(allKinds)) {
      throw new AssertionError("Not all Soy-accessible ContentKind enums have a Python sanitizer");
    }
  }

  /**
   * Given a {@link SanitizedContentKind}, returns the corresponding JS SanitizedContent
   * constructor.
   *
   * <p>These constructors may not be directly instantiated -- instead, an "ordainer" function is
   * used. This is so that Soy developers have to jump through extra hoops and carefully think about
   * the implications of directly calling the SanitizedContent constructors.
   */
  public static String toJsSanitizedContentCtorName(SanitizedContentKind contentKind) {
    // goog.soy.data.SanitizedHtml types etc are defined in Closure.
    return Preconditions.checkNotNull(KIND_TO_JS_CTOR_NAME.get(contentKind));
  }

  /**
   * Given a {@link SanitizedContentKind}, returns the corresponding JS SanitizedContent factory
   * function.
   */
  public static String toJsSanitizedContentOrdainer(SanitizedContentKind contentKind) {
    // soydata.VERY_UNSAFE.ordainSanitizedHtml etc are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(KIND_TO_JS_ORDAINER_NAME.get(contentKind));
  }

  /**
   * Returns the ordainer function for param and let blocks, which behaves subtly differently than
   * the normal ordainers to ease migration.
   */
  public static String toJsSanitizedContentOrdainerForInternalBlocks(
      SanitizedContentKind contentKind) {
    // Functions are defined in soyutils{,_usegoog}.js.
    return Preconditions.checkNotNull(
        KIND_TO_JS_ORDAINER_NAME_FOR_INTERNAL_BLOCKS.get(contentKind));
  }

  /**
   * Returns the namespace to {@code goog.require} to access the ordainer functions provided by
   * {@link #toJsSanitizedContentOrdainer(SanitizedContentKind)} and {@link
   * #toJsSanitizedContentOrdainerForInternalBlocks(SanitizedContentKind)}.
   */
  public static String getJsImportForOrdainersFunctions(SanitizedContentKind contentKind) {
    return "soydata.VERY_UNSAFE";
  }

  /** Returns the unpack function for converting safe protos to JS SanitizedContent. */
  public static String toJsUnpackFunction(Descriptor protoDescriptor) {
    return Preconditions.checkNotNull(PROTO_TO_JS_UNPACK_FN.get(protoDescriptor.getFullName()));
  }

  /** Given a {@link SanitizedContentKind}, returns the corresponding Python sanitize class. */
  public static String toPySanitizedContentOrdainer(SanitizedContentKind contentKind) {
    // Sanitization classes are defined in sanitize.py.
    return Preconditions.checkNotNull(
        KIND_TO_PY_SANITIZED_NAME.get(contentKind),
        "expected to have an ordainer for %s",
        contentKind);
  }

  // Prevent instantiation.
  private NodeContentKinds() {}
}
