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

package com.google.template.soy.jssrc.internal;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.GenJsExprsVisitor.GenJsExprsVisitorFactory;
import com.google.template.soy.jssrc.internal.TranslateToJsExprVisitor.TranslateToJsExprVisitorFactory;
import com.google.template.soy.jssrc.restricted.SoyJsSrcFunction;
import com.google.template.soy.jssrc.restricted.SoyJsSrcPrintDirective;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.ModuleUtils;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;

import java.util.Map;
import java.util.Set;


/**
 * Guice module for the JS Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class JsSrcModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new SharedPassesModule());

    // Bindings for when explicit dependencies are required.
    bind(JsSrcMain.class);
    bind(GenJsCodeVisitor.class);
    bind(OptimizeBidiCodeGenVisitor.class);
    bind(ReplaceMsgsWithGoogMsgsVisitor.class);
    bind(CanInitOutputVarVisitor.class);
    bind(GenCallCodeUtils.class);
    bind(IsComputableAsJsExprsVisitor.class);
    bind(JsExprTranslator.class);

    // Bind providers of factories (created via assisted inject).
    install(new FactoryModuleBuilder().build(GenJsExprsVisitorFactory.class));
    install(new FactoryModuleBuilder().build(TranslateToJsExprVisitorFactory.class));

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyJsSrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyJsSrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
  }


  /**
   * Builds and provides the map of SoyJsSrcFunctions (name to function).
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyJsSrcFunction.
   */
  @Provides
  @Singleton
  Map<String, SoyJsSrcFunction> provideSoyJsSrcFunctionsMap(Set<SoyFunction> soyFunctionsSet) {

    return ModuleUtils.buildSpecificSoyFunctionsMap(SoyJsSrcFunction.class, soyFunctionsSet);
  }


  /**
   * Builds and provides the map of SoyJsSrcDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyJsSrcDirective.
   */
  @Provides
  @Singleton
  Map<String, SoyJsSrcPrintDirective> provideSoyJsSrcDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return ModuleUtils.buildSpecificSoyDirectivesMap(
        SoyJsSrcPrintDirective.class, soyDirectivesSet);
  }

}
