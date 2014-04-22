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
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.SoyFileSet.SoyFileSetFactory;
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.bididirectives.BidiDirectivesModule;
import com.google.template.soy.bidifunctions.BidiFunctionsModule;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.i18ndirectives.I18nDirectivesModule;
import com.google.template.soy.jssrc.internal.JsSrcModule;
import com.google.template.soy.parsepasses.CheckFunctionCallsVisitor.CheckFunctionCallsVisitorFactory;
import com.google.template.soy.parsepasses.PerformAutoescapeVisitor;
import com.google.template.soy.parsepasses.contextautoesc.ContextualAutoescaper;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.tofu.internal.TofuModule;
import com.google.template.soy.types.SoyTypeOps;

/**
 * Guice module for Soy's programmatic interface.
 *
 */
public class SoyModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new TofuModule());
    install(new JsSrcModule());

    // Bindings for when explicit dependencies are required.
    // Note: We don't promise to support this. We actually frown upon requireExplicitBindings.
    bind(ContextualAutoescaper.class);
    bind(PerformAutoescapeVisitor.class);
    bind(SoyFileSet.Builder.class);
    bind(SoyTypeOps.class);
    bind(SoyValueHelper.class);

    // Install default directive and function modules.
    install(new BasicDirectivesModule());
    install(new BidiDirectivesModule());
    install(new BasicFunctionsModule());
    install(new BidiFunctionsModule());
    install(new I18nDirectivesModule());

    // Bind providers of factories (created via assisted inject).
    install((new FactoryModuleBuilder()).build(CheckFunctionCallsVisitorFactory.class));
    install((new FactoryModuleBuilder()).build(SoyFileSetFactory.class));

    // This requests "static" initialization as soon as whatever Injector we are in is created.  If
    // multiple injectors are in the app, it's very likely the caller will get the wrong Injector.
    // TODO(gboyer): Remove the entirety of GuiceInitializer if we can get all clients of Soy to
    // inject SoyFileSEt.Builder rather than simply new'ing it.
    requestStaticInjection(GuiceInitializer.class);
  }

}
