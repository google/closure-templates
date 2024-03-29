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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.template.soy.exprtree.VarDefn;
import com.google.template.soy.jssrc.dsl.Expression;
import com.google.template.soy.soytree.MsgFallbackGroupNode;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Manages the mappings between Soy variables and their JavaScript equivalents
 * inside a single template.
 */
public final class SoyToJsVariableMappings {
  /** TODO(user): change the key type to {@link com.google.template.soy.exprtree.VarDefn}. */
  private final Map<String, Expression> mappings;

  /**
   * The MsgFallbackGroupNode to an expression that evaluates to whether or not the primary message
   * is in use.
   */
  private final IdentityHashMap<MsgFallbackGroupNode, Expression> isPrimaryMsgInUseForFallbackGroup;
  ;

  private SoyToJsVariableMappings(Map<String, ? extends Expression> initialMappings) {
    mappings = new HashMap<>(initialMappings);
    isPrimaryMsgInUseForFallbackGroup = new IdentityHashMap<>();
  }

  private SoyToJsVariableMappings(SoyToJsVariableMappings parent) {
    mappings = new HashMap<>(parent.mappings);
    // Confusingly this map doesn't reflect block scoping. however because the keys are nodes there
    // is no namespace issue we need to manage.
    isPrimaryMsgInUseForFallbackGroup = parent.isPrimaryMsgInUseForFallbackGroup;
  }

  /** Returns a new {@link SoyToJsVariableMappings} suitable for translating an entire template. */
  public static SoyToJsVariableMappings newEmpty() {
    return new SoyToJsVariableMappings(ImmutableMap.of());
  }

  static SoyToJsVariableMappings startingWith(SoyToJsVariableMappings initialMappings) {
    return new SoyToJsVariableMappings(initialMappings);
  }

  /** Returns a {@link SoyToJsVariableMappings} seeded with the given mappings. For testing only. */
  @VisibleForTesting
  static SoyToJsVariableMappings startingWith(
      ImmutableMap<String, ? extends Expression> initialMappings) {
    return new SoyToJsVariableMappings(initialMappings);
  }

  public SoyToJsVariableMappings put(VarDefn var, Expression translation) {
    return put(var.refName(), translation);
  }

  /**
   * Maps the Soy variable named {@code name} to the given translation. Any previous mapping for the
   * variable is lost.
   *
   * <p>TODO(user): this API requires callers to mangle the names they pass in to ensure
   * uniqueness. Do the mangling internally.
   */
  @CanIgnoreReturnValue
  public SoyToJsVariableMappings put(String var, Expression translation) {
    mappings.put(var, translation);
    return this;
  }

  @CanIgnoreReturnValue
  public SoyToJsVariableMappings setIsPrimaryMsgInUse(MsgFallbackGroupNode msg, Expression var) {
    isPrimaryMsgInUseForFallbackGroup.put(msg, var);
    return this;
  }

  /** Returns the JavaScript translation for the Soy variable with the given name, */
  public Expression get(String name) {
    return Preconditions.checkNotNull(
        mappings.get(name),
        "No value for key %s. Available keys: %s",
        name,
        String.join(",", mappings.keySet()));
  }

  public Expression isPrimaryMsgInUse(MsgFallbackGroupNode msg) {
    return isPrimaryMsgInUseForFallbackGroup.get(msg);
  }

  /**
   * Returns the JavaScript translation for the Soy variable with the given name, or null if no
   * mapping exists for that variable.
   *
   * <p>TODO(user): the null case is only for handling template params. Eliminate the @Nullable by
   * seeding {@link #newEmpty()} with the params.
   */
  @Nullable
  public Expression maybeGet(String name) {
    return mappings.get(name);
  }

  /** Returns true if there is an existing variable mapping for the given name. */
  public boolean has(String name) {
    return mappings.get(name) != null;
  }
}
