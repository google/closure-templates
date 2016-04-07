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

import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtmlProto;
import com.google.common.html.types.SafeScriptProto;
import com.google.common.html.types.SafeStyleProto;
import com.google.common.html.types.SafeStyleSheetProto;
import com.google.common.html.types.SafeUrlProto;
import com.google.common.html.types.TrustedResourceUrlProto;
import com.google.template.soy.types.primitive.SanitizedType;

/**
 * A relation between safe string types and sanitized content types.
 */
final class SafeStringTypes {

  private SafeStringTypes() {
    // Not instantiable.
  }

  /** Maps protobuf message descriptor names to sanitized types. */
  static final ImmutableMap<String, SanitizedType> SAFE_STRING_PROTO_NAME_TO_SANITIZED_TYPE =
      ImmutableMap.<String, SanitizedType>builder()
      .put(
          SafeHtmlProto.getDescriptor().getFullName(),
          SanitizedType.HtmlType.getInstance())
      .put(
          SafeScriptProto.getDescriptor().getFullName(),
          SanitizedType.JsType.getInstance())
      .put(
          SafeStyleProto.getDescriptor().getFullName(),
          SanitizedType.CssType.getInstance())
      .put(
          SafeStyleSheetProto.getDescriptor().getFullName(),
          SanitizedType.CssType.getInstance())
      .put(
          SafeUrlProto.getDescriptor().getFullName(),
          SanitizedType.UriType.getInstance())
      .put(
          TrustedResourceUrlProto.getDescriptor().getFullName(),
          SanitizedType.TrustedResourceUriType.getInstance())
      .build();

}
