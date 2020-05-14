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

package com.google.template.soy;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;

/**
 * Guice module for Soy's programmatic interface.
 *
 * @deprecated switch to using APIs like SoySauceBuilder, or use the command line interface for soy
 *     compilation
 */
@Deprecated
public final class SoyModule extends AbstractModule {

  @Override
  protected void configure() {
    // Create empty multibinders so we can inject user-supplied ones.
    Multibinder.newSetBinder(binder(), SoyFunction.class);
    Multibinder.newSetBinder(binder(), SoyPrintDirective.class);
  }

  // N.B. we provide the builder here instead of having an @Inject constructor to issue a slightly
  // better error message when people try to inject without installing this module
  @Provides
  SoyFileSet.Builder provideBuilder(
      Set<SoyFunction> pluginFunctions, Set<SoyPrintDirective> pluginDirectives) {
    return SoyFileSet.builder()
        .addSoyFunctions(pluginFunctions)
        .addSoyPrintDirectives(pluginDirectives);
  }

  // make this module safe to install multiple times.

  @Override
  public int hashCode() {
    return SoyModule.class.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SoyModule;
  }
}
