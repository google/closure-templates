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

package com.google.template.soy.tofu.internal;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyValue;
import com.google.template.soy.shared.internal.ModuleUtils;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyJavaFunction;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;
import com.google.template.soy.tofu.internal.BaseTofu.BaseTofuFactory;
import com.google.template.soy.tofu.restricted.SoyTofuFunction;
import com.google.template.soy.tofu.restricted.SoyTofuPrintDirective;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Guice module for the Tofu backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TofuModule extends AbstractModule {


  /**
   * Annotation for values provided by TofuModule (that need to be distinguished).
   */
  @BindingAnnotation
  @Target({FIELD, PARAMETER, METHOD})
  @Retention(RUNTIME)
  public @interface Tofu {}


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new SharedPassesModule());

    // Bindings for when explicit dependencies are required.
    bind(TofuEvalVisitorFactory.class);
    bind(TofuRenderVisitorFactory.class);

    // Bind providers of factories (created via assisted inject).
    install(new FactoryModuleBuilder().build(BaseTofuFactory.class));
  }


  /**
   * Builds and provides the map of SoyJavaFunctions (name to function).
   *
   * This actually collects all SoyFunctions that implement either SoyJavaFunction or
   * SoyTofuFunction (deprecated). The latter are adapted to the former interface.
   *
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyJavaFunction or SoyTofuFunction.
   */
  @Provides
  @Singleton
  @Tofu Map<String, SoyJavaFunction> provideSoyJavaFunctionsMap(
      Set<SoyFunction> soyFunctionsSet) {

    return ModuleUtils.buildSpecificSoyFunctionsMapWithAdaptation(
        soyFunctionsSet, SoyJavaFunction.class, SoyTofuFunction.class,
        new Function<SoyTofuFunction, SoyJavaFunction>() {
          @Override
          public SoyJavaFunction apply(SoyTofuFunction input) {
            return new SoyTofuFunctionAdapter(input);
          }
        });
  }


  /**
   * Private helper class for provideSoyJavaFunctionsMap() to adapt SoyTofuFunction to
   * SoyJavaFunction.
   */
  private static class SoyTofuFunctionAdapter implements SoyJavaFunction {

    /** The underlying SoyTofuFunction that is being adapted. */
    private final SoyTofuFunction adaptee;

    public SoyTofuFunctionAdapter(SoyTofuFunction adaptee) {
      this.adaptee = adaptee;
    }

    @Override public SoyValue computeForJava(List<SoyValue> args) {
      List<SoyData> castArgs = Lists.newArrayListWithCapacity(args.size());
      for (SoyValue arg : args) {
        castArgs.add((SoyData) arg);
      }
      return adaptee.computeForTofu(castArgs);
    }

    @Override public String getName() { return adaptee.getName(); }

    @Override public Set<Integer> getValidArgsSizes() { return adaptee.getValidArgsSizes(); }
  }


  /**
   * Builds and provides the map of SoyJavaPrintDirectives (name to directive).
   *
   * This actually collects all SoyPrintDirectives that implement either SoyJavaPrintDirective or
   * SoyTofuPrintDirective (deprecated). The latter are adapted to the former interface.
   *
   * @param soyDirectivesSet The installed set of SoyPrintDirectives (from Guice Multibinder). Each
   *     SoyPrintDirective may or may not implement SoyJavaPrintDirective or
   *     SoyTofuPrintDirective.
   */
  @Provides
  @Singleton
  @Tofu Map<String, SoyJavaPrintDirective> provideSoyJavaDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return ModuleUtils.buildSpecificSoyDirectivesMapWithAdaptation(
        soyDirectivesSet, SoyJavaPrintDirective.class, SoyTofuPrintDirective.class,
        new Function<SoyTofuPrintDirective, SoyJavaPrintDirective>() {
          @Override public SoyJavaPrintDirective apply(SoyTofuPrintDirective input) {
            return new SoyTofuPrintDirectiveAdapter(input);
          }
        });
  }


  /**
   * Private helper class for provideSoyJavaDirectivesMap() to adapt SoyTofuPrintDirective to
   * SoyJavaPrintDirective.
   */
  private static class SoyTofuPrintDirectiveAdapter implements SoyJavaPrintDirective {

    /** The underlying SoyTofuPrintDirective that is being adapted. */
    private final SoyTofuPrintDirective adaptee;

    public SoyTofuPrintDirectiveAdapter(SoyTofuPrintDirective adaptee) {
      this.adaptee = adaptee;
    }

    @Override public SoyValue applyForJava(SoyValue value, List<SoyValue> args) {
      SoyData castValue = (SoyData) value;
      List<SoyData> castArgs = Lists.newArrayListWithCapacity(args.size());
      for (SoyValue arg : args) {
        castArgs.add((SoyData) arg);
      }
      return adaptee.applyForTofu(castValue, castArgs);
    }

    @Override public String getName() { return adaptee.getName(); }

    @Override public Set<Integer> getValidArgsSizes() { return adaptee.getValidArgsSizes(); }

    @Override public boolean shouldCancelAutoescape() { return adaptee.shouldCancelAutoescape(); }
  }

}
