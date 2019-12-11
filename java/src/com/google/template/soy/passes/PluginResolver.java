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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.plugin.java.internal.PluginAnalyzer;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyDeprecated;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import java.util.Map;
import java.util.Optional;
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

  private static final SoyErrorKind FUNCTION_PRINT_DIRECTIVE_COLLISION =
      SoyErrorKind.of("Plugin ''{0}'' named ''{1}'' collides with print directive ''{2}''.");

  private static final SoyErrorKind FUNCTION_NOT_CALLABLE =
      SoyErrorKind.of(
          "Function ''{0}'' cannot be called as a print directive."
          );

  /**
   * Whitelist for function name + print directive name collisions. We will not allow functions with
   * these names to be callableAsDeprecatedPrintDirective=true.
   */
  private static final ImmutableSet<String> COLLISION_WHITELIST =
      ImmutableSet.<String>builder()
          .build();

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
          Set<String> instances =
              PluginAnalyzer.analyze((SoyJavaSourceFunction) function).pluginInstanceNames();
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

    for (String pdName : soyPrintDirectives.keySet()) {
      String functionName = getFunctionNameEquivalentToPrintDirectiveName(pdName);
      if (COLLISION_WHITELIST.contains(functionName)) {
        continue;
      }
      if (functions.containsKey(functionName)) {
        reporter.report(
            SourceLocation.UNKNOWN,
            FUNCTION_PRINT_DIRECTIVE_COLLISION,
            functions.get(functionName).getClass().getName(),
            functionName,
            soyPrintDirectives.get(pdName).getClass().getName());
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
   * Returns a function equivalent to the print directive named {@code directiveName}, only if no
   * print directive of that name exists and several other conditions are met.
   */
  Optional<SoySourceFunction> getFunctionCallableAsPrintDirective(
      String directiveName, SourceLocation sourceLocation) {
    if (printDirectives.containsKey(directiveName)) {
      return Optional.empty();
    }
    String functionName = getFunctionNameEquivalentToPrintDirectiveName(directiveName);
    if (COLLISION_WHITELIST.contains(functionName)) {
      return Optional.empty();
    }
    Object function = functions.get(functionName);
    if (function == null) {
      return Optional.empty();
    }
    if (function instanceof SoySourceFunction) {
      SoyFunctionSignature signature =
          function.getClass().getAnnotation(SoyFunctionSignature.class);
      if (signature.callableAsDeprecatedPrintDirective()) {
        return Optional.of((SoySourceFunction) function);
      }
    }
    reporter.report(sourceLocation, FUNCTION_NOT_CALLABLE, functionName);
    return Optional.empty();
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
    Set<Integer> validArgsSize = getValidArgsSizes(soyFunction);
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

  private static Set<Integer> getValidArgsSizes(Object soyFunction) {
    if (soyFunction instanceof SoyFunction) {
      return ((SoyFunction) soyFunction).getValidArgsSizes();
    } else {
      SoyFunctionSignature signature =
          soyFunction.getClass().getAnnotation(SoyFunctionSignature.class);
      Preconditions.checkArgument(signature != null);
      return getValidArgsSizes(signature.value());
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

  /** Converts a | prepended print directive name to an equivalent function name. */
  static String getFunctionNameEquivalentToPrintDirectiveName(String printDirectiveName) {
    Preconditions.checkArgument(printDirectiveName.startsWith("|"));
    return printDirectiveName.substring(1);
  }
}
