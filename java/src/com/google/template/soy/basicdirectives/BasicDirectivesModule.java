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

package com.google.template.soy.basicdirectives;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.shared.restricted.SoyPrintDirective;


/**
 * Guice module for basic Soy print directives.
 *
 */
public class BasicDirectivesModule extends AbstractModule {


  @Override public void configure() {

    Multibinder<SoyPrintDirective> soyDirectivesSetBinder =
        Multibinder.newSetBinder(binder(), SoyPrintDirective.class);

    // Basic escape directives.
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.EscapeCssString());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.FilterCssValue());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.EscapeHtmlRcdata());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.EscapeHtmlAttribute());
    soyDirectivesSetBinder.addBinding().toInstance(
        new BasicEscapeDirective.EscapeHtmlAttributeNospace());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.FilterHtmlAttribute());
    soyDirectivesSetBinder.addBinding().toInstance(
        new BasicEscapeDirective.FilterHtmlElementName());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.EscapeJsString());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.EscapeJsValue());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.FilterNormalizeUri());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.NormalizeUri());
    soyDirectivesSetBinder.addBinding().toInstance(new BasicEscapeDirective.EscapeUri());

    // Other directives.
    soyDirectivesSetBinder.addBinding().to(ChangeNewlineToBrDirective.class);
    soyDirectivesSetBinder.addBinding().to(InsertWordBreaksDirective.class);
    soyDirectivesSetBinder.addBinding().to(TruncateDirective.class);
  }

}
