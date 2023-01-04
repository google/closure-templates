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

import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Manages a set of unique names within a given context, and provides helper methods for generating
 * unique names from other names, which may or may not be sufficiently unique on their own.
 */
public final class UniqueNameGenerator {
  private static final Pattern ENDING_DIGITS = Pattern.compile("[1-9]\\d*$");

  /** All reserved keywords. These will always be suffixed if passed to generate(). */
  private final ImmutableSet<String> reserved;
  /** All currently reserved symbols, not including reserved. */
  private final Set<String> used;
  /** For every symbol base (symbol excluding delimiter and index) the last index used. */
  private final Map<String, Integer> baseToMaxIndex;

  private final CharMatcher bannedCharacters;
  private final String collisionSeparator;

  public UniqueNameGenerator(CharMatcher bannedCharacters, String collisionSeparator) {
    this(bannedCharacters, collisionSeparator, ImmutableSet.of());
  }

  public UniqueNameGenerator(
      CharMatcher bannedCharacters, String collisionSeparator, Set<String> reserved) {
    checkArgument(
        bannedCharacters.matchesNoneOf(collisionSeparator),
        "separator %s contains banned characters",
        collisionSeparator);
    this.reserved = ImmutableSet.copyOf(reserved);
    this.used = new HashSet<>();
    this.baseToMaxIndex = new HashMap<>();
    this.bannedCharacters = bannedCharacters;
    this.collisionSeparator = collisionSeparator;
  }

  /** Creates and returns an independent copy of this generator. */
  public UniqueNameGenerator branch() {
    UniqueNameGenerator copy =
        new UniqueNameGenerator(bannedCharacters, collisionSeparator, reserved);
    used.forEach(copy::exact);
    return copy;
  }

  /**
   * Reserves the exact name {@code name}, throwing an IllegalArgumentException if it has already
   * been reserved.
   */
  public void exact(String name) {
    String unused = generate(name, /* exact= */ true, /* lenient= */ false);
  }

  /** Reserves the exact name {@code name}, failing silently if it has already been reserved. */
  public void exactLenient(String name) {
    String unused = generate(name, /* exact= */ true, /* lenient= */ true);
  }

  /**
   * Returns a name based on the supplied one that is guaranteed to be unique among the names that
   * have been returned by this method.
   */
  public String generate(String name) {
    return generate(name, /* exact= */ false, /* lenient= */ false);
  }

  private String generate(String name, boolean exact, boolean lenient) {
    checkName(name);
    Pair parts = split(name);
    boolean isUsed = used.contains(name);
    boolean isReserved = !isUsed && reserved.contains(name);
    if (isUsed || isReserved) {
      if (exact) {
        if (lenient) {
          return name;
        }
        // give a slightly better error message in this case
        if (isReserved) {
          throw new IllegalArgumentException("Tried to claim a reserved name: " + name);
        }
        throw new IllegalArgumentException("Name: " + name + " was already claimed!");
      }
      int index =
          baseToMaxIndex.compute(
              parts.base(), (key, oldValue) -> oldValue == null ? 1 : oldValue + 1);
      name = parts.base() + collisionSeparator + index;
    } else if (parts.index() != null) {
      baseToMaxIndex.compute(
          parts.base(),
          (key, oldValue) -> Math.max(oldValue != null ? oldValue : 0, parts.index()));
    }
    Preconditions.checkArgument(used.add(name));
    return name;
  }

  public boolean has(String name) {
    return used.contains(name);
  }

  private void checkName(String name) {
    checkArgument(!name.isEmpty());
    checkArgument(!bannedCharacters.matchesAnyOf(name), "%s contains dangerous characters!", name);
  }

  private Pair split(String name) {
    Matcher m = ENDING_DIGITS.matcher(name);
    if (m.find()) {
      String intString = m.group();
      String rest = name.substring(0, name.length() - intString.length());
      if (!rest.isEmpty() && rest.endsWith(collisionSeparator)) {
        return Pair.of(
            rest.substring(0, rest.length() - collisionSeparator.length()),
            Integer.parseInt(intString));
      }
    }
    return Pair.of(name, null);
  }

  // No guava Pair.
  @AutoValue
  abstract static class Pair {
    static Pair of(String base, Integer index) {
      return new AutoValue_UniqueNameGenerator_Pair(base, index);
    }

    abstract String base();

    @Nullable
    abstract Integer index();
  }
}
