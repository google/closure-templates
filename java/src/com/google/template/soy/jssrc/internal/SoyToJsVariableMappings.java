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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Manages the mappings between Soy variables and their JavaScript equivalents inside a single
 * template.
 */
public final class SoyToJsVariableMappings {

  private final IdentityHashMap<VarDefn, Expression> varDefnMappings;

  /**
   * The MsgFallbackGroupNode to an expression that evaluates to whether or not the primary message
   * is in use.
   */
  private final IdentityHashMap<MsgFallbackGroupNode, Expression> isPrimaryMsgInUseForFallbackGroup;

  private SoyToJsVariableMappings(Map<VarDefn, ? extends Expression> initialMappings) {
    varDefnMappings = new IdentityHashMap<>(initialMappings);
    isPrimaryMsgInUseForFallbackGroup = new IdentityHashMap<>();
  }

  private SoyToJsVariableMappings(SoyToJsVariableMappings parent) {
    varDefnMappings = new IdentityHashMap<>(parent.varDefnMappings);
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
      ImmutableMap<VarDefn, ? extends Expression> initialMappings) {
    return new SoyToJsVariableMappings(initialMappings);
  }

  @CanIgnoreReturnValue
  public SoyToJsVariableMappings put(VarDefn var, Expression translation) {
    varDefnMappings.put(var, translation);
    return this;
  }

  @CanIgnoreReturnValue
  public SoyToJsVariableMappings setIsPrimaryMsgInUse(MsgFallbackGroupNode msg, Expression var) {
    isPrimaryMsgInUseForFallbackGroup.put(msg, var);
    return this;
  }

  /** Returns the JavaScript translation for the given Soy variable. */
  public Expression get(VarDefn var) {
    return Preconditions.checkNotNull(
        varDefnMappings.get(var),
        "No value for key %s. Available keys: %s",
        var.refName(),
        varDefnMappings.keySet().stream().map(VarDefn::name).collect(Collectors.joining(",")));
  }

  public Expression isPrimaryMsgInUse(MsgFallbackGroupNode msg) {
    return isPrimaryMsgInUseForFallbackGroup.get(msg);
  }

  /**
   * Returns the JavaScript translation for the given Soy variable, or null if no mapping exists for
   * that variable.
   */
  @Nullable
  public Expression maybeGet(VarDefn var) {
    return varDefnMappings.get(var);
  }

  @Nullable
  public Expression getValueOfSameName(VarDefn var) {
    return varDefnMappings.entrySet().stream()
        .filter(e -> e.getKey().refName().equals(var.refName()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }
}
