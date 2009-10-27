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

package com.google.template.soy.bidifunctions;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.shared.restricted.SoyFunction;


/**
 * Guice module for bidi Soy functions.
 *
 * @author Kai Huang
 */
public class BidiFunctionsModule extends AbstractModule {


  @Override public void configure() {

    Multibinder<SoyFunction> soyFunctionsSetBinder =
        Multibinder.newSetBinder(binder(), SoyFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiDirAttrFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiEndEdgeFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiGlobalDirFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiMarkAfterFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiMarkFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiStartEdgeFunction.class);
    soyFunctionsSetBinder.addBinding().to(BidiTextDirFunction.class);
  }

}
