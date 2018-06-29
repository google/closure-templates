/*
 * Copyright 2015 Google Inc.
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

package com.google.template.soy.jssrc.internal;

import static com.google.template.soy.jssrc.internal.JsRuntime.SOY_COERCE_TO_BOOLEAN;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.types.SoyType;
import com.google.template.soy.types.SoyType.Kind;
import com.google.template.soy.types.UnionType;

final class Truthiness {
  private Truthiness() {}

  private static final ImmutableSet<Kind> TYPES_TO_COERCE =
      Sets.immutableEnumSet(
          Kind.ANY,
          Kind.UNKNOWN,
          Kind.HTML,
          Kind.ATTRIBUTES,
          Kind.JS,
          Kind.CSS,
          Kind.URI,
          Kind.TRUSTED_RESOURCE_URI);

  static Expression maybeCoerce(SoyType type, Expression chunk) {
    if (TYPES_TO_COERCE.contains(type.getKind())) {
      return SOY_COERCE_TO_BOOLEAN.call(chunk);
    }

    if (type instanceof UnionType) {
      for (SoyType member : ((UnionType) type).getMembers()) {
        if (TYPES_TO_COERCE.contains(member.getKind())) {
          return SOY_COERCE_TO_BOOLEAN.call(chunk);
        }
      }
    }

    return chunk;
  }
}
