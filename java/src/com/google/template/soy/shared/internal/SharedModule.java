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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.coredirectives.CoreDirectivesModule;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.internal.i18n.BidiGlobalDir;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.ApiCall;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.IsUsingIjData;
import com.google.template.soy.shared.restricted.ApiCallScopeBindingAnnotations.LocaleString;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyJavaRuntimeFunction;
import com.google.template.soy.shared.restricted.SoyJavaRuntimePrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Guice module for shared classes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class SharedModule extends AbstractModule {


  /**
   * Annotation for values provided by SharedModule (that need to be distinguished).
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Shared {}


  @Override protected void configure() {

    // Install the core directives.
    install(new CoreDirectivesModule());

    // If no functions or print directives are bound, we want an empty set instead of an error.
    Multibinder.newSetBinder(binder(), SoyFunction.class);
    Multibinder.newSetBinder(binder(), SoyPrintDirective.class);

    // Create the API call scope.
    GuiceSimpleScope apiCallScope = new GuiceSimpleScope();
    bindScope(ApiCallScope.class, apiCallScope);
    // Make the API call scope instance injectable.
    bind(GuiceSimpleScope.class).annotatedWith(ApiCall.class)
        .toInstance(apiCallScope);

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(Boolean.class).annotatedWith(IsUsingIjData.class)
        .toProvider(GuiceSimpleScope.<Boolean>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(SoyMsgBundle.class)
        .toProvider(GuiceSimpleScope.<SoyMsgBundle>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(String.class).annotatedWith(LocaleString.class)
        .toProvider(GuiceSimpleScope.<String>getUnscopedProvider())
        .in(ApiCallScope.class);
    bind(BidiGlobalDir.class)
        .toProvider(GuiceSimpleScope.<BidiGlobalDir>getUnscopedProvider())
        .in(ApiCallScope.class);

    Multibinder.newSetBinder(binder(), SoyTypeProvider.class);
    bind(SoyTypeRegistry.class).in(Singleton.class);
  }


  /**
   * Builds and provides the map of all installed SoyFunctions (name to function).
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder).
   */
  @Provides
  @Singleton
  Map<String, SoyFunction> provideSoyFunctionsMap(Set<SoyFunction> soyFunctionsSet) {

    ImmutableMap.Builder<String, SoyFunction> mapBuilder = ImmutableMap.builder();
    for (SoyFunction function : soyFunctionsSet) {
      mapBuilder.put(function.getName(), function);
    }
    return mapBuilder.build();
  }


  /**
   * Builds and provides the map of all installed SoyPrintDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder).
   */
  @Provides
  @Singleton
  Map<String, SoyPrintDirective> provideSoyDirectivesMap(Set<SoyPrintDirective> soyDirectivesSet) {

    ImmutableMap.Builder<String, SoyPrintDirective> mapBuilder = ImmutableMap.builder();
    for (SoyPrintDirective directive : soyDirectivesSet) {
      mapBuilder.put(directive.getName(), directive);
    }
    return mapBuilder.build();
  }


  /**
   * Builds and provides the map of SoyJavaFunctions (name to function).
   *
   * This actually collects all SoyFunctions that implement either SoyJavaFunction or
   * SoyJavaRuntimeFunction (deprecated). The latter are adapted to the former interface.
   *
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyJavaFunction or SoyJavaRuntimeFunction.
   */
  @Provides
  @Singleton
  @Shared Map<String, SoyJavaFunction> provideSoyJavaFunctionsMap(
      Set<SoyFunction> soyFunctionsSet) {

    return ModuleUtils.buildSpecificSoyFunctionsMapWithAdaptation(
        soyFunctionsSet, SoyJavaFunction.class, SoyJavaRuntimeFunction.class,
        new Function<SoyJavaRuntimeFunction, SoyJavaFunction>() {
          @Override public SoyJavaFunction apply(SoyJavaRuntimeFunction input) {
            return new SoyJavaRuntimeFunctionAdapter(input);
          }
        });
  }


  /**
   * Private helper class for provideSoyJavaFunctionsMap() to adapt SoyJavaRuntimeFunction to
   * SoyJavaFunction.
   */
  private static class SoyJavaRuntimeFunctionAdapter implements SoyJavaFunction {

    /** The underlying SoyJavaRuntimeFunction that is being adapted. */
    private final SoyJavaRuntimeFunction adaptee;

    public SoyJavaRuntimeFunctionAdapter(SoyJavaRuntimeFunction adaptee) {
      this.adaptee = adaptee;
    }

    @Override public SoyValue computeForJava(List<SoyValue> args) {
      List<SoyData> castArgs = Lists.newArrayListWithCapacity(args.size());
      for (SoyValue arg : args) {
        castArgs.add((SoyData) arg);
      }
      return adaptee.compute(castArgs);
    }

    @Override public String getName() { return adaptee.getName(); }

    @Override public Set<Integer> getValidArgsSizes() { return adaptee.getValidArgsSizes(); }
  }


  /**
   * Builds and provides the map of SoyJavaPrintDirectives (name to directive).
   *
   * This actually collects all SoyPrintDirectives that implement either SoyJavaPrintDirective or
   * SoyJavaRuntimePrintDirective (deprecated). The latter are adapted to the former interface.
   *
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyPrintDirective may or may not implement SoyJavaPrintDirective or
   *     SoyJavaRuntimePrintDirective.
   */
  @Provides
  @Singleton
  @Shared Map<String, SoyJavaPrintDirective> provideSoyJavaDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return ModuleUtils.buildSpecificSoyDirectivesMapWithAdaptation(
        soyDirectivesSet, SoyJavaPrintDirective.class, SoyJavaRuntimePrintDirective.class,
        new Function<SoyJavaRuntimePrintDirective, SoyJavaPrintDirective>() {
          @Override public SoyJavaPrintDirective apply(SoyJavaRuntimePrintDirective input) {
            return new SoyJavaRuntimePrintDirectiveAdapter(input);
          }
        });
  }


  /**
   * Private helper class for provideSoyJavaDirectivesMap() to adapt SoyJavaRuntimePrintDirective to
   * SoyJavaPrintDirective.
   */
  private static class SoyJavaRuntimePrintDirectiveAdapter implements SoyJavaPrintDirective {

    /** The underlying SoyJavaRuntimePrintDirective that is being adapted. */
    private final SoyJavaRuntimePrintDirective adaptee;

    public SoyJavaRuntimePrintDirectiveAdapter(SoyJavaRuntimePrintDirective adaptee) {
      this.adaptee = adaptee;
    }

    @Override public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
      SoyData castValue = (SoyData) value;
      List<SoyData> castArgs = Lists.newArrayListWithCapacity(args.size());
      for (SoyValue arg : args) {
        castArgs.add((SoyData) arg);
      }
      return adaptee.apply(castValue, castArgs);
    }

    @Override public String getName() { return adaptee.getName(); }

    @Override public Set<Integer> getValidArgsSizes() { return adaptee.getValidArgsSizes(); }

    @Override public boolean shouldCancelAutoescape() { return adaptee.shouldCancelAutoescape(); }
  }


  @Override public boolean equals(Object other) {
    return other != null && this.getClass().equals(other.getClass());
  }


  @Override public int hashCode() {
    return this.getClass().hashCode();
  }

}
