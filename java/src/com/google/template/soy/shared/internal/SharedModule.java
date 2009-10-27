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
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.coredirectives.CoreDirectivesModule;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.BidiGlobalDir;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

import java.util.Map;
import java.util.Set;


/**
 * Guice module for shared classes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SharedModule extends AbstractModule {


  @Override protected void configure() {

    // Install the core directives.
    install(new CoreDirectivesModule());

    // If no SoyFunctions are bound, we want an empty set instead of an error.
    Multibinder.newSetBinder(binder(), SoyFunction.class);

    // Create the API call scope.
    GuiceSimpleScope apiCallScope = new GuiceSimpleScope();
    bindScope(ApiCallScope.class, apiCallScope);
    // Make the API call scope instance injectable.
    bind(GuiceSimpleScope.class).annotatedWith(ApiCall.class)
        .toInstance(apiCallScope);

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyMsgBundle.class)
        .toProvider(GuiceSimpleScope.<SoyMsgBundle>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(String.class).annotatedWith(LocaleString.class)
        .toProvider(GuiceSimpleScope.<String>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(Integer.class).annotatedWith(BidiGlobalDir.class)
        .toProvider(GuiceSimpleScope.<Integer>getUnscopedProvider())
        .in(ApiCallScope.class);
  }


  /**
   * Builds and provides the map of all installed SoyPrintDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder).
   */
  @Provides
  @Singleton
  Map<String, SoyPrintDirective> provideSoyDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    ImmutableMap.Builder<String, SoyPrintDirective> mapBuilder = ImmutableMap.builder();
    for (SoyPrintDirective directive : soyDirectivesSet) {
      mapBuilder.put(directive.getName(), directive);
    }
    return mapBuilder.build();
  }


  @Override public boolean equals(Object other) {
    return other != null && this.getClass().equals(other.getClass());
  }


  @Override public int hashCode() {
    return 21485;
  }

}
