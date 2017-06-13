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

package com.google.template.soy.pysrc.internal;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.GenPyExprsVisitor.GenPyExprsVisitorFactory;
import com.google.template.soy.pysrc.internal.MsgFuncGenerator.MsgFuncGeneratorFactory;
import com.google.template.soy.pysrc.internal.PyApiCallScopeBindingAnnotations.PyCurrentManifest;
import com.google.template.soy.pysrc.restricted.SoyPySrcPrintDirective;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.FunctionAdapters;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Guice module for the Python Source backend.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class PySrcModule extends AbstractModule {

  @Override
  protected void configure() {
    // Bindings for when explicit dependencies are required.
    bind(PySrcMain.class);
    bind(GenPyCodeVisitor.class);
    bind(GenPyCallExprVisitor.class);
    bind(IsComputableAsPyExprVisitor.class);

    // Bind providers of factories (created via assisted inject).
    install(new FactoryModuleBuilder().build(GenPyExprsVisitorFactory.class));
    install(new FactoryModuleBuilder().build(MsgFuncGeneratorFactory.class));

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyPySrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyPySrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(new Key<ImmutableMap<String, String>>(PyCurrentManifest.class) {})
        .toProvider(GuiceSimpleScope.<ImmutableMap<String, String>>getUnscopedProvider())
        .in(ApiCallScope.class);
  }

  /**
   * Builds and provides the map of SoyPySrcDirectives (name to directive).
   *
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyPySrcPrintDirective.
   */
  @Provides
  @Singleton
  ImmutableMap<String, SoyPySrcPrintDirective> provideSoyPySrcDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {
    return FunctionAdapters.buildSpecificSoyDirectivesMap(
        soyDirectivesSet, SoyPySrcPrintDirective.class);
  }
}
