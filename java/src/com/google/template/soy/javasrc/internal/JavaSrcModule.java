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

package com.google.template.soy.javasrc.internal;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.internal.GenJavaExprsVisitor.GenJavaExprsVisitorFactory;
import com.google.template.soy.javasrc.internal.TranslateToJavaExprVisitor.TranslateToJavaExprVisitorFactory;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
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
 * Guice module for the Java Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class JavaSrcModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new SharedPassesModule());

    // Bindings for when explicit dependencies are required.
    bind(JavaSrcMain.class);
    bind(GenJavaCodeVisitor.class);
    bind(OptimizeBidiCodeGenVisitor.class);
    bind(CanInitOutputVarVisitor.class);
    bind(GenCallCodeUtils.class);
    bind(IsComputableAsJavaExprsVisitor.class);

    // Bind providers of factories (created via assisted inject).
    install(new FactoryModuleBuilder().build(GenJavaExprsVisitorFactory.class));
    install(new FactoryModuleBuilder().build(TranslateToJavaExprVisitorFactory.class));

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyJavaSrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyJavaSrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
  }


  /**
   * Builds and provides the map of SoyJavaSrcFunctions (name to function).
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyJavaSrcFunction.
   */
  @Provides
  @Singleton
  Map<String, SoyJavaSrcFunction> provideSoyJavaSrcFunctionsMap(Set<SoyFunction> soyFunctionsSet) {

    return ModuleUtils.buildSpecificSoyFunctionsMap(SoyJavaSrcFunction.class, soyFunctionsSet);
  }


  /**
   * Builds and provides the map of SoyJavaSrcDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyJavaSrcDirective.
   */
  @Provides
  @Singleton
  Map<String, SoyJavaSrcPrintDirective> provideSoyJavaSrcDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return ModuleUtils.buildSpecificSoyDirectivesMap(
        SoyJavaSrcPrintDirective.class, soyDirectivesSet);
  }

}
