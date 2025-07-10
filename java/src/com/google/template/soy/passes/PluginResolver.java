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
import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.template.soy.base.SourceLocation;
import com.google.template.soy.base.internal.BaseUtils;
import com.google.template.soy.error.ErrorReporter;
import com.google.template.soy.error.SoyErrorKind;
import com.google.template.soy.error.SoyErrorKind.StyleAllowance;
import com.google.template.soy.error.SoyErrors;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.plugin.internal.SoySourceFunctionDescriptor;
import com.google.template.soy.plugin.java.internal.PluginAnalyzer;
import com.google.template.soy.plugin.java.restricted.SoyJavaSourceFunction;
import com.google.template.soy.plugin.restricted.SoySourceFunction;
import com.google.template.soy.shared.internal.BuiltinFunction;
import com.google.template.soy.shared.restricted.Signature;
import com.google.template.soy.shared.restricted.SoyDeprecated;
import com.google.template.soy.shared.restricted.SoyFieldSignature;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyFunctionSignature;
import com.google.template.soy.shared.restricted.SoyMethodSignature;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.shared.restricted.SoySourceFunctionMethod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/** Encapsulates the logic for looking up plugins. */
public final class PluginResolver {
  /**
   * Returns an empty resolver. Useful for tests, or situations where it is known that no plugins
   * will be needed.
   */
  public static PluginResolver nullResolver(Mode mode, ErrorReporter reporter) {
    return new PluginResolver(
        mode,
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of(),
        ImmutableList.of(),
        reporter);
  }

  private static final SoyErrorKind UNKNOWN_PLUGIN =
      SoyErrorKind.of("Unknown {0} ''{1}''.{2}", StyleAllowance.NO_PUNCTUATION);

  static final SoyErrorKind DEPRECATED_PLUGIN =
      SoyErrorKind.deprecation(
          "{0} is deprecated: {1}", StyleAllowance.NO_PUNCTUATION, StyleAllowance.NO_CAPS);

  private static final SoyErrorKind INCORRECT_NUM_ARGS =
      SoyErrorKind.of("{0} called with {1} arguments (expected {2}).");

  private static final SoyErrorKind PLUGIN_NAME_NOT_ALLOWED =
      SoyErrorKind.of(
          "Plugin ''{0}'' is named ''{1}'' which is not allowed "
              + "because it conflicts with Soy''s {1}() literal syntax."
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

  private static final SoyErrorKind MISSING_METHOD_SIGNATURE =
      SoyErrorKind.of(
          "Plugin class ''{0}'' has no @SoyMethodSignature annotation. "
              + "Method classes implementing SoySourceFunction must be annotated with "
              + "@SoyMethodSignature.");

  private static final SoyErrorKind DIFFERENT_METHOD_IMPLS_REGISTERED =
      SoyErrorKind.of(
          "Plugin method named ''{0}'' with base type ''{1}'' has two different implementations"
              + " registered: ''{2}'' and ''{3}''.");

  private static final SoyErrorKind DIFFERENT_FIELD_IMPLS_REGISTERED =
      SoyErrorKind.of(
          "Plugin field named ''{0}'' with base type ''{1}'' has two different implementations"
              + " registered: ''{2}'' and ''{3}''.");

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
    REQUIRE_DEFINITIONS
  }

  private final Mode mode;
  private final ImmutableMap<String, SoyPrintDirective> printDirectives;
  private final ImmutableMap<String, Object> functions;
  private final ImmutableSetMultimap<String, SoySourceFunction> methodsByName;
  private final ImmutableSetMultimap<String, SoySourceFunction> fieldsByName;
  private final ImmutableMap<SoySourceFunction, SoySourceFunctionDescriptor> functionToDesc;
  private final ErrorReporter reporter;

  public PluginResolver(
      Mode mode,
      List<SoyPrintDirective> soyPrintDirectives,
      List<SoyFunction> soyFunctions,
      List<SoySourceFunctionDescriptor> sourceFunctions,
      List<SoySourceFunction> soyMethods,
      ErrorReporter reporter) {
    this.mode = checkNotNull(mode);
    this.reporter = checkNotNull(reporter);
    // Merge the SoyFunctions & SoySourceFunctions.  While merging, confirm that we only have
    // one implementation for each plugin. They can overlap, but impl must be the same. This
    // indicates a partially migrated plugin.
    // Also confirm that each SoySourceFunction has a @SoyFunctionSignature, which is required.
    Map<String, Object> mergedFunctions =
        Maps.newLinkedHashMapWithExpectedSize(soyFunctions.size() + sourceFunctions.size());
    ImmutableMap.Builder<SoySourceFunction, SoySourceFunctionDescriptor> functionToDesc =
        ImmutableMap.builder();
    for (Object function : Iterables.concat(soyFunctions, sourceFunctions)) {
      String name;
      if (function instanceof SoySourceFunctionDescriptor) {
        SoySourceFunctionDescriptor desc = (SoySourceFunctionDescriptor) function;
        functionToDesc.put(desc.soySourceFunction(), desc);
        function = desc.soySourceFunction();
      }
      if (function instanceof SoySourceFunction) {
        SoyFunctionSignature sig = function.getClass().getAnnotation(SoyFunctionSignature.class);
        if (sig == null) {
          // Make sure a function sig exists.
          reporter.report(
              SourceLocation.UNKNOWN, MISSING_FUNCTION_SIGNATURE, function.getClass().getName());
          continue;
        }
        name = sig.name();
        if (function instanceof SoyJavaSourceFunction) {
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
      } else {
        SoyFunction legacyFunction = (SoyFunction) function;
        name = legacyFunction.getName();
      }
      Object old = mergedFunctions.put(name, function);
      if (old != null) {
        reporter.report(SourceLocation.UNKNOWN, DIFFERENT_IMPLS_REGISTERED, name, old, function);
      }
      if (BaseUtils.ILLEGAL_PLUGIN_NAMES.contains(name)) {
        reporter.report(
            SourceLocation.UNKNOWN, PLUGIN_NAME_NOT_ALLOWED, function.getClass().getName(), name);
      }
    }
    this.functions = ImmutableMap.copyOf(mergedFunctions);
    this.functionToDesc = functionToDesc.buildOrThrow();

    Map<String, SoyPrintDirective> indexedDirectives =
        Maps.newLinkedHashMapWithExpectedSize(soyPrintDirectives.size());
    for (SoyPrintDirective directive : soyPrintDirectives) {
      SoyPrintDirective old = indexedDirectives.put(directive.getName(), directive);
      if (old != null) {
        reporter.report(
            SourceLocation.UNKNOWN,
            DIFFERENT_IMPLS_REGISTERED,
            directive.getName(),
            directive,
            old);
      }
      String functionName = getFunctionNameEquivalentToPrintDirectiveName(directive.getName());
      if (BaseUtils.ILLEGAL_PLUGIN_NAMES.contains(functionName)) {
        reporter.report(
            SourceLocation.UNKNOWN,
            PLUGIN_NAME_NOT_ALLOWED,
            directive.getClass().getName(),
            functionName);
      }
      if (COLLISION_WHITELIST.contains(functionName)) {
        continue;
      }
      if (functions.containsKey(functionName)) {
        reporter.report(
            SourceLocation.UNKNOWN,
            FUNCTION_PRINT_DIRECTIVE_COLLISION,
            functions.get(functionName).getClass().getName(),
            functionName,
            directive.getClass().getName());
      }
    }
    this.printDirectives = ImmutableMap.copyOf(indexedDirectives);

    soyMethods.stream()
        .filter(SoyMethodSignature.IS_SOY_METHOD.negate())
        .forEach(
            method ->
                reporter.report(
                    SourceLocation.UNKNOWN, MISSING_METHOD_SIGNATURE, method.getClass().getName()));

    Map<String, SoySourceFunction> uniqueMethods = new HashMap<>();
    this.methodsByName =
        soyMethods.stream()
            .filter(method -> method.getClass().isAnnotationPresent(SoyMethodSignature.class))
            .filter(
                method -> {
                  SoyMethodSignature sig =
                      method.getClass().getAnnotation(SoyMethodSignature.class);
                  String key = sig.name() + "/" + sig.baseType();
                  SoySourceFunction old = uniqueMethods.put(key, method);
                  if (old != null) {
                    reporter.report(
                        SourceLocation.UNKNOWN,
                        DIFFERENT_METHOD_IMPLS_REGISTERED,
                        sig.name(),
                        sig.baseType(),
                        old.getClass().getCanonicalName(),
                        method.getClass().getCanonicalName());
                    return false;
                  }
                  return true;
                })
            .collect(
                toImmutableSetMultimap(
                    method -> method.getClass().getAnnotation(SoyMethodSignature.class).name(),
                    method -> method));

    Map<String, SoySourceFunction> uniqueFields = new HashMap<>();
    this.fieldsByName =
        soyMethods.stream()
            .filter(method -> method.getClass().isAnnotationPresent(SoyFieldSignature.class))
            .filter(
                method -> {
                  SoyFieldSignature sig = method.getClass().getAnnotation(SoyFieldSignature.class);

                  String key = sig.name() + "/" + sig.baseType();
                  SoySourceFunction old = uniqueFields.put(key, method);
                  if (old != null) {
                    reporter.report(
                        SourceLocation.UNKNOWN,
                        DIFFERENT_FIELD_IMPLS_REGISTERED,
                        sig.name(),
                        sig.baseType(),
                        old.getClass().getCanonicalName(),
                        method.getClass().getCanonicalName());
                    return false;
                  }
                  return true;
                })
            .collect(
                toImmutableSetMultimap(
                    method -> method.getClass().getAnnotation(SoyFieldSignature.class).name(),
                    method -> method));
  }

  public Mode getPluginResolutionMode() {
    return mode;
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

  public boolean containsFunctionNamed(String name) {
    return functions.containsKey(name);
  }

  /**
   * Returns a function with the given name and arity.
   *
   * <p>An error will be reported according to the current {@link Mode} and a placeholder function
   * will be returned if it cannot be found.
   */
  @Nullable
  public Object lookupSoyFunction(String name, int numArgs, SourceLocation location) {
    Object soyFunction = functions.get(name);
    if (soyFunction == null) {
      return null;
    }
    Set<Integer> validArgsSize = getValidArgsSizes(soyFunction);
    checkNumArgs("function", validArgsSize, numArgs, location);
    warnIfDeprecated(name, soyFunction, location);
    return soyFunction;
  }

  public ImmutableSet<SoySourceFunction> lookupSoyMethods(String methodName) {
    return methodsByName.get(methodName);
  }

  public ImmutableSet<String> getAllMethodNames() {
    return methodsByName.keySet();
  }

  public ImmutableSet<SoySourceFunction> lookupSoyFields(String methodName) {
    return fieldsByName.get(methodName);
  }

  public ImmutableSet<String> getAllFieldNames() {
    return fieldsByName.keySet();
  }

  public void reportUnresolved(FunctionNode fct) {
    Preconditions.checkArgument(!fct.isResolved());
    if (fct.hasStaticName()) {
      reportMissing(
          fct.getFunctionNameLocation(),
          "function",
          fct.getStaticFunctionName(),
          functions.keySet());
    } else {
      reportMissing(
          fct.getNameExpr().getSourceLocation(),
          "function",
          fct.getNameExpr().toSourceString(),
          "");
    }
    fct.setSoyFunction(ERROR_PLACEHOLDER_FUNCTION);
  }

  @Nullable
  public SoySourceFunctionDescriptor getDescriptor(Object function) {
    return functionToDesc.get(function);
  }

  private void reportMissing(
      SourceLocation location, String type, String name, Set<String> alternatives) {
    String didYouMean = SoyErrors.getDidYouMeanMessage(alternatives, name);
    reportMissing(location, type, name, didYouMean);
  }

  private void reportMissing(SourceLocation location, String type, String name, String didYouMean) {
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

  static Set<Integer> getValidArgsSizes(Signature[] signatures) {
    ImmutableSortedSet.Builder<Integer> builder = ImmutableSortedSet.naturalOrder();
    for (Signature signature : signatures) {
      // Varargs are represented as the last parameter type with a trailing "..."
      if (signature.parameterTypes().length > 0
          && signature.parameterTypes()[signature.parameterTypes().length - 1].endsWith("...")) {
        builder.add(-1);
      } else {
        builder.add(signature.parameterTypes().length);
      }
    }
    return builder.build();
  }

  private void checkNumArgs(
      String pluginKind, Set<Integer> arities, int actualNumArgs, SourceLocation location) {
    if (arities.contains(-1)) {
      return;
    }
    if (!arities.contains(actualNumArgs)) {
      reporter.report(
          location, INCORRECT_NUM_ARGS, pluginKind, actualNumArgs, Joiner.on(" or ").join(arities));
    }
  }

  private void warnIfDeprecated(String name, Object plugin, SourceLocation location) {
    warnIfDeprecated(reporter, name, plugin, location);
  }

  static void warnIfDeprecated(
      ErrorReporter reporter, String name, Object plugin, SourceLocation location) {

    if (plugin instanceof SoySourceFunctionMethod) {
      // A SoySourceFunction called as a method is deprecated if the implementation is annotated
      // with @SoyDeprecated or SoyMethodSignature#deprecatedWarning is not empty.
      SoySourceFunction function = ((SoySourceFunctionMethod) plugin).getImpl();
      if (warnIfSoyDeprecated(reporter, name, function, location)) {
        return;
      }
      SoyMethodSignature sig = function.getClass().getAnnotation(SoyMethodSignature.class);
      if (sig != null && !sig.deprecatedWarning().isEmpty()) {
        reporter.warn(location, DEPRECATED_PLUGIN, name, sig.deprecatedWarning());
      }
      return;
    } else if (plugin instanceof BuiltinFunction) {
      BuiltinFunction builtin = (BuiltinFunction) plugin;
      if (!builtin.deprecatedWarning().isEmpty()) {
        reporter.warn(location, DEPRECATED_PLUGIN, name, builtin.deprecatedWarning());
      }
    }

    // SoyMethod, SoyPrintDirective, and SoySourceFunction can all be annotated with @SoyDeprecated.
    if (warnIfSoyDeprecated(reporter, name, plugin, location)) {
      return;
    }

    if (plugin instanceof SoySourceFunction) {
      // A SoySourceFunction called as a function is also deprecated if
      // SoyFunctionSignature#deprecatedWarning is not empty.
      SoyFunctionSignature sig = plugin.getClass().getAnnotation(SoyFunctionSignature.class);
      if (sig != null && !sig.deprecatedWarning().isEmpty()) {
        reporter.warn(location, DEPRECATED_PLUGIN, name, sig.deprecatedWarning());
      }
    }
  }

  private static boolean warnIfSoyDeprecated(
      ErrorReporter reporter, String name, Object anything, SourceLocation location) {
    SoyDeprecated deprecatedNotice = anything.getClass().getAnnotation(SoyDeprecated.class);
    if (deprecatedNotice == null) {
      return false;
    }
    reporter.warn(location, DEPRECATED_PLUGIN, name, deprecatedNotice.value());
    return true;
  }

  private static SoyPrintDirective createPlaceholderPrintDirective(String name, int arity) {
    ImmutableSet<Integer> validArgSizes = ImmutableSet.of(arity);
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
    Preconditions.checkArgument(
        printDirectiveName.startsWith("|"),
        "Expected print directive name '%s' to start with '|'",
        printDirectiveName);
    return printDirectiveName.substring(1);
  }
}
