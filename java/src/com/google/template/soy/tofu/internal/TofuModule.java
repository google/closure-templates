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

package com.google.template.soy.tofu.internal;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.shared.internal.ModuleUtils;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.tofu.internal.BaseTofu.BaseTofuFactory;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.util.Map;
import java.util.Set;


/**
 * Guice module for the Tofu backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TofuModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new SharedPassesModule());

    // Bindings for when explicit dependencies are required.
    bind(TofuEvalVisitorFactory.class);
    bind(TofuRenderVisitorFactory.class);

    // Bind providers of factories (created via assisted inject).
    install(new FactoryModuleBuilder().build(BaseTofuFactory.class));
  }


  /**
   * Builds and provides the map of SoyTofuFunctions (name to function).
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyTofuFunction.
   */
  @Provides
  @Singleton
  Map<String, SoyTofuFunction> provideSoyTofuFunctionsMap(Set<SoyFunction> soyFunctionsSet) {

    return ModuleUtils.buildSpecificSoyFunctionsMap(SoyTofuFunction.class, soyFunctionsSet);
  }


  /**
   * Builds and provides the map of SoyTofuDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyTofuDirective.
   */
  @Provides
  @Singleton
  Map<String, SoyTofuPrintDirective> provideSoyTofuDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return ModuleUtils.buildSpecificSoyDirectivesMap(SoyTofuPrintDirective.class, soyDirectivesSet);
  }

}
