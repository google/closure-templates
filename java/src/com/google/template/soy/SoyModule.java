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
import com.google.inject.assistedinject.FactoryProvider;
import com.google.template.soy.SoyFileSet.SoyFileSetFactory;
import com.google.template.soy.basicdirectives.BasicDirectivesModule;
import com.google.template.soy.basicfunctions.BasicFunctionsModule;
import com.google.template.soy.bididirectives.BidiDirectivesModule;
import com.google.template.soy.bidifunctions.BidiFunctionsModule;
import com.google.template.soy.javasrc.internal.JavaSrcModule;
import com.google.template.soy.jssrc.internal.JsSrcModule;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.tofu.internal.TofuModule;


/**
 * Guice module for Soy's programmatic interface.
 *
 * @author Kai Huang
 */
public class SoyModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new TofuModule());
    install(new JsSrcModule());
    install(new JavaSrcModule());

    // Install default directive and function modules.
    install(new BasicDirectivesModule());
    install(new BidiDirectivesModule());
    install(new BasicFunctionsModule());
    install(new BidiFunctionsModule());

    // Bind providers of factories (created via assisted inject).
    bind(SoyFileSetFactory.class)
        .toProvider(FactoryProvider.newFactory(SoyFileSetFactory.class, SoyFileSet.class));

    // The static injection of SoyFileSetFactory into SoyFileSet.Builder is what allows the Soy
    // compiler to use Guice even if the user of the Soy API does not use Guice.
    requestStaticInjection(SoyFileSet.Builder.class);

    // Mark the fact that Guice has been initialized.
    requestStaticInjection(GuiceInitializer.class);
  }

}
