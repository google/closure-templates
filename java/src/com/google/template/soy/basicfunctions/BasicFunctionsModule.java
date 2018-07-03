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

package com.google.template.soy.basicfunctions;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.shared.restricted.SoyFunction;

/**
 * Guice module for basic Soy functions.
 *
 */
public final class BasicFunctionsModule extends AbstractModule {

  @Override
  public void configure() {
    Multibinder<SoyFunction> soyFunctionsSetBinder =
        Multibinder.newSetBinder(binder(), SoyFunction.class);
    soyFunctionsSetBinder.addBinding().to(HtmlToTextFunction.class);
    soyFunctionsSetBinder.addBinding().to(KeysFunction.class);
    soyFunctionsSetBinder.addBinding().to(MapKeysFunction.class);
    soyFunctionsSetBinder.addBinding().to(LegacyObjectMapToMapFunction.class);
    soyFunctionsSetBinder.addBinding().to(MapToLegacyObjectMapFunction.class);
    soyFunctionsSetBinder.addBinding().to(StrContainsFunction.class);
    soyFunctionsSetBinder.addBinding().to(StrIndexOfFunction.class);
    soyFunctionsSetBinder.addBinding().to(StrLenFunction.class);
    soyFunctionsSetBinder.addBinding().to(StrSubFunction.class);
    soyFunctionsSetBinder.addBinding().to(StrToAsciiLowerCaseFunction.class);
    soyFunctionsSetBinder.addBinding().to(StrToAsciiUpperCaseFunction.class);
  }
}
