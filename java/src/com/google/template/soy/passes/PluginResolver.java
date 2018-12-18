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

package com.google.template.soy.passes;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.plugin.java.internal.PluginInstanceFinder;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyDeprecated;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Map;
import java.util.Set;

/** Encapsulates the logic for looking up plugins. */
public final class PluginResolver {
  /**
   * Returns an empty resolver. Useful for tests, or situations where it is known that no plugins
   * will be needed.
   */
  public static PluginResolver nullResolver(Mode mode, ErrorReporter reporter) {
    return new PluginResolver(
        mode, ImmutableMap.of(), ImmutableMap.of(), ImmutableMap.of(), reporter);
  }

  private static final SoyErrorKind UNKNOWN_PLUGIN =
      SoyErrorKind.of("Unknown {0} ''{1}''.{2}", StyleAllowance.NO_PUNCTUATION);

  private static final SoyErrorKind DEPRECATED_PLUGIN =
      SoyErrorKind.of(
          "{0} is deprecated: {1}", StyleAllowance.NO_PUNCTUATION, StyleAllowance.NO_CAPS);

  private static final SoyErrorKind INCORRECT_NUM_ARGS =
      SoyErrorKind.of("{0} called with {1} arguments (expected {2}).");

  private static final SoyErrorKind PLUGIN_NAME_NOT_ALLOWED =
      SoyErrorKind.of(
          "Plugins named ''{0}'' are not allowed, "
              + "since they conflict with Soy''s {0}() literal syntax."
          );

  private static final SoyErrorKind DIFFERENT_IMPLS_REGISTERED =
      SoyErrorKind.of(
          "Plugin named ''{0}'' has two different implementations registered: "
              + "''{1}'' and ''{2}''.");

  private static final SoyErrorKind MISSING_FUNCTION_SIGNATURE =
      SoyErrorKind.of(
          "Plugin class ''{0}'' has no @SoyFunctionSignature annotation. "
              + "Classes implementing SoySourceFunction must be annotated with "
              + "@SoyFunctionSignature.");

  private static final SoyErrorKind MULTIPLE_PLUGIN_INSTANCES =
      SoyErrorKind.of(
          "Plugin class ''{0}'' uses callInstanceMethod for methods on multiple classes {1}. "
              + "SoyJavaSourceFunctions must only use a single class for callInstanceMethod.");

  private static final SoySourceFunction ERROR_PLACEHOLDER_FUNCTION = new SoySourceFunction() {};

  /** Configures the behavior of the resolver when a lookup fails. */
  public enum Mode {
    /**
     * Allows unknown functions and print directives
     *
     * <p>This option is only available for the parseinfo generator and message extractor which
     * historically has not had proper build dependencies and thus often references unknown plugins.
     */
    ALLOW_UNDEFINED,
    /** Same as above, but issues warnings. */
    ALLOW_UNDEFINED_AND_WARN,
    /** This is the normal thing, it is an error for the plugin to not exist. */
    REQUIRE_DEFINITIONS;
  }

  private final Mode mode;
  private final ImmutableMap<String, SoyPrintDirective> printDirectives;
  private final ImmutableMap<String, Object> functions;
  private final ErrorReporter reporter;

  public PluginResolver(
      Mode mode,
      ImmutableMap<String, SoyPrintDirective> soyPrintDirectives,
      ImmutableMap<String, SoyFunction> soyFunctions,
      ImmutableMap<String, SoySourceFunction> sourceFunctions,
      ErrorReporter reporter) {
    this.mode = checkNotNull(mode);
    this.printDirectives = checkNotNull(soyPrintDirectives);
    this.reporter = checkNotNull(reporter);
    for (String illegalName : BaseUtils.ILLEGAL_PLUGIN_NAMES) {
      if (soyFunctions.containsKey(illegalName) || sourceFunctions.containsKey(illegalName)) {
        reporter.report(SourceLocation.UNKNOWN, PLUGIN_NAME_NOT_ALLOWED, illegalName);
      }
    }
    // Merge the SoyFunctions & SoySourceFunctions.  While merging, confirm that we only have
    // one implementation for each plugin. They can overlap, but impl must be the same. This
    // indicates a partially migrated plugin.
    // Also confirm that each SoySourceFunction has a @SoyFunctionSignature, which is required.
    ImmutableMap.Builder<String, Object> mergedFunctions = ImmutableMap.builder();
    for (Map.Entry<String, SoyFunction> entry : soyFunctions.entrySet()) {
      SoySourceFunction source = sourceFunctions.get(entry.getKey());
      if (source != null) {
        if (source != entry.getValue()) {
          reporter.report(
              SourceLocation.UNKNOWN,
              DIFFERENT_IMPLS_REGISTERED,
              entry.getKey(),
              entry.getValue(),
              source);
        }
      } else {
        // We only insert non-duplicates into the merged map to avoid IllegalArugmentExceptions
        // building the map.
        mergedFunctions.put(entry.getKey(), entry.getValue());
      }
    }
    mergedFunctions.putAll(sourceFunctions);
    this.functions = mergedFunctions.build();

    // Go back over our merged functions and validate all the SoySourceFunction implementations.
    // We explicitly look *after* merging because SoySourceFunctions might be registered
    // as SoyFunctions if they also implemented other backends like SoyJsFunction.
    for (Object function : this.functions.values()) {
      if (function instanceof SoySourceFunction) {
        if (!function.getClass().isAnnotationPresent(SoyFunctionSignature.class)) {
          // Make sure a function sig exists.
          reporter.report(
              SourceLocation.UNKNOWN, MISSING_FUNCTION_SIGNATURE, function.getClass().getName());
        } else if (function instanceof SoyJavaSourceFunction) {
          // Also make sure that the applyForJavaSource impl uses a single plugin instance.
          // We don't support multiple instances.
          Set<Class<?>> instances = PluginInstanceFinder.find((SoyJavaSourceFunction) function);
          if (instances.size() > 1) {
            reporter.report(
                SourceLocation.UNKNOWN,
                MULTIPLE_PLUGIN_INSTANCES,
                function.getClass().getName(),
                instances);
          }
        }
      }
    }
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
      reportMissing(location, "print directive", name, printDirectives.keySet());
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
  public Object lookupSoyFunction(String name, int numArgs, SourceLocation location) {
    Object soyFunction = functions.get(name);
    if (soyFunction == null) {
      reportMissing(location, "function", name, functions.keySet());
      return ERROR_PLACEHOLDER_FUNCTION;
    }
    Set<Integer> validArgsSize;
    if (soyFunction instanceof SoyFunction) {
      validArgsSize = ((SoyFunction) soyFunction).getValidArgsSizes();
    } else {
      validArgsSize =
          getValidArgsSizes(
              soyFunction.getClass().getAnnotation(SoyFunctionSignature.class).value());
    }
    checkNumArgs("function", validArgsSize, numArgs, location);
    warnIfDeprecated(name, soyFunction, location);
    return soyFunction;
  }

  private void reportMissing(
      SourceLocation location, String type, String name, Set<String> alternatives) {
    String didYouMean = SoyErrors.getDidYouMeanMessage(alternatives, name);
    switch (mode) {
      case REQUIRE_DEFINITIONS:
        reporter.report(location, UNKNOWN_PLUGIN, type, name, didYouMean);
        break;
      case ALLOW_UNDEFINED_AND_WARN:
        reporter.warn(location, UNKNOWN_PLUGIN, type, name, didYouMean);
        break;
      case ALLOW_UNDEFINED:
        // do nothing :(
        break;
    }
  }

  private static Set<Integer> getValidArgsSizes(Signature[] signatures) {
    ImmutableSortedSet.Builder<Integer> builder = ImmutableSortedSet.naturalOrder();
    for (Signature signature : signatures) {
      builder.add(signature.parameterTypes().length);
    }
    return builder.build();
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
    };
  }
}
