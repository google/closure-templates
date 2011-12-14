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

import com.google.inject.Guice;
import com.google.inject.Inject;


/**
 * Helper class to initialize Guice for Soy users that do not use Guice.
 *
 * <p> Specifically:
 * <p> (a) If the Soy user creates a Guice injector containing the {@link SoyModule}, then when
 *     {@code SoyModule.configure()} is executed, this class's {@code markInitialized()} method
 *     will be called.
 * <p> (b) If the Soy user does not create a Guice injector (or creates one not including the
 *     {@code SoyModule}), then at the programmatic entry point to Soy (currently the
 *     constructor of {@code SoyFileSet}), this class's {@code initializeIfNecessary()} method will
 *     be called. This method creates a Guice injector containing only the {@code SoyModule},
 *     which serves to bind the default Soy plugins (e.g. basic functions).
 *
 */
class GuiceInitializer {


  /** Whether the SoyModule has been initialized. */
  private static boolean isInitialized = false;


  /** Marks that the SoyModule has been initialized. */
  @Inject
  static void markInitialized() {
    isInitialized = true;
  }


  /** Initializes the SoyModule if it has not already been initialized. */
  static void initializeIfNecessary() {
    if (!isInitialized) {
      // This injector creation has the important side effect of performing static injections.
      Guice.createInjector(new SoyModule());
      // The injector creation above should have called this class's markInitialized().
      if (!isInitialized) {
        throw new AssertionError("Injector creation failed to do static injection.");
      }
    }
  }

}
