/*
 * Copyright 2009 Google Inc.
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

package com.google.template.soy.shared.internal;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Guice module for users that want to contribute plugins to Soy via Guice.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SharedModule extends AbstractModule {

  public SharedModule() {}

  @Override
  protected void configure() {
    // Create empty multibinders so we can inject user-supplied ones.
    Multibinder.newSetBinder(binder(), SoyFunction.class);
    Multibinder.newSetBinder(binder(), SoyPrintDirective.class);

    bind(SoyScopedData.class).toInstance(new SoySimpleScope());
  }

  // Unused by Soy, but provided because user plugins currently inject this.
  @Provides
  @LocaleString
  String provideLocaleString(SoyScopedData data) {
    return data.getLocale();
  }

  /**
   * Builds and provides the map of all installed SoyFunctions (name to function).
   *
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder).
   */
  @Provides
  @Singleton
  ImmutableMap<String, ? extends SoyFunction> provideSoyFunctionsMap(
      Set<SoyFunction> soyFunctionsSet) {
    Map<String, SoyFunction> mapBuilder = new LinkedHashMap<>();
    for (SoyFunction function : soyFunctionsSet) {
      SoyFunction old = mapBuilder.put(function.getName(), function);
      if (old != null && !old.getClass().getName().equals(function.getClass().getName())) {
        throw new IllegalStateException(
            "Found two functions with the same name: '"
                + function.getName()
                + "' and different implementations: "
                + function
                + " vs "
                + old);
      }
    }
    return ImmutableMap.copyOf(mapBuilder);
  }

  /**
   * Builds and provides the map of all installed SoyPrintDirectives (name to directive).
   *
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder).
   */
  @Provides
  @Singleton
  ImmutableMap<String, ? extends SoyPrintDirective> provideSoyDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {
    Map<String, SoyPrintDirective> mapBuilder = new LinkedHashMap<>();
    for (SoyPrintDirective directive : soyDirectivesSet) {
      SoyPrintDirective old = mapBuilder.put(directive.getName(), directive);
      if (old != null && !old.getClass().getName().equals(directive.getClass().getName())) {
        throw new IllegalStateException(
            "Found two print directives with the same name: '"
                + directive.getName()
                + "' and different implementations: "
                + directive
                + " vs "
                + old);
      }
    }
    return ImmutableMap.copyOf(mapBuilder);
  }

  @Override
  public boolean equals(Object other) {
    return other != null && this.getClass().equals(other.getClass());
  }

  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }
}
