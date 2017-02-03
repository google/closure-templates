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
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.bididirectives.BidiDirectivesModule;
import com.google.template.soy.bidifunctions.BidiFunctionsModule;
import com.google.template.soy.coredirectives.CoreDirectivesModule;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.i18ndirectives.I18nDirectivesModule;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Guice module for shared classes.
 *
 * <p>Contains all the bindings shared between the runtime and compiler
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SharedModule extends AbstractModule {

  @Override
  protected void configure() {
    // Install the core directives.
    install(new CoreDirectivesModule());

    // Install default directive and function modules.
    install(new BasicDirectivesModule());
    install(new BidiDirectivesModule());
    install(new BasicFunctionsModule());
    install(new BidiFunctionsModule());
    install(new I18nDirectivesModule());

    // Create the API call scope.
    GuiceSimpleScope apiCallScope = new GuiceSimpleScope();
    bindScope(ApiCallScope.class, apiCallScope);
    // Make the API call scope instance injectable.
    bind(GuiceSimpleScope.class).annotatedWith(ApiCall.class).toInstance(apiCallScope);

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(String.class)
        .annotatedWith(LocaleString.class)
        .toProvider(GuiceSimpleScope.<String>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(BidiGlobalDir.class)
        .toProvider(GuiceSimpleScope.<BidiGlobalDir>getUnscopedProvider())
        .in(ApiCallScope.class);

    Multibinder.newSetBinder(binder(), SoyTypeProvider.class);
    bind(SoyTypeRegistry.class).in(Singleton.class);

    bind(SoyValueConverter.class).in(Singleton.class);
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
    ImmutableMap.Builder<String, SoyFunction> mapBuilder = ImmutableMap.builder();
    for (SoyFunction function : soyFunctionsSet) {
      mapBuilder.put(function.getName(), function);
    }
    return mapBuilder.build();
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
    ImmutableMap.Builder<String, SoyPrintDirective> mapBuilder = ImmutableMap.builder();
    for (SoyPrintDirective directive : soyDirectivesSet) {
      mapBuilder.put(directive.getName(), directive);
    }
    return mapBuilder.build();
  }

  /**
   * Builds and provides the map of SoyJavaPrintDirectives (name to directive).
   *
   * <p>This actually collects all SoyPrintDirectives that implement either SoyJavaPrintDirective or
   * SoyJavaRuntimePrintDirective (deprecated). The latter are adapted to the former interface.
   *
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyPrintDirective may or may not implement SoyJavaPrintDirective or
   *     SoyJavaRuntimePrintDirective.
   */
  @Provides
  @Singleton
  ImmutableMap<String, ? extends SoyJavaPrintDirective> provideSoyJavaDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {
    return FunctionAdapters.buildSpecificSoyDirectivesMap(
        soyDirectivesSet, SoyJavaPrintDirective.class);
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
