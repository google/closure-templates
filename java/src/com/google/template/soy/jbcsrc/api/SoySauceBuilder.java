/*
 * Copyright 2018 Google Inc.
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

package com.google.template.soy.jbcsrc.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.shared.internal.InternalPlugins;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.internal.SoySimpleScope;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Map;

/** Constructs {@link SoySauce} implementations. */
public final class SoySauceBuilder {
  private ImmutableSet<String> allDeltemplates = ImmutableSet.of();
  private ImmutableMap<String, SoyFunction> userFunctions = ImmutableMap.of();
  private ImmutableMap<String, SoyPrintDirective> userDirectives = ImmutableMap.of();
  private SoyScopedData scopedData;

  public SoySauceBuilder() {}

  /** Sets the delTemplates, to be used when constructing the SoySauce. */
  public SoySauceBuilder withDelTemplates(Iterable<String> delTemplates) {
    this.allDeltemplates = ImmutableSet.copyOf(delTemplates);
    return this;
  }

  /**
   * Sets the user functions. Not exposed externally because external usage should solely be
   * SoySourceFunction, which are only needed at compile time.
   */
  SoySauceBuilder withFunctions(Map<String, ? extends SoyFunction> userFunctions) {
    this.userFunctions = ImmutableMap.copyOf(userFunctions);
    return this;
  }

  /**
   * Sets user directives. Not exposed externally because internal directives should be enough, and
   * additional functionality can be built as SoySourceFunctions.
   */
  SoySauceBuilder withDirectives(Map<String, ? extends SoyPrintDirective> userDirectives) {
    this.userDirectives = ImmutableMap.copyOf(userDirectives);
    return this;
  }

  /** Sets the scope. Only useful with PrecompiledSoyModule, which has a pre-built scope. */
  SoySauceBuilder withScope(SoyScopedData scope) {
    this.scopedData = scope;
    return this;
  }

  /** Creates a SoySauce. */
  public SoySauce build() {
    if (scopedData == null) {
      scopedData = new SoySimpleScope();
    }
    return new SoySauceImpl(
        new CompiledTemplates(allDeltemplates),
        scopedData.enterable(),
        userFunctions, // We don't need internal functions because they only matter at compile time
        ImmutableMap.<String, SoyPrintDirective>builder()
            // but internal directives are still required at render time.
            // in order to handle escaping logging function invocations.
            .putAll(InternalPlugins.internalDirectiveMap(scopedData))
            .putAll(userDirectives)
            .build());
  }
}
