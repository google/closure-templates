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
import com.google.inject.assistedinject.FactoryProvider;
import com.google.template.soy.javasrc.SoyJavaSrcOptions;
import com.google.template.soy.javasrc.internal.GenJavaExprsVisitor.GenJavaExprsVisitorFactory;
import com.google.template.soy.javasrc.internal.TranslateToJavaExprVisitor.TranslateToJavaExprVisitorFactory;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcFunction;
import com.google.template.soy.javasrc.restricted.SoyJavaSrcPrintDirective;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.BackendModuleUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

import java.util.Map;
import java.util.Set;


/**
 * Guice module for the Java Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class JavaSrcModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());

    // Bind providers of factories (created via assisted inject).
    bind(GenJavaExprsVisitorFactory.class)
        .toProvider(FactoryProvider.newFactory(
            GenJavaExprsVisitorFactory.class, GenJavaExprsVisitor.class));
    bind(TranslateToJavaExprVisitorFactory.class)
        .toProvider(FactoryProvider.newFactory(
            TranslateToJavaExprVisitorFactory.class, TranslateToJavaExprVisitor.class));

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

    return BackendModuleUtils.buildBackendSpecificSoyFunctionsMap(
        SoyJavaSrcFunction.class, soyFunctionsSet);
  }


  /**
   * Builds and provides the map of SoyJavaSrcDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyJavaSrcDirective.
   */
  @Provides
  @Singleton
  Map<String, SoyJavaSrcPrintDirective> provideSoyJavaSrcDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return BackendModuleUtils.buildBackendSpecificSoyDirectivesMap(
        SoyJavaSrcPrintDirective.class, soyDirectivesSet);
  }

}
