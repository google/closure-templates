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

package com.google.template.soy.jbcsrc;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.CharMatcher;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Manages a set of unique names within a given context, and provides helper methods for generating
 * unique names from other names, which may or may not be sufficiently unique on their own.
 */
final class UniqueNameGenerator {
  // Characters that are not generally safe to use as an identifier in class files.
  // from https://blogs.oracle.com/jrose/entry/symbolic_freedom_in_the_vm
  // plus the '%' character, which is safe but we use it as a delimiter
  private static final CharMatcher DANGEROUS_CHARACTERS = 
      CharMatcher.anyOf("/.;<<>[]:\\%").precomputed();
  
  private static final CharMatcher DANGEROUS_CHARACTERS_WITH_DOLLARSIGN = 
      DANGEROUS_CHARACTERS.or(CharMatcher.anyOf("$")).precomputed();

  /**
   * Returns a {@link UniqueNameGenerator} that is suitable for managing names used for fields in a
   * class.
   */
  static UniqueNameGenerator forFieldNames() {
    return new UniqueNameGenerator(DANGEROUS_CHARACTERS, '%');
  }

  /**
   * Returns a {@link UniqueNameGenerator} that is suitable for managing simple names used for
   * classes.
   *
   * <p>For example, all the inner classes of a class, or all the classes in a package.
   */
  static UniqueNameGenerator forClassNames() {
    // roman numeral 10
    return new UniqueNameGenerator(DANGEROUS_CHARACTERS_WITH_DOLLARSIGN, '\u2169');
  }

  private final Multiset<String> names = HashMultiset.create();
  private final CharMatcher bannedCharacters;
  private final char collisionSeparator;

  private UniqueNameGenerator(CharMatcher bannedCharacters, char collisionSeparator) {
    this.bannedCharacters = bannedCharacters;
    this.collisionSeparator = collisionSeparator;
  }

  /**
   * Registers the name, throwing an IllegalArgumentException if it has already been registered.
   */
  void claimName(String name) {
    checkName(name);
    if (names.contains(name)) {
      throw new IllegalArgumentException("Name: " + name + " was already claimed!");
    }
    names.add(name);
  }

  /**
   * Returns a name based on the supplied one that is guaranteed to be unique among the names that
   * have been returned by this method.
   */
  String generateName(String name) {
    checkName(name);
    names.add(name);
    int count = names.count(name);
    if (count == 1) {
      return name;
    }
    return name + collisionSeparator + (count - 1);
  }

  boolean hasName(String name) {
    int separator = name.indexOf(collisionSeparator);
    return names.contains(separator == -1 ? name : name.substring(0, separator));
  }

  private void checkName(String name) {
    checkArgument(!bannedCharacters.matchesAnyOf(name), "%s contains dangerous characters!", 
        name);
  }
}
