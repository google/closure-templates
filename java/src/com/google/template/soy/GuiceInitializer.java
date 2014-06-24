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

import java.util.logging.Level;
import java.util.logging.Logger;

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
 * @author Kai Huang
 */
// TODO(gboyer): This class is very unfortunate. Static injection essentially means that whichever
// Injector installed SoyModule last wins, which can cause bewildering errors.
class GuiceInitializer {
  private static final Logger LOGGER = Logger.getLogger(GuiceInitializer.class.getName());

  /** How many time this has been statically injected. */
  private static int initializationCount;

  /** Factory created from static injection. */
  @Inject
  private static SoyFileSet.SoyFileSetFactory soyFileSetFactory = null;

  /** Marks that the SoyModule has been initialized. */
  @Inject
  static synchronized void markInitialized() {
    initializationCount++;
  }

  /** Initializes the SoyModule if it has not already been initialized. */
  private static synchronized void initializeIfNecessary() {
    if (initializationCount == 0) {
      // This injector creation has the important side effect of performing static injections.
      Guice.createInjector(new SoyModule());
      // The injector creation above should have called this class's markInitialized().
      if (initializationCount == 0) {
        throw new AssertionError("Injector creation failed to do static injection.");
      }
    }
  }

  /**
   * Returns the hacky static-injected SoyFileSetFactory containing bindings from whichever
   * injector happened to install SoyModule first.
   */
  static synchronized SoyFileSet.SoyFileSetFactory getHackySoyFileSetFactory() {
    initializeIfNecessary();
    if (initializationCount > 1) {
      String message =
          "The SoyFileSet.Builder constructor is trying to guess which Injector to use, but"
          + " multiple Injectors have already installed a new SoyModule(). We will essentially"
          + " configure Soy at random, so you get an inconsistent set of plugins or Soy types."
          + " To fix, inject SoyFileSet.Builder (with SoyModule installed) instead of new'ing it.";
      LOGGER.log(Level.SEVERE, message, new IllegalStateException(message));
    }
    // NOTE(bslatkin): This warning isn't useful in the slightest bit.
    /* else {
      String message =
          "Falling back to statically-injected SoyFileSetFactory; unpredictable behavior is likely."
          + " Instead of constructing a SoyFileSet.Builder directly, either inject it using Guice"
          + " (with SoyModule installed), or call the static SoyFileSet.builder() method.";
      LOGGER.log(Level.WARNING, message);
    }
    */
    return soyFileSetFactory;
  }
}
