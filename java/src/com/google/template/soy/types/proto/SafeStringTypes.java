/*
 * Copyright 2016 Google Inc.
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

package com.google.template.soy.types.proto;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScript;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyle;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheet;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrl;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.protobuf.Message;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContents;
import com.google.template.soy.data.SoyValueProvider;
import com.google.template.soy.types.primitive.SanitizedType;
import java.lang.reflect.Modifier;
import javax.annotation.Nullable;

/** A relation between safe string types and sanitized content types. */
final class SafeStringTypes {

  private SafeStringTypes() {
    // Not instantiable.
  }

  /** Maps protobuf message descriptor names to sanitized types. */
  static final ImmutableMap<String, SanitizedType> SAFE_PROTO_TO_SANITIZED_TYPE =
      ImmutableMap.<String, SanitizedType>builder()
          .put(SafeHtmlProto.getDescriptor().getFullName(), SanitizedType.HtmlType.getInstance())
          .put(SafeScriptProto.getDescriptor().getFullName(), SanitizedType.JsType.getInstance())
          .put(SafeStyleProto.getDescriptor().getFullName(), SanitizedType.CssType.getInstance())
          .put(
              SafeStyleSheetProto.getDescriptor().getFullName(),
              SanitizedType.CssType.getInstance())
          .put(SafeUrlProto.getDescriptor().getFullName(), SanitizedType.UriType.getInstance())
          .put(
              TrustedResourceUrlProto.getDescriptor().getFullName(),
              SanitizedType.TrustedResourceUriType.getInstance())
          .build();

  private interface Converter extends Function<Object, SoyValueProvider> {}

  private static final ImmutableMap<Class<?>, Converter> TO_SOY_VALUE =
      ImmutableMap.<Class<?>, Converter>builder()
          .put(
              SafeHtml.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeHtml((SafeHtml) obj);
                }
              })
          .put(
              SafeHtmlProto.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeHtmlProto((SafeHtmlProto) obj);
                }
              })
          .put(
              SafeScript.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeScript((SafeScript) obj);
                }
              })
          .put(
              SafeScriptProto.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeScriptProto((SafeScriptProto) obj);
                }
              })
          .put(
              SafeStyle.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeStyle((SafeStyle) obj);
                }
              })
          .put(
              SafeStyleProto.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeStyleProto((SafeStyleProto) obj);
                }
              })
          .put(
              SafeStyleSheet.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeStyleSheet((SafeStyleSheet) obj);
                }
              })
          .put(
              SafeStyleSheetProto.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeStyleSheetProto((SafeStyleSheetProto) obj);
                }
              })
          .put(
              SafeUrl.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeUrl((SafeUrl) obj);
                }
              })
          .put(
              SafeUrlProto.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromSafeUrlProto((SafeUrlProto) obj);
                }
              })
          .put(
              TrustedResourceUrl.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromTrustedResourceUrl((TrustedResourceUrl) obj);
                }
              })
          .put(
              TrustedResourceUrlProto.class,
              new Converter() {
                @Override
                public SoyValueProvider apply(Object obj) {
                  return SanitizedContents.fromTrustedResourceUrlProto(
                      (TrustedResourceUrlProto) obj);
                }
              })
          .build();

  static {
    // If our class lookup table includes classes that can be extended,
    // then the .get(...) call below may spuriously return null.
    for (Class<?> cl : TO_SOY_VALUE.keySet()) {
      if (!Modifier.isFinal(cl.getModifiers())) {
        throw new AssertionError(
            cl + " is not final so there might be subclasses that aren't keyed in this map");
      }
    }
  }

  @Nullable
  static SoyValueProvider convertToSoyValue(Object obj) {
    if (obj == null) {
      return null;
    }
    Converter converter = TO_SOY_VALUE.get(obj.getClass());
    if (converter != null) {
      return converter.apply(obj);
    }
    return null;
  }

  static Message convertToProto(SanitizedContent value, String protoName) {
    switch (value.getContentKind()) {
      case HTML:
        return value.toSafeHtmlProto();
      case ATTRIBUTES:
        throw new IllegalStateException("ContentKind.ATTRIBUTES is incompatible with " + protoName);
      case JS:
        return value.toSafeScriptProto();
      case CSS:
        // We use ContentKind.CSS for both SafeStyleProto (list of property: value pairs) and
        // SafeStyleSheetProto (a complete CSS style sheet).
        // Use the full name of the message descriptor to disambiguate.
        if (SafeStyleProto.getDescriptor().getFullName().equals(protoName)) {
          return value.toSafeStyleProto();
        } else if (SafeStyleSheetProto.getDescriptor().getFullName().equals(protoName)) {
          return value.toSafeStyleSheetProto();
        }

        throw new AssertionError("unexpected proto name: " + protoName);
      case URI:
        return value.toSafeUrlProto();
      case TRUSTED_RESOURCE_URI:
        return value.toTrustedResourceUrlProto();
      default:
        throw new AssertionError("unexpected ContentKind: " + value.getContentKind());
    }
  }
}
