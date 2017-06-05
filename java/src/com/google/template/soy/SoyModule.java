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

import com.google.common.base.Optional;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.template.soy.incrementaldomsrc.IncrementalDomSrcModule;
import com.google.template.soy.jbcsrc.api.SoySauceImpl;
import com.google.template.soy.jssrc.internal.JsSrcModule;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.msgs.SoyMsgPlugin;
import com.google.template.soy.passes.SharedPassesModule;
import com.google.template.soy.pysrc.internal.PySrcModule;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.tofu.internal.TofuModule;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;
import javax.inject.Singleton;

/**
 * Guice module for Soy's programmatic interface.
 *
 */
public final class SoyModule extends AbstractModule {

  @Override
  protected void configure() {
    // This module is mostly available for configuring the compiler (SoyFileSet).  Consider
    // splitting SoyFileSet into a smaller number of objects and backend specific apis so this isn't
    // so monolithic (compiling JS shouldn't require Tofu and python backends).
    // eliminating injection points from the backends would help with this effort also.

    // Install requisite modules.
    install(new TofuModule());
    install(new JsSrcModule());
    install(new PySrcModule());
    install(new IncrementalDomSrcModule());

    // TODO(user): get rid of guice injection in passes.
    install(new SharedPassesModule());

    install(new SharedModule());

    Multibinder.newSetBinder(binder(), SoyTypeProvider.class);
    bind(SoyTypeRegistry.class).in(Singleton.class);

    // Bindings for when explicit dependencies are required.
    // Note: We don't promise to support this. We actually frown upon requireExplicitBindings.
    bind(SoySauceImpl.Factory.class);
    // optionally depend on a user supplied msg plugin
    OptionalBinder.newOptionalBinder(binder(), SoyMsgPlugin.class);
  }

  @Provides
  SoyMsgBundleHandler provideHandler(Optional<SoyMsgPlugin> plugin) {
    if (plugin.isPresent()) {
      return new SoyMsgBundleHandler(plugin.get());
    }
    // default
    return new SoyMsgBundleHandler(new XliffMsgPlugin());
  }

  // N.B. we provide the builder here instead of having an @Inject constructor to get guice to
  // provide less spammy error messages.  Now instead of complaining that we are missing every
  // dependency of CoreDependencies, guice will simply complain that there is no binding for
  // SoyFileSet.Builder.
  @Provides
  SoyFileSet.Builder provideBuilder(SoyFileSet.CoreDependencies coreDeps) {
    return new SoyFileSet.Builder(coreDeps);
  }

  // make this module safe to install multiple times.  This is necessary because things like
  // JsSrcModule conflict with themselves

  @Override
  public int hashCode() {
    return SoyModule.class.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SoyModule;
  }
}
