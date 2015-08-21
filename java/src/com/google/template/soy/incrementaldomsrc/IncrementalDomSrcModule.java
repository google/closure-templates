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

package com.google.template.soy.incrementaldomsrc;

import com.google.inject.AbstractModule;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.jssrc.internal.JsSrcModule;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.GuiceSimpleScope;


/**
 * Guice module for the Incremental DOM JS Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 */
public class IncrementalDomSrcModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new JsSrcModule());

    // Bindings for when explicit dependencies are required.
    bind(IncrementalDomSrcMain.class);
    bind(GenIncrementalDomCodeVisitor.class);
    bind(IncrementalDomGenCallCodeUtils.class);

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyJsSrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyJsSrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
  }
}
