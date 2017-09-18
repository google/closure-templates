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
import com.google.template.soy.pysrc.SoyPySrcOptions;
import com.google.template.soy.pysrc.internal.PyApiCallScopeBindingAnnotations.PyCurrentManifest;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope;

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

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyPySrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyPySrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(new Key<ImmutableMap<String, String>>(PyCurrentManifest.class) {})
        .toProvider(GuiceSimpleScope.<ImmutableMap<String, String>>getUnscopedProvider())
        .in(ApiCallScope.class);
  }
}
