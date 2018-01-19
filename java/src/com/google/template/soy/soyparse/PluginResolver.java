/*
 * Copyright 2017 Google Inc.
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

package com.google.template.soy.soyparse;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.shared.restricted.SoyDeprecated;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Set;

/** Encapsulates the logic for looking up plugins. */
public final class PluginResolver {
  /**
   * Returns an empty resolver. Useful for tests, or situations where it is known that no plugins
   * will be needed.
   */
  public static PluginResolver nullResolver(Mode mode, ErrorReporter reporter) {
    return new PluginResolver(
        mode,
        ImmutableMap.<String, SoyPrintDirective>of(),
        ImmutableMap.<String, SoyFunction>of(),
        reporter);
  }

  private static final SoyErrorKind UNKNOWN_PLUGIN =
      SoyErrorKind.of("Unknown {0} ''{1}''.{2}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind DEPRECATED_PLUGIN =
      SoyErrorKind.of(
          "{0} is deprecated: {1}", StyleAllowance.NO_PUNCTUATION, StyleAllowance.NO_CAPS);

  private static final SoyErrorKind INCORRECT_NUM_ARGS =
      SoyErrorKind.of("{0} called with {1} arguments (expected {2}).");

  /** Configures the behavior of the resolver when a lookup fails. */
  public enum Mode {
    /**
     * Allows unknown functions and print directives
     *
     * <p>This option is only available for the parseinfo generator and message extractor which
     * historically has not had proper build dependencies and thus often references unknown plugins.
     */
    ALLOW_UNDEFINED,
    /** This is the normal thing, it is an error for the plugin to not exist. */
    REQUIRE_DEFINITIONS,
    /**
     * This is a special case for deprecatedv1 where we allow the function to not exist and just
     * plop it into the js gencode.
     */
    ALLOW_UNDEFINED_FUNCTIONS_FOR_V1_SUPPORT;
  }

  private final Mode mode;
  private final ImmutableMap<String, ? extends SoyPrintDirective> printDirectives;
  private final ImmutableMap<String, ? extends SoyFunction> functions;
  private final ErrorReporter reporter;

  public PluginResolver(
      Mode mode,
      ImmutableMap<String, ? extends SoyPrintDirective> printDirectives,
      ImmutableMap<String, ? extends SoyFunction> functions,
      ErrorReporter reporter) {
    this.mode = checkNotNull(mode);
    this.printDirectives = checkNotNull(printDirectives);
    this.functions = checkNotNull(functions);
    this.reporter = checkNotNull(reporter);
  }

  /**
   * Returns a print directive with the given name and arity.
   *
   * <p>An error will be reported according to the current {@link Mode} and a placeholder function
   * will be returned if it cannot be found.
   */
  public SoyPrintDirective lookupPrintDirective(String name, int numArgs, SourceLocation location) {
    SoyPrintDirective soyPrintDirective = printDirectives.get(name);
    if (soyPrintDirective == null) {
      if (mode == Mode.REQUIRE_DEFINITIONS
          || mode == Mode.ALLOW_UNDEFINED_FUNCTIONS_FOR_V1_SUPPORT) {
        reporter.report(
            location,
            UNKNOWN_PLUGIN,
            "print directive",
            name,
            SoyErrors.getDidYouMeanMessage(printDirectives.keySet(), name));
      }
      soyPrintDirective = createPlaceholderPrintDirective(name, numArgs);
    }
    checkNumArgs("print directive", soyPrintDirective.getValidArgsSizes(), numArgs, location);
    warnIfDeprecated(name, soyPrintDirective, location);
    return soyPrintDirective;
  }

  /**
   * Returns a function with the given name and arity.
   *
   * <p>An error will be reported according to the current {@link Mode} and a placeholder function
   * will be returned if it cannot be found.
   */
  public SoyFunction lookupSoyFunction(String name, int numArgs, SourceLocation location) {
    SoyFunction soyFunction = functions.get(name);
    if (soyFunction == null) {
      if (mode == Mode.REQUIRE_DEFINITIONS) {
        reporter.report(
            location,
            UNKNOWN_PLUGIN,
            "function",
            name,
            SoyErrors.getDidYouMeanMessage(functions.keySet(), name));
      }
      soyFunction = createPlaceholderSoyFunction(name, numArgs);
    }
    checkNumArgs("function", soyFunction.getValidArgsSizes(), numArgs, location);
    warnIfDeprecated(name, soyFunction, location);
    return soyFunction;
  }

  private void checkNumArgs(
      String pluginKind, Set<Integer> arities, int actualNumArgs, SourceLocation location) {
    if (!arities.contains(actualNumArgs)) {
      reporter.report(
          location, INCORRECT_NUM_ARGS, pluginKind, actualNumArgs, Joiner.on(" or ").join(arities));
    }
  }

  private void warnIfDeprecated(String name, Object plugin, SourceLocation location) {
    SoyDeprecated deprecatedNotice = plugin.getClass().getAnnotation(SoyDeprecated.class);
    if (deprecatedNotice != null) {
      reporter.warn(location, DEPRECATED_PLUGIN, name, deprecatedNotice.value());
    }
  }

  private static SoyPrintDirective createPlaceholderPrintDirective(final String name, int arity) {
    final ImmutableSet<Integer> validArgSizes = ImmutableSet.of(arity);
    return new SoyPrintDirective() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Set<Integer> getValidArgsSizes() {
        return validArgSizes;
      }

      @Override
      public boolean shouldCancelAutoescape() {
        return false;
      }
    };
  }

  private static SoyFunction createPlaceholderSoyFunction(final String name, int arity) {
    final ImmutableSet<Integer> validArgSizes = ImmutableSet.of(arity);
    return new SoyFunction() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public Set<Integer> getValidArgsSizes() {
        return validArgSizes;
      }
    };
  }
}
