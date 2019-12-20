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

package com.google.template.soy.shared.internal;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;

/**
 * Guice module for users that want to contribute plugins to Soy via Guice.
 *
 * <p>Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public final class SharedModule extends AbstractModule {

  public SharedModule() {}

  @Override
  protected void configure() {
    // Create empty multibinders so we can inject user-supplied ones.
    Multibinder.newSetBinder(binder(), SoyFunction.class);
    Multibinder.newSetBinder(binder(), SoyPrintDirective.class);
  }

  @Override
  public boolean equals(Object other) {
    return other != null && this.getClass().equals(other.getClass());
  }

  @Override
  public int hashCode() {
    return this.getClass().hashCode();
  }
}
