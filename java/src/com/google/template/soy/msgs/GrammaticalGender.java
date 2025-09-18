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

package com.google.template.soy.msgs;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableMap;
import java.util.function.Function;

/**
 * The user's grammatical gender for serving grammatically correct UI messages in gendered
 * languages.
 */
public enum GrammaticalGender {
  /** The grammatical gender is unknown, or there is no variation in the message between genders. */
  UNSPECIFIED(0),
  /** User should be addressed and referred to in feminine grammatical gender. */
  FEMININE(1),
  /** User should be addressed and referred to in masculine grammatical gender. */
  MASCULINE(2),
  /** All other cases. */
  OTHER(3),
  /** User should be addressed and referred to in neuter grammatical gender. */
  NEUTER(4);

  private final int code;

  GrammaticalGender(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  private static final ImmutableMap<Integer, GrammaticalGender> CODE_TO_ENUM_MAP =
      ImmutableMap.copyOf(
          stream(GrammaticalGender.values())
              .collect(toImmutableMap(GrammaticalGender::getCode, Function.identity())));

  public static GrammaticalGender fromCode(int code) {
    return CODE_TO_ENUM_MAP.get(code);
  }
}
