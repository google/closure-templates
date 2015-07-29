/*
 * Copyright 2015 Google Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.template.soy.ErrorReporterImpl;
import com.google.template.soy.error.ErrorReporter;

import javax.inject.Singleton;

/**
 * Provides a default {@link ErrorReporter} binding.
 * Users who need a different error reporter (for example, tests)
 * can install a different module.
 *
 * @author brndn@google.com (Brendan Linn)
 */
public final class ErrorReporterModule extends AbstractModule {

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  ErrorReporter provideErrorReporter() {
    return new ErrorReporterImpl();
  }
}
