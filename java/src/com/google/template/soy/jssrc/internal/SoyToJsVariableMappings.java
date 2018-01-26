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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.template.soy.jssrc.dsl.CodeChunk;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Manages the mappings between Soy variables and their JavaScript equivalents
 * inside a single template.
 */
public final class SoyToJsVariableMappings {
  /** TODO(brndn): change the key type to {@link com.google.template.soy.exprtree.VarDefn}. */
  private final Map<String, CodeChunk.WithValue> mappings;

  private SoyToJsVariableMappings(
      ImmutableMap<String, ? extends CodeChunk.WithValue> initialMappings) {
    mappings = new HashMap<>(initialMappings);
  }

  /** Returns a new {@link SoyToJsVariableMappings} suitable for translating an entire template. */
  public static SoyToJsVariableMappings forNewTemplate() {
    return new SoyToJsVariableMappings(ImmutableMap.<String, CodeChunk.WithValue>of());
  }

  /** Returns a {@link SoyToJsVariableMappings} seeded with the given mappings. For testing only. */
  @VisibleForTesting
  static SoyToJsVariableMappings startingWith(
      ImmutableMap<String, ? extends CodeChunk.WithValue> initialMappings) {
    return new SoyToJsVariableMappings(initialMappings);
  }

  /**
   * Maps the Soy variable named {@code name} to the given translation.
   * Any previous mapping for the variable is lost.
   * TODO(brndn): this API requires callers to mangle the names they pass in to ensure uniqueness.
   * Do the mangling internally.
   */
  public SoyToJsVariableMappings put(String var, CodeChunk.WithValue translation) {
    mappings.put(var, translation);
    return this;
  }

  /** Returns the JavaScript translation for the Soy variable with the given name, */
  public CodeChunk.WithValue get(String name) {
    return Preconditions.checkNotNull(mappings.get(name));
  }

  /**
   * Returns the JavaScript translation for the Soy variable with the given name,
   * or null if no mapping exists for that variable.
   * TODO(brndn): the null case is only for handling template params. Eliminate the @Nullable
   * by seeding {@link #forNewTemplate()} with the params.
   */
  @Nullable public CodeChunk.WithValue maybeGet(String name) {
    return mappings.get(name);
  }
}
