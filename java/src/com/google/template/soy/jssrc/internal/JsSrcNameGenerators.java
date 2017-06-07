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
package com.google.template.soy.jssrc.internal;

import com.google.common.base.CharMatcher;
import com.google.template.soy.base.internal.UniqueNameGenerator;

/**
 * A name generator for jssrc local variables.
 */
public final class JsSrcNameGenerators {
  // javascript is more permissive than this, but we are purposively restrictive
  private static final CharMatcher DANGEROUS_CHARACTERS =
      CharMatcher.ascii()
          .or(CharMatcher.digit())
          .or(CharMatcher.anyOf("_$"))
          .negate()
          .precomputed();

  /** Returns a name generator suitable for generating local variable names. */
  public static UniqueNameGenerator forLocalVariables() {
    UniqueNameGenerator generator = new UniqueNameGenerator(DANGEROUS_CHARACTERS, "$$");
    generator.reserve(JsSrcUtils.JS_LITERALS);
    generator.reserve(JsSrcUtils.JS_RESERVED_WORDS);
    return generator;
  }
}
