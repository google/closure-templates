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

package com.google.template.soy.jbcsrc.api;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.multibindings.OptionalBinder;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.internal.SoyScopedData;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import javax.inject.Singleton;

/**
 * A module for accessing a precompiled {@link SoySauce} object.
 *
 * <p>Optionally consumes a key for a {@code @Deltemplates ImmutableSet<String>} which defines the
 * full set of deltemplates that should be rendered.
 *
 * <p>Can be installed multiple times.
 */
public final class PrecompiledSoyModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new SharedModule());
    OptionalBinder.newOptionalBinder(
        binder(), new Key<ImmutableMap<String, Supplier<Object>>>(PluginInstances.class) {});
  }

  @Provides
  @Singleton
  @Precompiled
  SoySauce provideSoySauce(
      SoyScopedData scopedData,
      ImmutableMap<String, ? extends SoyFunction> pluginFunctions,
      ImmutableMap<String, ? extends SoyPrintDirective> pluginDirectives,
      @PluginInstances Optional<ImmutableMap<String, Supplier<Object>>> pluginInstances) {
    return new SoySauceBuilder()
        .withScope(scopedData)
        .withFunctions(pluginFunctions)
        .withDirectives(pluginDirectives)
        .withPluginInstances(pluginInstances.or(ImmutableMap.<String, Supplier<Object>>of()))
        .build();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PrecompiledSoyModule;
  }

  @Override
  public int hashCode() {
    return PrecompiledSoyModule.class.hashCode();
  }
}
