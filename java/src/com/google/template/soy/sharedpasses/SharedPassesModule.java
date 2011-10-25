/*
 * Copyright 2011 Google Inc.
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

package com.google.template.soy.sharedpasses;

import com.google.inject.AbstractModule;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.sharedpasses.opti.OptiModule;
import com.google.template.soy.sharedpasses.render.EvalVisitor.EvalVisitorFactory;
import com.google.template.soy.sharedpasses.render.EvalVisitorFactoryImpl;
import com.google.template.soy.sharedpasses.render.RenderVisitor.RenderVisitorFactory;
import com.google.template.soy.sharedpasses.render.RenderVisitorFactoryImpl;


/**
 * Guice module for shared passes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class SharedPassesModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());

    // Install OptiModule, which explicitly binds classes in the opti package.
    // We use a separate module rather than inlining the classes here to allow
    // classes in the package to be package-private.
    install(new OptiModule());

    // Bind factories.
    bind(EvalVisitorFactory.class).to(EvalVisitorFactoryImpl.class);
    bind(RenderVisitorFactory.class).to(RenderVisitorFactoryImpl.class);
  }


  @Override public boolean equals(Object other) {
    return other != null && this.getClass().equals(other.getClass());
  }


  @Override public int hashCode() {
    return this.getClass().hashCode();
  }

}
