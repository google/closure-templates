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

package com.google.template.soy.base.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.CharMatcher;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages a set of unique names within a given context, and provides helper methods for generating
 * unique names from other names, which may or may not be sufficiently unique on their own.
 */
public final class UniqueNameGenerator {
  private final Set<String> reserved = new HashSet<>();
  private final Multiset<String> names = HashMultiset.create();
  private final CharMatcher bannedCharacters;
  private final String collisionSeparator;

  public UniqueNameGenerator(CharMatcher bannedCharacters, String collisionSeparator) {
    checkArgument(
        bannedCharacters.matchesNoneOf(collisionSeparator),
        "separator %s contains banned characters",
        collisionSeparator);
    this.bannedCharacters = bannedCharacters;
    this.collisionSeparator = collisionSeparator;
  }

  /** Registers the name, throwing an IllegalArgumentException if it has already been registered. */
  public void claimName(String name) {
    checkName(name);
    if (names.add(name, 1) != 0) {
      names.remove(name);
      // give a slightly better error message in this case
      if (reserved.contains(name)) {
        throw new IllegalArgumentException("Tried to claim a reserved name: " + name);
      }
      throw new IllegalArgumentException("Name: " + name + " was already claimed!");
    }
  }

  /** Reserves the names, useful for keywords. */
  public void reserve(Iterable<String> names) {
    for (String name : names) {
      reserve(name);
    }
  }

  /** Reserves the name, useful for keywords. */
  public void reserve(String name) {
    checkName(name);
    // if this is new
    if (reserved.add(name)) {
      // add it to names, so that generateName will still work for reserved names (they will just
      // get suffixes).
      if (!names.add(name)) {
        names.remove(name);
        throw new IllegalArgumentException("newly reserved name: " + name + " was already used!");
      }
    }
  }

  /**
   * Returns a name based on the supplied one that is guaranteed to be unique among the names that
   * have been returned by this method.
   */
  public String generateName(String name) {
    checkName(name);
    names.add(name);
    int count = names.count(name);
    if (count == 1) {
      return name;
    }
    return name + collisionSeparator + (count - 1);
  }

  public boolean hasName(String name) {
    int separator = name.lastIndexOf(collisionSeparator);
    return names.contains(separator == -1 ? name : name.substring(0, separator));
  }

  private void checkName(String name) {
    checkArgument(!name.isEmpty());
    checkArgument(
        !name.contains(collisionSeparator),
        "%s contains the separation character: '%s'",
        name,
        collisionSeparator);
    checkArgument(!bannedCharacters.matchesAnyOf(name), "%s contains dangerous characters!", name);
  }
}
