/*
 * Copyright 2024 Google Inc.
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

package com.google.template.soy.msgs.restricted;

import com.google.errorprone.annotations.Immutable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A simple identity wrapper around a placeholder name. Used to accelerate rendering. */
@Immutable
public final class PlaceholderName {
  private static final ConcurrentMap<String, PlaceholderName> internedNames =
      new ConcurrentHashMap<>();

  public static PlaceholderName create(String name) {
    // intern the string because it is also encoded in jbcsrc gencode. If this isn't for a
    // rendering usecase this will be a bit wasteful.
    return internedNames.computeIfAbsent(name.intern(), PlaceholderName::new);
  }

  private final String name;

  private PlaceholderName(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return "PlaceholderName{" + name + "}";
  }

  // don't implement equals or hashcode, we want identity semantics.
}
