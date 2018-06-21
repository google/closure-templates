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
package com.google.template.soy.jbcsrc.internal;

import com.google.common.base.CharMatcher;
import com.google.template.soy.base.internal.UniqueNameGenerator;

/** {@link UniqueNameGenerator} implementations for java bytecode. */
public final class JbcSrcNameGenerators {
  // Characters that are not generally safe to use as an identifier in class files.
  // from https://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm
  private static final CharMatcher DANGEROUS_CHARACTERS =
      CharMatcher.anyOf("/.;<<>[]:\\").precomputed();

  private static final CharMatcher DANGEROUS_CHARACTERS_WITH_DOLLARSIGN =
      DANGEROUS_CHARACTERS.or(CharMatcher.is('$')).precomputed();

  /**
   * Returns a {@link UniqueNameGenerator} that is suitable for managing names used for fields in a
   * class.
   */
  public static UniqueNameGenerator forFieldNames() {
    return new UniqueNameGenerator(DANGEROUS_CHARACTERS, "%");
  }

  /**
   * Returns a {@link UniqueNameGenerator} that is suitable for managing simple names used for
   * classes.
   *
   * <p>For example, all the inner classes of a class, or all the classes in a package.
   */
  public static UniqueNameGenerator forClassNames() {
    // roman numeral 10
    return new UniqueNameGenerator(DANGEROUS_CHARACTERS_WITH_DOLLARSIGN, "\u2169");
  }
}
